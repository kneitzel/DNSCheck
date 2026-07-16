package de.neitzel.dnscheck.core.zone;

public final class ZoneDefinitionNotFoundException extends RuntimeException {

    private final String zone;

    public ZoneDefinitionNotFoundException(String zone) {
        super("Zone definition not found: " + zone);
        this.zone = zone;
    }

    public String zone() {
        return zone;
    }
}
