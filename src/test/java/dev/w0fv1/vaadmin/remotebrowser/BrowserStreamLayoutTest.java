package dev.w0fv1.vaadmin.remotebrowser;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Timeout(60)
class BrowserStreamLayoutTest {
    @Test
    void resizesLocalCanvasEvenWhenRemoteViewportRemainsAtMinimumWidth() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stream.js", exchange -> {
            try (exchange; var input = getClass().getResourceAsStream("/META-INF/frontend/browser-stream.js")) {
                byte[] bytes = input.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/javascript");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.createContext("/", exchange -> {
            byte[] bytes = """
                    <html><body><browser-stream style="display:block;width:200px;height:600px"></browser-stream>
                    <script>
                    window.WebSocket = class {
                      static OPEN = 1; readyState = 1;
                      constructor() { window.socket = this; setTimeout(() => this.onopen?.(), 0); }
                      send() {} close() {}
                    };
                    </script><script type="module" src="/stream.js"></script></body></html>
                    """.getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "text/html;charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        var properties = new BrowserRuntimeProperties(BrowserMode.HEADLESS, BrowserChannel.CHROME, TestProfileRoot.create("layout"), null);
        var mapper = new ObjectMapper();
        var manager = new BrowserSessionManager(new BrowserRuntime(properties, mapper), properties, new BrowserEnvironmentStore(properties, mapper), mapper);
        try (var session = manager.openPrivate(BrowserProfileId.DEFAULT, "http://127.0.0.1:" + server.getAddress().getPort())) {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(session.evaluate("!!document.querySelector('canvas')").asBoolean()).isTrue());
            session.evaluate("document.querySelector('browser-stream').sessionId = 'fixture'");
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(session.evaluate("!!window.socket?.onmessage").asBoolean()).isTrue());
            session.evaluate("window.socket.onmessage({data:JSON.stringify({tabs:[],viewportWidth:320,viewportHeight:420,quality:75,intervalMillis:40})})");
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(session.evaluate("document.querySelector('canvas').getBoundingClientRect().width").asDouble()).isEqualTo(200));
            session.evaluate("document.querySelector('browser-stream').style.width='280px'");
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(session.evaluate("document.querySelector('canvas').getBoundingClientRect().width").asDouble()).isEqualTo(280));
            session.evaluate("window.socket.send = data => window.lastInput = JSON.parse(data); document.querySelector('canvas').classList.add('is-ready')");
            var point = session.evaluate("(() => {const r=document.querySelector('canvas').getBoundingClientRect(); return {x:r.x+r.width/2,y:r.y+r.height/2};})()");
            double x = point.path("x").asDouble();
            double y = point.path("y").asDouble();
            session.session().down(new BrowserInput("down", 1, x, y, BrowserMouseButton.LEFT, 1, 1, java.util.Set.of()));
            session.session().up(new BrowserInput("up", 2, x, y, BrowserMouseButton.LEFT, 0, 1, java.util.Set.of()));
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                var input = session.evaluate("window.lastInput");
                assertThat(input.path("type").asString()).isEqualTo("up");
                assertThat(input.path("x").asDouble()).isCloseTo(160, within(1.0));
                assertThat(input.path("y").asDouble()).isCloseTo(210, within(1.0));
            });
        } finally {
            manager.shutdown();
            server.stop(0);
        }
    }
}
