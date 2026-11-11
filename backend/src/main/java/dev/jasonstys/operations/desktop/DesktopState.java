/*
 * File: DesktopState.java
 * Purpose: Per-launch session, provenance/readiness and coordinated service/window/profile shutdown.
 * State: only this launch owns context/window/root; symbols are indexed in docs/code-index.md.
 */
package dev.jasonstys.operations.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.springframework.context.ConfigurableApplicationContext;

/** A local launch session, not a substitute for production user authentication. */
public final class DesktopState {
    public static final String VERSION = "1.1.0";
    private final DesktopProfile profile;
    private final String token = DesktopProfile.secret();
    private final String instance = DesktopProfile.secret();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Properties build = new Properties();
    private ConfigurableApplicationContext context;
    private JFrame window;
    private String origin;

    /** @param profile exclusively locked local data, held until the database closes */
    public DesktopState(DesktopProfile profile) throws IOException {
        this.profile = profile;
        try (var stream = DesktopState.class.getResourceAsStream("/desktop-build.properties")) {
            if (stream != null) {
                build.load(stream);
            }
        }
    }

    /** Bind the actual listener before constructing the native window; do not publish readiness yet. */
    public void attach(ConfigurableApplicationContext started, int port) {
        context = started;
        origin = "http://127.0.0.1:" + port;
    }

    /** Publish only after both the service and the required native window have started successfully. */
    public void ready(boolean test) throws IOException {
        if (context == null || !context.isActive()) {
            throw new IOException("Native application is not running");
        }
        String source = build.getProperty("source", "unrecorded-source-build");
        if (!source.matches("[a-f0-9]{40}|unrecorded-source-build")) {
            throw new IOException("Invalid embedded build identity");
        }
        String status = "{\"version\":\"" + VERSION + "\",\"instance\":\"" + instance
                + "\",\"origin\":\"" + origin + "\",\"source\":\"" + source + "\"}\n";
        Path ready = profile.root().resolve("desktop-ready.json");
        Files.writeString(ready, status, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        DesktopProfile.protect(ready, false);
        if (test) {
            Path session = profile.root().resolve("desktop-test-session.json");
            Files.writeString(session, "{\"url\":\"" + launchUrl() + "\"}\n",
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            DesktopProfile.protect(session, false);
        }
        System.out.println("IOC_DESKTOP_READY version=" + VERSION + " instance=" + instance);
    }

    /** @return process-local browser URL; never log or export its fragment session value */
    public String launchUrl() {
        return origin + "/#ioc=" + token;
    }

    /** @return public ownership/version metadata without settings, tokens or filesystem paths */
    public Map<String, Object> health() {
        return Map.of("mode", "private-local-demo", "version", VERSION, "instance", instance,
                "source", build.getProperty("source", "unrecorded-source-build"),
                "sourceDirty", Boolean.parseBoolean(build.getProperty("dirty", "true")),
                "nativeWindowVisible", window != null && window.isShowing());
    }

    /** @return whether a supplied per-launch value matches without ordinary string comparison */
    public boolean authorized(String value) {
        return value != null && value.length() == token.length()
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII),
                        value.getBytes(StandardCharsets.UTF_8));
    }

    /** @return whether graceful shutdown has started */
    public boolean stopping() {
        return stopping.get();
    }

    /** @return truthful persistence scope for the active datasource, never its URL or credentials */
    public String persistence() {
        String url = context.getEnvironment().getProperty("spring.datasource.url", "");
        return url.startsWith("jdbc:h2:file:") ? "durable-local-file" : "custom-database-configuration";
    }

    /** @param frame owned small native status/help/quit window */
    public void window(JFrame frame) {
        window = frame;
    }

    /** Start owned shutdown after the caller has flushed its response; never force-kill active writes. */
    public void requestClose() {
        if (stopping.compareAndSet(false, true)) {
            Thread.ofPlatform().name("ioc-desktop-close").start(() -> {
                try {
                    close();
                    System.exit(0);
                } catch (Exception failure) {
                    System.err.println("Desktop close needs review; existing data was preserved");
                    System.exit(2);
                }
            });
        }
    }

    /** Close Spring/database first, then the window and ownership lock. No data is deleted. */
    public synchronized void close() throws Exception {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        stopping.set(true);
        if (context != null) {
            context.close();
        }
        if (window != null) {
            SwingUtilities.invokeAndWait(window::dispose);
        }
        // On failure retain ownership until this process exits, rather than admit another writer early.
        Files.deleteIfExists(profile.root().resolve("desktop-ready.json"));
        Files.deleteIfExists(profile.root().resolve("desktop-test-session.json"));
        profile.close();
    }
}
