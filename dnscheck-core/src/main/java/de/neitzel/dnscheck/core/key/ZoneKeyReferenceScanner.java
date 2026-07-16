package de.neitzel.dnscheck.core.key;

import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;
import de.neitzel.dnscheck.core.ownership.ScopedStoragePaths;
import de.neitzel.dnscheck.core.zone.ZoneCatalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans the zone catalog for scoped key file references (in-use and missing).
 */
public final class ZoneKeyReferenceScanner {

    private ZoneKeyReferenceScanner() {}

    public record KeyReference(CatalogEntryIdentity entry, String keyFile) {}

    public record MissingKeySummary(String scopedKeyPath, int affectedEntryCount) {}

    public static List<KeyReference> findReferences(ZoneCatalog catalog, String scopedKeyPath)
            throws IOException {
        if (scopedKeyPath == null || scopedKeyPath.isBlank()) {
            throw new IllegalArgumentException("scoped key path is required");
        }
        List<KeyReference> matches = new ArrayList<>();
        for (ZoneDefinition definition : catalog.loadAll()) {
            if (scopedKeyPath.equals(definition.keyFile())) {
                matches.add(new KeyReference(identityFor(definition), definition.keyFile()));
            }
        }
        return matches;
    }

    public static List<MissingKeySummary> findMissing(ZoneCatalog catalog, Path keysDirectory)
            throws IOException {
        return findMissing(catalog, keysDirectory, identity -> true);
    }

    /**
     * @param include when false for an identity, that catalog entry is skipped
     */
    public static List<MissingKeySummary> findMissing(
            ZoneCatalog catalog,
            Path keysDirectory,
            java.util.function.Predicate<CatalogEntryIdentity> include)
            throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ZoneDefinition definition : catalog.loadAll()) {
            CatalogEntryIdentity identity = identityFor(definition);
            if (!include.test(identity)) {
                continue;
            }
            if (!keyExists(keysDirectory, definition.keyFile())) {
                counts.merge(definition.keyFile(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new MissingKeySummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    public static boolean keyExists(Path keysDirectory, String keyFile) {
        if (keyFile == null || keyFile.isBlank()) {
            return false;
        }
        Path resolved = resolveKeyFile(keysDirectory, keyFile);
        return Files.isRegularFile(resolved);
    }

    public static Path resolveKeyFile(Path keysDirectory, String keyFile) {
        if (keyFile.contains("/")) {
            return ScopedStoragePaths.resolveKeyFile(keysDirectory, keyFile);
        }
        return keysDirectory.resolve(keyFile);
    }

    private static CatalogEntryIdentity identityFor(ZoneDefinition definition) {
        Path zoneFile = definition.sourcePath();
        Path ownerDir = zoneFile.getParent();
        if (ownerDir == null) {
            throw new IllegalStateException("Zone definition has no owner folder: " + definition.zone());
        }
        return CatalogEntryIdentity.of(ownerDir.getFileName().toString(), definition.zone());
    }
}
