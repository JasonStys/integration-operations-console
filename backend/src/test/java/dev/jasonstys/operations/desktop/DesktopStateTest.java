/*
 * File: DesktopStateTest.java
 * Purpose: Verify readiness/session privacy and database-before-profile shutdown ownership with real files.
 * State: exclusively temporary workspace/context; locations: docs/code-index.md.
 */
package dev.jasonstys.operations.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Shutdown acknowledgement and completed resource close are distinct lifecycle stages. */
class DesktopStateTest {
    @TempDir
    Path temporary;

    @Test
    void failedServiceCloseDoesNotReleaseWorkspaceOwnershipEarly() throws Exception {
        DesktopProfile profile = DesktopProfile.open(temporary.resolve("failed-close"));
        AtomicBoolean failure = new AtomicBoolean(true);
        GenericApplicationContext context = new GenericApplicationContext() {
            @Override
            public void close() {
                if (failure.get()) {
                    throw new IllegalStateException("synthetic close failure");
                }
                super.close();
            }
        };
        context.refresh();
        DesktopState state = new DesktopState(profile);
        state.attach(context, 38125);
        try {
            assertThrows(IllegalStateException.class, state::close);
            assertTrue(state.stopping());
            assertThrows(IOException.class, () -> DesktopProfile.open(profile.root()));
        } finally {
            // Test-owned stand-in for process exit; production never hands this failed ownership to another app.
            failure.set(false);
            context.close();
            profile.close();
        }
    }

    @Test
    void correlatedReadinessDoesNotPublishTheSessionAndCloseRetainsData() throws Exception {
        Path root = temporary.resolve("owned");
        DesktopProfile profile = DesktopProfile.open(root);
        byte[] settings = Files.readAllBytes(root.resolve("desktop.properties"));
        Path fixture = root.resolve("database/retained.synthetic");
        Files.writeString(fixture, "synthetic-data");
        GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture",
                Map.of("spring.datasource.url", profile.databaseUrl())));
        AtomicBoolean closedWhileOwned = new AtomicBoolean();
        context.getDefaultListableBeanFactory().registerDisposableBean("databaseCloseWitness", () -> {
            assertThrows(IOException.class, () -> DesktopProfile.open(root));
            closedWhileOwned.set(true);
        });
        context.refresh();
        DesktopState state = new DesktopState(profile);
        assertThrows(IOException.class, () -> state.ready(true));
        assertFalse(Files.exists(root.resolve("desktop-ready.json")));
        state.attach(context, 38123);
        assertFalse(Files.exists(root.resolve("desktop-ready.json")));
        state.ready(true);
        String session = state.launchUrl().split("#ioc=")[1];
        assertTrue(session.matches("[a-f0-9]{64}"));
        assertTrue(state.authorized(session));
        assertFalse(state.authorized(null));
        assertFalse(state.authorized(session + "0"));
        assertFalse(state.authorized("c".repeat(64)));
        assertEquals("durable-local-file", state.persistence());
        assertFalse(state.health().toString().contains(session));
        assertFalse(Files.readString(root.resolve("desktop-ready.json")).contains(session));
        assertTrue(Files.readString(root.resolve("desktop-test-session.json")).contains(session));
        state.close();
        state.close();
        assertTrue(closedWhileOwned.get());
        assertTrue(state.stopping());
        assertFalse(Files.exists(root.resolve("desktop-ready.json")));
        assertFalse(Files.exists(root.resolve("desktop-test-session.json")));
        assertEquals("synthetic-data", Files.readString(fixture));
        assertTrue(java.util.Arrays.equals(settings, Files.readAllBytes(root.resolve("desktop.properties"))));
        try (DesktopProfile reopened = DesktopProfile.open(root)) {
            assertEquals(profile.databaseUrl(), reopened.databaseUrl());
        }
    }

    @Test
    void normalReadinessHasNoTestSessionFileAndHonorsConfiguredDatasource() throws Exception {
        DesktopProfile profile = DesktopProfile.open(temporary.resolve("normal"));
        GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture",
                Map.of("spring.datasource.url", "jdbc:postgresql://localhost/custom")));
        context.refresh();
        DesktopState state = new DesktopState(profile);
        try {
            state.attach(context, 38124);
            state.ready(false);
            assertFalse(Files.exists(profile.root().resolve("desktop-test-session.json")));
            assertEquals("custom-database-configuration", state.persistence());
        } finally {
            state.close();
        }
    }
}
