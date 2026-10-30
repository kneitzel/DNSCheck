package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;
import de.neitzel.dnscheck.core.ownership.ScopedStoragePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZoneDefinitionMutator {

    private final ZoneDefinitionStore store;

    public ZoneDefinitionMutator() {
        this(new ZoneDefinitionStore());
    }

    ZoneDefinitionMutator(ZoneDefinitionStore store) {
        this.store = store;
    }

    public ZoneCatalogEntry update(
            Path zonesDirectory, String ownerFolder, String zone, String nameserver, String keyFile)
            throws IOException {
        if (ownerFolder == null || ownerFolder.isBlank()) {
            throw new IllegalArgumentException("owner folder is required");
        }
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("zone is required");
        }
        if (nameserver == null && keyFile == null) {
            throw new IllegalArgumentException("At least one of nameserver or keyFile is required");
        }
        if (nameserver != null && nameserver.isBlank()) {
            throw new IllegalArgumentException("nameserver must not be blank");
        }
        if (keyFile != null && keyFile.isBlank()) {
            throw new IllegalArgumentException("keyFile must not be blank");
        }
        if (!Files.isDirectory(zonesDirectory)) {
            throw new IOException("Zone catalog directory does not exist: " + zonesDirectory);
        }

        CatalogEntryIdentity identity = CatalogEntryIdentity.of(ownerFolder, zone);
        Path zoneFile = ScopedStoragePaths.resolveZoneDefinition(
                zonesDirectory, identity.ownerFolder().folderName(), identity.zoneName());
        if (!Files.isRegularFile(zoneFile)) {
            throw new ZoneDefinitionNotFoundException(identity.catalogRef());
        }

        ZoneDefinition existing = store.read(zoneFile);
        String updatedNameserver = nameserver != null ? nameserver : existing.nameserver();
        String updatedKeyFile = keyFile != null ? keyFile : existing.keyFile();
        ZoneDefinition updated = new ZoneDefinition(
                existing.zone(), updatedNameserver, updatedKeyFile, existing.records(), zoneFile);
        store.write(updated);
        return ZoneCatalogEntry.valid(
                identity.ownerFolder().folderName(),
                zone,
                identity.relativePath(),
                updatedNameserver,
                updatedKeyFile);
    }

    public void delete(Path zonesDirectory, String ownerFolder, String zone) throws IOException {
        if (ownerFolder == null || ownerFolder.isBlank()) {
            throw new IllegalArgumentException("owner folder is required");
        }
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("zone is required");
        }
        if (!Files.isDirectory(zonesDirectory)) {
            throw new IOException("Zone catalog directory does not exist: " + zonesDirectory);
        }

        CatalogEntryIdentity identity = CatalogEntryIdentity.of(ownerFolder, zone);
        Path zoneFile = ScopedStoragePaths.resolveZoneDefinition(
                zonesDirectory, identity.ownerFolder().folderName(), identity.zoneName());
        if (!Files.isRegularFile(zoneFile)) {
            throw new ZoneDefinitionNotFoundException(identity.catalogRef());
        }
        Files.delete(zoneFile);
    }

    public ZoneCatalogEntry transfer(
            Path zonesDirectory, String fromOwnerFolder, String zone, String toOwnerFolder)
            throws IOException {
        if (fromOwnerFolder == null || fromOwnerFolder.isBlank()) {
            throw new IllegalArgumentException("source owner folder is required");
        }
        if (toOwnerFolder == null || toOwnerFolder.isBlank()) {
            throw new IllegalArgumentException("target owner folder is required");
        }
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("zone is required");
        }
        if (!Files.isDirectory(zonesDirectory)) {
            throw new IOException("Zone catalog directory does not exist: " + zonesDirectory);
        }

        CatalogEntryIdentity source = CatalogEntryIdentity.of(fromOwnerFolder, zone);
        CatalogEntryIdentity target = CatalogEntryIdentity.of(toOwnerFolder, zone);
        if (source.ownerFolder().folderName().equals(target.ownerFolder().folderName())) {
            throw new IllegalArgumentException("target owner folder must differ from source");
        }

        Path sourceFile = ScopedStoragePaths.resolveZoneDefinition(
                zonesDirectory, source.ownerFolder().folderName(), source.zoneName());
        if (!Files.isRegularFile(sourceFile)) {
            throw new ZoneDefinitionNotFoundException(source.catalogRef());
        }

        Path targetFile = ScopedStoragePaths.resolveZoneDefinition(
                zonesDirectory, target.ownerFolder().folderName(), target.zoneName());
        if (Files.exists(targetFile)) {
            throw new ZoneAlreadyExistsException(target.catalogRef());
        }

        ZoneDefinition existing = store.read(sourceFile);
        Files.createDirectories(targetFile.getParent());
        ZoneDefinition relocated = new ZoneDefinition(
                existing.zone(),
                existing.nameserver(),
                existing.keyFile(),
                existing.records(),
                targetFile);
        store.write(relocated);
        Files.delete(sourceFile);
        return ZoneCatalogEntry.valid(
                target.ownerFolder().folderName(),
                zone,
                target.relativePath(),
                existing.nameserver(),
                existing.keyFile());
    }
}
