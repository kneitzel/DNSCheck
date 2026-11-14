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

    /**
     * Root of the keys directory. Key files are stored in owner folders under this path.
     */
    private final Path keysRoot;

    /**
     * Parser used to reject content that is not a BIND key block before it is written.
     */
    private final BindKeyFileParser parser;

    /**
     * Creates a store rooted at {@code keysRoot} that validates content with a new BIND key parser.
     *
     * @param keysRoot the keys directory.
     * @throws IllegalArgumentException when {@code keysRoot} is missing.
     */
    public ScopedKeyFileStore(Path keysRoot) {
        this(keysRoot, new BindKeyFileParser());
    }

    /**
     * Creates a store that validates written content with {@code parser}.
     *
     * @param keysRoot the keys directory.
     * @param parser the parser that checks BIND key syntax.
     * @throws IllegalArgumentException when {@code keysRoot} is missing.
     */
    ScopedKeyFileStore(Path keysRoot, BindKeyFileParser parser) {
        if (keysRoot == null) {
            throw new IllegalArgumentException("keys root is required");
        }
        this.keysRoot = keysRoot;
        this.parser = parser;
    }

    /**
     * Builds the operator owner folder {@code {username}.u}.
     *
     * @param username the operator name encoded in the folder.
     * @return the personal owner folder.
     * @throws IllegalArgumentException when {@code username} is blank or is not a valid owner identifier.
     */
    public static OwnerFolder personalFolderFor(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        return OwnerFolder.parse(username + ".u");
    }

    /**
     * Builds the group owner folder {@code {groupName}.g}.
     *
     * @param groupName the group name encoded in the folder.
     * @return the group owner folder.
     * @throws IllegalArgumentException when {@code groupName} is blank or is not a valid owner identifier.
     */
    public static OwnerFolder groupFolderFor(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("group name is required");
        }
        return OwnerFolder.parse(groupName + ".g");
    }

    /**
     * Lists {@code .key} files in the owner folder, sorted by file name.
     * A missing owner folder yields an empty list.
     *
     * @param ownerFolder the owner folder to list.
     * @return the key file basenames.
     * @throws IOException when the owner folder exists but cannot be listed.
     */
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

    /**
     * Reads the text of one key file.
     *
     * @param ownerFolder the owner folder that contains the key file.
     * @param basename the key file basename.
     * @return the key file text.
     * @throws IOException when the file is missing or cannot be read.
     * @throws IllegalArgumentException when {@code basename} is not a safe key file name.
     */
    public String readContent(OwnerFolder ownerFolder, String basename) throws IOException {
        Path keyFile = keyPath(ownerFolder, basename);
        if (!Files.isRegularFile(keyFile)) {
            throw new IOException("Key file not found: " + basename);
        }
        return Files.readString(keyFile);
    }

    /**
     * Builds the scoped path {@code {owner}/{basename}} for a key file in the owner folder.
     *
     * @param ownerFolder the owner folder that contains the key file.
     * @param basename the key file basename.
     * @return the scoped key path.
     * @throws IllegalArgumentException when {@code basename} is not a safe {@code .key} file name.
     */
    public String scopedPath(OwnerFolder ownerFolder, String basename) {
        return new ScopedRelativePath(ownerFolder, basename).value();
    }

    /**
     * Creates a key file after checking that the content is a BIND key block.
     * Fails when that basename already exists. The caller decides whether zone definitions may reference it.
     *
     * @param ownerFolder the owner folder that will contain the key file.
     * @param basename the key file basename.
     * @param content the BIND key text to write.
     * @throws IOException when the owner folder cannot be created or the file cannot be written.
     * @throws KeyAlreadyExistsException when a file with that basename is already in the owner folder.
     * @throws IllegalArgumentException when the content is not a BIND key block or the basename is not a safe
     *                                  key file name.
     */
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

    /**
     * Replaces the contents of an existing key file after checking BIND key syntax.
     *
     * @param ownerFolder the owner folder that contains the key file.
     * @param basename the key file basename.
     * @param content the BIND key text to write.
     * @throws IOException when the file is missing or cannot be written.
     * @throws IllegalArgumentException when the content is not a BIND key block or the basename is not a safe
     *                                  key file name.
     */
    public void replace(OwnerFolder ownerFolder, String basename, String content) throws IOException {
        validateContent(content);
        Path keyFile = keyPath(ownerFolder, basename);
        if (!Files.isRegularFile(keyFile)) {
            throw new IOException("Key file not found: " + basename);
        }
        Files.writeString(keyFile, content);
    }

    /**
     * Deletes one key file from the owner folder.
     * This method does not check whether zone definitions still reference the key.
     *
     * @param ownerFolder the owner folder that contains the key file.
     * @param basename the key file basename.
     * @throws IOException when the file is missing or cannot be deleted.
     * @throws IllegalArgumentException when {@code basename} is not a safe key file name.
     */
    public void delete(OwnerFolder ownerFolder, String basename) throws IOException {
        Path keyFile = keyPath(ownerFolder, basename);
        if (!Files.isRegularFile(keyFile)) {
            throw new IOException("Key file not found: " + basename);
        }
        Files.delete(keyFile);
    }

    /**
     * Resolves the owner folder under the keys directory.
     *
     * @param ownerFolder the owner folder to resolve.
     * @return the owner folder path.
     * @throws IllegalArgumentException when the folder name would escape the keys directory.
     */
    private Path ownerDirectory(OwnerFolder ownerFolder) {
        return ScopedStoragePaths.resolveUnderRoot(keysRoot, ownerFolder.folderName());
    }

    /**
     * Resolves {@code basename} inside the owner folder.
     *
     * @param ownerFolder the owner folder that contains the key file.
     * @param basename the key file basename.
     * @return the key file path.
     * @throws IllegalArgumentException when the basename is not a safe key file name or the path escapes
     *                                  the keys directory.
     */
    private Path keyPath(OwnerFolder ownerFolder, String basename) {
        return ScopedStoragePaths.resolveKeyFile(keysRoot, ownerFolder.folderName() + "/" + basename);
    }

    /**
     * Rejects content that is not a BIND key block.
     *
     * @param content the key file text to check.
     * @throws IOException when the parser reports that the content cannot be read.
     * @throws IllegalArgumentException when the content is empty or missing the key name, algorithm, or secret.
     */
    private void validateContent(String content) throws IOException {
        parser.parseContent(content);
    }
}
