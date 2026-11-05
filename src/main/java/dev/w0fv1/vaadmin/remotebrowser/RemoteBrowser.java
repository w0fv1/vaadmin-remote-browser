package dev.w0fv1.vaadmin.remotebrowser;

import java.util.List;

public interface RemoteBrowser {
    List<BrowserProfileId> profiles();

    BrowserEnvironmentProfile environment(BrowserProfileId profileId);

    void saveEnvironment(BrowserEnvironmentProfile environment);

    RemoteBrowserSession open(BrowserProfileId profileId, String url);

    default RemoteBrowserSession openPrivate(BrowserProfileId templateProfile, String url) {
        return openPrivate(templateProfile, url, BrowserProxyMode.SYSTEM);
    }

    RemoteBrowserSession openPrivate(BrowserProfileId templateProfile, String url, BrowserProxyMode proxyMode);

    default BrowserResource loadResource(String url, String contentTypePrefix, BrowserProxyMode proxyMode) {
        try (var session = openPrivate(BrowserProfileId.DEFAULT, "about:blank", proxyMode)) {
            return session.loadResource(url, contentTypePrefix);
        }
    }

    boolean isOpen(BrowserProfileId profileId);

    String streamPath();
}
