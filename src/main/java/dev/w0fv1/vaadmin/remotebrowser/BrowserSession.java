package dev.w0fv1.vaadmin.remotebrowser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class BrowserSession {
    static final int DEFAULT_VIEWPORT_WIDTH = 1280;
    static final int DEFAULT_VIEWPORT_HEIGHT = 720;
    static final int DEFAULT_QUALITY = 75;
    static final int DEFAULT_INTERVAL_MILLIS = 40;
    static final boolean DEFAULT_NETWORK_ENABLED = false;
    static final int DEFAULT_BANDWIDTH_BYTES_PER_SECOND = 500 * 1024;
    static final int DEFAULT_LATENCY_MILLIS = 0;

    private static final int MIN_VIEWPORT_WIDTH = 320;
    private static final int MAX_VIEWPORT_WIDTH = 3840;
    private static final int MIN_VIEWPORT_HEIGHT = 240;
    private static final int MAX_VIEWPORT_HEIGHT = 2160;
    private static final int MAX_PENDING_INPUT_OPERATIONS = 128;
    private static final int MIN_BANDWIDTH_BYTES_PER_SECOND = 32 * 1024;
    private static final int MAX_BANDWIDTH_BYTES_PER_SECOND = 20 * 1024 * 1024;
    private static final int MIN_LATENCY_MILLIS = 0;
    private static final int MAX_LATENCY_MILLIS = 1000;
    private static final int MIN_QUALITY = 20;
    private static final int MAX_QUALITY = 90;
    private static final int MIN_INTERVAL_MILLIS = 40;
    private static final int MAX_INTERVAL_MILLIS = 2000;
    private static final int MAX_TEXT_LENGTH = 256;
    private static final long STATE_REFRESH_NANOS = 250_000_000L;
    private static final Logger log = LoggerFactory.getLogger(BrowserSession.class);

    private final String id;
    private final String profileId;
    private final ChromeBrowser browser;
    private final ObjectMapper mapper;
    private final BrowserMode mode;
    private final BrowserChannel channel;
    private final BrowserMediaPolicy mediaPolicy;
    private final BrowserProfileLease profileLease;
    private final ThreadPoolExecutor inputLane;
    private final ExecutorService captureLane;
    private final ExecutorService navigationLane;
    private final AtomicBoolean capturePending = new AtomicBoolean();
    private final AtomicReference<BrowserInput> pendingMove = new AtomicReference<>();
    private final AtomicBoolean moveScheduled = new AtomicBoolean();
    private final Object resourceLock = new Object();
    private final Object lifecycleLock = new Object();
    private final Map<String, BrowserTab> tabs = new LinkedHashMap<>();
    private final Map<String, BrowserTab> tabsByTarget = new LinkedHashMap<>();
    private final AtomicLong version = new AtomicLong();
    private final AtomicLong lastReceivedInputSequence = new AtomicLong(-1);
    private final AtomicLong lastAppliedInputSequence = new AtomicLong(-1);
    private final AtomicLong rejectedInputOperations = new AtomicLong();
    private final AtomicReference<BrowserFailure> lastFailure = new AtomicReference<>();
    private volatile boolean closing;
    private volatile long lastStateRefresh;
    private Future<?> closeFuture;
    private volatile String activeTabId;
    private volatile List<TabState> tabsSnapshot = List.of();
    private volatile BrowserMouseButton pressedButton;
    private volatile String pressedTabId;
    private volatile int viewportWidth = DEFAULT_VIEWPORT_WIDTH;
    private volatile int viewportHeight = DEFAULT_VIEWPORT_HEIGHT;
    private volatile int quality = DEFAULT_QUALITY;
    private volatile int intervalMillis = DEFAULT_INTERVAL_MILLIS;
    private volatile boolean networkEnabled = DEFAULT_NETWORK_ENABLED;
    private volatile int bandwidthBytesPerSecond = DEFAULT_BANDWIDTH_BYTES_PER_SECOND;
    private volatile int latencyMillis = DEFAULT_LATENCY_MILLIS;
    private volatile String cursor = "auto";

    BrowserSession(
            String id,
            ChromeBrowser browser,
            ObjectMapper mapper,
            BrowserMode mode,
            BrowserChannel channel,
            BrowserMediaPolicy mediaPolicy,
            BrowserProfileLease profileLease) {
        this.id = id;
        this.profileId = profileLease.profileId();
        this.browser = browser;
        this.mapper = mapper;
        this.mode = mode;
        this.channel = channel;
        this.mediaPolicy = mediaPolicy;
        this.profileLease = profileLease;
        this.inputLane = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_INPUT_OPERATIONS),
                runnable -> daemon(runnable, "browser-input-" + id),
                new ThreadPoolExecutor.AbortPolicy());
        this.captureLane = Executors.newSingleThreadExecutor(
                runnable -> daemon(runnable, "browser-capture-" + id));
        this.navigationLane = Executors.newSingleThreadExecutor(
                runnable -> daemon(runnable, "browser-navigation-" + id));
    }

    public String id() {
        return id;
    }

    void initialize(String url) {
        String target = normalizeUrl(url);
        submit(() -> {
            browser.connection().on("Target.targetCreated", event -> {
                JsonNode info = event.path("targetInfo");
                if (!"page".equals(info.path("type").asString(""))) return;
                discoverTarget(info.path("targetId").asString(""));
            });
            browser.connection().on("Target.targetDestroyed", event -> {
                String targetId = event.path("targetId").asString("");
                execute(() -> removeClosedTarget(targetId));
            });
            ObjectNode discover = mapper.createObjectNode();
            discover.put("discover", true);
            browser.call("Target.setDiscoverTargets", discover);
            List<String> targets = pageTargets();
            for (int index = 1; index < targets.size(); index++) closeTarget(targets.get(index));
            if (targets.isEmpty()) {
                createTab(target);
                return null;
            }
            BrowserTab tab = register(targets.getFirst());
            activeTabId = tab.id();
            tab.activate();
            tab.resize(viewportWidth, viewportHeight);
            tab.navigate(target);
            publish();
            return null;
        });
    }

    public TabsState state() {
        return new TabsState(
                version.get(),
                activeTabId,
                tabsSnapshot,
                quality,
                intervalMillis,
                networkEnabled,
                bandwidthBytesPerSecond,
                latencyMillis,
                activeViewportWidth(),
                activeViewportHeight());
    }

    private int activeViewportWidth() {
        BrowserTab tab = tabs.get(activeTabId);
        int width = tab == null ? 0 : tab.viewportWidth();
        return width > 0 ? width : viewportWidth;
    }

    private int activeViewportHeight() {
        BrowserTab tab = tabs.get(activeTabId);
        int height = tab == null ? 0 : tab.viewportHeight();
        return height > 0 ? height : viewportHeight;
    }

    public BrowserInfo browserInfo() {
        TabsState state = state();
        TabState active = state.tabs().stream()
                .filter(tab -> tab.id().equals(state.activeTabId()))
                .findFirst()
                .orElse(null);
        String activeTitle = "";
        String activeUrl = "";
        if (active != null) {
            activeTitle = active.title();
            activeUrl = active.url();
        }
        return new BrowserInfo(
                channel.displayName(),
                browser.product(),
                browser.protocolVersion(),
                profileId,
                profileLease.environment().mode(),
                state.tabs().size(),
                activeTitle,
                activeUrl,
                contextOpen(),
                mode.headless(),
                new InputInfo(
                        lastReceivedInputSequence.get(),
                        lastAppliedInputSequence.get(),
                        inputLane.getQueue().size(),
                        rejectedInputOperations.get()),
                lastFailure.get());
    }

    public boolean canCapture() {
        return !closing && !captureLane.isShutdown() && !capturePending.get();
    }

    boolean isAvailable() {
        return !closing && !inputLane.isShutdown() && browser.isOpen();
    }

    public String cursor() {
        return cursor;
    }

    public String clipboardText() {
        BrowserTab tab = tabs.get(activeTabId);
        if (tab == null) return "";
        return tab.evaluate("navigator.clipboard.readText().catch(() => '')").asString("");
    }

    BrowserResource loadResource(String url, String contentTypePrefix) {
        synchronized (resourceLock) {
            BrowserTab tab = submit(() -> tabs.get(activeTabId));
            if (tab == null) { throw new IllegalStateException("浏览器没有活动标签页"); }
            return tab.loadResource(url, contentTypePrefix);
        }
    }

    JsonNode evaluate(String expression) {
        BrowserTab tab = submit(() -> tabs.get(activeTabId));
        if (tab == null) throw new IllegalStateException("浏览器没有活动标签页");
        return tab.evaluate(expression);
    }

    public BrowserFrame capture() {
        if (!canCapture() || !capturePending.compareAndSet(false, true)) return null;
        Future<BrowserFrame> pending;
        try {
            pending = captureLane.submit(() -> {
                try {
                    BrowserTab tab = tabs.get(activeTabId);
                    if (tab == null) return null;
                    BrowserFrame frame = tab.capture(quality, viewportWidth, viewportHeight);
                    refreshActiveState(tab);
                    return frame;
                } finally {
                    capturePending.set(false);
                }
            });
        } catch (RejectedExecutionException failure) {
            capturePending.set(false);
            return null;
        }
        try {
            return pending.get();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (!closing) recordFailure("capture", cause == null ? failure : cause);
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void resizeViewport(int requestedWidth, int requestedHeight) {
        int nextWidth = Math.min(MAX_VIEWPORT_WIDTH, Math.max(MIN_VIEWPORT_WIDTH, requestedWidth));
        int nextHeight = Math.min(MAX_VIEWPORT_HEIGHT, Math.max(MIN_VIEWPORT_HEIGHT, requestedHeight));
        if (nextWidth == viewportWidth && nextHeight == viewportHeight) return;
        viewportWidth = nextWidth;
        viewportHeight = nextHeight;
        String tabId = activeTabId;
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab != null) tab.resize(viewportWidth, viewportHeight);
            publish();
        });
    }

    public void openTab(String url) {
        execute(() -> createTab(normalizeUrl(url)));
    }

    public void activateTab(String tabId) {
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            activeTabId = tab.id();
            tab.activate();
            tab.resize(viewportWidth, viewportHeight);
            publish();
        });
    }

    public void closeTab(String tabId) {
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            if (tabs.size() == 1) {
                tab.startNavigation("about:blank");
                tab.navigate("about:blank");
                publish();
                return;
            }
            closeTarget(tab.targetId());
        });
    }

    public void navigateActive(String url) {
        String tabId = activeTabId;
        String target = normalizeUrl(url);
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            tab.startNavigation(target);
            publish();
            navigateLater(() -> tab.navigate(target));
        });
    }

    public void historyBack() {
        stepHistory(-1);
    }

    public void historyForward() {
        stepHistory(1);
    }

    public void reloadActive() {
        String tabId = activeTabId;
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab != null) navigateLater(tab::reload);
        });
    }

    private void stepHistory(int delta) {
        String tabId = activeTabId;
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            navigateLater(() -> {
                tab.stepHistory(delta);
                publish();
            });
        });
    }

    private void navigateLater(Runnable action) {
        try {
            navigationLane.submit(() -> {
                try {
                    action.run();
                } catch (RuntimeException failure) {
                    recordFailure("navigate", failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            recordFailure("navigate", failure);
        }
    }

    public void configure(
            int requestedQuality,
            int requestedIntervalMillis,
            boolean requestedNetworkEnabled,
            int requestedBandwidthBytesPerSecond,
            int requestedLatencyMillis) {
        execute(() -> {
            quality = Math.min(MAX_QUALITY, Math.max(MIN_QUALITY, requestedQuality));
            intervalMillis = Math.min(MAX_INTERVAL_MILLIS, Math.max(MIN_INTERVAL_MILLIS, requestedIntervalMillis));
            networkEnabled = requestedNetworkEnabled;
            bandwidthBytesPerSecond = Math.min(
                    MAX_BANDWIDTH_BYTES_PER_SECOND,
                    Math.max(MIN_BANDWIDTH_BYTES_PER_SECOND, requestedBandwidthBytesPerSecond));
            latencyMillis = Math.min(MAX_LATENCY_MILLIS, Math.max(MIN_LATENCY_MILLIS, requestedLatencyMillis));
            publish();
        });
    }

    public void move(BrowserInput input) {
        lastReceivedInputSequence.accumulateAndGet(input.sequence(), Math::max);
        pendingMove.set(input);
        scheduleMove();
    }

    public void down(BrowserInput input) {
        String tabId = activeTabId;
        executeInput(input, () -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            tab.move(input.x(), input.y(), input);
            tab.press(input.x(), input.y(), input);
            pressedButton = input.button();
            pressedTabId = tab.id();
        });
    }

    public void up(BrowserInput input) {
        String tabId = activeTabId;
        executeInput(input, () -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            tab.move(input.x(), input.y(), input);
            tab.release(input.x(), input.y(), input);
            pressedButton = null;
            pressedTabId = null;
        });
    }

    public void context(BrowserInput input) {
        String tabId = activeTabId;
        executeInput(input, () -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab != null) tab.click(input.x(), input.y(), input);
        });
    }

    public void wheel(double x, double y, double deltaX, double deltaY) {
        String tabId = activeTabId;
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            tab.wheel(x, y, deltaX, deltaY);
        });
    }

    public void key(BrowserKeyInput input) {
        String tabId = activeTabId;
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab != null) tab.key(input);
        });
    }

    public void insertText(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_TEXT_LENGTH) return;
        String tabId = activeTabId;
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab != null) tab.insertText(text);
        });
    }

    public void releasePressedButton() {
        BrowserMouseButton button = pressedButton;
        String tabId = pressedTabId;
        pressedButton = null;
        pressedTabId = null;
        if (button == null || tabId == null) return;
        execute(() -> {
            BrowserTab tab = tabs.get(tabId);
            if (tab == null) return;
            BrowserInput input = new BrowserInput("up", 0, 0, 0, button, 0, 1, Set.of());
            tab.release(0, 0, input);
        });
    }

    public void close() {
        Future<?> pendingClose;
        synchronized (lifecycleLock) {
            if (!closing) {
                closing = true;
                try {
                    pendingClose = inputLane.submit(() -> {
                        browser.close();
                        return null;
                    });
                } catch (RejectedExecutionException failure) {
                    pendingClose = null;
                    recordFailure("close", failure);
                }
                closeFuture = pendingClose;
                inputLane.shutdown();
            } else {
                pendingClose = closeFuture;
            }
        }
        boolean interrupted = Thread.interrupted();
        try {
            boolean closed = false;
            if (pendingClose != null) {
                while (!closed) {
                    try {
                        pendingClose.get();
                        closed = true;
                    } catch (InterruptedException failure) {
                        interrupted = true;
                    } catch (ExecutionException failure) {
                        Throwable cause = failure.getCause();
                        if (cause != null) recordFailure("close", cause);
                        break;
                    }
                }
            }
            if (!closed) {
                try { browser.close(); }
                catch (RuntimeException failure) { recordFailure("close", failure); }
            }
            captureLane.shutdownNow();
            navigationLane.shutdownNow();
            profileLease.close();
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void scheduleMove() {
        if (!moveScheduled.compareAndSet(false, true)) return;
        if (!enqueueInput(this::applyPendingMove)) {
            moveScheduled.set(false);
        }
    }

    private void applyPendingMove() {
        BrowserInput latest = pendingMove.getAndSet(null);
        if (latest != null) {
            BrowserTab tab = tabs.get(activeTabId);
            if (tab != null) {
                try {
                    tab.move(latest.x(), latest.y(), latest);
                } catch (RuntimeException failure) {
                    recordFailure("input", failure);
                }
            }
            lastAppliedInputSequence.accumulateAndGet(latest.sequence(), Math::max);
        }
        moveScheduled.set(false);
        if (pendingMove.get() != null) scheduleMove();
    }

    private BrowserTab createTab(String url) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("url", "about:blank");
        parameters.put("newWindow", false);
        parameters.put("background", false);
        String targetId = browser.call("Target.createTarget", parameters).path("targetId").asString("");
        BrowserTab tab = register(targetId);
        activeTabId = tab.id();
        tab.activate();
        tab.resize(viewportWidth, viewportHeight);
        publish();
        tab.navigate(url);
        publish();
        return tab;
    }

    private BrowserTab register(String targetId) {
        BrowserTab existing = tabsByTarget.get(targetId);
        if (existing != null) return existing;
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("targetId", targetId);
        parameters.put("flatten", true);
        String sessionId = browser.call("Target.attachToTarget", parameters).path("sessionId").asString("");
        if (sessionId.isEmpty()) throw new IllegalStateException("无法附加浏览器标签页: " + targetId);
        BrowserEnvironmentProfile environment = profileLease.environment();
        CdpSession session = new CdpSession(browser.connection(), sessionId);
        BrowserTab tab = new BrowserTab(
                UUID.randomUUID().toString(),
                targetId,
                session,
                browser.connection(),
                mapper,
                mode,
                environment.deviceScaleFactor(),
                mediaPolicy);
        tab.onCursor(value -> cursor = value);
        tab.applyEnvironment(environment);
        tab.applyTargetInfo(session.call("Target.getTargetInfo", null).path("targetInfo"));
        tab.onLoad(() -> execute(() -> {
            if (tabs.get(tab.id()) != tab) return;
            tab.markLoaded();
            tab.refresh();
            tab.refreshHistory();
            publish();
        }));
        tabs.put(tab.id(), tab);
        tabsByTarget.put(targetId, tab);
        publish();
        return tab;
    }

    private void refreshActiveState(BrowserTab active) {
        long now = System.nanoTime();
        if (now - lastStateRefresh < STATE_REFRESH_NANOS) return;
        lastStateRefresh = now;
        if (active.refresh()) publish();
    }

    private void discoverTarget(String targetId) {
        execute(() -> {
            boolean known = tabsByTarget.containsKey(targetId);
            BrowserTab tab;
            try {
                tab = register(targetId);
            } catch (RuntimeException failure) {
                recordFailure("attach", failure);
                return;
            }
            if (known) return;
            activeTabId = tab.id();
            tab.activate();
            tab.resize(viewportWidth, viewportHeight);
            publish();
        });
    }

    private void removeClosedTarget(String targetId) {
        BrowserTab tab = tabsByTarget.remove(targetId);
        if (tab == null) return;
        tabs.remove(tab.id());
        if (tab.id().equals(activeTabId)) {
            BrowserTab next = tabs.values().stream().findFirst().orElse(null);
            activeTabId = null;
            if (next != null) {
                activeTabId = next.id();
                next.activate();
                next.resize(viewportWidth, viewportHeight);
            }
        }
        if (tab.id().equals(pressedTabId)) {
            pressedButton = null;
            pressedTabId = null;
        }
        if (tabs.isEmpty() && browser.isOpen()) createTab("about:blank");
        publish();
    }

    private void closeTarget(String targetId) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("targetId", targetId);
        browser.call("Target.closeTarget", parameters);
        removeClosedTarget(targetId);
    }

    private List<String> pageTargets() {
        List<String> targets = new ArrayList<>();
        for (JsonNode target : browser.call("Target.getTargets", null).path("targetInfos")) {
            if (!"page".equals(target.path("type").asString(""))) continue;
            targets.add(target.path("targetId").asString(""));
        }
        return targets;
    }

    private void publish() {
        List<TabState> snapshot = new ArrayList<>(tabs.size());
        for (BrowserTab tab : tabs.values()) {
            snapshot.add(new TabState(
                    tab.id(),
                    tab.title(),
                    tab.url(),
                    tab.loading(),
                    tab.error(),
                    tab.canGoBack(),
                    tab.canGoForward()));
        }
        tabsSnapshot = List.copyOf(snapshot);
        version.incrementAndGet();
    }

    private void executeInput(BrowserInput input, Runnable action) {
        lastReceivedInputSequence.accumulateAndGet(input.sequence(), Math::max);
        boolean queued = enqueueInput(() -> {
            try {
                action.run();
                lastAppliedInputSequence.accumulateAndGet(input.sequence(), Math::max);
            } catch (RuntimeException failure) {
                recordFailure("input", failure);
            }
        });
        if (!queued) rejectedInputOperations.incrementAndGet();
    }

    private boolean enqueueInput(Runnable command) {
        synchronized (lifecycleLock) {
            if (closing || inputLane.isShutdown()) return false;
            try {
                inputLane.execute(command);
                return true;
            } catch (RejectedExecutionException ignored) {
            }
            try {
                return inputLane.getQueue().offer(command, 50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private void execute(Runnable action) {
        enqueueInput(() -> {
            try {
                action.run();
            } catch (RuntimeException failure) {
                recordFailure("session", failure);
            }
        });
    }

    private <T> T submit(Callable<T> action) {
        Future<T> future;
        synchronized (lifecycleLock) {
            if (closing || inputLane.isShutdown()) {
                throw new IllegalStateException("浏览器会话已关闭");
            }
            try {
                future = inputLane.submit(action);
            } catch (RejectedExecutionException exception) {
                throw new IllegalStateException("浏览器会话已关闭", exception);
            }
        }
        try {
            return future.get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException(cause);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void recordFailure(String operation, Throwable failure) {
        String message = failure.getClass().getSimpleName();
        if (failure.getMessage() != null) {
            message = failure.getMessage();
        }
        BrowserFailure current = new BrowserFailure(operation, message, System.currentTimeMillis());
        BrowserFailure previous = lastFailure.getAndSet(current);
        boolean changed = previous == null
                || !previous.operation().equals(current.operation())
                || !previous.message().equals(current.message());
        if (changed) {
            log.warn("浏览器{}操作失败: {}", operation, message);
        }
    }

    private boolean contextOpen() {
        if (closing || inputLane.isShutdown()) return false;
        return browser.isOpen();
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static String normalizeUrl(String url) {
        String value = "";
        if (url != null) {
            value = url.trim();
        }
        if (value.isEmpty()) return "about:blank";
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$")) return value;
        return "https://" + value;
    }

    public record TabState(String id, String title, String url, boolean loading, String error, boolean canGoBack, boolean canGoForward) {
    }

    public record TabsState(
            long version,
            String activeTabId,
            List<TabState> tabs,
            int quality,
            int intervalMillis,
            boolean networkEnabled,
            int bandwidthBytesPerSecond,
            int latencyMillis,
            int viewportWidth,
            int viewportHeight) {
    }

    public record BrowserInfo(
            String engine,
            String browserVersion,
            String protocolVersion,
            String profileId,
            BrowserEnvironmentMode environmentMode,
            int tabCount,
            String activeTitle,
            String activeUrl,
            boolean contextOpen,
            boolean headless,
            InputInfo input,
            BrowserFailure lastFailure) {
    }

    public record InputInfo(
            long lastReceivedSequence,
            long lastAppliedSequence,
            int pendingOperations,
            long rejectedOperations) {
    }

    public record BrowserFailure(String operation, String message, long timestamp) {
    }
}
