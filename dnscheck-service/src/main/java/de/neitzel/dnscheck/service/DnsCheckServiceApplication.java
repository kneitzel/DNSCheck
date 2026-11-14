package de.neitzel.dnscheck.service;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Starts the DNSCheck service, including operator sessions and scheduled reconciliation.
 */
@QuarkusMain
public class DnsCheckServiceApplication {

    /**
     * Launches the Quarkus runtime.
     *
     * @param args Command-line arguments forwarded to Quarkus.
     */
    public static void main(String[] args) {
        Quarkus.run(args);
    }
}
