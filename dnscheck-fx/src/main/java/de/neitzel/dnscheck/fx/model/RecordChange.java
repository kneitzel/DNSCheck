package de.neitzel.dnscheck.fx.model;

import de.neitzel.dnscheck.core.model.RecordSpec;

/** Explicit and independent file-storage and immediate-DNS choices from the record dialog. */
public record RecordChange(RecordRow original, RecordSpec replacement, boolean store, boolean online) {
    public RecordChange {
        if (!store && !online && (original == null || original.stored() == null)) {
            throw new IllegalArgumentException("Choose file storage or an immediate DNS change.");
        }
        if (original != null && original.readOnly() || replacement.type().equalsIgnoreCase("SOA")) {
            throw new IllegalArgumentException("SOA records are displayed read-only.");
        }
    }
}
