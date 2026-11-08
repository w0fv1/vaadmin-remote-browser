package dev.w0fv1.vaadmin.remotebrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class BrowserRuntimeTest {
    @Test
    void directSessionDisablesProxyWithoutChangingSystemDefault() {
        var runtime = runtime(BrowserMode.HEADLESS);
        var environment = BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT);
        assertThat(runtime.arguments(profileRoot, environment, BrowserProxyMode.DIRECT)).contains("--no-proxy-server");
        assertThat(runtime.arguments(profileRoot, environment, BrowserProxyMode.SYSTEM)).doesNotContain("--no-proxy-server");
    }
    @TempDir
    Path profileRoot;

    @Test
    void launchesNativeChromeWithoutAutomationFlags() {
        BrowserRuntime runtime = runtime(BrowserMode.HEADED);
        Path directory = profileRoot.resolve("default");

        List<String> arguments = runtime.arguments(
                directory, BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT));

        assertThat(arguments).containsExactly(
                "--user-data-dir=" + directory,
                "--remote-debugging-port=0",
                "--remote-allow-origins=*",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-background-timer-throttling",
                "--disable-blink-features=AutomationControlled",
                "--window-size=1280,720",
                "--disable-extensions",
                "--disable-sync",
                "--disable-background-networking",
                "--disable-component-update",
                "--disable-default-apps",
                "--disable-domain-reliability",
                "--disable-breakpad",
                "--no-pings",
                "--metrics-recording-only",
                "--disable-features=Translate,MediaRouter,OptimizationHints",
                "--mute-audio",
                "--autoplay-policy=user-gesture-required");
        assertThat(arguments).noneMatch(argument -> argument.startsWith("--enable-automation"));
        assertThat(arguments).noneMatch(argument -> argument.contains("no-sandbox"));
    }

    @Test
    void mediaPolicyControlsAudioAndPlaybackFlags() {
        assertThat(argumentsWith(BrowserMediaPolicy.ALLOW))
                .noneMatch(argument -> argument.equals("--mute-audio"))
                .noneMatch(argument -> argument.startsWith("--autoplay-policy="));
        assertThat(argumentsWith(BrowserMediaPolicy.MUTE))
                .contains("--mute-audio")
                .noneMatch(argument -> argument.startsWith("--autoplay-policy="));
        assertThat(argumentsWith(BrowserMediaPolicy.BLOCK))
                .contains("--mute-audio", "--autoplay-policy=user-gesture-required");
    }

    private List<String> argumentsWith(BrowserMediaPolicy mediaPolicy) {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADED,
                BrowserChannel.CHROME,
                profileRoot,
                null,
                null,
                null,
                null,
                null,
                mediaPolicy);
        return new BrowserRuntime(properties, new ObjectMapper()).arguments(
                profileRoot.resolve("media"),
                BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT));
    }

    @Test
    void headlessModeAddsNativeHeadlessFlag() {
        List<String> arguments = runtime(BrowserMode.HEADLESS).arguments(
                profileRoot.resolve("headless"),
                BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT));

        assertThat(arguments).contains("--headless=new");
        assertThat(arguments).noneMatch(argument -> argument.startsWith("--accept-lang="));
    }

    @Test
    void headlessBundledBrowserOverridesUserAgentThroughCdpWithoutLaunchFlag() {
        BrowserRuntime runtime = runtime(BrowserMode.HEADLESS);
        List<String> arguments = runtime.arguments(
                profileRoot.resolve("headless-agent"),
                BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT));

        assertThat(arguments).noneMatch(argument -> argument.startsWith("--user-agent="));
        BrowserUserAgent userAgent = runtime.userAgent();
        assertThat(userAgent).isNotNull();
        assertThat(userAgent.userAgent())
                .contains("Mozilla/5.0", "AppleWebKit/537.36",
                        "Chrome/" + BundledBrowser.version().split("\\.")[0])
                .doesNotContain("HeadlessChrome");
        assertThat(userAgent.metadata().path("brands").size()).isPositive();
        assertThat(userAgent.metadata().path("fullVersionList").size()).isPositive();
        assertThat(userAgent.metadata().path("platform").asString()).isNotBlank();
        assertThat(userAgent.metadata().path("fullVersion").asString()).isEqualTo(BundledBrowser.version());
    }

    @Test
    void headedBundledBrowserKeepsNativeUserAgent() {
        assertThat(runtime(BrowserMode.HEADED).userAgent()).isNull();
    }

    @Test
    void configuredExecutableKeepsNativeUserAgent() {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.MSEDGE, profileRoot, BrowserChannel.CHROME.executable());
        assertThat(new BrowserRuntime(properties, new ObjectMapper()).userAgent()).isNull();
    }

    @Test
    void managedEnvironmentDrivesLocaleAndScaleFactor() {
        BrowserEnvironmentProfile environment = BrowserEnvironmentProfile.managedProfile(
                new BrowserProfileId("managed"),
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("America/Toronto"),
                BrowserColorScheme.DARK,
                1.25,
                Set.of(BrowserPermission.GEOLOCATION));

        List<String> arguments = runtime(BrowserMode.HEADED).arguments(
                profileRoot.resolve("managed"), environment);

        assertThat(arguments).contains("--accept-lang=fr-CA,fr", "--force-device-scale-factor=1.25");
        assertThat(arguments).noneMatch(argument -> argument.startsWith("--enable-automation"));
    }

    @Test
    void keepsChromiumSandboxUnlessExplicitlyDisabled() {
        assertThat(runtime(BrowserMode.HEADLESS).arguments(
                        profileRoot.resolve("sandboxed"),
                        BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT)))
                .noneMatch(argument -> argument.equals("--no-sandbox"));

        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADLESS, BrowserChannel.CHROME, profileRoot, null, true, false, null, null, null);
        assertThat(new BrowserRuntime(properties, new ObjectMapper()).arguments(
                        profileRoot.resolve("unsandboxed"),
                        BrowserEnvironmentProfile.nativeProfile(BrowserProfileId.DEFAULT)))
                .contains("--no-sandbox");
    }

    @Test
    void resolvesConfiguredExecutable() {
        Path chrome = BrowserChannel.CHROME.executable();
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                BrowserMode.HEADED, BrowserChannel.MSEDGE, profileRoot, chrome);
        BrowserRuntime runtime = new BrowserRuntime(properties, new ObjectMapper());

        assertThat(runtime.executable()).isEqualTo(chrome);
        assertThat(chrome).isRegularFile();
    }

    private BrowserRuntime runtime(BrowserMode mode) {
        BrowserRuntimeProperties properties = new BrowserRuntimeProperties(
                mode, BrowserChannel.CHROME, profileRoot, null);
        return new BrowserRuntime(properties, new ObjectMapper());
    }
}
