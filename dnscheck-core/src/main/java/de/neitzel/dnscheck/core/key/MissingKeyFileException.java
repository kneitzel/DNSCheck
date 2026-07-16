package de.neitzel.dnscheck.core.key;

/**
 * Raised when a zone definition references a key file that is not present on disk.
 */
public final class MissingKeyFileException extends RuntimeException {

    private final String scopedKeyPath;

    public MissingKeyFileException(String scopedKeyPath) {
        super("Referenced key file is missing: " + scopedKeyPath);
        this.scopedKeyPath = scopedKeyPath;
    }

    public String scopedKeyPath() {
        return scopedKeyPath;
    }
}
