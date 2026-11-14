package de.neitzel.dnscheck.core.zone;

/**
 * Raised when an update, deletion, or transfer names a zone definition that is not in the zone catalog.
 */
public final class ZoneDefinitionNotFoundException extends RuntimeException {

    /**
     * The catalog reference that was not found.
     */
    private final String zone;

    /**
     * Records the missing catalog reference.
     *
     * @param zone the catalog reference that was not found.
     */
    public ZoneDefinitionNotFoundException(String zone) {
        super("Zone definition not found: " + zone);
        this.zone = zone;
    }

    /**
     * Returns the catalog reference that was not found.
     *
     * @return the catalog reference.
     */
    public String zone() {
        return zone;
    }
}
