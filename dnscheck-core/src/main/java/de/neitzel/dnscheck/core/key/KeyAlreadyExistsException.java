package de.neitzel.dnscheck.core.key;

/**
 * Thrown when a key file already exists in an owner folder.
 */
public final class KeyAlreadyExistsException extends Exception {

    private final String basename;

    public KeyAlreadyExistsException(String basename) {
        super("Key already exists: " + basename);
        this.basename = basename;
    }

    public String basename() {
        return basename;
    }
}
