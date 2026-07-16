package de.neitzel.dnscheck.core.zone;

public final class ZoneAlreadyExistsException extends RuntimeException {

    public ZoneAlreadyExistsException(String zone) {
        super("Zone definition already exists: " + zone);
    }
}
