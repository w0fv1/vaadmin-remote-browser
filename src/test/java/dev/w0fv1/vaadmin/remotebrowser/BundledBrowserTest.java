package dev.w0fv1.vaadmin.remotebrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class BundledBrowserTest {
    @TempDir Path directory;

    @Test void extractsNestedResources() throws Exception {
        Path archive = directory.resolve("input.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("locales/en-US.pak"));
            output.write("payload".getBytes());
        }
        BundledBrowser.extract(archive, directory);
        assertThat(Files.readString(directory.resolve("locales/en-US.pak"))).isEqualTo("payload");
    }

    @Test void rejectsArchiveTraversal() throws Exception {
        Path archive = directory.resolve("input.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("../outside"));
            output.write(1);
        }
        assertThatThrownBy(() -> BundledBrowser.extract(archive, directory)).isInstanceOf(java.io.IOException.class);
    }

    @Test void resolvesExecutableFromPackagedRuntime() {
        Path browser = BundledBrowser.executable();
        assertThat(browser).isRegularFile();
        assertThat(browser.toString()).contains(".vaadmin-remote-browser");
        assertThat(BundledBrowser.executable()).isEqualTo(browser);
    }
}
