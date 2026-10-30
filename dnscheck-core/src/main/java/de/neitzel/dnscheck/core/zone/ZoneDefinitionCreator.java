package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;
import de.neitzel.dnscheck.core.ownership.ScopedStoragePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ZoneDefinitionCreator {

    private final ZoneDefinitionStore store;

    public ZoneDefinitionCreator() {
        this(new ZoneDefinitionStore());
    }

    ZoneDefinitionCreator(ZoneDefinitionStore store) {
        this.store = store;
    }

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
