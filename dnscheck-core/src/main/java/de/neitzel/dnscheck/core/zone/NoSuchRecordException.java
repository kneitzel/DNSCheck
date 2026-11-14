package de.neitzel.dnscheck.core.zone;

/**
 * Raised when a zone management action names a record that is not in the zone definition or the live zone,
 * as that action requires.
 */
public final class NoSuchRecordException extends RuntimeException {

    /**
     * Creates the exception with the reason the record could not be found.
     *
     * @param message the reason shown to the caller.
     */
    public NoSuchRecordException(String message) {
        super(message);
    }
}
