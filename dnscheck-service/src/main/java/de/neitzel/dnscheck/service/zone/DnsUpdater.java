package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.dns.NsupdateClient;
import de.neitzel.dnscheck.core.key.BindKeyFileParser;
import de.neitzel.dnscheck.core.key.MissingKeyFileException;
import de.neitzel.dnscheck.core.key.TsigKey;
import de.neitzel.dnscheck.core.key.ZoneKeyReferenceScanner;
import de.neitzel.dnscheck.core.model.PlannedChange;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Sends planned record changes to a live zone with nsupdate, authenticated by the zone definition's TSIG key.
 */
@ApplicationScoped
public class DnsUpdater {

    /**
     * Client that applies planned changes to the live zone in one nsupdate session.
     */
    private final NsupdateClient nsupdateClient = new NsupdateClient();

    /**
     * Parser that reads a TSIG key from the key file referenced by a zone definition.
     */
    private final BindKeyFileParser keyFileParser = new BindKeyFileParser();

    /**
     * Applies planned changes to the live zone using the zone definition's TSIG key.
     *
     * @param definition Zone definition that names the zone, nameserver, and key file.
     * @param keysDirectory Keys directory that contains the owner folder and key file.
     * @param changes Planned additions, corrections, or deletions for the live zone.
     * @throws MissingKeyFileException When the zone definition's key file is absent.
     * @throws IOException When the key file cannot be read or nsupdate fails.
     */
    public void apply(ZoneDefinition definition, Path keysDirectory, List<PlannedChange> changes)
            throws IOException {
        if (!ZoneKeyReferenceScanner.keyExists(keysDirectory, definition.keyFile())) {
            throw new MissingKeyFileException(definition.keyFile());
        }
        TsigKey tsigKey = keyFileParser.parse(
                ZoneKeyReferenceScanner.resolveKeyFile(keysDirectory, definition.keyFile()));
        nsupdateClient.apply(definition.zone(), definition.nameserver(), tsigKey, changes);
    }
}
