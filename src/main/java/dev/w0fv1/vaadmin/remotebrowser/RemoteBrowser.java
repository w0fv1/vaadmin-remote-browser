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

    boolean isOpen(BrowserProfileId profileId);

    String streamPath();
}
