package de.neitzel.dnscheck.core.key;

import de.neitzel.dnscheck.core.ownership.OwnerFolder;
import de.neitzel.dnscheck.core.ownership.ScopedRelativePath;
import de.neitzel.dnscheck.core.ownership.ScopedStoragePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads and writes TSIG key files under scoped owner folders.
 */
public final class ScopedKeyFileStore {

    private final Path keysRoot;
    private final BindKeyFileParser parser;

    public ScopedKeyFileStore(Path keysRoot) {
        this(keysRoot, new BindKeyFileParser());
    }

    ScopedKeyFileStore(Path keysRoot, BindKeyFileParser parser) {
        if (keysRoot == null) {
            throw new IllegalArgumentException("keys root is required");
        }
        this.keysRoot = keysRoot;
        this.parser = parser;
    }

    public static OwnerFolder personalFolderFor(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        return OwnerFolder.parse(username + ".u");
    }

    public static OwnerFolder groupFolderFor(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("group name is required");
        }
        return OwnerFolder.parse(groupName + ".g");
    }

    public List<String> listBasenames(OwnerFolder ownerFolder) throws IOException {
        Path ownerDir = ownerDirectory(ownerFolder);
        if (!Files.isDirectory(ownerDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(ownerDir)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase().endsWith(".key"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    public String readContent(OwnerFolder ownerFolder, String basename) throws IOException {
        Path keyFile = keyPath(ownerFolder, basename);
        if (!Files.isRegularFile(keyFile)) {
            throw new IOException("Key file not found: " + basename);
        }
        return Files.readString(keyFile);
    }

    public String scopedPath(OwnerFolder ownerFolder, String basename) {
        return new ScopedRelativePath(ownerFolder, basename).value();
    }

    public void create(OwnerFolder ownerFolder, String basename, String content)
            throws IOException, KeyAlreadyExistsException {
        validateContent(content);
        Path keyFile = keyPath(ownerFolder, basename);
        if (Files.exists(keyFile)) {
            throw new KeyAlreadyExistsException(basename);
        }
        Files.createDirectories(keyFile.getParent());
        Files.writeString(keyFile, content);
    }

    public void replace(OwnerFolder ownerFolder, String basename, String content) throws IOException {
        validateContent(content);
        Path keyFile = keyPath(ownerFolder, basename);
        if (!Files.isRegularFile(keyFile)) {
            throw new IOException("Key file not found: " + basename);
        }
        Files.writeString(keyFile, content);
    }

    public void delete(OwnerFolder ownerFolder, String basename) throws IOException {
        Path keyFile = keyPath(ownerFolder, basename);
        if (!Files.isRegularFile(keyFile)) {
            throw new IOException("Key file not found: " + basename);
        }
        Files.delete(keyFile);
    }

    private Path ownerDirectory(OwnerFolder ownerFolder) {
        return ScopedStoragePaths.resolveUnderRoot(keysRoot, ownerFolder.folderName());
    }

    private Path keyPath(OwnerFolder ownerFolder, String basename) {
        return ScopedStoragePaths.resolveKeyFile(keysRoot, ownerFolder.folderName() + "/" + basename);
    }

    private void validateContent(String content) throws IOException {
        parser.parseContent(content);
    }
}
