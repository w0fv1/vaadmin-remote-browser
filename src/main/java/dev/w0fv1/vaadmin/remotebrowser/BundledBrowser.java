package dev.w0fv1.vaadmin.remotebrowser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.zip.ZipInputStream;

final class BundledBrowser {
    static synchronized Path executable() {
        try {
            Properties properties = new Properties();
            try (InputStream metadata = BundledBrowser.class.getResourceAsStream("/browser-runtime/runtime.properties")) {
                if (metadata == null) throw new IllegalStateException("发布包缺少浏览器运行时");
                properties.load(metadata);
            }
            String platform = properties.getProperty("platform");
            String os = System.getProperty("os.name");
            String arch = System.getProperty("os.arch");
            if (!(os.startsWith("Windows") || os.equals("Linux")) || !(arch.equals("amd64") || arch.equals("x86_64"))) {
                throw new IllegalStateException("随包浏览器仅支持 Windows x64 和 Linux x64");
            }
            String expected = os.startsWith("Windows") ? "win64" : "linux64";
            if (!expected.equals(platform)) throw new IllegalStateException("浏览器运行时平台不匹配: " + platform);
            String digest = properties.getProperty("sha256");
            if (digest == null || !digest.matches("[a-f0-9]{64}")) throw new IllegalStateException("浏览器运行时摘要无效");
            Path cache = Path.of(System.getProperty("user.home"), ".vaadmin-remote-browser", "runtime");
            Files.createDirectories(cache);
            Path destination = cache.resolve(digest);
            try (FileChannel lockChannel = FileChannel.open(cache.resolve(digest + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = lockChannel.lock()) {
                if (!Files.isDirectory(destination)) {
                    Path staging = Files.createTempDirectory(cache, "extract-");
                    try {
                        Path archive = staging.resolve("runtime.zip");
                        try (InputStream input = BundledBrowser.class.getResourceAsStream("/browser-runtime/runtime.zip")) {
                            if (input == null) throw new IllegalStateException("发布包缺少浏览器文件");
                            Files.copy(input, archive);
                        }
                        MessageDigest sha = MessageDigest.getInstance("SHA-256");
                        try (var input = Files.newInputStream(archive)) {
                            byte[] bytes = new byte[65536];
                            int length;
                            while ((length = input.read(bytes)) != -1) sha.update(bytes, 0, length);
                        }
                        if (!digest.equals(HexFormat.of().formatHex(sha.digest()))) throw new IllegalStateException("浏览器文件校验失败");
                        extract(archive, staging);
                        Files.delete(archive);
                        for (String binary : new String[]{"chrome", "chrome_crashpad_handler", "chrome_sandbox"}) {
                            Path file = staging.resolve(binary);
                            if (Files.exists(file) && !file.toFile().setExecutable(true, true)) {
                                throw new IOException("无法设置浏览器执行权限: " + file);
                            }
                        }
                        if (platform.equals("win64")) {
                            Process permissions = new ProcessBuilder("icacls", staging.toString(), "/grant",
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
                        Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        if (Files.exists(staging)) {
                            try (var paths = Files.walk(staging)) {
                                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                            }
                        }
                    }
                }
            }
            Path executable = destination.resolve(platform.equals("win64") ? "chrome.exe" : "chrome");
            if (!Files.isRegularFile(executable)) throw new IllegalStateException("浏览器运行时不完整: " + executable);
            return executable;
        } catch (IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("无法准备随包浏览器", failure);
        }
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
