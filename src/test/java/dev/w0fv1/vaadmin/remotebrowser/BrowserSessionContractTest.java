package dev.w0fv1.vaadmin.remotebrowser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(120)
final class BrowserSessionContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path profileRoot = TestProfileRoot.create("contract");

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
    void headlessPreservesViewportInputTabsAndPersistentStorage() throws Exception {
        verifyContract(BrowserMode.HEADLESS);
    }

    @Test
    @Tag("headed")
    void headedPreservesViewportInputTabsAndPersistentStorage() throws Exception {
        verifyContract(BrowserMode.HEADED);
    }

    @Test
    void publishesViewportInCssPixels() throws Exception {
        BrowserSessionManager manager = manager(BrowserMode.HEADLESS);
        try (RemoteBrowserSession lease = manager.open(new BrowserProfileId("viewport-pixels"), baseUrl)) {
            BrowserSession session = lease.session();
            session.resizeViewport(1000, 600);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(session.state().viewportWidth()).isEqualTo(1000);
                assertThat(session.state().viewportHeight()).isEqualTo(600);
            });

            session.resizeViewport(1600, 900);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(session.state().viewportWidth()).isEqualTo(1600);
                assertThat(session.state().viewportHeight()).isEqualTo(900);
                BrowserFrame frame = session.capture();
                assertThat(frame).isNotNull();
                assertThat(frame.width()).isEqualTo(1600);
                assertThat(frame.height()).isEqualTo(900);
                assertThat(ImageIO.read(new ByteArrayInputStream(frame.jpeg()))).isNotNull();
            });
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void walksHistoryBackAndForward() throws Exception {
        BrowserSessionManager manager = manager(BrowserMode.HEADLESS);
        try (RemoteBrowserSession lease = manager.open(new BrowserProfileId("history"), baseUrl)) {
            BrowserSession session = lease.session();
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                session.capture();
                assertThat(session.browserInfo().activeUrl()).startsWith(baseUrl);
            });
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                    assertThat(session.state().tabs().getFirst().loading()).isFalse());

            session.navigateActive(baseUrl + "/second");
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                session.capture();
                assertThat(session.browserInfo().activeUrl()).endsWith("/second");
                assertThat(session.state().tabs().getFirst().loading()).isFalse();
            });

            session.historyBack();
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                session.capture();
                assertThat(session.browserInfo().activeUrl()).doesNotEndWith("/second");
                assertThat(session.state().tabs().getFirst().canGoForward()).isTrue();
            });

            session.historyForward();
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                session.capture();
                assertThat(session.browserInfo().activeUrl()).endsWith("/second");
            });
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void closingLastTabResetsItInsteadOfKillingBrowser() throws Exception {
        BrowserSessionManager manager = manager(BrowserMode.HEADLESS);
        try (RemoteBrowserSession lease = manager.open(new BrowserProfileId("last-tab"), baseUrl)) {
            BrowserSession session = lease.session();
            awaitTitle(session, "state:1280x720:none");

            session.closeTab(session.state().activeTabId());

            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                BrowserSession.TabsState state = session.state();
                assertThat(state.tabs()).hasSize(1);
                assertThat(state.tabs().getFirst().url()).isEqualTo("about:blank");
                assertThat(session.isAvailable()).isTrue();
            });
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                    assertThat(session.capture()).isNotNull());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void closesWhileCaptureIsRunningWithoutHanging() throws Exception {
        BrowserSessionManager manager = manager(BrowserMode.HEADLESS);
        try {
            RemoteBrowserSession lease = manager.open(new BrowserProfileId("close-race"), baseUrl);
            BrowserSession session = lease.session();
            session.resizeViewport(800, 600);
            awaitImage(session);

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<?> captures = workers.submit(() -> {
                    awaitLatch(start);
                    for (int index = 0; index < 100; index++) {
                        session.capture();
                    }
                    return null;
                });
                Future<?> closing = workers.submit(() -> {
                    awaitLatch(start);
                    lease.close();
                    return null;
                });
                start.countDown();

                closing.get(30, TimeUnit.SECONDS);
                captures.get(30, TimeUnit.SECONDS);
                assertThat(session.capture()).isNull();
                assertThat(session.browserInfo().contextOpen()).isFalse();
            } finally {
                workers.shutdownNow();
                lease.close();
            }
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void managedHeadlessEnvironmentPreservesViewportAndInput() throws Exception {
        BrowserRuntimeProperties properties = properties(BrowserMode.HEADLESS);
        BrowserEnvironmentStore environments = new BrowserEnvironmentStore(properties, MAPPER);
        BrowserProfileId profileId = new BrowserProfileId("managed-headless");
        environments.save(BrowserEnvironmentProfile.managedProfile(
                profileId,
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("Asia/Tokyo"),
                BrowserColorScheme.DARK,
                1.5,
                Set.of(BrowserPermission.GEOLOCATION)));

        BrowserSessionManager manager = manager(properties);
        RemoteBrowserSession lease = null;
        try {
            lease = manager.open(profileId, baseUrl);
            BrowserSession session = lease.session();
            session.resizeViewport(1000, 600);
            awaitTitle(session, "state:1000x600:none");
            session.down(new BrowserInput("down", 1, 500, 300, BrowserMouseButton.LEFT, 1, 1, Set.of()));
            session.up(new BrowserInput("up", 2, 500, 300, BrowserMouseButton.LEFT, 0, 1, Set.of()));
            awaitTitle(session, "click:500x300:1000x600");

            BufferedImage image = awaitImage(session);
            assertThat(image.getWidth()).isEqualTo(1500);
            assertThat(image.getHeight()).isEqualTo(900);
            assertThat(session.browserInfo().environmentMode()).isEqualTo(BrowserEnvironmentMode.MANAGED);
        } finally {
            if (lease != null) lease.close();
            manager.shutdown();
        }
    }

    @Test
    void pointerFloodKeepsCaptureAndInputAlive() throws Exception {
        BrowserSessionManager manager = manager(BrowserMode.HEADLESS);
        try (RemoteBrowserSession lease = manager.open(new BrowserProfileId("pointer-flood"), baseUrl)) {
            BrowserSession session = lease.session();
            session.resizeViewport(800, 600);
            awaitImage(session);

            for (int index = 0; index < 5000; index++) {
                session.move(new BrowserInput(
                        "move", index, 100 + index % 200, 120, BrowserMouseButton.NONE, 0, 1, Set.of()));
            }

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(session.capture()).isNotNull();
                assertThat(session.browserInfo().input().lastAppliedSequence()).isPositive();
            });
            assertThat(session.browserInfo().input().rejectedOperations()).isZero();
        } finally {
            manager.shutdown();
        }
    }

    private void verifyContract(BrowserMode mode) throws Exception {
        BrowserProfileId profileId = new BrowserProfileId("contract-" + mode.name().toLowerCase());
        BrowserSessionManager manager = manager(mode);
        RemoteBrowserSession lease = null;
        try {
            lease = manager.open(profileId, baseUrl);
            BrowserSession session = lease.session();
            session.resizeViewport(1000, 600);
            awaitTitle(session, "state:1000x600:none");

            session.down(new BrowserInput("down", 1, 500, 300, BrowserMouseButton.LEFT, 1, 1, Set.of()));
            session.up(new BrowserInput("up", 2, 500, 300, BrowserMouseButton.LEFT, 0, 1, Set.of()));
            awaitTitle(session, "click:500x300:1000x600");

            BufferedImage image = awaitImage(session);
            assertFrameScale(image);
            int pixel = image.getRGB(10, 10);
            assertThat(red(pixel)).isGreaterThan(150);
            assertThat(green(pixel)).isLessThan(90);
            assertThat(blue(pixel)).isLessThan(90);

            session.openTab(baseUrl + "/second");
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                session.capture();
                BrowserSession.TabsState state = session.state();
                assertThat(state.tabs()).hasSize(2);
                assertThat(state.tabs().get(1).url()).endsWith("/second");
                assertThat(state.activeTabId()).isEqualTo(state.tabs().get(1).id());
            });

            BufferedImage secondImage = awaitImage(session);
            assertFrameScale(secondImage);
            int secondPixel = secondImage.getRGB(500, 300);
            assertThat(green(secondPixel)).isGreaterThan(80);
            assertThat(red(secondPixel)).isLessThan(90);
            assertThat(blue(secondPixel)).isLessThan(90);

            BrowserSession.TabsState state = session.state();
            session.closeTab(state.activeTabId());
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                session.capture();
                assertThat(session.state().tabs()).hasSize(1);
            });

            session.key(new BrowserKeyInput("a", "KeyA", 65, 0, false, Set.of()));
            awaitTitle(session, "key:a:KeyA:65");
            session.down(new BrowserInput("down", 3, 950, 20, BrowserMouseButton.LEFT, 1, 1, Set.of()));
            session.up(new BrowserInput("up", 4, 950, 20, BrowserMouseButton.LEFT, 0, 1, Set.of()));
            awaitTitle(session, "click:950x20:1000x600");
            session.insertText("中文输入");
            awaitTitle(session, "text:中文输入");

            BrowserSession.BrowserInfo info = session.browserInfo();
            assertThat(info.profileId()).isEqualTo(profileId.value());
            assertThat(info.headless()).isEqualTo(mode.headless());
            assertThat(info.engine()).isEqualTo("Google Chrome");
            assertThat(info.browserVersion()).startsWith("Chrome/");
            assertThat(info.contextOpen()).isTrue();
            assertThat(info.lastFailure()).isNull();
            assertThat(session.evaluate(
                    "Object.getOwnPropertyNames(window).filter(name => /vaadmin|w0fv1/i.test(name)).length")
                    .asInt()).isZero();
            assertThat(session.evaluate("navigator.userAgentData.brands.length").asInt()).isPositive();

            lease.close();
            assertThat(session.browserInfo().contextOpen()).isFalse();

            AtomicReference<RemoteBrowserSession> reopened = new AtomicReference<>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                if (reopened.get() == null) reopened.set(manager.open(profileId, baseUrl));
            });
            try {
                reopened.get().session().resizeViewport(1000, 600);
                awaitTitle(reopened.get().session(), "state:1000x600:950x20");
            } finally {
                reopened.get().close();
            }
        } finally {
            if (lease != null) lease.close();
            manager.shutdown();
        }
    }

    private BrowserRuntimeProperties properties(BrowserMode mode) {
        return new BrowserRuntimeProperties(mode, BrowserChannel.CHROME, profileRoot, null);
    }

    private BrowserSessionManager manager(BrowserMode mode) {
        return manager(properties(mode));
    }

    private BrowserSessionManager manager(BrowserRuntimeProperties properties) {
        return new BrowserSessionManager(
                new BrowserRuntime(properties, MAPPER),
                properties,
                new BrowserEnvironmentStore(properties, MAPPER),
                MAPPER);
    }

    private static void awaitLatch(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitTitle(BrowserSession session, String expected) {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertThat(session.capture()).isNotNull();
            assertThat(session.browserInfo().activeTitle()).isEqualTo(expected);
        });
    }

    private BufferedImage awaitImage(BrowserSession session) throws IOException {
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            BrowserFrame frame = session.capture();
            assertThat(frame).isNotNull();
            image.set(ImageIO.read(new ByteArrayInputStream(frame.jpeg())));
            assertThat(image.get()).isNotNull();
        });
        return image.get();
    }

    private static void assertFrameScale(BufferedImage image) {
        int scaleX = image.getWidth() / 1000;
        int scaleY = image.getHeight() / 600;
        assertThat(scaleX).isGreaterThanOrEqualTo(1);
        assertThat(scaleX).isEqualTo(scaleY);
        assertThat(image.getWidth()).isEqualTo(1000 * scaleX);
        assertThat(image.getHeight()).isEqualTo(600 * scaleX);
    }

    private static int red(int pixel) {
        return (pixel >> 16) & 0xff;
    }

    private static int green(int pixel) {
        return (pixel >> 8) & 0xff;
    }

    private static int blue(int pixel) {
        return pixel & 0xff;
    }

    private void serveFixture(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] body;
        if ("/second".equals(path)) {
            body = """
                    <!doctype html>
                    <html>
                    <head><meta charset="utf-8"><title>second</title></head>
                    <body style="margin:0;background:#1f7a1f">second</body>
                    </html>
                    """.getBytes(StandardCharsets.UTF_8);
        } else {
            body = """
                    <!doctype html>
                    <html>
                    <head><meta charset="utf-8"><title>fixture</title></head>
                    <body style="margin:0;width:100vw;height:100vh;overflow:hidden;background:#d22a2a">
                    <input id="text" style="position:absolute;right:8px;top:8px;width:200px">
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
                    window.addEventListener('keydown', event => {
                      document.title = `key:${event.key}:${event.code}:${event.keyCode}`;
                    });
                    document.getElementById('text').addEventListener('input', event => {
                      document.title = `text:${event.target.value}`;
                    });
                    render();
                    </script>
                    </body>
                    </html>
                    """.getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
