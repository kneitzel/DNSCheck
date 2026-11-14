package de.neitzel.dnscheck.fx;

import javafx.application.Application;

/**
 * Launches the desktop application without extending {@link Application}.
 */
public final class Main {

    /**
     * Prevents instantiation. The class is only a launch entry point.
     */
    private Main() {
    }

    /**
     * Starts DNSCheck FX.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        Application.launch(DnsCheckFxApp.class, args);
    }
}
