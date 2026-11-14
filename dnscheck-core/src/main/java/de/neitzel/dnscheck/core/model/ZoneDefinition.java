package de.neitzel.dnscheck.core.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A zone definition loaded from the zone catalog.
 *
 * @param zone the DNS zone name. It must match the zone definition file name.
 * @param nameserver the authoritative nameserver contacted for AXFR and nsupdate.
 * @param keyFile the scoped path of the TSIG key file used to authenticate those operations.
 * @param records the managed records and deletion markers in this zone definition.
 * @param sourcePath the zone definition file this value was loaded from or will be written to.
 */
public record ZoneDefinition(
        String zone,
        String nameserver,
        String keyFile,
        List<RecordSpec> records,
        Path sourcePath) {

    /**
     * Requires the zone name, nameserver, and key file, and stores an immutable copy of the record entries.
     *
     * @throws IllegalArgumentException when {@code zone}, {@code nameserver}, or {@code keyFile} is blank.
     */
    public ZoneDefinition {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("zone is required");
        }
        if (nameserver == null || nameserver.isBlank()) {
            throw new IllegalArgumentException("nameserver is required");
        }
        if (keyFile == null || keyFile.isBlank()) {
            throw new IllegalArgumentException("keyFile is required");
        }
        records = List.copyOf(records);
    }

    /**
     * Returns the expected records this zone definition enforces.
     *
     * @return the managed records, excluding deletion markers.
     */
    public List<RecordSpec> managedRecords() {
        return records.stream().filter(RecordSpec::isManagedRecord).toList();
    }

    /**
     * Returns the deletion markers in this zone definition.
     *
     * @return the entries that remove matching live records.
     */
    public List<RecordSpec> deletionMarkers() {
        return records.stream().filter(RecordSpec::deletionMarker).toList();
    }

    /**
     * Returns a zone definition that appends {@code record}.
     * The zone definition file is not updated.
     *
     * @param record the managed record or deletion marker to append.
     * @return a new zone definition with the same nameserver, key file, and source path.
     */
    public ZoneDefinition withRecord(RecordSpec record) {
        List<RecordSpec> updatedRecords = new ArrayList<>(records);
        updatedRecords.add(record);
        return new ZoneDefinition(zone, nameserver, keyFile, updatedRecords, sourcePath);
    }

    /**
     * Finds a managed record with the same owner name, type, and rdata.
     * Name and type are compared without case.
     *
     * @param nameFqdn the fully qualified owner name.
     * @param type the DNS record type.
     * @param rdata the expected rdata.
     * @return the matching managed record, or empty when none matches.
     */
    public Optional<RecordSpec> findManagedRecord(String nameFqdn, String type, String rdata) {
        return records.stream()
                .filter(RecordSpec::isManagedRecord)
                .filter(record -> matchesManagedRecord(record, nameFqdn, type, rdata))
                .findFirst();
    }

    /**
     * Returns a zone definition without the managed record that matches the owner name, type, and rdata.
     * Returns this definition when no managed record matches. The file is not updated.
     *
     * @param nameFqdn the fully qualified owner name.
     * @param type the DNS record type.
     * @param rdata the expected rdata.
     * @return this definition, or a copy that omits the matching managed record.
     */
    public ZoneDefinition withoutManagedRecord(String nameFqdn, String type, String rdata) {
        List<RecordSpec> updatedRecords = records.stream()
                .filter(record -> !(record.isManagedRecord()
                        && matchesManagedRecord(record, nameFqdn, type, rdata)))
                .toList();
        if (updatedRecords.size() == records.size()) {
            return this;
        }
        return new ZoneDefinition(zone, nameserver, keyFile, updatedRecords, sourcePath);
    }

    /**
     * Returns a zone definition in which every matching managed record is replaced.
     * Returns this definition when none match. The file is not updated.
     *
     * @param nameFqdn the fully qualified owner name of the managed record to replace.
     * @param type the DNS record type.
     * @param oldRdata the rdata that identifies the managed record.
     * @param replacement the managed record to store in its place.
     * @return this definition, or a copy with each match replaced.
     */
    public ZoneDefinition withReplacedManagedRecord(
            String nameFqdn, String type, String oldRdata, RecordSpec replacement) {
        List<RecordSpec> updatedRecords = new ArrayList<>();
        boolean replaced = false;
        for (RecordSpec record : records) {
            if (record.isManagedRecord() && matchesManagedRecord(record, nameFqdn, type, oldRdata)) {
                updatedRecords.add(replacement);
                replaced = true;
            } else {
                updatedRecords.add(record);
            }
        }
        if (!replaced) {
            return this;
        }
        return new ZoneDefinition(zone, nameserver, keyFile, updatedRecords, sourcePath);
    }

    /**
     * Reports whether a deletion marker exists for the owner name, type, and rdata pattern.
     * A blank pattern matches a marker whose pattern was omitted.
     *
     * @param nameFqdn the fully qualified owner name.
     * @param type the DNS record type.
     * @param rdataPattern the rdata pattern, or blank when the marker matches every rdata value.
     * @return {@code true} when such a deletion marker is present.
     */
    public boolean hasDeletionMarker(String nameFqdn, String type, String rdataPattern) {
        return records.stream().anyMatch(record -> matchesDeletionMarker(record, nameFqdn, type, rdataPattern));
    }

    /**
     * Returns a zone definition without the deletion marker that matches the owner name, type, and rdata pattern.
     * Returns this definition when no marker matches. The file is not updated.
     *
     * @param nameFqdn the fully qualified owner name.
     * @param type the DNS record type.
     * @param rdataPattern the rdata pattern, or blank when the marker matches every rdata value.
     * @return this definition, or a copy that omits the matching deletion marker.
     */
    public ZoneDefinition withoutDeletionMarker(String nameFqdn, String type, String rdataPattern) {
        List<RecordSpec> updatedRecords = records.stream()
                .filter(record -> !matchesDeletionMarker(record, nameFqdn, type, rdataPattern))
                .toList();
        if (updatedRecords.size() == records.size()) {
            return this;
        }
        return new ZoneDefinition(zone, nameserver, keyFile, updatedRecords, sourcePath);
    }

    /**
     * Reports whether {@code record} is the deletion marker identified by owner name, type, and rdata pattern.
     * Name and type are compared without case. A blank pattern matches an omitted pattern.
     *
     * @param record the zone definition entry to test.
     * @param nameFqdn the fully qualified owner name.
     * @param type the DNS record type.
     * @param rdataPattern the rdata pattern, or blank when the marker matches every rdata value.
     * @return {@code true} when {@code record} is that deletion marker.
     */
    private static boolean matchesDeletionMarker(
            RecordSpec record, String nameFqdn, String type, String rdataPattern) {
        if (!record.deletionMarker()) {
            return false;
        }
        return record.nameFqdn().equalsIgnoreCase(nameFqdn)
                && record.type().equalsIgnoreCase(type)
                && patternEquals(record.rdataPattern(), rdataPattern);
    }

    /**
     * Treats a null pattern and a blank pattern as the same omitted rdata pattern.
     *
     * @param left one rdata pattern.
     * @param right the other rdata pattern.
     * @return {@code true} when both patterns are omitted or both contain the same text.
     */
    private static boolean patternEquals(String left, String right) {
        String normalizedLeft = (left == null || left.isBlank()) ? null : left;
        String normalizedRight = (right == null || right.isBlank()) ? null : right;
        return Objects.equals(normalizedLeft, normalizedRight);
    }

    /**
     * Reports whether {@code record} is the managed record identified by owner name, type, and exact rdata.
     * Name and type are compared without case.
     *
     * @param record the zone definition entry to test. The caller has already limited this to managed records.
     * @param nameFqdn the fully qualified owner name.
     * @param type the DNS record type.
     * @param rdata the expected rdata.
     * @return {@code true} when the owner, type, and rdata match.
     */
    private static boolean matchesManagedRecord(RecordSpec record, String nameFqdn, String type, String rdata) {
        return record.nameFqdn().equalsIgnoreCase(nameFqdn)
                && record.type().equalsIgnoreCase(type)
                && Objects.equals(record.rdata(), rdata);
    }
}
