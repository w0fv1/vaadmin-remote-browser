package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;

final class CdpTestPage implements AutoCloseable {
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(20);
    private static final long POLL_MILLIS = 50;

    private final ObjectMapper mapper;
    private final ChromeBrowser browser;
    private final String targetId;
    private final CdpSession session;

    private CdpTestPage(ObjectMapper mapper, ChromeBrowser browser, String targetId, CdpSession session) {
        this.mapper = mapper;
        this.browser = browser;
        this.targetId = targetId;
        this.session = session;
    }

    static CdpTestPage open(
            ChromeBrowser browser,
            String url,
            BrowserEnvironmentProfile environment) {
        return open(browser, url, environment, null);
    }

    static CdpTestPage open(
            ChromeBrowser browser,
            String url,
            BrowserEnvironmentProfile environment,
            BrowserUserAgent userAgent) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode create = mapper.createObjectNode();
        create.put("url", "about:blank");
        String targetId = browser.call("Target.createTarget", create).path("targetId").asString("");
        ObjectNode attach = mapper.createObjectNode();
        attach.put("targetId", targetId);
        attach.put("flatten", true);
        String sessionId = browser.call("Target.attachToTarget", attach).path("sessionId").asString("");
        CdpSession session = new CdpSession(browser.connection(), sessionId);
        BrowserPageEnvironment.apply(session, mapper, environment, userAgent);
        ObjectNode metrics = mapper.createObjectNode();
        metrics.put("width", BrowserSession.DEFAULT_VIEWPORT_WIDTH);
        metrics.put("height", BrowserSession.DEFAULT_VIEWPORT_HEIGHT);
        metrics.put("deviceScaleFactor", environment.deviceScaleFactor() > 0 ? environment.deviceScaleFactor() : 1);
        metrics.put("mobile", false);
        metrics.put("screenWidth", BrowserViewport.HEADLESS_SCREEN_WIDTH);
        metrics.put("screenHeight", BrowserViewport.HEADLESS_SCREEN_HEIGHT);
        session.call("Emulation.setDeviceMetricsOverride", metrics);
        ObjectNode navigate = mapper.createObjectNode();
        navigate.put("url", url);
        session.call("Page.navigate", navigate);
        CdpTestPage page = new CdpTestPage(mapper, browser, targetId, session);
        page.awaitReady(url);
        return page;
    }

    JsonNode evaluate(String expression) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("expression", expression);
        parameters.put("returnByValue", true);
        parameters.put("awaitPromise", true);
        return session.call("Runtime.evaluate", parameters).path("result").path("value");
    }

    boolean absentGlobal(String name) {
        return evaluate("typeof window[" + mapper.writeValueAsString(name) + "] === 'undefined'").asBoolean();
    }

    @Override
    public void close() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("targetId", targetId);
        browser.call("Target.closeTarget", parameters);
    }

    private void awaitReady(String expectedUrl) {
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                String state = evaluate("document.readyState + '|' + location.href").asString("");
                if (state.startsWith("complete|") && state.contains(expectedUrl)) return;
            } catch (RuntimeException ignored) {
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
        throw new IllegalStateException("页面未就绪: " + expectedUrl);
    }
}
