package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Base64;
import java.util.function.Consumer;

final class BrowserTab {
    static final String CURSOR_BINDING = "__vaadminCursor";
    private static final String CURSOR_SCRIPT = """
            (() => {
                let last = '';
                let lastAt = 0;
                const report = event => {
                    const now = performance.now();
                    if (now - lastAt < 50) return;
                    lastAt = now;
                    const element = document.elementFromPoint(event.clientX, event.clientY);
                    const cursor = element ? getComputedStyle(element).cursor : '';
                    const value = cursor && cursor !== 'auto' ? cursor : 'auto';
                    if (value === last) return;
                    last = value;
                    if (typeof window.__vaadminCursor === 'function') window.__vaadminCursor(value);
                };
                document.addEventListener('mousemove', report, true);
                document.addEventListener('mousedown', report, true);
            })();
            """;
    private static final String BLOCK_MEDIA_SCRIPT = """
            (() => {
                const suspend = element => {
                    if (!element) return;
                    try { element.pause(); } catch (ignored) {}
                    try { element.muted = true; } catch (ignored) {}
                };
                const suspendAll = () => document.querySelectorAll('video,audio').forEach(suspend);
                HTMLMediaElement.prototype.play = function () {
                    suspend(this);
                    return Promise.resolve();
                };
                document.addEventListener('play', event => suspend(event.target), true);
                document.addEventListener('loadeddata', suspendAll, true);
                document.addEventListener('DOMContentLoaded', suspendAll, true);
            })();
            """;

    private final String id;
    private final String targetId;
    private final CdpSession session;
    private final CdpConnection connection;
    private final BrowserViewport viewport;
    private final ObjectMapper mapper;
    private String title = "";
    private String url = "about:blank";
    private boolean loading;
    private String error = "";
    private boolean canGoBack;
    private boolean canGoForward;
    private volatile Consumer<String> cursorHandler;

    BrowserTab(
            String id,
            String targetId,
            CdpSession session,
            CdpConnection connection,
            ObjectMapper mapper,
            BrowserMode mode,
            double deviceScaleFactor,
            BrowserMediaPolicy mediaPolicy) {
        this.id = id;
        this.targetId = targetId;
        this.session = session;
        this.connection = connection;
        this.mapper = mapper;
        this.viewport = new BrowserViewport(mode, targetId, session, connection, mapper, deviceScaleFactor);
        session.call("Page.enable", null);
        session.call("Runtime.enable", null);
        if (mode.headless()) {
            ObjectNode focus = mapper.createObjectNode();
            focus.put("enabled", true);
            session.call("Emulation.setFocusEmulationEnabled", focus);
        }
        if (mediaPolicy.blocksPlayback()) {
            ObjectNode parameters = mapper.createObjectNode();
            parameters.put("source", BLOCK_MEDIA_SCRIPT);
            session.call("Page.addScriptToEvaluateOnNewDocument", parameters);
        }
        session.on("Page.javascriptDialogOpening", event -> dismissDialog());
        trackCursor();
    }

    void onCursor(Consumer<String> handler) {
        cursorHandler = handler;
    }

    void reload() {
        session.call("Page.reload", null);
    }

    boolean canGoBack() {
        return canGoBack;
    }

    boolean canGoForward() {
        return canGoForward;
    }

    void refreshHistory() {
        JsonNode history = session.call("Page.getNavigationHistory", null);
        int current = history.path("currentIndex").asInt();
        int size = history.path("entries").size();
        canGoBack = current > 0;
        canGoForward = current >= 0 && current < size - 1;
    }

    void stepHistory(int delta) {
        JsonNode history = session.call("Page.getNavigationHistory", null);
        JsonNode entries = history.path("entries");
        int target = history.path("currentIndex").asInt() + delta;
        if (target < 0 || target >= entries.size()) return;
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("entryId", entries.get(target).path("id").asInt());
        session.call("Page.navigateToHistoryEntry", parameters);
    }

