package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.node.ObjectNode;

record BrowserUserAgent(String userAgent, ObjectNode metadata) {
}
