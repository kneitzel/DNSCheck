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

@ApplicationScoped
public class LiveZoneLoader {

    private final LiveZoneClient liveZoneClient = new LiveZoneClient();
    private final BindKeyFileParser keyFileParser = new BindKeyFileParser();

    public LiveZone load(ZoneDefinition definition, Path keysDirectory) throws IOException {
        if (!ZoneKeyReferenceScanner.keyExists(keysDirectory, definition.keyFile())) {
            throw new MissingKeyFileException(definition.keyFile());
        }
        TsigKey tsigKey = keyFileParser.parse(
                ZoneKeyReferenceScanner.resolveKeyFile(keysDirectory, definition.keyFile()));
        return liveZoneClient.load(definition.zone(), definition.nameserver(), tsigKey);
    }
}
