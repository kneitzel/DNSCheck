package de.neitzel.dnscheck.core.key;

import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;

import java.util.List;

/**
 * Raised when deleting a key that is still referenced by zone catalog entries.
 */
public final class KeyInUseException extends RuntimeException {

    private final String scopedKeyPath;
    private final List<CatalogEntryIdentity> references;

    public KeyInUseException(String scopedKeyPath, List<CatalogEntryIdentity> references) {
        super("Key is referenced by " + references.size() + " zone catalog entr"
                + (references.size() == 1 ? "y" : "ies") + ": " + scopedKeyPath);
        this.scopedKeyPath = scopedKeyPath;
        this.references = List.copyOf(references);
    }

    public String scopedKeyPath() {
        return scopedKeyPath;
    }

    public List<CatalogEntryIdentity> references() {
        return references;
    }

    public int referencedByCount() {
        return references.size();
    }
}
