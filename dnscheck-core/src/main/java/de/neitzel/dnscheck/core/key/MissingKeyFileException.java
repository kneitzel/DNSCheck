package de.neitzel.dnscheck.core.key;

/**
 * Raised when a zone definition references a key file that is not present on disk.
 */
public final class MissingKeyFileException extends RuntimeException {

    /**
     * Scoped path of the key file that the zone definition names and that is not on disk.
     */
    private final String scopedKeyPath;

    /**
     * Records the scoped path of the missing key file.
     *
     * @param scopedKeyPath the scoped path that could not be found.
     */
    public MissingKeyFileException(String scopedKeyPath) {
        super("Referenced key file is missing: " + scopedKeyPath);
        this.scopedKeyPath = scopedKeyPath;
    }

    /**
     * Returns the scoped path of the missing key file.
     *
     * @return the scoped key path.
     */
    public String scopedKeyPath() {
        return scopedKeyPath;
    }
}
