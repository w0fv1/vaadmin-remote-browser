package dev.w0fv1.vaadmin.remotebrowser;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "browser.remote")
public record BrowserRuntimeProperties(
        BrowserMode mode,
        BrowserChannel channel,
        Path profileRoot,
        Path executable,
        Boolean enabled,
        Boolean sandbox,
        String websocketPath,
        List<String> allowedOriginPatterns,
        BrowserMediaPolicy mediaPolicy) {
    static final String DEFAULT_WEBSOCKET_PATH = "/browser-stream";

    @ConstructorBinding
    public BrowserRuntimeProperties {
        if (mode == null) {
            mode = BrowserMode.HEADLESS;
        }
        if (channel == null) {
            channel = BrowserChannel.CHROME;
        }
        Path defaultProfileRoot = Path.of(
                System.getProperty("user.home"), ".vaadmin-remote-browser", "profiles");
        Path configuredProfileRoot = defaultProfileRoot;
        if (profileRoot != null) {
            configuredProfileRoot = profileRoot;
        }
        if (configuredProfileRoot.toString().isBlank()) {
            throw new IllegalArgumentException("浏览器配置目录不能为空");
        }
        profileRoot = configuredProfileRoot.toAbsolutePath().normalize();
        if (executable != null) {
            Path resolved = executable.toAbsolutePath().normalize();
            if (!Files.isRegularFile(resolved)) {
                throw new IllegalArgumentException("浏览器可执行文件不存在: " + resolved);
            }
            executable = resolved;
        }
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        if (sandbox == null) {
            sandbox = Boolean.TRUE;
        }
        websocketPath = normalizePath(websocketPath);
        allowedOriginPatterns = allowedOriginPatterns == null ? List.of() : List.copyOf(allowedOriginPatterns);
        if (mediaPolicy == null) {
            mediaPolicy = BrowserMediaPolicy.BLOCK;
        }
    }

    public BrowserRuntimeProperties(
            BrowserMode mode,
            BrowserChannel channel,
            Path profileRoot,
            Path executable) {
        this(mode, channel, profileRoot, executable, null, null, null, null, null);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return DEFAULT_WEBSOCKET_PATH;
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) trimmed = "/" + trimmed;
        while (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
