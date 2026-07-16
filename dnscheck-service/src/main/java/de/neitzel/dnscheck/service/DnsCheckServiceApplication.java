package de.neitzel.dnscheck.service;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class DnsCheckServiceApplication {

    public static void main(String[] args) {
        Quarkus.run(args);
    }
}
