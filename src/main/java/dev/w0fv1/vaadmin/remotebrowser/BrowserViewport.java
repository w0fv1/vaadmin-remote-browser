package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

final class BrowserViewport {
    static final int HEADLESS_SCREEN_WIDTH = 1920;
    static final int HEADLESS_SCREEN_HEIGHT = 1080;
    private final BrowserMode mode;
    private final String targetId;
    private final CdpSession session;
    private final CdpConnection connection;
    private final ObjectMapper mapper;
    private final double deviceScaleFactor;
    private volatile int clientWidth;
    private volatile int clientHeight;
    private volatile int appliedWidth;
    private volatile int appliedHeight;
    private int lockedOuterWidth;
    private int lockedOuterHeight;

    BrowserViewport(
            BrowserMode mode,
            String targetId,
            CdpSession session,
            CdpConnection connection,
            ObjectMapper mapper,
            double deviceScaleFactor) {
        this.mode = mode;
        this.targetId = targetId;
        this.session = session;
        this.connection = connection;
        this.mapper = mapper;
        this.deviceScaleFactor = deviceScaleFactor;
    }

    void resize(int targetWidth, int targetHeight) {
        if (mode.headless()) {
            resizeHeadless(targetWidth, targetHeight);
            return;
        }
        resizeHeaded(targetWidth, targetHeight);
    }

    int clientWidth() {
        return clientWidth;
    }

    int clientHeight() {
        return clientHeight;
    }

    private void resizeHeadless(int targetWidth, int targetHeight) {
        if (appliedWidth == targetWidth && appliedHeight == targetHeight) return;
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("width", targetWidth);
        parameters.put("height", targetHeight);
        parameters.put("deviceScaleFactor", deviceScaleFactor);
        parameters.put("mobile", false);
        parameters.put("screenWidth", Math.max(targetWidth, HEADLESS_SCREEN_WIDTH));
        parameters.put("screenHeight", Math.max(targetHeight, HEADLESS_SCREEN_HEIGHT));
        parameters.put("positionX", 0);
        parameters.put("positionY", 0);
        session.call("Emulation.setDeviceMetricsOverride", parameters);
        readViewportMetrics();
        appliedWidth = targetWidth;
        appliedHeight = targetHeight;
    }

    private void resizeHeaded(int targetWidth, int targetHeight) {
        readViewportMetrics();
        JsonNode window = connection.call("Browser.getWindowForTarget", windowParameters());
        int windowId = window.get("windowId").asInt();
        JsonNode bounds = window.path("bounds");
        String windowState = "normal";
        if (bounds.has("windowState") && !bounds.get("windowState").isNull()) {
            windowState = bounds.get("windowState").asString();
        }
        int outerWidth = bounds.get("width").asInt();
        int outerHeight = bounds.get("height").asInt();
        boolean viewportChanged = appliedWidth != targetWidth || appliedHeight != targetHeight;
        boolean windowDrifted = !"normal".equals(windowState)
                || (lockedOuterWidth > 0 && Math.abs(outerWidth - lockedOuterWidth) > 2)
                || (lockedOuterHeight > 0 && Math.abs(outerHeight - lockedOuterHeight) > 2);
        if (!viewportChanged && !windowDrifted) return;

        if (!"normal".equals(windowState)) {
            ObjectNode normalBounds = mapper.createObjectNode();
            normalBounds.put("windowState", "normal");
            setWindowBounds(windowId, normalBounds);
            bounds = connection.call("Browser.getWindowForTarget", windowParameters()).path("bounds");
            outerWidth = bounds.get("width").asInt();
            outerHeight = bounds.get("height").asInt();
            readViewportMetrics();
        }

        int insetWidth = 16;
        if (clientWidth > 0) insetWidth = Math.max(0, outerWidth - clientWidth);
        int insetHeight = 88;
        if (clientHeight > 0) insetHeight = Math.max(0, outerHeight - clientHeight);
        ObjectNode targetBounds = mapper.createObjectNode();
        targetBounds.put("windowState", "normal");
        targetBounds.put("width", targetWidth + insetWidth);
        targetBounds.put("height", targetHeight + insetHeight);
        setWindowBounds(windowId, targetBounds);
        readViewportMetrics();
        if (clientWidth != targetWidth || clientHeight != targetHeight) {
            targetBounds.put("width", targetBounds.get("width").asInt() + targetWidth - clientWidth);
            targetBounds.put("height", targetBounds.get("height").asInt() + targetHeight - clientHeight);
            setWindowBounds(windowId, targetBounds);
        }

        JsonNode applied = connection.call("Browser.getWindowForTarget", windowParameters()).path("bounds");
        lockedOuterWidth = applied.get("width").asInt();
        lockedOuterHeight = applied.get("height").asInt();
        appliedWidth = targetWidth;
        appliedHeight = targetHeight;
        readViewportMetrics();
    }

    private void readViewportMetrics() {
        JsonNode viewport = session.call("Page.getLayoutMetrics", null).path("cssVisualViewport");
        int width = viewport.get("clientWidth").asInt();
        int height = viewport.get("clientHeight").asInt();
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("浏览器视口尺寸无效");
        }
        clientWidth = width;
        clientHeight = height;
    }

    private ObjectNode windowParameters() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("targetId", targetId);
        return parameters;
    }

    private void setWindowBounds(int windowId, ObjectNode bounds) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("windowId", windowId);
        parameters.set("bounds", bounds);
        connection.call("Browser.setWindowBounds", parameters);
    }
}
