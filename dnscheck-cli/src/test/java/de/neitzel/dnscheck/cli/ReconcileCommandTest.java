package de.neitzel.dnscheck.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ReconcileCommandTest {
    @TempDir Path temp;

    @Test
    void failsForAnEmptyDirectoryOrAMissingBareZone() {
        assertEquals(1, new CommandLine(new DnsCheckCli()).execute(
                "reconcile", "--directory", temp.toString(), "--dry-run"));
        assertEquals(1, new CommandLine(new DnsCheckCli()).execute(
                "reconcile", "example.com", "--directory", temp.toString(), "--dry-run"));
    }

    @Test
    void malformedDefinitionAndMissingKeyFailWithoutNetworkAccess() throws Exception {
        Files.writeString(temp.resolve("bad.domain"), "{}");
        Files.writeString(temp.resolve("example.com.domain"), """
                {"zone":"example.com","nameserver":"ns.example.com","keyFile":"missing.key"}
                """);
        assertEquals(1, new CommandLine(new DnsCheckCli()).execute(
                "reconcile", "--directory", temp.toString(), "--dry-run"));
    }

    @Test
    void obsoleteDirectoryOptionsAreRejected() {
        for (String option : new String[]{"--zones-dir", "--keys-dir"}) {
            assertEquals(2, new CommandLine(new DnsCheckCli()).execute("reconcile", option, temp.toString()));
        }
    }
}
