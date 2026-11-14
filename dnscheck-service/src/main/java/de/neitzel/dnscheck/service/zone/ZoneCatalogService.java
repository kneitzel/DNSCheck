package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.zone.ZoneAlreadyExistsException;
import de.neitzel.dnscheck.core.zone.ZoneCatalogEntry;
import de.neitzel.dnscheck.core.zone.ZoneDefinitionCreator;
import de.neitzel.dnscheck.core.zone.ZoneDefinitionMutator;
import de.neitzel.dnscheck.core.zone.ZoneDefinitionNotFoundException;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Creates, updates, deletes, and transfers zone definitions in the zone catalog.
 */
@ApplicationScoped
public class ZoneCatalogService {

    /**
     * Resolves the zone catalog directory and opens the zone catalog.
     */
    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    /**
     * Checks zone rights before a zone definition is listed or changed.
     */
    @Inject
    OperatorRightsService operatorRightsService;

    /**
     * Writes a new zone definition into an owner folder.
     */
    private final ZoneDefinitionCreator zoneDefinitionCreator = new ZoneDefinitionCreator();

    /**
     * Updates, deletes, and transfers an existing zone definition.
     */
    private final ZoneDefinitionMutator zoneDefinitionMutator = new ZoneDefinitionMutator();

    /**
     * Lists zone catalog entries the current operator is allowed to read.
     *
     * @return Zone catalog entries visible to the current operator.
     * @throws UncheckedIOException When the zone catalog cannot be scanned.
     */
    public List<ZoneCatalogEntry> listVisibleZones() {
        try {
            return zoneCatalogSupport.openCatalog().introspect().stream()
                    .filter(entry -> operatorRightsService.canReadZone(entry.ownerFolder(), entry.zone()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a zone definition in an owner folder.
     * Later reconciliation runs contact the given nameserver with the given TSIG key file.
     *
     * @param ownerFolder Name of the owner folder that will contain the zone definition.
     * @param zone Zone name, which also names the zone definition file.
     * @param nameserver Nameserver contacted for AXFR and nsupdate during reconciliation.
     * @param keyFile Scoped path of the TSIG key file used to authenticate those operations.
     * @return The new zone catalog entry.
     * @throws jakarta.ws.rs.ForbiddenException When the operator may not create a zone definition in that owner folder.
     * @throws WebApplicationException When the zone catalog entry already exists or a name is rejected.
     * @throws UncheckedIOException When the zone definition cannot be written.
     */
    public ZoneCatalogEntry createZone(String ownerFolder, String zone, String nameserver, String keyFile) {
        operatorRightsService.requireCreateInOwnerFolder(ownerFolder);
        try {
            return zoneDefinitionCreator.create(
                    zoneCatalogSupport.zonesDir(), ownerFolder, zone, nameserver, keyFile);
        } catch (ZoneAlreadyExistsException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Updates the nameserver and key file stored in a zone definition.
     * The next reconciliation run uses those values for AXFR and nsupdate.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zone Zone name of the zone catalog entry.
     * @param nameserver Nameserver contacted for AXFR and nsupdate during reconciliation.
     * @param keyFile Scoped path of the TSIG key file used to authenticate those operations.
     * @return Zone catalog entry after the update.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry does not exist.
     * @throws WebApplicationException When a nameserver or key file value is rejected.
     * @throws UncheckedIOException When the zone definition cannot be rewritten.
     */
    public ZoneCatalogEntry updateZone(String ownerFolder, String zone, String nameserver, String keyFile) {
        operatorRightsService.requireWrite(ownerFolder, zone);
        try {
            return zoneDefinitionMutator.update(
                    zoneCatalogSupport.zonesDir(), ownerFolder, zone, nameserver, keyFile);
        } catch (ZoneDefinitionNotFoundException e) {
            throw new NotFoundException();
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Deletes a zone definition from the zone catalog and leaves the live zone in place.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zone Zone name of the zone catalog entry.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry does not exist.
     * @throws WebApplicationException When an owner folder or zone name is rejected.
     * @throws UncheckedIOException When the zone definition cannot be deleted.
     */
    public void deleteZone(String ownerFolder, String zone) {
        operatorRightsService.requireWrite(ownerFolder, zone);
        try {
            zoneDefinitionMutator.delete(zoneCatalogSupport.zonesDir(), ownerFolder, zone);
        } catch (ZoneDefinitionNotFoundException e) {
            throw new NotFoundException();
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Moves a zone definition to another owner folder and retargets its supplemental zone grants.
     *
     * @param fromOwnerFolder Owner folder that currently contains the zone definition.
     * @param zone Zone name of the zone catalog entry.
     * @param toOwnerFolder Owner folder that will contain the zone definition.
     * @return Zone catalog entry after the move.
     * @throws jakarta.ws.rs.ForbiddenException When the operator may not transfer the zone definition.
     * @throws NotFoundException When the zone catalog entry does not exist.
     * @throws WebApplicationException When the destination already has that zone name, or a folder name is rejected.
     * @throws UncheckedIOException When the zone definition cannot be moved.
     */
    @Transactional
    public ZoneCatalogEntry transferZone(String fromOwnerFolder, String zone, String toOwnerFolder) {
        operatorRightsService.requireTransfer(fromOwnerFolder);
        try {
            ZoneCatalogEntry moved = zoneDefinitionMutator.transfer(
                    zoneCatalogSupport.zonesDir(), fromOwnerFolder, zone, toOwnerFolder);
            rekeySupplementalGrants(fromOwnerFolder, zone, toOwnerFolder);
            return moved;
        } catch (ZoneDefinitionNotFoundException e) {
            throw new NotFoundException();
        } catch (ZoneAlreadyExistsException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Points supplemental zone grants at the destination owner folder.
     * A grant that would duplicate one already stored for the destination is removed.
     *
     * @param fromOwnerFolder Owner folder the zone definition is leaving.
     * @param zone Zone name of the zone catalog entry.
     * @param toOwnerFolder Owner folder the zone definition is entering.
     */
    private static void rekeySupplementalGrants(String fromOwnerFolder, String zone, String toOwnerFolder) {
        List<ZoneGrant> grants = ZoneGrant.list("ownerFolder = ?1 and zoneName = ?2", fromOwnerFolder, zone);
        for (ZoneGrant grant : grants) {
            boolean duplicate = grant.operator != null
                    ? ZoneGrant.count(
                                    "operator = ?1 and ownerFolder = ?2 and zoneName = ?3 and kind = ?4",
                                    grant.operator,
                                    toOwnerFolder,
                                    zone,
                                    grant.kind)
                            > 0
                    : ZoneGrant.count(
                                    "group = ?1 and ownerFolder = ?2 and zoneName = ?3 and kind = ?4",
                                    grant.group,
                                    toOwnerFolder,
                                    zone,
                                    grant.kind)
                            > 0;
            if (duplicate) {
                grant.delete();
            } else {
                grant.ownerFolder = toOwnerFolder;
            }
        }
    }

    /**
     * Returns the configured directory that holds the zone catalog.
     *
     * @return Filesystem path of the zone catalog.
     */
    public String zonesDir() {
        return zoneCatalogSupport.zonesDir().toString();
    }
}
