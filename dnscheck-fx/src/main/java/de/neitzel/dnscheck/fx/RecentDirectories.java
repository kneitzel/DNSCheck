package de.neitzel.dnscheck.fx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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

    /** Startup uses the last opened folder, even if older entries still exist. */
    public synchronized Optional<Path> lastDirectory() throws IOException {
        JsonNode entries = read().get("recentDirectories");
        if (entries == null || entries.isEmpty() || !entries.get(0).isTextual()) return Optional.empty();
        return existingDirectory(entries.get(0).textValue());
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

    public synchronized Locale language() throws IOException {
        JsonNode value = read().get("language");
        return value == null || !value.isTextual() ? null : Locale.forLanguageTag(value.textValue());
    }

    public synchronized void rememberLanguage(Locale locale) throws IOException {
        ObjectNode settings = read();
        settings.put("language", locale.toLanguageTag());
        write(settings);
    }

    /** Application appearance preference, independent of directory and docking layouts. */
    public synchronized String theme() throws IOException {
        JsonNode value = read().get("theme");
        if (value == null) return "light";
        if (!value.isTextual()) throw new IOException("Expected theme to be a string in " + configuration);
        return value.textValue();
    }

    public synchronized void rememberTheme(String id) throws IOException {
        if (!java.util.Set.of("light", "dark", "high-contrast").contains(id))
            throw new IllegalArgumentException("Unknown DNSCheck theme: " + id);
        ObjectNode settings = read();
        settings.put("theme", id);
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
            existingDirectory(entry.textValue()).filter(path -> !recent.contains(path)).ifPresent(recent::add);
            if (recent.size() == LIMIT) break;
        }
        return List.copyOf(recent);
    }

    private static Optional<Path> existingDirectory(String value) {
        try {
            Path path = Path.of(value);
            return path.isAbsolute() && Files.isDirectory(path)
                    ? Optional.of(path.toRealPath()) : Optional.empty();
        } catch (IOException | InvalidPathException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private void write(ObjectNode settings) throws IOException {
        Files.createDirectories(configuration.getParent());
        Path temporary = Files.createTempFile(configuration.getParent(), "config-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), settings);
            AtomicFiles.replace(temporary, configuration);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
