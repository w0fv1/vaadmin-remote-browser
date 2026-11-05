package dev.w0fv1.vaadmin.remotebrowser;

public record BrowserResource(String url, String contentType, byte[] content) {
    public BrowserResource { content = content.clone(); }
    @Override public byte[] content() { return content.clone(); }
    @Override public String toString() { return "BrowserResource[" + contentType + ", " + content.length + " bytes]"; }
}
