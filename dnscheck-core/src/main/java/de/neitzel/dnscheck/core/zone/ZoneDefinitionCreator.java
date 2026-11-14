package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;
import de.neitzel.dnscheck.core.ownership.ScopedStoragePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Creates a new zone definition file with no managed records.
 */
public final class ZoneDefinitionCreator {

    /**
     * Writes the new zone definition.
     */
    private final ZoneDefinitionStore store;

    /**
     * Creates a creator that uses a new zone definition store.
     */
    public ZoneDefinitionCreator() {
        this(new ZoneDefinitionStore());
    }

    /**
     * Creates a creator that writes through {@code store}.
     *
     * @param store the store that writes the zone definition file.
     */
    ZoneDefinitionCreator(ZoneDefinitionStore store) {
        this.store = store;
    }

    /**
     * Creates {@code {owner}/{zone}.json} under the zone catalog.
     * The file starts with the nameserver, the key file, and an empty record list.
     *
     * @param zonesDirectory the zone catalog root.
     * @param ownerFolder the owner folder that will contain the zone definition.
     * @param zone the DNS zone name. It becomes the file name.
     * @param nameserver the authoritative nameserver to contact.
     * @param keyFile the scoped path of the TSIG key file.
     * @return the catalog entry for the new file.
     * @throws IllegalArgumentException when the owner folder, zone, nameserver, or key file is blank,
     *                                  or the owner folder or zone name is not safe.
     * @throws IOException when the catalog directory is missing or the file cannot be written.
     * @throws ZoneAlreadyExistsException when that catalog entry already exists.
     */
    public ZoneCatalogEntry create(
            Path zonesDirectory, String ownerFolder, String zone, String nameserver, String keyFile)
            throws IOException {
        if (ownerFolder == null || ownerFolder.isBlank()) {
            throw new IllegalArgumentException("owner folder is required");
        }
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("zone is required");
        }
        if (nameserver == null || nameserver.isBlank()) {
            throw new IllegalArgumentException("nameserver is required");
        }
        if (keyFile == null || keyFile.isBlank()) {
            throw new IllegalArgumentException("keyFile is required");
        }
        if (!Files.isDirectory(zonesDirectory)) {
            throw new IOException("Zone catalog directory does not exist: " + zonesDirectory);
        }

        CatalogEntryIdentity identity = CatalogEntryIdentity.of(ownerFolder, zone);
        Path zoneFile = ScopedStoragePaths.resolveZoneDefinition(
                zonesDirectory, identity.ownerFolder().folderName(), identity.zoneName());
        if (Files.exists(zoneFile)) {
            throw new ZoneAlreadyExistsException(identity.catalogRef());
        }

        Files.createDirectories(zoneFile.getParent());
        ZoneDefinition definition = new ZoneDefinition(zone, nameserver, keyFile, List.of(), zoneFile);
        store.write(definition);
        return ZoneCatalogEntry.valid(
                identity.ownerFolder().folderName(), zone, identity.relativePath(), nameserver, keyFile);
    }
}
