package dev.w0fv1.vaadmin.remotebrowser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
final class BrowserEnvironmentTest {
    private final Path profileRoot = TestProfileRoot.create("environment");

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
    void realChromeAppliesManagedEnvironmentConsistently() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot, null);
        ObjectMapper mapper = new ObjectMapper();
        BrowserEnvironmentProfile environment = BrowserEnvironmentProfile.managedProfile(
                new BrowserProfileId("managed"),
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("Asia/Tokyo"),
                BrowserColorScheme.DARK,
                1.5,
                Set.of(BrowserPermission.GEOLOCATION));

        BrowserRuntime runtime = new BrowserRuntime(properties, mapper);
        try (ChromeBrowser browser = runtime.launch(profileRoot.resolve("managed"), environment);
             CdpTestPage page = CdpTestPage.open(browser, baseUrl + "/environment", environment, runtime.userAgent())) {
            JsonNode surface = page.evaluate("""
                    (async () => {
                      const permission = await navigator.permissions.query({name: 'geolocation'});
                      return {
                        language: navigator.language,
                        primaryLanguage: navigator.languages[0],
                        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                        deviceScaleFactor: window.devicePixelRatio,
                        dark: matchMedia('(prefers-color-scheme: dark)').matches,
                        geolocation: permission.state,
                        webdriver: navigator.webdriver
                      };
                    })()
                    """);

            assertThat(surface.path("language").asString()).isEqualTo("fr-CA");
            assertThat(surface.path("primaryLanguage").asString()).isEqualTo("fr-CA");
            assertThat(surface.path("timezone").asString()).isEqualTo("Asia/Tokyo");
            assertThat(surface.path("deviceScaleFactor").asDouble()).isEqualTo(1.5);
            assertThat(surface.path("dark").asBoolean()).isTrue();
            assertThat(surface.path("geolocation").asString()).isEqualTo("granted");
            assertThat(surface.path("webdriver").asBoolean()).isFalse();
        }
    }

    private void serveFixture(HttpExchange exchange) throws IOException {
        byte[] body = """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>environment</title></head>
                <body>environment</body></html>
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
