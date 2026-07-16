package de.neitzel.dnscheck.core.zone;

public final class NoSuchRecordException extends RuntimeException {

    public NoSuchRecordException(String message) {
        super(message);
    }
}
