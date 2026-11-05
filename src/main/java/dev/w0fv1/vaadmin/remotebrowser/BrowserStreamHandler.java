package dev.w0fv1.vaadmin.remotebrowser;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

final class BrowserStreamHandler extends AbstractWebSocketHandler {
    private static final long TELEMETRY_INTERVAL_NANOS = 500_000_000L;
    private static final int IDLE_INTERVAL_MILLIS = 500;
    private static final int IDLE_THRESHOLD = 2;
    private static final int FRAME_MAGIC = 0x42535452;
    private static final int FRAME_HEADER_BYTES = 20;
    private static final int FRAME_CHUNK_BYTES = 16 * 1024;
    private static final String HOST_NAME = hostName();
    private final BrowserSessionManager sessions;
    private final ObjectMapper mapper;

    BrowserStreamHandler(BrowserSessionManager sessions, ObjectMapper mapper) {
        this.sessions = sessions;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession socket) {
        BrowserSession browser = resolve(socket);
        if (browser == null) return;
        Thread.startVirtualThread(() -> stream(socket, browser));
    }

    private BrowserSession resolve(WebSocketSession socket) {
        try {
            return sessions.require(sessionId(socket));
        } catch (IllegalArgumentException missing) {
            closeQuietly(socket);
            return null;
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
        BrowserSession browser = resolve(socket);
        if (browser == null) return;
        JsonNode input = mapper.readTree(message.getPayload());
        try {
            switch (input.path("type").asString()) {
                case "move" -> browser.move(BrowserInput.parse(input));
                case "down" -> browser.down(BrowserInput.parse(input));
                case "up", "cancel" -> browser.up(BrowserInput.parse(input));
                case "context" -> browser.context(BrowserInput.parse(input));
                case "wheel" -> browser.wheel(
                        input.path("x").asDouble(),
                        input.path("y").asDouble(),
                        input.path("deltaX").asDouble(),
                        input.path("deltaY").asDouble());
                case "key" -> browser.key(BrowserKeyInput.parse(input));
                case "text" -> browser.insertText(input.path("text").asString(""));
                case "clipboard.read" -> sendClipboard(socket, browser);
                case "tab.open" -> browser.openTab(input.path("url").asString("about:blank"));
                case "tab.activate" -> browser.activateTab(input.path("tabId").asString(""));
                case "tab.close" -> browser.closeTab(input.path("tabId").asString(""));
                case "tab.navigate" -> browser.navigateActive(input.path("url").asString("about:blank"));
                case "history.back" -> browser.historyBack();
                case "history.forward" -> browser.historyForward();
                case "reload" -> browser.reloadActive();
                case "viewport.resize" -> browser.resizeViewport(
                        input.path("width").asInt(BrowserSession.DEFAULT_VIEWPORT_WIDTH),
                        input.path("height").asInt(BrowserSession.DEFAULT_VIEWPORT_HEIGHT));
                case "stream.configure" -> browser.configure(
                        input.path("quality").asInt(BrowserSession.DEFAULT_QUALITY),
                        input.path("intervalMillis").asInt(BrowserSession.DEFAULT_INTERVAL_MILLIS),
                        input.path("networkEnabled").asBoolean(BrowserSession.DEFAULT_NETWORK_ENABLED),
                        input.path("bandwidthBytesPerSecond").asInt(BrowserSession.DEFAULT_BANDWIDTH_BYTES_PER_SECOND),
                        input.path("latencyMillis").asInt(BrowserSession.DEFAULT_LATENCY_MILLIS));
                default -> {
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void sendClipboard(WebSocketSession socket, BrowserSession browser) {
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "clipboard");
        message.put("text", browser.clipboardText());
        try {
            send(socket, new TextMessage(mapper.writeValueAsString(message)));
        } catch (Exception failure) {
            closeQuietly(socket);
        }
    }

    private static void send(WebSocketSession socket, WebSocketMessage<?> message) throws Exception {
        synchronized (socket) {
            socket.sendMessage(message);
        }
    }

    private static void closeQuietly(WebSocketSession socket) {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    private void stream(WebSocketSession socket, BrowserSession browser) {
        long sentVersion = -1;
        long nextTelemetry = 0;
        byte[] lastFrame = null;
        int idleFrames = 0;
        String sentCursor = "";
        StreamMetrics metrics = new StreamMetrics();
        TrafficShaper shaper = new TrafficShaper();
        try {
            while (socket.isOpen()) {
                BrowserSession.TabsState state = browser.state();
                if (state.version() != sentVersion) {
                    send(socket, new TextMessage(mapper.writeValueAsString(state)));
                    sentVersion = state.version();
                    lastFrame = null;
                    idleFrames = 0;
                }
                if (browser.canCapture()) {
                    BrowserFrame frame = browser.capture();
                    if (frame != null) {
                        if (lastFrame != null && Arrays.equals(frame.jpeg(), lastFrame)) {
                            idleFrames++;
                        } else {
                            lastFrame = frame.jpeg();
                            idleFrames = 0;
                            sendFrame(socket, frame, shaper, state, metrics);
                        }
                    }
                }
                String cursor = browser.cursor();
                if (!cursor.equals(sentCursor)) {
                    sentCursor = cursor;
                    ObjectNode message = mapper.createObjectNode();
                    message.put("type", "cursor");
                    message.put("value", cursor);
                    send(socket, new TextMessage(mapper.writeValueAsString(message)));
                }
                long now = System.nanoTime();
                if (now >= nextTelemetry) {
                    send(socket, new TextMessage(mapper.writeValueAsString(
                            telemetry(socket, browser, metrics))));
                    nextTelemetry = now + TELEMETRY_INTERVAL_NANOS;
                }
                Thread.sleep(idleFrames >= IDLE_THRESHOLD ? IDLE_INTERVAL_MILLIS : state.intervalMillis());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        } finally {
            browser.releasePressedButton();
        }
    }

    private static void sendFrame(
            WebSocketSession socket,
            BrowserFrame captured,
            TrafficShaper shaper,
            BrowserSession.TabsState state,
            StreamMetrics metrics) throws Exception {
        byte[] frame = captured.jpeg();
        int chunkCount = (frame.length + FRAME_CHUNK_BYTES - 1) / FRAME_CHUNK_BYTES;
        int frameId = metrics.nextFrameId();
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int offset = chunkIndex * FRAME_CHUNK_BYTES;
            int length = Math.min(FRAME_CHUNK_BYTES, frame.length - offset);
            byte[] message = new byte[FRAME_HEADER_BYTES + length];
            ByteBuffer header = ByteBuffer.wrap(message);
            header.putInt(FRAME_MAGIC);
            header.putInt(frameId);
            header.putShort((short) chunkIndex);
            header.putShort((short) chunkCount);
            header.putInt(captured.width());
            header.putInt(captured.height());
            System.arraycopy(frame, offset, message, FRAME_HEADER_BYTES, length);
            shaper.await(
                    message.length,
                    state.networkEnabled(),
                    state.bandwidthBytesPerSecond(),
                    state.latencyMillis());
            send(socket, new BinaryMessage(message));
            metrics.recordChunk(message.length);
        }
        metrics.recordFrame(frame.length);
    }

    private static TelemetryMessage telemetry(
            WebSocketSession socket,
            BrowserSession browser,
            StreamMetrics metrics) {
        BrowserSession.BrowserInfo browserInfo = browser.browserInfo();
        BrowserSession.TabsState state = browser.state();
        Runtime runtime = Runtime.getRuntime();
        OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        double processCpuPercent = -1;
        double systemCpuPercent = -1;
        long totalMemoryBytes = -1;
        long freeMemoryBytes = -1;
        if (operatingSystem instanceof com.sun.management.OperatingSystemMXBean extended) {
            processCpuPercent = extended.getProcessCpuLoad() * 100;
            systemCpuPercent = extended.getCpuLoad() * 100;
            totalMemoryBytes = extended.getTotalMemorySize();
            freeMemoryBytes = extended.getFreeMemorySize();
        }
        return new TelemetryMessage(
                "telemetry",
                System.currentTimeMillis(),
                sessionId(socket),
                new ConnectionInfo(
                        ip(socket.getLocalAddress()),
                        port(socket.getLocalAddress()),
                        ip(socket.getRemoteAddress()),
                        port(socket.getRemoteAddress()),
                        socket.isOpen()),
                new ServerInfo(
                        HOST_NAME,
                        System.getProperty("os.name", "unknown"),
                        System.getProperty("os.version", "unknown"),
                        System.getProperty("os.arch", "unknown"),
                        System.getProperty("java.version", "unknown"),
                        ManagementFactory.getRuntimeMXBean().getVmName(),
                        ProcessHandle.current().pid(),
                        runtime.availableProcessors(),
                        runtime.totalMemory() - runtime.freeMemory(),
                        runtime.maxMemory(),
                        processCpuPercent,
                        systemCpuPercent,
                        totalMemoryBytes,
                        freeMemoryBytes,
                        ManagementFactory.getRuntimeMXBean().getUptime()),
                browserInfo,
                metrics.info(
                        state.quality(),
                        state.intervalMillis(),
                        state.networkEnabled(),
                        state.bandwidthBytesPerSecond(),
                        state.latencyMillis()));
    }

    private static String sessionId(WebSocketSession socket) {
        String path = socket.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String ip(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) return inet.getHostString();
        return "unknown";
    }

    private static int port(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) return inet.getPort();
        return -1;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception failure) {
            return "unknown";
        }
    }

    private record TelemetryMessage(
            String type,
            long timestamp,
            String sessionId,
            ConnectionInfo connection,
            ServerInfo server,
            BrowserSession.BrowserInfo browser,
            StreamInfo stream) {
    }

    private record ConnectionInfo(
            String localIp,
            int localPort,
            String remoteIp,
            int remotePort,
            boolean open) {
    }

    private record ServerInfo(
            String hostName,
            String osName,
            String osVersion,
            String osArch,
            String javaVersion,
            String jvmName,
            long pid,
            int processors,
            long heapUsedBytes,
            long heapMaxBytes,
            double processCpuPercent,
            double systemCpuPercent,
            long totalMemoryBytes,
            long freeMemoryBytes,
            long uptimeMillis) {
    }

    private record StreamInfo(
            long framesSent,
            long bytesSent,
            long lastFrameBytes,
            double sendFps,
            double bytesPerSecond,
            int quality,
            int intervalMillis,
            boolean networkEnabled,
            int bandwidthBytesPerSecond,
            int latencyMillis) {
    }

    private static final class StreamMetrics {
        private int nextFrameId;
        private long framesSent;
        private long bytesSent;
        private long lastFrameBytes;
        private long windowStartedAt = System.nanoTime();
        private long windowFrames;
        private long windowBytes;
        private double sendFps;
        private double bytesPerSecond;

        private int nextFrameId() {
            return nextFrameId++;
        }

        private void recordChunk(int bytes) {
            bytesSent += bytes;
            windowBytes += bytes;
        }

        private void recordFrame(int bytes) {
            framesSent++;
            lastFrameBytes = bytes;
            windowFrames++;
            long now = System.nanoTime();
            long elapsed = now - windowStartedAt;
            if (elapsed >= 1_000_000_000L) {
                sendFps = windowFrames * 1_000_000_000d / elapsed;
                bytesPerSecond = windowBytes * 1_000_000_000d / elapsed;
                windowFrames = 0;
                windowBytes = 0;
                windowStartedAt = now;
            }
        }

        private StreamInfo info(
                int quality,
                int intervalMillis,
                boolean networkEnabled,
                int bandwidthBytesPerSecond,
                int latencyMillis) {
            return new StreamInfo(
                    framesSent,
                    bytesSent,
                    lastFrameBytes,
                    sendFps,
                    bytesPerSecond,
                    quality,
                    intervalMillis,
                    networkEnabled,
                    bandwidthBytesPerSecond,
                    latencyMillis);
        }
    }

    private static final class TrafficShaper {
        private long nextSendNanos;

        private void await(
                int bytes,
                boolean enabled,
                int bandwidthBytesPerSecond,
                int latencyMillis) throws InterruptedException {
            if (!enabled) {
                nextSendNanos = 0;
                return;
            }
            long now = System.nanoTime();
            if (nextSendNanos == 0) nextSendNanos = now + latencyMillis * 1_000_000L;
            long scheduled = Math.max(now, nextSendNanos);
            long waitNanos = scheduled - now;
            if (waitNanos > 0) {
                Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
            }
            long sentAt = System.nanoTime();
            long transmissionNanos = (long) Math.ceil(bytes * 1_000_000_000d / bandwidthBytesPerSecond);
            nextSendNanos = Math.max(scheduled, sentAt) + transmissionNanos;
        }
    }
}
