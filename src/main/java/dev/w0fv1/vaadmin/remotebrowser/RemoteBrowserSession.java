package dev.w0fv1.vaadmin.remotebrowser;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RemoteBrowserSession implements AutoCloseable {
    private final BrowserSession session;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    RemoteBrowserSession(BrowserSession session, Runnable release) {
        this.session = session;
        this.release = release;
    }

    public String sessionId() {
        return session.id();
    }

    public BrowserProfileId profileId() {
        return new BrowserProfileId(session.browserInfo().profileId());
    }

    public BrowserEnvironmentMode environmentMode() {
        return session.browserInfo().environmentMode();
    }

    public tools.jackson.databind.JsonNode evaluate(String expression) {
        if (closed.get()) throw new IllegalStateException("浏览器会话已关闭");
        return session.evaluate(java.util.Objects.requireNonNull(expression));
    }

    public BrowserResource loadResource(String url, String contentTypePrefix) {
        if (closed.get()) { throw new IllegalStateException("浏览器会话已关闭"); }
        return session.loadResource(url, contentTypePrefix);
    }

    BrowserSession session() {
        return session;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) release.run();
    }

    @Override
    public String toString() {
        return "RemoteBrowserSession[" + sessionId() + "]";
    }
}
