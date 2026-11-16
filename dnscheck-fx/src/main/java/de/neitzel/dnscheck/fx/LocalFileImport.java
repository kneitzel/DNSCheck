package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.key.BindKeyFileParser;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.ownership.SafeNames;
import de.neitzel.dnscheck.core.zone.LocalZoneDirectory;
import de.neitzel.dnscheck.core.zone.ZoneDefinitionStore;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.xbill.DNS.Name;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Validates and installs files in the selected desktop directory. */
public final class LocalFileImport {
    private final BindKeyFileParser keys = new BindKeyFileParser();
    private final ZoneDefinitionStore definitions = ZoneDefinitionStore.localDirectoryStore();

    public enum Kind { KEY, ZONE }

    /** File material stays local and is never presented as diagnostic text. */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Pending {
        private final Path target;
        private final Kind kind;
        private final byte[] content;

        public Path target() { return target; }
        public Kind kind() { return kind; }
        public boolean exists() { return Files.exists(target, LinkOption.NOFOLLOW_LINKS); }

        public void install(boolean replace) throws IOException {
            Path temp = Files.createTempFile(target.getParent(), ".dnscheck-import-", ".tmp");
            try {
                Files.write(temp, content);
                if (replace) AtomicFiles.replace(temp, target);
                else Files.move(temp, target);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    public static boolean supports(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".key") || name.endsWith(".domain");
    }

    public String keyName(String text) {
        try {
            String name = keys.parseContent(text).name().replaceAll("[^a-zA-Z0-9._-]", "_");
            return name.endsWith(".key") ? name : name + ".key";
        } catch (IOException | RuntimeException failure) {
            return "";
        }
    }

    public Pending pastedKey(Path directory, String filename, String text) throws IOException {
        validateKey(text);
        return new Pending(directory.resolve(filename(filename, ".key")), Kind.KEY, text.getBytes(UTF_8));
    }

    public Pending droppedFile(Path directory, Path source) throws IOException {
        if (!supports(source) || !Files.isRegularFile(source)) throw new IOException("Choose a .key or .domain file.");
        String name = source.getFileName().toString();
        if (name.endsWith(".key")) {
            return pastedKey(directory, name, Files.readString(source));
        }
        String zone = name.substring(0, name.length() - ".domain".length());
        validateZoneName(zone);
        byte[] content = Files.readAllBytes(source);
        Path stagedDirectory = Files.createTempDirectory("dnscheck-definition-");
        Path staged = stagedDirectory.resolve(name);
        try {
            Files.write(staged, content);
            ZoneDefinition definition = definitions.read(staged);
            localFilename(definition.keyFile());
            return new Pending(directory.resolve(name), Kind.ZONE, content);
        } finally {
            Files.deleteIfExists(staged);
            Files.deleteIfExists(stagedDirectory);
        }
    }

    public void createZone(Path directory, String zone, String nameserver, String key) throws IOException {
        String name = validateZoneName(zone.strip()) + ".domain";
        new LocalZoneDirectory(directory).readKey(key);
        Path tempDirectory = Files.createTempDirectory(directory, ".dnscheck-zone-");
        Path temp = tempDirectory.resolve(name);
        try {
            definitions.write(new ZoneDefinition(zone.strip(), nameserver.strip(), key, List.of(), temp));
            Files.move(temp, directory.resolve(name));
        } finally {
            Files.deleteIfExists(temp);
            Files.deleteIfExists(tempDirectory);
        }
    }

    public List<String> keyFiles(Path directory) throws IOException {
        LocalZoneDirectory local = new LocalZoneDirectory(directory);
        try (var paths = Files.list(directory)) {
            return paths.map(Path::getFileName).map(Path::toString).filter(name -> name.endsWith(".key"))
                    .filter(name -> validKey(local, name)).sorted().toList();
        }
    }

    private static String validateZoneName(String value) throws IOException {
        String zone = SafeNames.requireZoneName(value);
        Name.fromString(zone, Name.root);
        return zone;
    }

    private void validateKey(String text) throws IOException {
        try {
            var key = keys.parseContent(text);
            Name.fromString(key.name(), Name.root);
            Base64.getDecoder().decode(key.secret());
        } catch (IOException | RuntimeException failure) {
            throw new IOException("The file does not contain a valid BIND key.");
        }
    }

    private static boolean validKey(LocalZoneDirectory directory, String name) {
        try {
            directory.readKey(name);
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static String filename(String value, String suffix) {
        String name = localFilename(value.strip());
        if (!name.endsWith(suffix) || name.length() == suffix.length()) {
            throw new IllegalArgumentException("Enter a filename ending in " + suffix + ".");
        }
        return name;
    }

    private static String localFilename(String name) {
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.equals(".")
                || name.equals("..") || Path.of(name).getRoot() != null) {
            throw new IllegalArgumentException("Enter a filename in the working folder.");
        }
        return name;
    }
}
