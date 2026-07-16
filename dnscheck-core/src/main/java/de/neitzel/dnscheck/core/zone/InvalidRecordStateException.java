package de.neitzel.dnscheck.core.zone;

public final class InvalidRecordStateException extends RuntimeException {

    public InvalidRecordStateException(String message) {
        super(message);
    }
}
