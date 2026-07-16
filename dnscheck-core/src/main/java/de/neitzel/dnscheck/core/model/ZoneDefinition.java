package de.neitzel.dnscheck.core.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A zone definition loaded from the zone catalog.
 */
public record ZoneDefinition(
        String zone,
        String nameserver,
        String keyFile,
        List<RecordSpec> records,
        Path sourcePath) {

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

    public List<RecordSpec> managedRecords() {
        return records.stream().filter(RecordSpec::isManagedRecord).toList();
    }

    public List<RecordSpec> deletionMarkers() {
        return records.stream().filter(RecordSpec::deletionMarker).toList();
    }

    public ZoneDefinition withRecord(RecordSpec record) {
        List<RecordSpec> updatedRecords = new ArrayList<>(records);
        updatedRecords.add(record);
        return new ZoneDefinition(zone, nameserver, keyFile, updatedRecords, sourcePath);
    }

    public Optional<RecordSpec> findManagedRecord(String nameFqdn, String type, String rdata) {
        return records.stream()
                .filter(RecordSpec::isManagedRecord)
                .filter(record -> matchesManagedRecord(record, nameFqdn, type, rdata))
                .findFirst();
    }

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

    public boolean hasDeletionMarker(String nameFqdn, String type, String rdataPattern) {
        return records.stream().anyMatch(record -> matchesDeletionMarker(record, nameFqdn, type, rdataPattern));
    }

    public ZoneDefinition withoutDeletionMarker(String nameFqdn, String type, String rdataPattern) {
        List<RecordSpec> updatedRecords = records.stream()
                .filter(record -> !matchesDeletionMarker(record, nameFqdn, type, rdataPattern))
                .toList();
        if (updatedRecords.size() == records.size()) {
            return this;
        }
        return new ZoneDefinition(zone, nameserver, keyFile, updatedRecords, sourcePath);
    }

    private static boolean matchesDeletionMarker(
            RecordSpec record, String nameFqdn, String type, String rdataPattern) {
        if (!record.deletionMarker()) {
            return false;
        }
        return record.nameFqdn().equalsIgnoreCase(nameFqdn)
                && record.type().equalsIgnoreCase(type)
                && patternEquals(record.rdataPattern(), rdataPattern);
    }

    private static boolean patternEquals(String left, String right) {
        String normalizedLeft = (left == null || left.isBlank()) ? null : left;
        String normalizedRight = (right == null || right.isBlank()) ? null : right;
        return Objects.equals(normalizedLeft, normalizedRight);
    }

    private static boolean matchesManagedRecord(RecordSpec record, String nameFqdn, String type, String rdata) {
        return record.nameFqdn().equalsIgnoreCase(nameFqdn)
                && record.type().equalsIgnoreCase(type)
                && Objects.equals(record.rdata(), rdata);
    }
}
