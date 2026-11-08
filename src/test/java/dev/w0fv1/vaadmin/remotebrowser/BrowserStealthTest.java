package dev.w0fv1.vaadmin.remotebrowser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
final class BrowserStealthTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> AUTOMATION_GLOBALS = List.of(
            "__playwright",
            "__pw_manual",
            "__PW_inspect",
            "__puppeteer_evaluation_script__",
            "__puppeteer_utility_world__",
            "__nightmare",
            "_phantom",
            "callPhantom",
            "__webdriver_evaluate",
            "__driver_evaluate",
            "__selenium_unwrapped",
            "__fxdriver_unwrapped",
            "__webdriver_script_fn",
            "_Selenium_IDE_Recorder");

    private final Path profileRoot = TestProfileRoot.create("stealth");

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serveFixture);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopFixture() {
        if (server != null) server.stop(0);
    }

    @Test
    void headlessKeepsNativeAutomationSurface() {
        verifyNativeSurface(BrowserMode.HEADLESS);
    }

    @Test
    @Tag("headed")
    void headedKeepsNativeAutomationSurface() {
        verifyNativeSurface(BrowserMode.HEADED);
    }

    private void verifyNativeSurface(BrowserMode mode) {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                mode, BrowserChannel.CHROME, profileRoot, null);
        BrowserRuntime runtime = new BrowserRuntime(properties, MAPPER);
        BrowserEnvironmentProfile environment = BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT);
        try (ChromeBrowser browser = runtime.launch(
                profileRoot.resolve("stealth-" + mode.name().toLowerCase()), environment);
             CdpTestPage page = CdpTestPage.open(browser, baseUrl + "/stealth", environment, runtime.userAgent())) {
            assertThat(page.evaluate("navigator.webdriver").asBoolean()).isFalse();
            assertThat(page.evaluate("typeof window.chrome === 'object' && window.chrome !== null").asBoolean())
                    .isTrue();
            JsonNode automationNames = page.evaluate(
                    "Object.getOwnPropertyNames(window).filter(name => "
                            + "/playwright|puppeteer|selenium|webdriver|phantom|cdc_/i.test(name))");
            assertThat(automationNames.isArray()).isTrue();
            assertThat(automationNames.isEmpty()).isTrue();
            for (String global : AUTOMATION_GLOBALS) {
                assertThat(page.absentGlobal(global)).as(global).isTrue();
            }
            assertThat(page.evaluate("navigator.languages.length").asInt()).isPositive();
            assertThat(page.evaluate("navigator.plugins.length").asInt()).isPositive();
            assertThat(page.evaluate("navigator.userAgent").asString())
                    .contains("Chrome/")
                    .doesNotContain("HeadlessChrome");
            assertThat(page.evaluate("navigator.userAgentData !== undefined").asBoolean()).isTrue();
            assertThat(page.evaluate("navigator.userAgentData.brands.length").asInt()).isPositive();
            assertThat(page.evaluate("""
                    navigator.userAgentData.brands.map(brand => brand.brand).join(',')
                    """).asString())
                    .contains("Chromium")
                    .doesNotContain("Headless");
            assertThat(page.evaluate("navigator.userAgentData.platform").asString()).isNotBlank();
            assertThat(page.evaluate("screen.width").asInt()).isGreaterThanOrEqualTo(1024);
            assertThat(page.evaluate("screen.height").asInt()).isGreaterThanOrEqualTo(768);
            JsonNode customGlobals = page.evaluate(
                    "Object.getOwnPropertyNames(window).filter(name => /vaadmin|w0fv1/i.test(name))");
            assertThat(customGlobals.isArray()).isTrue();
            assertThat(customGlobals.isEmpty()).as(customGlobals.toString()).isTrue();
        }
    }

    private void serveFixture(HttpExchange exchange) throws IOException {
        byte[] body = """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>stealth</title></head>
                <body>stealth</body></html>
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
