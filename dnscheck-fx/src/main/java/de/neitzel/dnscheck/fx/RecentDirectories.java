package de.neitzel.dnscheck.fx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Persistent, most-recent-first history of successfully opened local directories. */
@Singleton
public class RecentDirectories {
    private static final int LIMIT = 10;
    private final Path configuration;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecentDirectories() {
        this(configurationFile(System.getProperty("os.name", ""),
                Path.of(System.getProperty("user.home")), System.getenv()));
    }

    RecentDirectories(Path configuration) {
        this.configuration = configuration.toAbsolutePath().normalize();
    }

    static Path configurationFile(String operatingSystem, Path home, Map<String, String> environment) {
        String os = operatingSystem.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac") || os.equals("darwin")) {
            return home.resolve("Library/Preferences/DNSCheck/config.json");
        }
        if (os.startsWith("windows")) {
            return absoluteOverride(environment.get("APPDATA"), home.resolve("AppData/Roaming"))
                    .resolve("DNSCheck/config.json");
        }
        return absoluteOverride(environment.get("XDG_CONFIG_HOME"), home.resolve(".config"))
                .resolve("dnscheck/config.json");
    }

    private static Path absoluteOverride(String value, Path fallback) {
        if (value != null && !value.isBlank()) {
            try {
                Path path = Path.of(value);
                if (path.isAbsolute()) return path;
            } catch (InvalidPathException ignored) {
                // An invalid environment override must not prevent application startup.
            }
        }
        return fallback;
    }

    /** Recheck the filesystem every time the submenu is opened. No zone files or keys are read. */
    public synchronized List<Path> existingDirectories() throws IOException {
        return directories(read());
    }

    public synchronized void remember(Path directory) throws IOException {
        Path opened = directory.toRealPath();
        if (!Files.isDirectory(opened)) throw new IOException("Not a directory: " + directory);
        ObjectNode settings = read();
        List<Path> recent = new ArrayList<>(directories(settings));
        recent.remove(opened);
        recent.addFirst(opened);
        var array = settings.putArray("recentDirectories");
        recent.stream().limit(LIMIT).map(Path::toString).forEach(array::add);
        write(settings);
    }

    private ObjectNode read() throws IOException {
        if (!Files.exists(configuration)) return mapper.createObjectNode();
        JsonNode settings = mapper.readTree(configuration.toFile());
        if (!(settings instanceof ObjectNode object)) {
            throw new IOException("Expected a JSON object in " + configuration);
        }
        JsonNode recent = object.get("recentDirectories");
        if (recent != null && !recent.isArray()) {
            throw new IOException("Expected recentDirectories to be an array in " + configuration);
        }
        return object;
    }

    private List<Path> directories(ObjectNode settings) {
        List<Path> recent = new ArrayList<>();
        JsonNode entries = settings.get("recentDirectories");
        if (entries == null) return List.of();
        for (JsonNode entry : entries) {
            if (!entry.isTextual()) continue;
            try {
                Path path = Path.of(entry.textValue());
                if (!path.isAbsolute() || !Files.isDirectory(path)) continue;
                Path canonical = path.toRealPath();
                if (!recent.contains(canonical)) recent.add(canonical);
                if (recent.size() == LIMIT) break;
            } catch (IOException | InvalidPathException | SecurityException ignored) {
                // Missing, inaccessible or malformed entries do not belong in the menu.
            }
        }
        return List.copyOf(recent);
    }

    private void write(ObjectNode settings) throws IOException {
        Files.createDirectories(configuration.getParent());
        Path temporary = Files.createTempFile(configuration.getParent(), "config-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), settings);
            try {
                Files.move(temporary, configuration, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, configuration, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
