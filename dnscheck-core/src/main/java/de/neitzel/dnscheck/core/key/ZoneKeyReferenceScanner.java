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

    /**
     * Prevents instantiation. Scanning is done through the static methods.
     */
    private ZoneKeyReferenceScanner() {}

    /**
     * A zone catalog entry that references a key path.
     *
     * @param entry the catalog entry that references the key.
     * @param keyFile the key path stored in that zone definition.
     */
    public record KeyReference(CatalogEntryIdentity entry, String keyFile) {}

    /**
     * A key file that zone definitions reference but that is not present under the keys directory.
     *
     * @param scopedKeyPath the missing key file's scoped path.
     * @param affectedEntryCount how many included catalog entries reference that path.
     */
    public record MissingKeySummary(String scopedKeyPath, int affectedEntryCount) {}

    /**
     * Lists zone definitions whose key file path equals {@code scopedKeyPath}.
     *
     * @param catalog the zone catalog to scan.
     * @param scopedKeyPath the key path to look for.
     * @return the matching catalog entries and the key path each one stores.
     * @throws IOException when the zone catalog cannot be loaded.
     * @throws IllegalArgumentException when {@code scopedKeyPath} is blank.
     */
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

    /**
     * Lists referenced key files that are absent from {@code keysDirectory}.
     * Every catalog entry is included.
     *
     * @param catalog the zone catalog to scan.
     * @param keysDirectory the keys directory that should contain the referenced key files.
     * @return one summary per missing key path.
     * @throws IOException when the zone catalog cannot be loaded.
     */
    public static List<MissingKeySummary> findMissing(ZoneCatalog catalog, Path keysDirectory)
            throws IOException {
        return findMissing(catalog, keysDirectory, identity -> true);
    }

    /**
     * Lists key files that included zone definitions reference and that are not present under the keys directory.
     *
     * @param catalog the zone catalog whose zone definitions are scanned.
     * @param keysDirectory the keys directory that should contain those key files.
     * @param include when false for an identity, that catalog entry is skipped
     * @return one summary per missing scoped key path, counting the included catalog entries that reference it.
     * @throws IOException when the zone catalog cannot be loaded.
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

    /**
     * Reports whether the key file exists as a regular file under the keys directory.
     * A blank path does not exist. A path that contains a slash is resolved as a scoped path;
     * any other path is a file name directly in the keys directory.
     *
     * @param keysDirectory the keys directory.
     * @param keyFile the key path stored in a zone definition.
     * @return {@code true} when the resolved path is a regular file.
     */
    public static boolean keyExists(Path keysDirectory, String keyFile) {
        if (keyFile == null || keyFile.isBlank()) {
            return false;
        }
        Path resolved = resolveKeyFile(keysDirectory, keyFile);
        return Files.isRegularFile(resolved);
    }

    /**
     * Resolves a key file reference.
     * A value that contains {@code /} is a scoped path under the keys directory.
     * Any other value is a file name directly in that directory.
     *
     * @param keysDirectory the keys directory.
     * @param keyFile the key path stored in a zone definition.
     * @return the resolved path.
     * @throws IllegalArgumentException when a scoped path is unsafe.
     */
    public static Path resolveKeyFile(Path keysDirectory, String keyFile) {
        if (keyFile.contains("/")) {
            return ScopedStoragePaths.resolveKeyFile(keysDirectory, keyFile);
        }
        return keysDirectory.resolve(keyFile);
    }

    /**
     * Builds the catalog identity from the zone definition file's parent owner folder and zone name.
     *
     * @param definition the loaded zone definition.
     * @return the catalog identity of that file.
     * @throws IllegalStateException when the zone definition has no parent directory.
     */
    private static CatalogEntryIdentity identityFor(ZoneDefinition definition) {
        Path zoneFile = definition.sourcePath();
        Path ownerDir = zoneFile.getParent();
        if (ownerDir == null) {
            throw new IllegalStateException("Zone definition has no owner folder: " + definition.zone());
        }
        return CatalogEntryIdentity.of(ownerDir.getFileName().toString(), definition.zone());
    }
}
