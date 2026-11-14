package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;
import de.neitzel.dnscheck.core.ownership.OwnerFolder;
import de.neitzel.dnscheck.core.ownership.ScopedStoragePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads zone definitions from owner folders under the zone catalog and reports files that fail to load.
 */
public final class ZoneCatalog {

    /**
     * Root of the zone catalog. Only owner folders directly under this directory are scanned.
     */
    private final Path zonesDirectory;

    /**
     * Reads zone definition files and applies name and file-name checks.
     */
    private final ZoneDefinitionStore store;

    /**
     * Creates a catalog rooted at {@code zonesDirectory}.
     *
     * @param zonesDirectory the zone catalog root.
     */
    public ZoneCatalog(Path zonesDirectory) {
        this(zonesDirectory, new ZoneDefinitionStore());
    }

    /**
     * Creates a catalog that reads files with {@code store}.
     *
     * @param zonesDirectory the zone catalog root.
     * @param store the store that parses zone definition files.
     */
    ZoneCatalog(Path zonesDirectory, ZoneDefinitionStore store) {
        this.zonesDirectory = zonesDirectory;
        this.store = store;
    }

    /**
     * Loads every valid zone definition.
     * Invalid files reported by introspection are skipped.
     *
     * @return the zone definitions that loaded successfully.
     * @throws IOException when the zone catalog directory is missing or a valid file cannot be read.
     */
    public List<ZoneDefinition> loadAll() throws IOException {
        if (!Files.isDirectory(zonesDirectory)) {
            throw new IOException("Zone catalog directory does not exist: " + zonesDirectory);
        }
        List<ZoneDefinition> definitions = new ArrayList<>();
        for (ZoneCatalogEntry entry : introspect()) {
            if (!entry.valid()) {
                continue;
            }
            definitions.add(store.read(resolveZoneFile(entry)));
        }
        return definitions;
    }

    /**
     * Loads one zone definition by owner folder and zone name.
     *
     * @param ownerFolder the owner folder that contains the zone definition.
     * @param zoneName the DNS zone name.
     * @return the zone definition, or empty when the file is not there.
     * @throws IOException when the catalog directory is missing or the file cannot be read.
     * @throws IllegalArgumentException when the owner folder or zone name is not valid, or the file is present
     *                                  but is not a valid zone definition.
     */
    public Optional<ZoneDefinition> find(String ownerFolder, String zoneName) throws IOException {
        return find(CatalogEntryIdentity.of(ownerFolder, zoneName));
    }

    /**
     * Loads one zone definition by catalog identity.
     * A missing file is empty. A file that fails validation is rejected rather than treated as absent.
     *
     * @param identity the owner folder and zone name.
     * @return the zone definition, or empty when the file is not there.
     * @throws IOException when the catalog directory is missing or the file cannot be read.
     * @throws IllegalArgumentException when the file is present but is not a valid zone definition.
     */
    public Optional<ZoneDefinition> find(CatalogEntryIdentity identity) throws IOException {
        if (!Files.isDirectory(zonesDirectory)) {
            throw new IOException("Zone catalog directory does not exist: " + zonesDirectory);
        }
        Path zoneFile = zoneFileFor(identity);
        if (!Files.isRegularFile(zoneFile)) {
            return Optional.empty();
        }
        return Optional.of(store.read(zoneFile));
    }

    /**
     * Scans scoped owner folders and returns one entry per JSON file, including
     * validation errors for definitions that fail to load. Flat JSON at the catalog root is ignored.
     *
     * @return the catalog entries discovered under owner folders, including invalid files.
     * @throws IOException when the zone catalog directory is missing or cannot be listed.
     */
    public List<ZoneCatalogEntry> introspect() throws IOException {
        if (!Files.isDirectory(zonesDirectory)) {
            throw new IOException("Zone catalog directory does not exist: " + zonesDirectory);
        }
        List<ZoneCatalogEntry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.list(zonesDirectory)) {
            paths.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        if (Files.isDirectory(path) && OwnerFolder.isValidFolderName(name)) {
                            try {
                                introspectOwnerFolder(name, path, entries);
                            } catch (IOException e) {
                                entries.add(ZoneCatalogEntry.invalid(
                                        name, name, name + "/?", e.getMessage()));
                            }
                        }
                    });
        }
        return entries;
    }

    /**
     * Adds one catalog entry for each JSON file in the owner folder, in file-name order.
     *
     * @param ownerFolder the owner folder name.
     * @param ownerDir the owner folder path.
     * @param entries the list that receives one entry per JSON file.
     * @throws IOException when the owner folder cannot be listed.
     */
    private void introspectOwnerFolder(String ownerFolder, Path ownerDir, List<ZoneCatalogEntry> entries)
            throws IOException {
        try (Stream<Path> paths = Files.list(ownerDir)) {
            paths.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> entries.add(introspectFile(ownerFolder, path)));
        }
    }

    /**
     * Loads one zone definition file.
     * A file that fails validation or cannot be read becomes an invalid catalog entry instead of failing the scan.
     *
     * @param ownerFolder the owner folder that contains the file.
     * @param zoneFile the zone definition file.
     * @return a valid entry, or an invalid entry that carries the error.
     */
    private ZoneCatalogEntry introspectFile(String ownerFolder, Path zoneFile) {
        String basename = zoneFile.getFileName().toString();
        String relativePath = ownerFolder + "/" + basename;
        String fallbackZone = stripJsonSuffix(basename);
        try {
            ZoneDefinition definition = store.read(zoneFile);
            return ZoneCatalogEntry.valid(
                    ownerFolder,
                    definition.zone(),
                    relativePath,
                    definition.nameserver(),
                    definition.keyFile());
        } catch (IllegalArgumentException e) {
            String zone = store.peekZoneName(zoneFile).orElse(fallbackZone);
            return ZoneCatalogEntry.invalid(ownerFolder, zone, relativePath, e.getMessage());
        } catch (IOException e) {
            return ZoneCatalogEntry.invalid(ownerFolder, fallbackZone, relativePath, e.getMessage());
        }
    }

    /**
     * Resolves the zone definition path for a catalog identity.
     *
     * @param identity the owner folder and zone name.
     * @return the path under the zone catalog.
     * @throws IllegalArgumentException when the path would escape the catalog root.
     */
    private Path zoneFileFor(CatalogEntryIdentity identity) {
        return ScopedStoragePaths.resolveZoneDefinition(
                zonesDirectory, identity.ownerFolder().folderName(), identity.zoneName());
    }

    /**
     * Resolves the zone definition path for a catalog entry.
     *
     * @param entry the catalog entry whose file should be loaded.
     * @return the path under the zone catalog.
     * @throws IllegalArgumentException when the entry's identity is not valid or the path would escape the catalog.
     */
    private Path resolveZoneFile(ZoneCatalogEntry entry) {
        return zoneFileFor(entry.identity());
    }

    /**
     * Removes a trailing {@code .json} suffix, ignoring case.
     *
     * @param filename the file name, which may include the suffix.
     * @return the file name without that suffix.
     */
    private static String stripJsonSuffix(String filename) {
        if (filename.toLowerCase().endsWith(".json")) {
            return filename.substring(0, filename.length() - 5);
        }
        return filename;
    }
}