    JsonNode evaluate(String expression) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("expression", expression);
        parameters.put("returnByValue", true);
        parameters.put("awaitPromise", true);
        parameters.put("userGesture", true);
        JsonNode response = session.call("Runtime.evaluate", parameters);
        if (response.has("exceptionDetails")) {
            throw new IllegalStateException(response.path("exceptionDetails").path("exception")
                    .path("description").asString("浏览器脚本执行失败"));
        }
        return response.path("result").path("value");
    }

    private void trackCursor() {
        ObjectNode binding = mapper.createObjectNode();
        binding.put("name", CURSOR_BINDING);
        session.call("Runtime.addBinding", binding);
        ObjectNode script = mapper.createObjectNode();
        script.put("source", CURSOR_SCRIPT);
        session.call("Page.addScriptToEvaluateOnNewDocument", script);
        session.on("Runtime.bindingCalled", event -> {
            if (!CURSOR_BINDING.equals(event.path("name").asString(""))) return;
            Consumer<String> handler = cursorHandler;
            if (handler != null) handler.accept(event.path("payload").asString("auto"));
        });
    }

    String id() {
        return id;
    }

    String targetId() {
        return targetId;
    }

    String title() {
        return title;
    }

    String url() {
        return url;
    }

    boolean loading() {
        return loading;
    }

    String error() {
        return error;
    }

    void applyEnvironment(BrowserEnvironmentProfile environment) {
        BrowserPageEnvironment.apply(session, mapper, environment);
    }

    void onLoad(Runnable handler) {
        session.on("Page.loadEventFired", event -> handler.run());
    }

    boolean refresh() {
        if (loading) return false;
        String previousUrl = url;
        String previousTitle = title;
        try {
            applyTargetInfo(session.call("Target.getTargetInfo", null).path("targetInfo"));
        } catch (RuntimeException ignored) {
        }
        if (!previousUrl.equals(url)) refreshHistory();
        return !previousUrl.equals(url) || !previousTitle.equals(title);
    }

    void applyTargetInfo(JsonNode info) {
        url = info.path("url").asString(url);
        title = info.path("title").asString(title);
    }

    void startNavigation(String target) {
        url = target;
        title = "";
        loading = true;
        error = "";
    }

    void navigate(String target) {
        startNavigation(target);
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("url", target);
        session.call("Page.navigate", parameters);
    }

    void markLoaded() {
        loading = false;
        error = "";
    }

    void activate() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("targetId", targetId);
        connection.call("Target.activateTarget", parameters);
    }

    void close() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("targetId", targetId);
        connection.call("Target.closeTarget", parameters);
    }

    int viewportWidth() {
        return viewport.clientWidth();
    }

    int viewportHeight() {
        return viewport.clientHeight();
    }

    void resize(int width, int height) {
        viewport.resize(width, height);
    }

    byte[] capture(int quality) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("format", "jpeg");
        parameters.put("quality", quality);
        parameters.put("fromSurface", true);
        JsonNode result = session.call("Page.captureScreenshot", parameters);
        return Base64.getDecoder().decode(result.path("data").asString(""));
    }

    void move(double x, double y, BrowserInput input) {
        dispatchMouse("mouseMoved", x, y, BrowserMouseButton.NONE, input.buttons(), 1, input);
    }

    void press(double x, double y, BrowserInput input) {
        dispatchMouse(
                "mousePressed",
                x,
                y,
                input.button(),
                input.buttons() == 0 ? input.button().bitmask() : input.buttons(),
                input.clickCount(),
                input);
    }

    void release(double x, double y, BrowserInput input) {
        dispatchMouse("mouseReleased", x, y, input.button(), input.buttons(), input.clickCount(), input);
    }

    void click(double x, double y, BrowserInput input) {
        dispatchMouse(
                "mousePressed",
                x,
                y,
                input.button(),
                input.button().bitmask(),
                input.clickCount(),
                input);
        dispatchMouse("mouseReleased", x, y, input.button(), 0, input.clickCount(), input);
    }

    void wheel(double x, double y, double deltaX, double deltaY) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "mouseWheel");
        parameters.put("x", x);
        parameters.put("y", y);
        parameters.put("deltaX", deltaX);
        parameters.put("deltaY", deltaY);
        parameters.put("pointerType", "mouse");
        session.call("Input.dispatchMouseEvent", parameters);
    }

    void key(BrowserKeyInput input) {
        int modifiers = BrowserModifier.mask(input.modifiers());
        boolean printable = input.printable();
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", printable ? "keyDown" : "rawKeyDown");
        parameters.put("modifiers", modifiers);
        parameters.put("key", input.key());
        parameters.put("code", input.code());
        parameters.put("windowsVirtualKeyCode", input.keyCode());
        parameters.put("nativeVirtualKeyCode", input.keyCode());
        parameters.put("autoRepeat", input.repeat());
        parameters.put("isKeypad", input.keypad());
        parameters.put("location", input.location());
        if (printable) {
            parameters.put("text", input.key());
            parameters.put("unmodifiedText", input.key());
        }
        session.call("Input.dispatchKeyEvent", parameters);
        parameters.put("type", "keyUp");
        parameters.remove("text");
        parameters.remove("unmodifiedText");
        session.call("Input.dispatchKeyEvent", parameters);
    }

    void insertText(String text) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("text", text);
        session.call("Input.insertText", parameters);
    }

    private void dispatchMouse(
            String type,
            double x,
            double y,
            BrowserMouseButton button,
            int buttons,
            int clickCount,
            BrowserInput input) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", type);
        parameters.put("x", x);
        parameters.put("y", y);
        parameters.put("button", button.cdpName());
        parameters.put("buttons", buttons);
        parameters.put("clickCount", clickCount);
        parameters.put("modifiers", BrowserModifier.mask(input.modifiers()));
        parameters.put("pointerType", "mouse");
        session.call("Input.dispatchMouseEvent", parameters);
    }

    private void dismissDialog() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("accept", false);
        session.call("Page.handleJavaScriptDialog", parameters);
    }
}
