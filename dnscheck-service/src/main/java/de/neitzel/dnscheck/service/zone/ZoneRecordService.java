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

@ApplicationScoped
public class ZoneRecordService {

    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    @Inject
    OperatorRightsService operatorRightsService;

    @Inject
    LiveZoneLoader liveZoneLoader;

    @Inject
    DnsUpdater dnsUpdater;

    private final ZoneRecordCreator recordCreator = new ZoneRecordCreator();
    private final ZoneRecordLifecycle recordLifecycle = new ZoneRecordLifecycle();
    private final ZoneViewMerger merger = new ZoneViewMerger();

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

    public ZoneView promoteRecord(
            String ownerFolder, String zoneName, String name, String type, String rdata, Integer ttl) {
        return executeWrite(ownerFolder, zoneName, (definition, keysDir) -> {
            var liveZone = liveZoneLoader.load(definition, keysDir);
            return recordLifecycle.promote(definition, liveZone, name, type, rdata, ttl);
        });
    }

    public ZoneView demoteRecord(String ownerFolder, String zoneName, String name, String type, String rdata) {
        return executeWrite(ownerFolder, zoneName, (definition, keysDir) ->
                recordLifecycle.demote(definition, name, type, rdata));
    }

    public ZoneView deleteRecord(String ownerFolder, String zoneName, String name, String type, String rdata) {
        return executeWrite(ownerFolder, zoneName, (definition, keysDir) -> {
            var liveZone = liveZoneLoader.load(definition, keysDir);
            return recordLifecycle.delete(definition, liveZone, name, type, rdata);
        });
    }

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

    @FunctionalInterface
    private interface LifecycleAction {
        ZoneRecordLifecycle.LifecycleResult apply(ZoneDefinition definition, Path keysDir) throws IOException;
    }
}
