package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

final class BrowserPageEnvironment {
    static void apply(
            CdpSession session,
            ObjectMapper mapper,
            BrowserEnvironmentProfile environment,
            BrowserUserAgent userAgent) {
        if (userAgent != null) {
            ObjectNode parameters = mapper.createObjectNode();
            parameters.put("userAgent", userAgent.userAgent());
            parameters.set("userAgentMetadata", userAgent.metadata());
            session.call("Emulation.setUserAgentOverride", parameters);
        }
        if (environment.mode() != BrowserEnvironmentMode.MANAGED) return;
        ObjectNode locale = mapper.createObjectNode();
        locale.put("locale", environment.locale().toLanguageTag());
        session.call("Emulation.setLocaleOverride", locale);
        ObjectNode timezone = mapper.createObjectNode();
        timezone.put("timezoneId", environment.timezone().getId());
        session.call("Emulation.setTimezoneOverride", timezone);
        if (environment.colorScheme().overriding()) {
            ObjectNode feature = mapper.createObjectNode();
            feature.put("name", "prefers-color-scheme");
            feature.put("value", environment.colorScheme().mediaValue());
            ObjectNode media = mapper.createObjectNode();
            media.set("features", mapper.createArrayNode().add(feature));
            session.call("Emulation.setEmulatedMedia", media);
        }
    }
}
