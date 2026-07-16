package de.neitzel.dnscheck.service.keys;

import de.neitzel.dnscheck.core.key.KeyInUseException;
import de.neitzel.dnscheck.core.key.MissingKeyFileException;
import de.neitzel.dnscheck.core.key.ScopedKeyFileStore;
import de.neitzel.dnscheck.core.key.ZoneKeyReferenceScanner;
import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;
import de.neitzel.dnscheck.core.ownership.OwnerFolder;
import de.neitzel.dnscheck.core.ownership.OwnerFolderKind;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import de.neitzel.dnscheck.service.auth.entity.Group;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.zone.ZoneCatalogSupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class KeyUsageService {

    private static final Logger LOG = Logger.getLogger(KeyUsageService.class);

    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    @Inject
    OperatorRightsService operatorRightsService;

    public void assertNotInUse(String scopedKeyPath) {
        try {
            List<ZoneKeyReferenceScanner.KeyReference> refs =
                    ZoneKeyReferenceScanner.findReferences(zoneCatalogSupport.openCatalog(), scopedKeyPath);
            if (refs.isEmpty()) {
                return;
            }
            List<CatalogEntryIdentity> identities =
                    refs.stream().map(ZoneKeyReferenceScanner.KeyReference::entry).toList();
            LOG.warnf(
                    "Key delete blocked for %s; referenced by %s",
                    scopedKeyPath,
                    identities.stream()
                            .map(id -> "(" + id.ownerFolder().folderName() + ", " + id.zoneName() + ")")
                            .toList());
            throw new KeyInUseException(scopedKeyPath, identities);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void assertKeyPresent(String keyFile) {
        if (!ZoneKeyReferenceScanner.keyExists(zoneCatalogSupport.keysDir(), keyFile)) {
            throw new MissingKeyFileException(keyFile);
        }
    }

    public List<MissingKeySummary> listMissingKeys() {
        try {
            boolean admin = operatorRightsService.isAdmin();
            return ZoneKeyReferenceScanner.findMissing(
                            zoneCatalogSupport.openCatalog(),
                            zoneCatalogSupport.keysDir(),
                            identity -> admin
                                    || operatorRightsService.canReadZone(
                                            identity.ownerFolder().folderName(), identity.zoneName()))
                    .stream()
                    .map(summary -> new MissingKeySummary(
                            summary.scopedKeyPath(), summary.affectedEntryCount()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<SelectableKey> listSelectableKeys(Operator operator) {
        ScopedKeyFileStore store = new ScopedKeyFileStore(zoneCatalogSupport.keysDir());
        List<SelectableKey> keys = new ArrayList<>();
        try {
            OwnerFolder personal = ScopedKeyFileStore.personalFolderFor(operator.username);
            for (String basename : store.listBasenames(personal)) {
                keys.add(new SelectableKey(
                        store.scopedPath(personal, basename), basename, OwnerFolderKind.USER));
            }
            for (Group group : operator.groups) {
                OwnerFolder groupFolder = ScopedKeyFileStore.groupFolderFor(group.name);
                for (String basename : store.listBasenames(groupFolder)) {
                    keys.add(new SelectableKey(
                            store.scopedPath(groupFolder, basename),
                            group.name + "/" + basename,
                            OwnerFolderKind.GROUP));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        keys.sort(Comparator.comparing(SelectableKey::displayLabel));
        return keys;
    }

    public record SelectableKey(String scopedPath, String displayLabel, OwnerFolderKind kind) {}

    public record MissingKeySummary(String scopedKeyPath, int affectedEntryCount) {}
}
