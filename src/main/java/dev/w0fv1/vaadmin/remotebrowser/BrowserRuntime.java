package dev.w0fv1.vaadmin.remotebrowser;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BrowserRuntime {
    private static final Duration LAUNCH_TIMEOUT = Duration.ofSeconds(30);
    private static final List<String> HOSTED_FLAGS = List.of(
            "--disable-extensions",
            "--disable-sync",
            "--disable-background-networking",
            "--disable-component-update",
            "--disable-default-apps",
            "--disable-domain-reliability",
            "--disable-breakpad",
            "--no-pings",
            "--metrics-recording-only",
            "--disable-features=Translate,MediaRouter,OptimizationHints");

    private final BrowserRuntimeProperties properties;
    private final ObjectMapper mapper;

    public BrowserRuntime(BrowserRuntimeProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    ChromeBrowser launch(Path profileDirectory, BrowserEnvironmentProfile environment) {
        return launch(profileDirectory, environment, BrowserProxyMode.SYSTEM);
    }

    ChromeBrowser launch(Path profileDirectory, BrowserEnvironmentProfile environment, BrowserProxyMode proxyMode) {
        ChromeProcess process = ChromeProcess.start(
                executable(), arguments(profileDirectory, environment, proxyMode), profileDirectory, LAUNCH_TIMEOUT);
        CdpConnection connection = null;
        try {
            connection = CdpConnection.open(process.browserEndpoint(), mapper);
            ChromeBrowser browser = new ChromeBrowser(process, connection);
            grantPermissions(browser, environment);
            return browser;
        } catch (RuntimeException failure) {
            if (connection != null) connection.close();
            process.close();
            throw failure;
        }
    }

    Path executable() {
        if (properties.executable() != null) return properties.executable();
        if (properties.channel() == BrowserChannel.MSEDGE) return properties.channel().executable();
        return BundledBrowser.executable();
    }

    List<String> arguments(Path profileDirectory, BrowserEnvironmentProfile environment) {
        return arguments(profileDirectory, environment, BrowserProxyMode.SYSTEM);
    }

    List<String> arguments(Path profileDirectory, BrowserEnvironmentProfile environment, BrowserProxyMode proxyMode) {
        List<String> arguments = new ArrayList<>(List.of(
                "--user-data-dir=" + profileDirectory,
                "--remote-debugging-port=0",
                "--remote-allow-origins=*",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-background-timer-throttling",
                "--disable-blink-features=AutomationControlled",
                "--window-size=" + BrowserSession.DEFAULT_VIEWPORT_WIDTH
                        + "," + BrowserSession.DEFAULT_VIEWPORT_HEIGHT));
        arguments.addAll(HOSTED_FLAGS);
        if (proxyMode == BrowserProxyMode.DIRECT) arguments.add("--no-proxy-server");
        if (properties.mode().headless()) arguments.add("--headless=new");
        if (!properties.sandbox()) arguments.add("--no-sandbox");
        if (properties.mediaPolicy().mutesAudio()) arguments.add("--mute-audio");
        if (properties.mediaPolicy().blocksPlayback()) {
            arguments.add("--autoplay-policy=user-gesture-required");
        }
        if (environment.mode() == BrowserEnvironmentMode.MANAGED) {
            arguments.add("--accept-lang=" + acceptLanguage(environment.locale()));
            arguments.add("--force-device-scale-factor=" + environment.deviceScaleFactor());
        }
        return List.copyOf(arguments);
    }

    private static String acceptLanguage(Locale locale) {
        String tag = locale.toLanguageTag();
        String language = locale.getLanguage();
        if (tag.equalsIgnoreCase(language)) return language;
        return tag + "," + language;
    }

    private String headlessUserAgent() {
        if (!usesBundledBrowser()) return null;
        String version = BundledBrowser.version();
        if (version == null || version.isBlank()) return null;
        String platform = System.getProperty("os.name").startsWith("Windows")
                ? "Windows NT 10.0; Win64; x64"
                : "X11; Linux x86_64";
        String major = version.split("\\.")[0];
        return "Mozilla/5.0 (" + platform + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + major + ".0.0.0 Safari/537.36";
    }

    BrowserUserAgent userAgent() {
        String userAgent = headlessUserAgent();
        if (userAgent == null || !properties.mode().headless()) return null;
        String version = BundledBrowser.version();
        String major = version.split("\\.")[0];
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        ObjectNode metadata = mapper.createObjectNode();
        metadata.set("brands", brands(mapper, major));
        metadata.set("fullVersionList", fullVersionList(mapper, version));
        metadata.put("fullVersion", version);
        metadata.put("platform", windows ? "Windows" : "Linux");
        metadata.put("platformVersion", windows ? "19.0.0" : "");
        metadata.put("architecture", "x86");
        metadata.put("bitness", "64");
        metadata.put("model", "");
        metadata.put("mobile", false);
        metadata.put("wow64", false);
        return new BrowserUserAgent(userAgent, metadata);
    }

    private static ArrayNode brands(ObjectMapper mapper, String major) {
        ArrayNode entries = mapper.createArrayNode();
        ObjectNode notBrand = entries.addObject();
        notBrand.put("brand", "Not:A-Brand");
        notBrand.put("version", "8");
        ObjectNode chromium = entries.addObject();
        chromium.put("brand", "Chromium");
        chromium.put("version", major);
        return entries;
    }

    private static ArrayNode fullVersionList(ObjectMapper mapper, String version) {
        ArrayNode entries = mapper.createArrayNode();
        ObjectNode notBrand = entries.addObject();
        notBrand.put("brand", "Not:A-Brand");
        notBrand.put("version", "8.0.0.0");
        ObjectNode chromium = entries.addObject();
        chromium.put("brand", "Chromium");
        chromium.put("version", version);
        return entries;
    }

    private boolean usesBundledBrowser() {
        return properties.executable() == null && properties.channel() != BrowserChannel.MSEDGE;
    }

    private void grantPermissions(ChromeBrowser browser, BrowserEnvironmentProfile environment) {
        ArrayNode permissions = mapper.createArrayNode();
        permissions.add("clipboardReadWrite").add("clipboardSanitizedWrite");
        environment.permissions().stream().sorted().forEach(permission -> permissions.add(permission.cdpName()));
        ObjectNode parameters = mapper.createObjectNode();
        parameters.set("permissions", permissions);
        browser.call("Browser.grantPermissions", parameters);
    }
}
