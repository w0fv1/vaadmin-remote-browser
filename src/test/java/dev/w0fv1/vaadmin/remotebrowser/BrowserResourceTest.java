package dev.w0fv1.vaadmin.remotebrowser;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

@Timeout(90)
class BrowserResourceTest {
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "BROWSER_RESOURCE_LIVE", matches = "true")
    void loadsCoolapkImageWithRealHeadlessBrowser() throws Exception {
        var properties = new BrowserRuntimeProperties(BrowserMode.HEADLESS, BrowserChannel.CHROME, TestProfileRoot.create("resource-live"), null);
        var mapper = new ObjectMapper();
        var manager = new BrowserSessionManager(new BrowserRuntime(properties, mapper), properties, new BrowserEnvironmentStore(properties, mapper), mapper);
        try (var browser = manager.openPrivate(BrowserProfileId.DEFAULT, "about:blank", BrowserProxyMode.DIRECT)) {
            var image = browser.loadResource("https://image.coolapk.com/image/2026/1105/10/E696B0-E4BB8AE697A5E985B7E5AE891105-0-o_1k6koh17cc7ji3p10lf1p53hla-uid-97100@1080x360.jpg", "image/");
            assertThat(image.contentType()).startsWith("image/");
            assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image.content()))).isNotNull();
        } finally { manager.shutdown(); }
    }

    @Test
    void loadsBinaryResourceAfterBrowserExecutesDocumentAndReusesCookies() throws Exception {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5WQAAAAASUVORK5CYII=");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            boolean verified = String.valueOf(exchange.getRequestHeaders().getFirst("Cookie")).contains("ready=yes");
            byte[] body = verified ? png : "<script>document.cookie='ready=yes;path=/';location.reload()</script>".getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", verified ? "image/png" : "text/html");
                exchange.sendResponseHeaders(verified ? 200 : 567, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        var properties = new BrowserRuntimeProperties(BrowserMode.HEADLESS, BrowserChannel.CHROME, TestProfileRoot.create("resource"), null);
        var mapper = new ObjectMapper();
        var manager = new BrowserSessionManager(new BrowserRuntime(properties, mapper), properties, new BrowserEnvironmentStore(properties, mapper), mapper);
        try (var browser = manager.openPrivate(BrowserProfileId.DEFAULT, "about:blank", BrowserProxyMode.DIRECT)) {
            var resource = browser.loadResource("http://127.0.0.1:" + server.getAddress().getPort() + "/image.png", "image/");
            assertThat(resource.contentType()).isEqualTo("image/png");
            assertThat(resource.content()).isEqualTo(png);
            assertThat(browser.loadResource("http://127.0.0.1:" + server.getAddress().getPort() + "/second.png", "image/").content()).isEqualTo(png);
        } finally { manager.shutdown(); server.stop(0); }
    }
}
