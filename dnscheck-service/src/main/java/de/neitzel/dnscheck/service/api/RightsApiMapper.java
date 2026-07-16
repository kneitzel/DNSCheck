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

public final class RightsApiMapper {

    private RightsApiMapper() {}

    public static RightsSummaryResponse toRightsSummaryResponse(RightsSummaryBuilder.RightsSummary summary) {
        RightsSummaryResponse response = new RightsSummaryResponse();
        response.setGlobalRights(summary.globalRights().stream()
                .map(RightsApiMapper::toGlobalRightEntry)
                .toList());
        response.setZoneRights(
                summary.zoneRights().stream().map(RightsApiMapper::toZoneRightEntry).toList());
        return response;
    }

    private static GlobalRightEntry toGlobalRightEntry(RightsSummaryBuilder.GlobalRightEntry entry) {
        GlobalRightEntry apiEntry = new GlobalRightEntry();
        apiEntry.setKind(toApiGlobalRight(entry.kind()));
        apiEntry.setGranted(entry.granted());
        apiEntry.setSources(entry.sources().stream().map(RightsApiMapper::toRightSource).toList());
        return apiEntry;
    }

    private static ZoneRightEntry toZoneRightEntry(RightsSummaryBuilder.ZoneRightEntry entry) {
        ZoneRightEntry apiEntry = new ZoneRightEntry();
        apiEntry.setOwnerFolder(entry.ownerFolder());
        apiEntry.setZoneName(entry.zoneName());
        apiEntry.setKind(toApiZoneRight(entry.kind()));
        apiEntry.setSources(entry.sources().stream().map(RightsApiMapper::toRightSource).toList());
        return apiEntry;
    }

    private static RightSource toRightSource(RightsSummaryBuilder.RightSource source) {
        RightSource apiSource = new RightSource();
        apiSource.setType(toApiSourceType(source.type()));
        apiSource.setGroupName(source.groupName());
        apiSource.setGrantId(source.grantId());
        apiSource.setEditable(source.editable());
        return apiSource;
    }

    private static AdminGlobalRight toApiGlobalRight(GlobalRightKind kind) {
        return switch (kind) {
            case ADMIN -> AdminGlobalRight.ADMIN;
            case ZONE_CREATE -> AdminGlobalRight.ZONE_CREATE;
        };
    }

    private static AdminZoneRight toApiZoneRight(ZoneRightKind kind) {
        return switch (kind) {
            case READ -> AdminZoneRight.READ;
            case WRITE -> AdminZoneRight.WRITE;
        };
    }

    private static RightSourceType toApiSourceType(RightsSummaryBuilder.RightSourceType type) {
        return switch (type) {
            case DIRECT -> RightSourceType.DIRECT;
            case GROUP -> RightSourceType.GROUP;
        };
    }
}
