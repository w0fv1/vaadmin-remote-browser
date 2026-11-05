package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class CdpConnection implements AutoCloseable {
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper;
    private final ExecutorService events;
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Map<String, Map<String, List<Consumer<JsonNode>>>> sessionHandlers = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<JsonNode>>> handlers = new ConcurrentHashMap<>();
    private final AtomicLong identifiers = new AtomicLong();
    private final Object writeLock = new Object();
    private final Object fragmentLock = new Object();
    private final StringBuilder fragment = new StringBuilder();
    private volatile WebSocket socket;
    private volatile boolean open = true;

    private CdpConnection(ObjectMapper mapper, int port) {
        this.mapper = mapper;
        this.events = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cdp-events-" + port);
            thread.setDaemon(true);
            return thread;
        });
    }

    static CdpConnection open(URI endpoint, ObjectMapper mapper) {
        CdpConnection connection = new CdpConnection(mapper, endpoint.getPort());
        try {
            connection.socket = CLIENT.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(endpoint, connection.new Listener())
                    .join();
        } catch (RuntimeException failure) {
            connection.events.shutdownNow();
            throw new IllegalStateException("无法连接浏览器调试端口: " + endpoint, failure);
        }
        return connection;
    }

    boolean isOpen() {
        return open;
    }

    JsonNode call(String method, ObjectNode parameters) {
        return call(null, method, parameters);
    }

    JsonNode call(String sessionId, String method, ObjectNode parameters) {
        long identifier = identifiers.incrementAndGet();
        ObjectNode message = mapper.createObjectNode();
        message.put("id", identifier);
        message.put("method", method);
        if (sessionId != null) message.put("sessionId", sessionId);
        if (parameters != null && !parameters.isEmpty()) message.set("params", parameters);
        CompletableFuture<JsonNode> response = new CompletableFuture<>();
        pending.put(identifier, response);
        try {
            write(mapper.writeValueAsString(message));
            JsonNode answer = response.get(CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            JsonNode error = answer.get("error");
            if (error != null && !error.isNull()) {
                throw new CdpException(
                        method,
                        error.path("code").asInt(-1),
                        error.path("message").asString(""));
            }
            return answer.path("result");
        } catch (TimeoutException timeout) {
            throw new IllegalStateException("CDP 调用超时: " + method, timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CDP 调用被中断: " + method, interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("CDP 调用失败: " + method, cause);
        } finally {
            pending.remove(identifier);
        }
    }

    void send(String method, ObjectNode parameters) {
        ObjectNode message = mapper.createObjectNode();
        message.put("method", method);
        if (parameters != null && !parameters.isEmpty()) message.set("params", parameters);
        write(mapper.writeValueAsString(message));
    }

    void send(String sessionId, String method, ObjectNode parameters) {
        ObjectNode message = mapper.createObjectNode();
        message.put("method", method);
        message.put("sessionId", sessionId);
        if (parameters != null && !parameters.isEmpty()) message.set("params", parameters);
        write(mapper.writeValueAsString(message));
    }

    void on(String method, Consumer<JsonNode> handler) {
        handlers.computeIfAbsent(method, key -> new CopyOnWriteArrayList<>()).add(handler);
    }

    Runnable on(String sessionId, String method, Consumer<JsonNode> handler) {
        var listeners = sessionHandlers.computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(method, key -> new CopyOnWriteArrayList<>());
        listeners.add(handler);
        return () -> listeners.remove(handler);
    }

    @Override
    public void close() {
        shutdown("CDP 连接已关闭");
    }

    private void write(String message) {
        synchronized (writeLock) {
            WebSocket current = socket;
            if (!open || current == null) throw new IllegalStateException("CDP 连接已关闭");
            current.sendText(message, true).join();
        }
    }

    private void handle(String message) {
        JsonNode node = mapper.readTree(message);
        JsonNode identifier = node.get("id");
        if (identifier != null) {
            CompletableFuture<JsonNode> response = pending.remove(identifier.asLong());
            if (response != null) response.complete(node);
            return;
        }
        String method = node.path("method").asString("");
        if (method.isEmpty()) return;
        JsonNode parameters = node.path("params");
        JsonNode session = node.get("sessionId");
        for (Consumer<JsonNode> handler : listeners(session == null ? null : session.asString(""), method)) {
            handler.accept(parameters);
        }
    }

    private List<Consumer<JsonNode>> listeners(String sessionId, String method) {
        Map<String, List<Consumer<JsonNode>>> scoped = sessionId == null
                ? handlers
                : sessionHandlers.getOrDefault(sessionId, Map.of());
        List<Consumer<JsonNode>> registered = scoped.get(method);
        return registered == null ? List.of() : registered;
    }

    private void shutdown(String reason) {
        if (!open) return;
        open = false;
        IllegalStateException failure = new IllegalStateException(reason);
        pending.values().forEach(future -> future.completeExceptionally(failure));
        pending.clear();
        WebSocket current = socket;
        if (current != null) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "")
                        .get(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException ignored) {
            }
            current.abort();
        }
        events.shutdownNow();
    }

    private final class Listener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);
            String message;
            synchronized (fragmentLock) {
                fragment.append(data);
                if (!last) return null;
                message = fragment.toString();
                fragment.setLength(0);
            }
            try {
                events.execute(() -> handle(message));
            } catch (RejectedExecutionException ignored) {
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            shutdown("CDP 连接已关闭");
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable failure) {
            shutdown("CDP 连接异常: " + failure.getMessage());
        }
    }
}
