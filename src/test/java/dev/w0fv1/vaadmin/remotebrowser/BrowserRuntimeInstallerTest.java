package dev.w0fv1.vaadmin.remotebrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowserRuntimeInstallerTest {
    @TempDir
    Path cacheRoot;

    @Test
    void installsFromBundledArchiveAndReusesCacheWithoutArchive() throws Exception {
        byte[] archive = archive("chrome", "payload");
        String digest = sha256(archive);
        BrowserRuntimeInstaller installer = new BrowserRuntimeInstaller(cacheRoot);

        Path installed = installer.install(digest, "chrome", "https://invalid.test/runtime.zip",
                new ByteArrayInputStream(archive));
        Path reused = installer.install(digest, "chrome", "https://invalid.test/runtime.zip", null);

        assertThat(installed).isRegularFile();
        assertThat(Files.readString(installed)).isEqualTo("payload");
        assertThat(reused).isEqualTo(installed);
        assertThat(Files.exists(cacheRoot.resolve(digest))).isTrue();
        assertThat(Files.exists(cacheRoot.resolve(digest).resolve("chrome"))).isTrue();
    }

    @Test
    void rejectsArchiveThatDoesNotMatchDigest() throws Exception {
        byte[] archive = archive("chrome", "payload");
        BrowserRuntimeInstaller installer = new BrowserRuntimeInstaller(cacheRoot);

        assertThatThrownBy(() -> installer.install(sha256("other".getBytes()), "chrome",
                "https://invalid.test/runtime.zip", new ByteArrayInputStream(archive)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("校验失败");
        assertThat(Files.exists(cacheRoot.resolve(sha256("other".getBytes())))).isFalse();
    }

    @Test
    void requiresDownloadUrlWhenArchiveMissing() {
        BrowserRuntimeInstaller installer = new BrowserRuntimeInstaller(cacheRoot);

        assertThatThrownBy(() -> installer.install(sha256("missing".getBytes()), "chrome", "", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("下载地址缺失");
    }

    private static byte[] archive(String entryName, String content) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes());
        }
        return buffer.toByteArray();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
