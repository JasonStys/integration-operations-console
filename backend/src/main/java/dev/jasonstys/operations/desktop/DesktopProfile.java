/*
 * File: DesktopProfile.java
 * Purpose: Own a durable, locked OS-local workspace without resetting existing settings/databases.
 * State: root/settings/lock belong to one process. Exact declarations: docs/code-index.md.
 */
package dev.jasonstys.operations.desktop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;

/** A dedicated private profile; existing operator settings are read, never silently regenerated. */
public final class DesktopProfile implements AutoCloseable {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path root;
    private final Properties settings;
    private final FileChannel channel;
    private final FileLock lock;

    private DesktopProfile(Path root, Properties settings, FileChannel channel, FileLock lock) {
        this.root = root;
        this.settings = settings;
        this.channel = channel;
        this.lock = lock;
    }

    /** Resolve an OS-local root; test overrides require an explicit test mode and absolute path. */
    public static Path location(Map<String, String> environment, String os, Path home) {
        if ("1".equals(environment.get("IOC_DESKTOP_TEST"))) {
            String selected = environment.get("IOC_DESKTOP_DATA");
            if (selected == null || !Path.of(selected).isAbsolute()) {
                throw new IllegalArgumentException("Native acceptance needs its own absolute test profile");
            }
            return Path.of(selected).normalize();
        }
        String name = os.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("win")) {
            String local = environment.get("LOCALAPPDATA");
            return (local == null || local.isBlank() ? home.resolve("AppData/Local") : Path.of(local))
                    .resolve("IntegrationOperationsConsole").toAbsolutePath().normalize();
        }
        if (name.contains("mac")) {
            return home.resolve("Library/Application Support/IntegrationOperationsConsole");
        }
        String local = environment.get("XDG_DATA_HOME");
        return (local == null || local.isBlank() ? home.resolve(".local/share") : Path.of(local))
                .resolve("IntegrationOperationsConsole").toAbsolutePath().normalize();
    }

    /** Create only a dedicated root, take its OS lock, and preserve existing credentials/settings. */
    public static DesktopProfile open(Path selected) throws IOException {
        Path root = selected.toAbsolutePath().normalize();
        if (root.toString().contains(";") || root.toString().contains("\n")
                || root.toString().contains("\r") || root.getParent() == null) {
            throw new IOException("Unsupported local profile path; existing files were preserved");
        }
        Files.createDirectories(root);
        requireRegularDirectory(root);
        protect(root, true);
        Path lockPath = root.resolve("instance.lock");
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        FileLock lock = null;
        try {
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException busy) {
                throw new IOException("This workspace is already open", busy);
            }
            if (lock == null) {
                throw new IOException("This workspace is already open");
            }
            protect(lockPath, false);
            Path settingsPath = root.resolve("desktop.properties");
            Properties settings = new Properties();
            if (Files.exists(settingsPath, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(settingsPath, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Preserve invalid existing desktop settings and seek support");
                }
                try (var input = Files.newInputStream(settingsPath, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] bytes = input.readNBytes(16385);
                    if (bytes.length > 16384) {
                        throw new IOException("Existing desktop settings exceed the supported bound");
                    }
                    settings.load(new ByteArrayInputStream(bytes));
                }
            } else {
                settings.setProperty("schema", "1");
                settings.setProperty("database.username", "workspace");
                settings.setProperty("database.password", secret());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                settings.store(bytes, "Private local application settings; never publish this file");
                Files.write(settingsPath, bytes.toByteArray(), StandardOpenOption.CREATE_NEW);
                protect(settingsPath, false);
            }
            if (!"1".equals(settings.getProperty("schema"))
                    || settings.getProperty("database.username", "").isBlank()
                    || settings.getProperty("database.password", "").isBlank()) {
                throw new IOException("Existing settings need review; no credentials were reset");
            }
            Path database = root.resolve("database");
            Files.createDirectories(database);
            requireRegularDirectory(database);
            protect(database, true);
            return new DesktopProfile(root, settings, channel, lock);
        } catch (IOException | RuntimeException failure) {
            if (lock != null) {
                lock.release();
            }
            channel.close();
            throw failure;
        }
    }

    /** A per-launch value; not a shared login or a credential for any external provider. */
    public static String secret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** @return profile root owned by this process */
    public Path root() {
        return root;
    }

    /** @return local file URL unless the operator retained an explicit custom URL in settings */
    public String databaseUrl() {
        return settings.getProperty("database.url", "jdbc:h2:file:"
                + root.resolve("database/operations").toString().replace('\\', '/')
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE");
    }

    /** @return private datasource username; never expose through the browser */
    public String username() {
        return settings.getProperty("database.username");
    }

    /** @return private datasource password; never expose through logs or browser */
    public String password() {
        return settings.getProperty("database.password");
    }

    /** Restrict newly managed files/directories to the OS owner; administrators remain trusted. */
    public static void protect(Path path, boolean directory) throws IOException {
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            var entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (directory) {
                entry.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
            }
            acl.setAcl(java.util.List.of(entry.build()));
        } else if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        }
    }

    private static void requireRegularDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Profile directory must not be a link");
        }
    }

    /** Release ownership only after the launcher has completed Spring/database shutdown. */
    @Override
    public void close() throws IOException {
        if (lock.isValid()) {
            lock.release();
        }
        channel.close();
    }
}
