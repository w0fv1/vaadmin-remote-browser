package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

final class BundledBrowser {
    static final String LOCK_RESOURCE = "/browser-runtime/browser-lock.json";
    static final String ARCHIVE_RESOURCE = "/browser-runtime/runtime.zip";

    private BundledBrowser() {
    }

    static synchronized Path executable() {
        try {
            JsonNode lock = lock();
            String platform = platform();
            JsonNode entry = lock.path("platforms").path(platform);
            if (entry.isMissingNode()) throw new IllegalStateException("浏览器运行时清单缺少平台: " + platform);
            String sha256 = entry.path("runtimeSha256").asString("");
            String url = entry.path("runtimeUrl").asString("");
            String executableName = "win64".equals(platform) ? "chrome.exe" : "chrome";
            Path cacheRoot = Path.of(System.getProperty("user.home"), ".vaadmin-remote-browser", "runtime");
            return new BrowserRuntimeInstaller(cacheRoot)
                    .install(sha256, executableName, url, bundledArchive());
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("无法准备随包浏览器", failure);
        }
    }

    static String version() {
        try {
            return lock().path("version").asString("");
        } catch (Exception failure) {
            return "";
        }
    }

    private static JsonNode lock() {
        try (InputStream lockStream = BundledBrowser.class.getResourceAsStream(LOCK_RESOURCE)) {
            if (lockStream == null) throw new IllegalStateException("发布包缺少浏览器运行时清单");
            return new ObjectMapper().readTree(lockStream);
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取浏览器运行时清单", failure);
        }
    }

    private static InputStream bundledArchive() {
        return BundledBrowser.class.getResourceAsStream(ARCHIVE_RESOURCE);
    }

    private static String platform() {
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        if (!(arch.equals("amd64") || arch.equals("x86_64"))) {
            throw new IllegalStateException("随包浏览器仅支持 x64 架构");
        }
        if (os.startsWith("Windows")) return "win64";
        if (os.equals("Linux")) return "linux64";
        throw new IllegalStateException("随包浏览器仅支持 Windows 与 Linux");
    }

    static void extract(Path archive, Path directory) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = directory.resolve(entry.getName()).normalize();
                if (!target.startsWith(directory)) throw new IOException("浏览器归档路径越界");
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target);
                }
            }
        }
    }
}
