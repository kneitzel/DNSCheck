package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;
import de.neitzel.dnscheck.core.ownership.ScopedStoragePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Updates, deletes, and transfers zone definition files inside the zone catalog.
 * An update of the nameserver or key file leaves managed records unchanged.
 */
public final class ZoneDefinitionMutator {

    /**
     * Reads and writes the zone definition being changed.
     */
    private final ZoneDefinitionStore store;

    /**
     * Creates a mutator that uses a new zone definition store.
     */
    public ZoneDefinitionMutator() {
        this(new ZoneDefinitionStore());
    }

    /**
     * Creates a mutator that reads and writes through {@code store}.
     *
     * @param store the store for the zone definition file.
     */
    ZoneDefinitionMutator(ZoneDefinitionStore store) {
        this.store = store;
    }

    /**
     * Replaces the nameserver, the key file, or both on an existing zone definition.
     * A null argument keeps the current value.
     *
     * @param zonesDirectory the zone catalog root.
     * @param ownerFolder the owner folder that contains the zone definition.
     * @param zone the DNS zone name.
     * @param nameserver the replacement nameserver, or {@code null} to keep the current one.
     * @param keyFile the replacement scoped key path, or {@code null} to keep the current one.
     * @return the catalog entry after the update.
     * @throws IllegalArgumentException when the owner folder or zone is blank, both replacements are null,
     *                                  a supplied value is blank, or a name is not safe.
     * @throws IOException when the catalog is missing or the file cannot be read or written.
     * @throws ZoneDefinitionNotFoundException when the catalog entry does not exist.
     */
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

    /**
     * Deletes the zone definition file.
     * The live zone is not changed.
     *
     * @param zonesDirectory the zone catalog root.
     * @param ownerFolder the owner folder that contains the zone definition.
     * @param zone the DNS zone name.
     * @throws IllegalArgumentException when the owner folder or zone is blank or not safe.
     * @throws IOException when the catalog is missing or the file cannot be deleted.
     * @throws ZoneDefinitionNotFoundException when the catalog entry does not exist.
     */
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

    /**
     * Moves a zone definition to another owner folder without changing its records, nameserver, or key file.
     * The source file is removed after the target file is written.
     *
     * @param zonesDirectory the zone catalog root.
     * @param fromOwnerFolder the owner folder that currently contains the zone definition.
     * @param zone the DNS zone name.
     * @param toOwnerFolder the owner folder that should contain the zone definition.
     * @return the catalog entry at the target owner folder.
     * @throws IllegalArgumentException when a folder or the zone is blank, the folders are the same,
     *                                  or a name is not safe.
     * @throws IOException when the catalog is missing or a file cannot be read, written, or deleted.
     * @throws ZoneDefinitionNotFoundException when the source file does not exist.
     * @throws ZoneAlreadyExistsException when the target catalog entry already exists.
     */
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
