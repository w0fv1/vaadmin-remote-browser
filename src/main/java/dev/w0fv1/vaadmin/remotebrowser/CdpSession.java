package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.function.Consumer;

record CdpSession(CdpConnection connection, String sessionId) {
    JsonNode call(String method, ObjectNode parameters) {
        return connection.call(sessionId, method, parameters);
    }

    void send(String method, ObjectNode parameters) {
        connection.send(sessionId, method, parameters);
    }

    Runnable on(String method, Consumer<JsonNode> handler) {
        return connection.on(sessionId, method, handler);
    }
}
