package de.neitzel.dnscheck.core.key;

/**
 * Thrown when a key file already exists in an owner folder.
 */
public final class KeyAlreadyExistsException extends Exception {

    /**
     * The key file basename that already exists in the owner folder.
     */
    private final String basename;

    /**
     * Records the basename of the key file that could not be created.
     *
     * @param basename the key file basename that is already present.
     */
    public KeyAlreadyExistsException(String basename) {
        super("Key already exists: " + basename);
        this.basename = basename;
    }

    /**
     * Returns the key file basename that already exists.
     *
     * @return the basename.
     */
    public String basename() {
        return basename;
    }
}
