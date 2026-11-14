package de.neitzel.dnscheck.core.zone;

/**
 * Raised when creating or transferring a zone definition would overwrite an existing catalog entry.
 */
public final class ZoneAlreadyExistsException extends RuntimeException {

    /**
     * Records the catalog reference that already exists.
     *
     * @param zone the catalog reference of the existing zone definition.
     */
    public ZoneAlreadyExistsException(String zone) {
        super("Zone definition already exists: " + zone);
    }
}
