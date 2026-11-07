package dev.w0fv1.vaadmin.remotebrowser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.ZipInputStream;

final class BrowserRuntimeInstaller {
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(30);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final Path cacheRoot;

    BrowserRuntimeInstaller(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    Path install(String sha256, String executableName, String downloadUrl, InputStream bundledArchive) {
        try {
            Files.createDirectories(cacheRoot);
            Path destination = cacheRoot.resolve(sha256);
            Path executable = destination.resolve(executableName);
            if (Files.isRegularFile(executable)) return executable;
            try (FileChannel lockChannel = FileChannel.open(cacheRoot.resolve(sha256 + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = lockChannel.lock()) {
                if (Files.isRegularFile(executable)) return executable;
                Path staging = Files.createTempDirectory(cacheRoot, "extract-");
                try {
                    Path archive = staging.resolve("runtime.zip");
                    if (bundledArchive != null) {
                        try (InputStream input = bundledArchive) {
                            Files.copy(input, archive);
                        }
                    } else {
                        download(downloadUrl, archive);
                    }
                    if (!sha256.equals(digest(archive))) throw new IllegalStateException("浏览器运行时校验失败");
                    BundledBrowser.extract(archive, staging);
                    Files.delete(archive);
                    for (String binary : new String[]{executableName, "chrome_crashpad_handler", "chrome_sandbox"}) {
                        Path file = staging.resolve(binary);
                        if (Files.exists(file) && !file.toFile().setExecutable(true, true)) {
                            throw new IOException("无法设置浏览器执行权限: " + file);
                        }
                    }
                    if (executableName.endsWith(".exe")) grantBrowserPackageAccess(staging);
                    Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    if (Files.exists(staging)) {
                        try (var paths = Files.walk(staging)) {
                            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                        }
                    }
                }
            }
            if (!Files.isRegularFile(executable)) throw new IllegalStateException("浏览器运行时不完整: " + executable);
            return executable;
        } catch (IOException failure) {
            throw new IllegalStateException("无法准备浏览器运行时", failure);
        }
    }

    private static void download(String url, Path target) throws IOException {
        if (url == null || url.isBlank() || "PENDING".equals(url)) {
            throw new IllegalStateException("浏览器运行时下载地址缺失");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(DOWNLOAD_TIMEOUT)
                .GET()
                .build();
        HttpResponse<Path> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(target));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("浏览器运行时下载被中断", interrupted);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("浏览器运行时下载失败，HTTP " + response.statusCode());
        }
    }

    private static String digest(Path archive) throws IOException {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(archive)) {
                byte[] buffer = new byte[65536];
                int length;
                while ((length = input.read(buffer)) != -1) sha.update(buffer, 0, length);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IOException("缺少 SHA-256 实现", failure);
        }
    }

    private static void grantBrowserPackageAccess(Path directory) throws IOException {
        Process permissions = new ProcessBuilder("icacls", directory.toString(), "/grant",
                "*S-1-15-2-1:(OI)(CI)(RX)", "/T", "/Q")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (permissions.waitFor() != 0) throw new IOException("无法设置浏览器沙箱读取权限");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("设置浏览器权限被中断", interrupted);
        }
    }
}
