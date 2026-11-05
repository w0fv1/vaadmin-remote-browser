package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class BrowserResourceLoader {
    private final CdpSession session;
    private final ObjectMapper mapper;

    BrowserResourceLoader(CdpSession session, ObjectMapper mapper) {
        this.session = session;
        this.mapper = mapper;
    }

    BrowserResource load(String url, String contentTypePrefix) {
        URI target = URI.create(url);
        if (!("http".equals(target.getScheme()) || "https".equals(target.getScheme())) || target.getHost() == null) {
            throw new IllegalArgumentException("资源地址必须为 HTTP(S)");
        }
        if (contentTypePrefix == null || contentTypePrefix.isBlank()) { throw new IllegalArgumentException("请指定资源类型"); }
        String frameId = session.call("Page.getFrameTree", null).path("frameTree").path("frame").path("id").asString();
        var pending = new CompletableFuture<JsonNode>();
        var responses = new ConcurrentHashMap<String, JsonNode>();
        var lastResponse = new AtomicReference<>("尚未收到响应");
        Runnable responseListener = session.on("Network.responseReceived", event -> {
            if (!event.path("frameId").asString("").equals(frameId) || !event.path("type").asString().equals("Document")) { return; }
            JsonNode response = event.path("response");
            int status = response.path("status").asInt();
            String mime = response.path("mimeType").asString("");
            lastResponse.set("HTTP " + status + " " + mime);
            if (status >= 200 && status < 300 && mime.startsWith(contentTypePrefix)) {
                responses.put(event.path("requestId").asString(), event);
            }
        });
        Runnable finishedListener = session.on("Network.loadingFinished", event -> {
            JsonNode response = responses.remove(event.path("requestId").asString());
            if (response != null) { pending.complete(response); }
        });
        try {
            var network = mapper.createObjectNode().put("maxTotalBufferSize", 40 * 1024 * 1024).put("maxResourceBufferSize", 20 * 1024 * 1024);
            session.call("Network.enable", network);
            JsonNode navigation = session.call("Page.navigate", mapper.createObjectNode().put("url", url));
            if (navigation.hasNonNull("errorText")) { throw new IllegalStateException("资源导航失败：" + navigation.path("errorText").asString()); }
            JsonNode result = pending.get(30, TimeUnit.SECONDS);
            JsonNode body = session.call("Network.getResponseBody", mapper.createObjectNode().put("requestId", result.path("requestId").asString()));
            byte[] content = body.path("base64Encoded").asBoolean()
                    ? Base64.getDecoder().decode(body.path("body").asString())
                    : body.path("body").asString().getBytes(StandardCharsets.UTF_8);
            if (content.length > 20 * 1024 * 1024) { throw new IllegalStateException("资源超过 20 MB"); }
            return new BrowserResource(result.path("response").path("url").asString(), result.path("response").path("mimeType").asString(), content);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("资源加载已取消", failure);
        } catch (java.util.concurrent.TimeoutException failure) {
            throw new IllegalStateException("资源加载超时：" + lastResponse.get(), failure);
        } catch (java.util.concurrent.ExecutionException failure) {
            throw new IllegalStateException("资源加载失败", failure.getCause());
        } finally {
            responseListener.run();
            finishedListener.run();
        }
    }
}
