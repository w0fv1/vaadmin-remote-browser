package dev.w0fv1.vaadmin.remotebrowser;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BrowserProfileRegistry {
    private static final String PRIVATE_DIRECTORY = "private";

    private final Path root;
    private final BrowserEnvironmentStore environments;
    private final Set<Path> activeProfiles = ConcurrentHashMap.newKeySet();

    BrowserProfileRegistry(Path root, BrowserEnvironmentStore environments) {
        this.root = root.toAbsolutePath().normalize();
        this.environments = environments;
    }

    BrowserProfileLease acquire(String profileId) {
        BrowserProfileId identifier = new BrowserProfileId(profileId);
        Path directory = root.resolve(identifier.value()).normalize();
        if (!directory.startsWith(root) || directory.equals(root)) {
            throw new IllegalArgumentException("浏览器配置路径无效");
        }
        BrowserEnvironmentProfile environment = environments.read(identifier);
        try {
            Files.createDirectories(directory);
            FileChannel owner = FileChannel.open(directory.resolve(".profile.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                if (owner.tryLock() == null) {
                    throw new IllegalStateException("浏览器配置目录正在使用: " + profileId);
                }
                return new BrowserProfileLease(directory, environment, () -> {
                    try {
                        owner.close();
                    } catch (IOException failure) {
                        throw new IllegalStateException("无法释放浏览器配置目录: " + profileId, failure);
                    }
                });
            } catch (IOException | RuntimeException failure) {
                owner.close();
                throw failure;
            }
        } catch (OverlappingFileLockException failure) {
            throw new IllegalStateException("浏览器配置目录正在使用: " + profileId, failure);
        } catch (IOException failure) {
            throw new IllegalStateException("无法打开浏览器配置目录: " + profileId, failure);
        }
    }

    BrowserProfileLease acquirePrivate(BrowserEnvironmentProfile template) {
        BrowserProfileId identifier = new BrowserProfileId("private-" + UUID.randomUUID());
        Path directory = root.resolve(PRIVATE_DIRECTORY).resolve(identifier.value()).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("浏览器配置路径无效");
        }
        if (!activeProfiles.add(directory)) {
            throw new IllegalStateException("浏览器配置目录正在使用: " + identifier.value());
        }
        try {
            Files.createDirectories(directory);
            return new BrowserProfileLease(
                    directory,
                    copyFor(identifier, template),
                    () -> {
                        activeProfiles.remove(directory);
                        delete(directory);
                    });
        } catch (IOException | RuntimeException failure) {
            activeProfiles.remove(directory);
            throw new IllegalStateException("无法创建浏览器私密配置目录: " + identifier.value(), failure);
        }
    }

    private static BrowserEnvironmentProfile copyFor(
            BrowserProfileId identifier,
            BrowserEnvironmentProfile template) {
        if (template.mode() == BrowserEnvironmentMode.NATIVE) {
            return BrowserEnvironmentProfile.nativeProfile(identifier);
        }
        return BrowserEnvironmentProfile.managedProfile(
                identifier,
                template.locale(),
                template.timezone(),
                template.colorScheme(),
                template.deviceScaleFactor(),
                template.permissions());
    }

    private static void delete(Path directory) {
        DirectoryTree.delete(directory);
    }
}
