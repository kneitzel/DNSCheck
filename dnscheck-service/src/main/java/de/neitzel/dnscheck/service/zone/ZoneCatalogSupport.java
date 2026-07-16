package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.zone.ZoneCatalog;
import de.neitzel.dnscheck.service.config.DnsCheckConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;

@ApplicationScoped
public class ZoneCatalogSupport {

    @Inject
    DnsCheckConfig config;

    public Path zonesDir() {
        return Path.of(config.zones().dir());
    }

    public Path keysDir() {
        return Path.of(config.keys().dir());
    }

    public ZoneCatalog openCatalog() throws IOException {
        return new ZoneCatalog(zonesDir());
    }
}
