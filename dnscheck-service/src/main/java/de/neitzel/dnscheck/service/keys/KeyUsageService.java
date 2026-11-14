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

/**
 * Checks TSIG key file references in the zone catalog before deletion, reconciliation, and zone definition edits.
 */
@ApplicationScoped
public class KeyUsageService {

    /**
     * Logger for key file references that block deletion of a key file.
     */
    private static final Logger LOG = Logger.getLogger(KeyUsageService.class);

    /**
     * Opens the zone catalog and resolves the keys directory used to locate key files.
     */
    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    /**
     * Limits missing-key reports to zone catalog entries the operator can read.
     */
    @Inject
    OperatorRightsService operatorRightsService;

    /**
     * Rejects deletion of a key file that any zone definition still references.
     *
     * @param scopedKeyPath Scoped path of the key file relative to the keys directory.
     * @throws KeyInUseException When one or more zone catalog entries reference the key file.
     * @throws UncheckedIOException When the zone catalog cannot be scanned.
     */
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

    /**
     * Requires the key file referenced by a zone definition to exist before that entry is reconciled.
     *
     * @param keyFile Scoped path of the key file stored in the zone definition.
     * @throws MissingKeyFileException When the key file is absent from the keys directory.
     */
    public void assertKeyPresent(String keyFile) {
        if (!ZoneKeyReferenceScanner.keyExists(zoneCatalogSupport.keysDir(), keyFile)) {
            throw new MissingKeyFileException(keyFile);
        }
    }

    /**
     * Lists key files that readable zone catalog entries reference and that are absent from the keys directory.
     * Reconciliation of those entries cannot authenticate AXFR or nsupdate until the key file is restored.
     *
     * @return Summaries of missing key files and how many zone catalog entries reference each one.
     * @throws UncheckedIOException When the zone catalog or keys directory cannot be scanned.
     */
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

    /**
     * Lists TSIG key files the operator may reference from a zone definition.
     * The list covers the operator's personal owner folder and each group owner folder the operator belongs to.
     *
     * @param operator Operator selecting a key file for a zone definition.
     * @return Selectable key files, ordered by display label.
     * @throws UncheckedIOException When an owner folder cannot be read.
     */
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

    /**
     * A TSIG key file an operator may store in a zone definition.
     *
     * @param scopedPath Scoped path of the key file relative to the keys directory, as stored in a zone definition.
     * @param displayLabel Label used when an operator selects this key file.
     * @param kind Owner kind of the owner folder that contains the key file.
     */
    public record SelectableKey(String scopedPath, String displayLabel, OwnerFolderKind kind) {}

    /**
     * A TSIG key file that zone definitions reference and that is missing from the keys directory.
     *
     * @param scopedKeyPath Scoped path stored in those zone definitions.
     * @param affectedEntryCount Number of zone catalog entries that reference this key file.
     */
    public record MissingKeySummary(String scopedKeyPath, int affectedEntryCount) {}
}
