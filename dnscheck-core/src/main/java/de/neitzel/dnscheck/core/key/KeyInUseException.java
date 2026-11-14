package de.neitzel.dnscheck.core.key;

import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;

import java.util.List;

/**
 * Raised when deleting a key that is still referenced by zone catalog entries.
 */
public final class KeyInUseException extends RuntimeException {

    /**
     * Scoped path of the key file that is still referenced and therefore cannot be deleted.
     */
    private final String scopedKeyPath;

    /**
     * Zone catalog entries that reference the key file. The list is immutable.
     */
    private final List<CatalogEntryIdentity> references;

    /**
     * Records the key path and the catalog entries that still reference it.
     *
     * @param scopedKeyPath the scoped path of the key file.
     * @param references the catalog entries that reference the key file.
     */
    public KeyInUseException(String scopedKeyPath, List<CatalogEntryIdentity> references) {
        super("Key is referenced by " + references.size() + " zone catalog entr"
                + (references.size() == 1 ? "y" : "ies") + ": " + scopedKeyPath);
        this.scopedKeyPath = scopedKeyPath;
        this.references = List.copyOf(references);
    }

    /**
     * Returns the scoped path of the key file that is still in use.
     *
     * @return the scoped key path.
     */
    public String scopedKeyPath() {
        return scopedKeyPath;
    }

    /**
     * Returns the zone catalog entries that still reference the key file.
     *
     * @return an immutable list of catalog identities.
     */
    public List<CatalogEntryIdentity> references() {
        return references;
    }

    /**
     * Returns how many zone catalog entries still reference the key file.
     *
     * @return the number of referencing catalog entries.
     */
    public int referencedByCount() {
        return references.size();
    }
}
