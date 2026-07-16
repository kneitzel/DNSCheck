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

@ApplicationScoped
public class ZoneCatalogService {

    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    @Inject
    OperatorRightsService operatorRightsService;

    private final ZoneDefinitionCreator zoneDefinitionCreator = new ZoneDefinitionCreator();
    private final ZoneDefinitionMutator zoneDefinitionMutator = new ZoneDefinitionMutator();

    public List<ZoneCatalogEntry> listVisibleZones() {
        try {
            return zoneCatalogSupport.openCatalog().introspect().stream()
                    .filter(entry -> operatorRightsService.canReadZone(entry.ownerFolder(), entry.zone()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

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

    public String zonesDir() {
        return zoneCatalogSupport.zonesDir().toString();
    }
}
