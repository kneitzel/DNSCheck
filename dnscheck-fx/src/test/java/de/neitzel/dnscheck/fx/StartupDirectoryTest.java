package de.neitzel.dnscheck.fx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StartupDirectoryTest {
    @TempDir Path temp;

    @Test void restoresTheLastOpenedFolderAcrossInstances() throws Exception {
        Path config = temp.resolve("config.json");
        var recent = new RecentDirectories(config);
        recent.remember(Files.createDirectory(temp.resolve("first")));
        Path last = Files.createDirectory(temp.resolve("last"));
        recent.remember(last);
        assertEquals(last.toRealPath().toString(), DnsCheckFxApp.session(new RecentDirectories(config), null).getDirectory());
    }
    @Test void skipsRemovedFoldersAndFallsBackToTheMostRecentExistingOne() throws Exception {
        var recent = new RecentDirectories(temp.resolve("config.json"));
        Path previous = Files.createDirectory(temp.resolve("previous"));
        Path last = Files.createDirectory(temp.resolve("last"));
        recent.remember(previous);
        recent.remember(last);
        Files.delete(last);
        assertEquals(previous.toRealPath().toString(), DnsCheckFxApp.session(recent, " ").getDirectory());
        Files.delete(previous);
        assertEquals(".", DnsCheckFxApp.session(recent, null).getDirectory());
    }
    @Test void explicitEnvironmentDirectoryTakesPriority() throws Exception {
        var recent = new RecentDirectories(temp.resolve("config.json"));
        recent.remember(Files.createDirectory(temp.resolve("recent")));
        String override = temp.resolve("explicit").toString();
        assertEquals(override, DnsCheckFxApp.session(recent, override).getDirectory());
    }
    @Test void missingOrCorruptHistoryDoesNotPreventStartup() throws Exception {
        Path config = temp.resolve("config.json");
        var recent = new RecentDirectories(config);
        assertEquals(".", DnsCheckFxApp.session(recent, null).getDirectory());
        Files.writeString(config, "invalid JSON");
        assertEquals(".", DnsCheckFxApp.session(recent, null).getDirectory());
        assertEquals("invalid JSON", Files.readString(config));
    }
}
