package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.AdminGlobalRight;
import de.neitzel.dnscheck.service.api.generated.model.AdminZoneRight;
import de.neitzel.dnscheck.service.api.generated.model.GlobalRightEntry;
import de.neitzel.dnscheck.service.api.generated.model.RightSource;
import de.neitzel.dnscheck.service.api.generated.model.RightSourceType;
import de.neitzel.dnscheck.service.api.generated.model.RightsSummaryResponse;
import de.neitzel.dnscheck.service.api.generated.model.ZoneRightEntry;
import de.neitzel.dnscheck.service.auth.RightsSummaryBuilder;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.ZoneRightKind;

/**
 * Maps effective global rights and zone rights onto the rights summary API models.
 */
public final class RightsApiMapper {

    /**
     * Prevents instantiation of this mapper.
     */
    private RightsApiMapper() {}

    /**
     * Maps effective global rights and zone rights onto the rights summary returned by the API.
     *
     * @param summary Supplies granted global rights, zone rights, and the sources they come from.
     * @return Rights summary for the current operator, another operator, or a group.
     */
    public static RightsSummaryResponse toRightsSummaryResponse(RightsSummaryBuilder.RightsSummary summary) {
        RightsSummaryResponse response = new RightsSummaryResponse();
        response.setGlobalRights(summary.globalRights().stream()
                .map(RightsApiMapper::toGlobalRightEntry)
                .toList());
        response.setZoneRights(
                summary.zoneRights().stream().map(RightsApiMapper::toZoneRightEntry).toList());
        return response;
    }

    /**
     * Maps one global right, whether it is granted, and the sources it comes from.
     *
     * @param entry Supplies the global right kind, grant flag, and sources.
     * @return API entry for the admin global right or the zone-create global right.
     */
    private static GlobalRightEntry toGlobalRightEntry(RightsSummaryBuilder.GlobalRightEntry entry) {
        GlobalRightEntry apiEntry = new GlobalRightEntry();
        apiEntry.setKind(toApiGlobalRight(entry.kind()));
        apiEntry.setGranted(entry.granted());
        apiEntry.setSources(entry.sources().stream().map(RightsApiMapper::toRightSource).toList());
        return apiEntry;
    }

    /**
     * Maps one zone right for a zone catalog entry, including its owner folder and sources.
     *
     * @param entry Supplies the owner folder, zone name, zone right, and sources.
     * @return API entry for a read or write zone right.
     */
    private static ZoneRightEntry toZoneRightEntry(RightsSummaryBuilder.ZoneRightEntry entry) {
        ZoneRightEntry apiEntry = new ZoneRightEntry();
        apiEntry.setOwnerFolder(entry.ownerFolder());
        apiEntry.setZoneName(entry.zoneName());
        apiEntry.setKind(toApiZoneRight(entry.kind()));
        apiEntry.setSources(entry.sources().stream().map(RightsApiMapper::toRightSource).toList());
        return apiEntry;
    }

    /**
     * Maps one explanation of why a global right or zone right is held.
     *
     * @param source Supplies the source type, group name, grant id, and whether that source can be
     *     edited.
     * @return API source for a direct grant or a grant held through a group.
     */
    private static RightSource toRightSource(RightsSummaryBuilder.RightSource source) {
        RightSource apiSource = new RightSource();
        apiSource.setType(toApiSourceType(source.type()));
        apiSource.setGroupName(source.groupName());
        apiSource.setGrantId(source.grantId());
        apiSource.setEditable(source.editable());
        return apiSource;
    }

    /**
     * Maps a persisted global right kind onto the admin API enum.
     *
     * @param kind Supplies the admin global right or the zone-create global right.
     * @return Matching API global right.
     */
    private static AdminGlobalRight toApiGlobalRight(GlobalRightKind kind) {
        return switch (kind) {
            case ADMIN -> AdminGlobalRight.ADMIN;
            case ZONE_CREATE -> AdminGlobalRight.ZONE_CREATE;
        };
    }

    /**
     * Maps a persisted zone right kind onto the admin API enum.
     *
     * @param kind Supplies the read zone right or the write zone right.
     * @return Matching API zone right.
     */
    private static AdminZoneRight toApiZoneRight(ZoneRightKind kind) {
        return switch (kind) {
            case READ -> AdminZoneRight.READ;
            case WRITE -> AdminZoneRight.WRITE;
        };
    }

    /**
     * Maps a direct operator grant or a group grant onto the API source type.
     *
     * @param type Supplies the domain source type.
     * @return API source type for a direct grant or a group grant.
     */
    private static RightSourceType toApiSourceType(RightsSummaryBuilder.RightSourceType type) {
        return switch (type) {
            case DIRECT -> RightSourceType.DIRECT;
            case GROUP -> RightSourceType.GROUP;
        };
    }
}
