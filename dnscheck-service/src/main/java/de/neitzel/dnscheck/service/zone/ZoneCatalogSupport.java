package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.zone.ZoneCatalog;
import de.neitzel.dnscheck.service.config.DnsCheckConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Resolves the zone catalog and keys directory from service configuration.
 */
@ApplicationScoped
public class ZoneCatalogSupport {

    /**
     * Service configuration for the zone catalog location and keys directory.
     */
    @Inject
    DnsCheckConfig config;

    /**
     * Returns the directory that holds owner folders and zone definitions.
     *
     * @return Path of the zone catalog.
     */
    public Path zonesDir() {
        return Path.of(config.zones().dir());
    }

    /**
     * Returns the directory that holds owner folders and TSIG key files.
     *
     * @return Path of the keys directory.
     */
    public Path keysDir() {
        return Path.of(config.keys().dir());
    }

    /**
     * Opens the zone catalog for listing zone definitions or loading one zone catalog entry.
     *
     * @return Zone catalog rooted at the configured zones directory.
     * @throws IOException When the zone catalog directory cannot be opened.
     */
    public ZoneCatalog openCatalog() throws IOException {
        return new ZoneCatalog(zonesDir());
    }
}
