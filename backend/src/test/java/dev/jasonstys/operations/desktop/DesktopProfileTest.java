/*
 * File: DesktopProfileTest.java
 * Purpose: Real disposable profile/settings/locking regression tests, never production user data.
 * Symbols and temporary variables are indexed in docs/code-index.md.
 */
package dev.jasonstys.operations.desktop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Check actual disk behavior and OS ownership without reading an installed user's workspace. */
class DesktopProfileTest {
    @TempDir
    Path temporary;

    @Test
    void resolvesIndependentOsRootsAndRequiresExplicitTestIsolation() {
        Path home = temporary.resolve("home");
        assertEquals(home.resolve("AppData/Local/IntegrationOperationsConsole"),
                DesktopProfile.location(Map.of(), "Windows 11", home));
        assertEquals(home.resolve("Library/Application Support/IntegrationOperationsConsole"),
                DesktopProfile.location(Map.of(), "Mac OS X", home));
        assertEquals(home.resolve(".local/share/IntegrationOperationsConsole"),
                DesktopProfile.location(Map.of(), "Linux", home));
        assertEquals(temporary.resolve("local/IntegrationOperationsConsole"), DesktopProfile.location(
                Map.of("LOCALAPPDATA", temporary.resolve("local").toString()), "Windows", home));
        assertEquals(temporary.resolve("xdg/IntegrationOperationsConsole"), DesktopProfile.location(
                Map.of("XDG_DATA_HOME", temporary.resolve("xdg").toString()), "Linux", home));
        assertThrows(IllegalArgumentException.class,
                () -> DesktopProfile.location(Map.of("IOC_DESKTOP_TEST", "1"), "Windows", home));
        assertThrows(IllegalArgumentException.class, () -> DesktopProfile.location(
                Map.of("IOC_DESKTOP_TEST", "1", "IOC_DESKTOP_DATA", "relative"), "Windows", home));
        assertEquals(temporary, DesktopProfile.location(
                Map.of("IOC_DESKTOP_TEST", "1", "IOC_DESKTOP_DATA", temporary.toString()), "Windows", home));
        assertFalse(DesktopProfile.location(Map.of("IOC_DESKTOP_DATA", temporary.toString()),
                "Linux", home).equals(temporary));
    }

    @Test
    void settingsAndDataSurviveReopenAndConcurrentOwnerIsRejected() throws Exception {
        Path root = temporary.resolve("workspace");
        byte[] original;
        String password;
        try (DesktopProfile first = DesktopProfile.open(root)) {
            assertEquals(root, first.root());
            assertTrue(first.databaseUrl().startsWith("jdbc:h2:file:"));
            assertTrue(first.databaseUrl().contains("DB_CLOSE_ON_EXIT=FALSE"));
            assertEquals("workspace", first.username());
            password = first.password();
            assertTrue(password.matches("[a-f0-9]{64}"));
            assertThrows(IOException.class, () -> DesktopProfile.open(root));
            original = Files.readAllBytes(root.resolve("desktop.properties"));
            Files.writeString(root.resolve("database/retain.txt"), "independent retained fixture");
        }
        try (DesktopProfile reopened = DesktopProfile.open(root)) {
            assertEquals(password, reopened.password());
            assertArrayEquals(original, Files.readAllBytes(root.resolve("desktop.properties")));
            assertEquals("independent retained fixture", Files.readString(root.resolve("database/retain.txt")));
        }
    }

    @Test
    void invalidExistingSettingsAndCustomConfigurationAreNeverReset() throws Exception {
        Path root = temporary.resolve("existing");
        Files.createDirectory(root);
        Path settings = root.resolve("desktop.properties");
        Files.writeString(settings, "schema=9\ndatabase.username=operator\n");
        byte[] bad = Files.readAllBytes(settings);
        assertThrows(IOException.class, () -> DesktopProfile.open(root));
        assertArrayEquals(bad, Files.readAllBytes(settings));
        String custom = "schema=1\ndatabase.username=operator\ndatabase.password=synthetic-private-setting\n"
                + "database.url=jdbc:h2:mem:explicit_custom\noperator.note=preserve-this-setting\n";
        Files.writeString(settings, custom);
        try (DesktopProfile profile = DesktopProfile.open(root)) {
            assertEquals("jdbc:h2:mem:explicit_custom", profile.databaseUrl());
            assertEquals("operator", profile.username());
            assertEquals(custom, Files.readString(settings));
        }
        assertThrows(IOException.class, () -> DesktopProfile.open(temporary.resolve("unsafe;INIT=example")));
        assertFalse(Files.exists(temporary.resolve("unsafe;INIT=example")));
    }
}
