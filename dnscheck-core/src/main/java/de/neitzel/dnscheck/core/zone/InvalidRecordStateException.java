package de.neitzel.dnscheck.core.zone;

/**
 * Raised when a promotion, demotion, or modification does not apply to the record's current enforcement.
 */
public final class InvalidRecordStateException extends RuntimeException {

    /**
     * Creates the exception with the reason the record state was rejected.
     *
     * @param message the reason shown to the caller.
     */
    public InvalidRecordStateException(String message) {
        super(message);
    }
}
