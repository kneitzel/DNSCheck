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

public final class ZoneCatalog {

    private final Path zonesDirectory;
    private final ZoneDefinitionStore store;

    public ZoneCatalog(Path zonesDirectory) {
        this(zonesDirectory, new ZoneDefinitionStore());
    }

    ZoneCatalog(Path zonesDirectory, ZoneDefinitionStore store) {
        this.zonesDirectory = zonesDirectory;
        this.store = store;
    }

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

    public Optional<ZoneDefinition> find(String ownerFolder, String zoneName) throws IOException {
        return find(CatalogEntryIdentity.of(ownerFolder, zoneName));
    }

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

    private void introspectOwnerFolder(String ownerFolder, Path ownerDir, List<ZoneCatalogEntry> entries)
            throws IOException {
        try (Stream<Path> paths = Files.list(ownerDir)) {
            paths.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> entries.add(introspectFile(ownerFolder, path)));
        }
    }

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

    private Path zoneFileFor(CatalogEntryIdentity identity) {
        return ScopedStoragePaths.resolveZoneDefinition(
                zonesDirectory, identity.ownerFolder().folderName(), identity.zoneName());
    }

    private Path resolveZoneFile(ZoneCatalogEntry entry) {
        return zoneFileFor(entry.identity());
    }

    private static String stripJsonSuffix(String filename) {
        if (filename.toLowerCase().endsWith(".json")) {
            return filename.substring(0, filename.length() - 5);
        }
        return filename;
    }
}
