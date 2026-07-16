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

@ApplicationScoped
public class DnsUpdater {

    private final NsupdateClient nsupdateClient = new NsupdateClient();
    private final BindKeyFileParser keyFileParser = new BindKeyFileParser();

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
