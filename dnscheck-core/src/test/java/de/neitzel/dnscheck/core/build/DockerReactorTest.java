package de.neitzel.dnscheck.core.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Validates Maven against the files actually copied before Docker's first RUN. */
class DockerReactorTest {
    @TempDir Path build;

    @Test
    void serviceAndCliReactorLoadsWithDockerBuildInputs() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("Dockerfile"))) {
            root = root.getParent();
            assertNotNull(root, "Cannot locate repository Dockerfile");
        }
        boolean copied = false;
        for (String line : Files.readAllLines(root.resolve("Dockerfile"))) {
            if (line.startsWith("RUN ")) break;
            if (!line.startsWith("COPY ")) continue;
            String[] copy = line.trim().split("\\s+");
            assertEquals(3, copy.length, "Extend this build-input harness for new COPY syntax");
            Path source = root.resolve(copy[1]);
            Path destination = build.resolve(copy[2]);
            if (copy[2].endsWith("/") || copy[2].equals(".")) {
                destination = destination.resolve(source.getFileName());
            }
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination);
            copied = true;
        }
        assertTrue(copied);
        Path output = build.resolve("maven.log");
        Process process = new ProcessBuilder("mvn", "-o", "-B", "-ntp", "-pl",
                "dnscheck-service,dnscheck-cli", "-am", "validate")
                .directory(build.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Docker reactor validation timed out");
            assertEquals(0, process.exitValue(), () -> {
                try { return Files.readString(output); }
                catch (java.io.IOException e) { return e.toString(); }
            });
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
