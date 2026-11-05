package dev.w0fv1.vaadmin.remotebrowser;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;

class BrowserRuntimeProbeTest {
    @Test
    void startsPackagedBrowserAndExecutesScript() {
        assertThatCode(() -> BrowserRuntimeProbe.main(new String[0])).doesNotThrowAnyException();
    }
}
