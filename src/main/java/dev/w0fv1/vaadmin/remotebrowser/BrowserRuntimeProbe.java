package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.ObjectMapper;

public final class BrowserRuntimeProbe {
    public static void main(String[] args) {
        var properties = new BrowserRuntimeProperties(BrowserMode.HEADLESS, BrowserChannel.CHROME, null, null);
        var mapper = new ObjectMapper();
        var manager = new BrowserSessionManager(new BrowserRuntime(properties, mapper), properties,
                new BrowserEnvironmentStore(properties, mapper), mapper);
        try {
            try (var session = manager.openPrivate(BrowserProfileId.DEFAULT, "about:blank", BrowserProxyMode.DIRECT)) {
                if (session.evaluate("1 + 1").asInt() != 2) {
                    throw new IllegalStateException("浏览器脚本执行验证失败");
                }
            }
        } finally {
            manager.shutdown();
        }
        System.out.println("BROWSER_RUNTIME_OK");
    }
}
