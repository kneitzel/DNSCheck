package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.model.PlannedChange;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.zone.InvalidRecordStateException;
import de.neitzel.dnscheck.core.zone.NoSuchRecordException;
import de.neitzel.dnscheck.core.zone.ZoneRecordCreator;
import de.neitzel.dnscheck.core.zone.ZoneRecordLifecycle;
import de.neitzel.dnscheck.core.zoneview.ZoneView;
import de.neitzel.dnscheck.core.zoneview.ZoneViewMerger;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Changes managed records and live zone records, then returns the zone view that shows the new record status.
 */
@ApplicationScoped
public class ZoneRecordService {

    /**
     * Opens the zone catalog and resolves the keys directory for the live zone and nsupdate.
     */
    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    /**
     * Requires write on the zone catalog entry before a record is changed.
     */
    @Inject
    OperatorRightsService operatorRightsService;

    /**
     * Loads the live zone before a change and again when building the returned zone view.
     */
    @Inject
    LiveZoneLoader liveZoneLoader;

    /**
     * Applies planned changes to the live zone with nsupdate.
     */
    @Inject
    DnsUpdater dnsUpdater;

    /**
     * Builds a managed record and the planned add for a new live record.
     */
    private final ZoneRecordCreator recordCreator = new ZoneRecordCreator();

    /**
     * Plans promotion, demotion, deletion, and modification of a record.
     */
    private final ZoneRecordLifecycle recordLifecycle = new ZoneRecordLifecycle();

    /**
     * Merges the zone definition with the refreshed live zone into a zone view.
     */
    private final ZoneViewMerger merger = new ZoneViewMerger();

    /**
     * Adds a record to the live zone.
     * When enforcement is requested, the record is also stored as a managed record in the zone definition; otherwise it remains an ad-hoc record.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param name Record name, as an FQDN or relative to the zone.
     * @param type DNS record type.
     * @param rdata Rdata of the new record.
     * @param ttl Requested TTL for the TTL policy. {@code null} ignores TTL, {@code -1} uses the zone default TTL, and a positive value is enforced.
     * @param enforce Whether the record is enforced. Enforced records are written to the zone definition.
     * @return Zone view after the live zone is updated, including record status.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry does not exist.
     * @throws BadRequestException When the record state is rejected.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent.
     * @throws UncheckedIOException When the zone definition, live zone, or nsupdate cannot be completed.
     */
    public ZoneView createRecord(
            String ownerFolder,
            String zoneName,
            String name,
            String type,
            String rdata,
            Integer ttl,
            boolean enforce) {
        return executeWrite(ownerFolder, zoneName, (definition, keysDir) -> {
            var liveZone = liveZoneLoader.load(definition, keysDir);
            RecordSpec spec = recordCreator.buildRecordSpec(zoneName, name, type, rdata, ttl);
            PlannedChange add = recordCreator.buildAddChange(spec, liveZone.defaultTtl(), enforce);

            ZoneDefinition viewDefinition = definition;
            if (enforce) {
                viewDefinition = recordCreator.enforceRecord(definition, spec);
            }

            return new ZoneRecordLifecycle.LifecycleResult(viewDefinition, List.of(add));
        });
    }

    /**
     * Promotes an ad-hoc record to a managed record so later reconciliation runs enforce it.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param name Record name, as an FQDN or relative to the zone.
     * @param type DNS record type.
     * @param rdata Rdata of the live record being promoted.
     * @param ttl TTL stored on the managed record. {@code null} ignores TTL, {@code -1} uses the zone default TTL, and a positive value is enforced.
     * @return Zone view showing the promoted record's status.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry or live record does not exist.
     * @throws BadRequestException When the record is already a managed record.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent.
     * @throws UncheckedIOException When the zone definition or live zone cannot be read or written.
     */
    public ZoneView promoteRecord(
            String ownerFolder, String zoneName, String name, String type, String rdata, Integer ttl) {
        return executeWrite(ownerFolder, zoneName, (definition, keysDir) -> {
            var liveZone = liveZoneLoader.load(definition, keysDir);
            return recordLifecycle.promote(definition, liveZone, name, type, rdata, ttl);
        });
    }

