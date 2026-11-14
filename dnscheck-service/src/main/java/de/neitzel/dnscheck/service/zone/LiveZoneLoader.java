package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.dns.LiveZoneClient;
import de.neitzel.dnscheck.core.key.BindKeyFileParser;
import de.neitzel.dnscheck.core.key.MissingKeyFileException;
import de.neitzel.dnscheck.core.key.TsigKey;
import de.neitzel.dnscheck.core.key.ZoneKeyReferenceScanner;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Loads a live zone by AXFR so drift can be compared with the zone definition.
 */
@ApplicationScoped
public class LiveZoneLoader {

    /**
     * Client that retrieves the live zone from the authoritative nameserver.
     */
    private final LiveZoneClient liveZoneClient = new LiveZoneClient();

    /**
     * Parser that reads a TSIG key from the key file referenced by a zone definition.
     */
    private final BindKeyFileParser keyFileParser = new BindKeyFileParser();

    /**
     * Loads the live zone for a zone definition using its TSIG key.
     * The SOA minimum from that transfer supplies the zone default TTL.
     *
     * @param definition Zone definition that names the zone, nameserver, and key file.
     * @param keysDirectory Keys directory that contains the owner folder and key file.
     * @return Live zone retrieved by AXFR.
     * @throws MissingKeyFileException When the zone definition's key file is absent.
     * @throws IOException When the key file cannot be read or AXFR fails.
     */
    public LiveZone load(ZoneDefinition definition, Path keysDirectory) throws IOException {
        if (!ZoneKeyReferenceScanner.keyExists(keysDirectory, definition.keyFile())) {
            throw new MissingKeyFileException(definition.keyFile());
        }
        TsigKey tsigKey = keyFileParser.parse(
                ZoneKeyReferenceScanner.resolveKeyFile(keysDirectory, definition.keyFile()));
        return liveZoneClient.load(definition.zone(), definition.nameserver(), tsigKey);
    }
}
