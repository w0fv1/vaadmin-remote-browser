package dev.w0fv1.vaadmin.remotebrowser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(120)
final class PrivateSessionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path profileRoot = TestProfileRoot.create("private");

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
    void privateSessionStartsCleanAndLeavesNothingBehind() throws Exception {
        BrowserSessionManager manager = manager();
        try {
            RemoteBrowserSession first = manager.openPrivate(BrowserProfileId.DEFAULT, baseUrl, BrowserProxyMode.DIRECT);
            try {
                BrowserSession session = first.session();
                session.resizeViewport(1000, 600);
                awaitTitle(session, "state:1000x600:none");
                click(session, 500, 300);
                awaitTitle(session, "click:500x300:1000x600");
            } finally {
                Thread.currentThread().interrupt();
                try { first.close(); }
                finally { assertThat(Thread.interrupted()).isTrue(); }
            }

            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(privateDirectories()).isZero());

            try (RemoteBrowserSession second = manager.openPrivate(BrowserProfileId.DEFAULT, baseUrl)) {
                BrowserSession session = second.session();
                session.resizeViewport(1000, 600);
                awaitTitle(session, "state:1000x600:none");
            }
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void privateSessionInheritsTemplateEnvironment() {
        BrowserSessionManager manager = manager();
        manager.saveEnvironment(BrowserEnvironmentProfile.managedProfile(
                new BrowserProfileId("managed-template"),
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("Asia/Tokyo"),
                BrowserColorScheme.DARK,
                1.5,
                Set.of()));
        try (RemoteBrowserSession session = manager.openPrivate(
                new BrowserProfileId("managed-template"), baseUrl)) {
            assertThat(session.environmentMode()).isEqualTo(BrowserEnvironmentMode.MANAGED);
            assertThat(session.profileId().value()).startsWith("private-");
        } finally {
            manager.shutdown();
        }
    }

    private void click(BrowserSession session, double x, double y) {
        session.down(new BrowserInput("down", 1, x, y, BrowserMouseButton.LEFT, 1, 1, Set.of()));
        session.up(new BrowserInput("up", 2, x, y, BrowserMouseButton.LEFT, 0, 1, Set.of()));
    }

    private long privateDirectories() throws IOException {
        Path privateRoot = profileRoot.resolve("private");
        if (!Files.isDirectory(privateRoot)) return 0;
        try (Stream<Path> paths = Files.list(privateRoot)) {
            return paths.count();
        }
    }

    private BrowserSessionManager manager() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot, null);
        return new BrowserSessionManager(
                new BrowserRuntime(properties, MAPPER),
                properties,
                new BrowserEnvironmentStore(properties, MAPPER),
                MAPPER);
    }

    private void awaitTitle(BrowserSession session, String expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(session.capture()).isNotNull();
            assertThat(session.browserInfo().activeTitle()).isEqualTo(expected);
        });
    }

    private void serveFixture(HttpExchange exchange) throws IOException {
        byte[] body = """
                <!doctype html>
                <html>
                <head><meta charset="utf-8"><title>fixture</title></head>
                <body style="margin:0;width:100vw;height:100vh;overflow:hidden;background:#d22a2a">
                <script>
                const viewport = () => `${innerWidth}x${innerHeight}`;
                const persisted = () => localStorage.getItem('persist') || 'none';
                const render = () => { document.title = `state:${viewport()}:${persisted()}`; };
                window.addEventListener('resize', render);
                window.addEventListener('pointerdown', event => {
                  const position = `${Math.round(event.clientX)}x${Math.round(event.clientY)}`;
                  localStorage.setItem('persist', position);
                  document.title = `click:${position}:${viewport()}`;
                });
                render();
                </script>
                </body>
                </html>
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
