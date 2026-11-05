package dev.w0fv1.vaadmin.remotebrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(90)
final class RemoteBrowserSessionTest {
    private final Path profileRoot = TestProfileRoot.create("lease");

    @Test
    void sharesSessionUntilLastLeaseCloses() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot, null);
        ObjectMapper mapper = new ObjectMapper();
        BrowserRuntime runtime = new BrowserRuntime(properties, mapper);
        BrowserEnvironmentStore environments = new BrowserEnvironmentStore(properties, mapper);
        BrowserSessionManager manager = new BrowserSessionManager(runtime, properties, environments, mapper);
        try {
            RemoteBrowserSession first = manager.openDefault("about:blank");
            RemoteBrowserSession second = manager.openDefault("about:blank");
            assertThat(first.evaluate("({answer: 6 * 7})").path("answer").asInt()).isEqualTo(42);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> first.evaluate("throw new Error('fixture failure')"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("fixture failure");

            assertThat(second.session().id()).isEqualTo(first.session().id());
            assertThat(first.session().browserInfo().contextOpen()).isTrue();

            first.close();
            assertThat(second.session().browserInfo().contextOpen()).isTrue();

            second.close();
            assertThat(second.session().browserInfo().contextOpen()).isFalse();

            try (RemoteBrowserSession reopened = manager.openDefault("about:blank")) {
                assertThat(reopened.session().id()).isNotEqualTo(second.session().id());
                assertThat(reopened.session().browserInfo().contextOpen()).isTrue();
            }
        } finally {
            manager.shutdown();
        }
    }
}
