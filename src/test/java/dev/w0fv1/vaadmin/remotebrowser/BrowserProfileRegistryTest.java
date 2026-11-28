package dev.w0fv1.vaadmin.remotebrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BrowserProfileRegistryTest {
    @TempDir
    Path root;

    @Test
    void independentRegistriesCannotOpenTheSamePersistentProfile() {
        BrowserProfileRegistry first = new BrowserProfileRegistry(root, store());
        BrowserProfileRegistry second = new BrowserProfileRegistry(root, store());
        try (BrowserProfileLease owner = first.acquire("shared")) {
            assertThatThrownBy(() -> second.acquire("shared")).isInstanceOf(IllegalStateException.class);
        }
        try (BrowserProfileLease owner = second.acquire("shared")) {
            assertThat(owner.directory()).isEqualTo(root.resolve("shared"));
        }
    }

    @Test
    void leasesNamedProfileExclusivelyAndReleasesOnClose() throws Exception {
        BrowserProfileRegistry registry = new BrowserProfileRegistry(root, store());

        try (BrowserProfileLease lease = registry.acquire("account-1")) {
            assertThat(lease.directory()).isEqualTo(root.resolve("account-1").toAbsolutePath().normalize());
            assertThat(Files.isDirectory(lease.directory())).isTrue();
            assertThat(lease.environment().mode()).isEqualTo(BrowserEnvironmentMode.NATIVE);
            assertThatThrownBy(() -> registry.acquire("account-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("正在使用");
        }

        try (BrowserProfileLease ignored = registry.acquire("account-1")) {
            assertThat(Files.isDirectory(root.resolve("account-1"))).isTrue();
        }
    }

    @Test
    void loadsManagedEnvironmentWithProfileLease() {
        BrowserEnvironmentStore store = store();
        BrowserProfileRegistry registry = new BrowserProfileRegistry(root, store);
        BrowserEnvironmentProfile profile = BrowserEnvironmentProfile.managedProfile(
                new BrowserProfileId("managed"),
                Locale.forLanguageTag("fr-CA"),
                ZoneId.of("Asia/Tokyo"),
                BrowserColorScheme.DARK,
                1.5,
                Set.of(BrowserPermission.GEOLOCATION));
        store.save(profile);

        try (BrowserProfileLease lease = registry.acquire("managed")) {
            assertThat(lease.environment()).isEqualTo(profile);
        }
    }

    @Test
    void rejectsUnsafeProfileIdentifiers() {
        BrowserProfileRegistry registry = new BrowserProfileRegistry(root, store());

        assertThatThrownBy(() -> registry.acquire("../escape"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.acquire(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.acquire("a".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BrowserEnvironmentStore store() {
        return new BrowserEnvironmentStore(
                new BrowserRuntimeProperties(BrowserMode.HEADED, BrowserChannel.CHROME, root, null),
                new ObjectMapper());
    }
}
