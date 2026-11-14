package de.neitzel.dnscheck.fx;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RecentDirectoriesTest {
    @TempDir Path temp;
    @Test void usesNativeConfigurationLocationsAndIgnoresRelativeEnvironmentOverrides() {
        assertEquals(temp.resolve("Library/Preferences/DNSCheck/config.json"),
                RecentDirectories.configurationFile("Mac OS X", temp, Map.of()));
        Path appdata = temp.resolve("roaming");
        assertEquals(appdata.resolve("DNSCheck/config.json"),
                RecentDirectories.configurationFile("Windows 11", temp, Map.of("APPDATA", appdata.toString())));
        assertEquals(temp.resolve("AppData/Roaming/DNSCheck/config.json"),
                RecentDirectories.configurationFile("Windows 11", temp, Map.of()));
        Path xdg = temp.resolve("xdg");
        assertEquals(xdg.resolve("dnscheck/config.json"),
                RecentDirectories.configurationFile("Linux", temp, Map.of("XDG_CONFIG_HOME", xdg.toString())));
        assertEquals(temp.resolve(".config/dnscheck/config.json"),
                RecentDirectories.configurationFile("Linux", temp, Map.of("XDG_CONFIG_HOME", "relative")));
    }
    @Test void survivesRestartAndMovesRepeatedFoldersToTheFront() throws Exception {
        Path config = temp.resolve("settings/config.json");
        var recent = new RecentDirectories(config);
        assertTrue(recent.existingDirectories().isEmpty());
        assertFalse(Files.exists(config));
        Path first = Files.createDirectory(temp.resolve("first folder_ä"));
        Path second = Files.createDirectory(temp.resolve("second"));
        recent.remember(first);
        recent.remember(second);
        recent.remember(first.resolve("."));
        assertEquals(List.of(first.toRealPath(), second.toRealPath()), new RecentDirectories(config).existingDirectories());
    }
    @Test void capsHistoryAtTenAndChecksForDeletedFoldersAndFiles() throws Exception {
        var recent = new RecentDirectories(temp.resolve("settings/config.json"));
        for (int index = 0; index < 12; index++) recent.remember(Files.createDirectory(temp.resolve("folder-" + index)));
        assertEquals(10, recent.existingDirectories().size());
        assertEquals(temp.resolve("folder-11").toRealPath(), recent.existingDirectories().getFirst());
        Files.delete(temp.resolve("folder-11"));
        Files.delete(temp.resolve("folder-10"));
        Files.writeString(temp.resolve("folder-10"), "now a file");
        assertEquals(8, recent.existingDirectories().size());
        assertEquals(temp.resolve("folder-9").toRealPath(), recent.existingDirectories().getFirst());
    }
    @Test void canonicalPathsDeduplicateSymlinkAliases() throws Exception {
        Path directory = Files.createDirectory(temp.resolve("folder"));
        Path alias = Files.createSymbolicLink(temp.resolve("alias"), directory);
        var recent = new RecentDirectories(temp.resolve("config.json"));
        recent.remember(alias);
        recent.remember(directory);
        assertEquals(List.of(directory.toRealPath()), recent.existingDirectories());
    }
    @Test void preservesOtherSettingsAndSkipsMalformedPathEntries() throws Exception {
        Path config = temp.resolve("config.json");
        var mapper = new ObjectMapper();
        var settings = mapper.createObjectNode();
        settings.putObject("appearance").put("theme", "dark");
        var entries = settings.putArray("recentDirectories");
        entries.add(12).add("relative").add("invalid\0path").add(temp.resolve("missing").toString());
        mapper.writeValue(config.toFile(), settings);
        var recent = new RecentDirectories(config);
        assertTrue(recent.existingDirectories().isEmpty());
        recent.remember(temp);
        assertEquals("dark", mapper.readTree(config.toFile()).path("appearance").path("theme").asText());
        assertEquals(List.of(temp.toRealPath()), recent.existingDirectories());
    }
    @Test void corruptConfigurationIsReportedAndNotOverwritten() throws Exception {
        Path config = temp.resolve("config.json");
        Files.writeString(config, "broken JSON");
        var recent = new RecentDirectories(config);
        assertThrows(IOException.class, recent::existingDirectories);
        assertThrows(IOException.class, () -> recent.remember(temp));
        assertEquals("broken JSON", Files.readString(config));
    }
    @Test void writeFailureDoesNotReplaceAnExistingParentFile() throws Exception {
        Path parent = temp.resolve("settings");
        Files.writeString(parent, "keep");
        var recent = new RecentDirectories(parent.resolve("config.json"));
        assertThrows(IOException.class, () -> recent.remember(temp));
        assertEquals("keep", Files.readString(parent));
    }
}