    /**
     * Demotes a managed record so it becomes an ad-hoc record.
     * The live zone record stays in DNS, and reconciliation leaves it unchanged unless a deletion marker matches.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param name Record name, as an FQDN or relative to the zone.
     * @param type DNS record type.
     * @param rdata Rdata of the managed record being demoted.
     * @return Zone view showing the record as ad-hoc.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry or managed record does not exist.
     * @throws BadRequestException When the record state is rejected.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent while refreshing the zone view.
     * @throws UncheckedIOException When the zone definition or live zone cannot be read or written.
     */
    public ZoneView demoteRecord(String ownerFolder, String zoneName, String name, String type, String rdata) {
        return executeWrite(ownerFolder, zoneName, (definition, keysDir) ->
                recordLifecycle.demote(definition, name, type, rdata));
    }

    /**
     * Deletes a record from the live zone.
     * A matching managed record is removed from the zone definition as well.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param name Record name, as an FQDN or relative to the zone.
     * @param type DNS record type.
     * @param rdata Rdata of the record to delete.
     * @return Zone view after the deletion.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry or record does not exist.
     * @throws BadRequestException When the record state is rejected.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent.
     * @throws UncheckedIOException When the zone definition, live zone, or nsupdate cannot be completed.
     */
    public ZoneView deleteRecord(String ownerFolder, String zoneName, String name, String type, String rdata) {
        return executeWrite(ownerFolder, zoneName, (definition, keysDir) -> {
            var liveZone = liveZoneLoader.load(definition, keysDir);
            return recordLifecycle.delete(definition, liveZone, name, type, rdata);
        });
    }

    /**
     * Changes the rdata or TTL of a record in the live zone.
     * A managed record is updated in the zone definition as well, and an ad-hoc record stays out of the zone definition.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param name Record name, as an FQDN or relative to the zone.
     * @param type DNS record type.
     * @param rdata Current rdata of the record.
     * @param newRdata Replacement rdata, or {@code null} when the rdata stays unchanged.
     * @param newTtl Replacement TTL for the TTL policy, or {@code null} when the TTL policy stays unchanged. {@code -1} uses the zone default TTL, and a positive value is enforced.
     * @return Zone view after the modification, including record status.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry or record does not exist.
     * @throws BadRequestException When neither rdata nor TTL is supplied, or the record state is rejected.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent.
     * @throws UncheckedIOException When the zone definition, live zone, or nsupdate cannot be completed.
     */
    public ZoneView modifyRecord(
            String ownerFolder,
            String zoneName,
            String name,
            String type,
            String rdata,
            String newRdata,
            Integer newTtl) {
        return executeWrite(ownerFolder, zoneName, (definition, keysDir) -> {
            var liveZone = liveZoneLoader.load(definition, keysDir);
            return recordLifecycle.modify(definition, liveZone, name, type, rdata, newRdata, newTtl);
        });
    }

    /**
     * Loads the zone definition, applies the planned live-zone changes, and returns the refreshed zone view.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param action Record change to plan against the zone definition and live zone.
     * @return Zone view merged from the updated zone definition and the refreshed live zone.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry or the targeted record does not exist.
     * @throws BadRequestException When the record is in a state that rejects the change.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent.
     * @throws UncheckedIOException When loading, nsupdate, or the zone view refresh fails.
     */
    private ZoneView executeWrite(String ownerFolder, String zoneName, LifecycleAction action) {
        operatorRightsService.requireWrite(ownerFolder, zoneName);
        try {
            Path keysDir = zoneCatalogSupport.keysDir();
            ZoneDefinition definition = zoneCatalogSupport
                    .openCatalog()
                    .find(ownerFolder, zoneName)
                    .orElseThrow(NotFoundException::new);

            ZoneRecordLifecycle.LifecycleResult result = action.apply(definition, keysDir);
            if (!result.dnsChanges().isEmpty()) {
                dnsUpdater.apply(definition, keysDir, result.dnsChanges());
            }

            var refreshedLive = liveZoneLoader.load(result.viewDefinition(), keysDir);
            return merger.merge(result.viewDefinition(), refreshedLive);
        } catch (NoSuchRecordException e) {
            throw new NotFoundException();
        } catch (InvalidRecordStateException e) {
            throw new BadRequestException(e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Plans one record change against a zone definition and its keys directory.
     */
    @FunctionalInterface
    private interface LifecycleAction {

        /**
         * Plans the zone definition update and the nsupdate changes for one record operation.
         *
         * @param definition Zone definition loaded for the zone catalog entry.
         * @param keysDir Keys directory used to load the live zone and the TSIG key file.
         * @return Planned zone definition and live-zone changes.
         * @throws IOException When the live zone or key file cannot be read.
         */
        ZoneRecordLifecycle.LifecycleResult apply(ZoneDefinition definition, Path keysDir) throws IOException;
    }
}
