package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.core.zoneview.RecordStatus;
import de.neitzel.dnscheck.core.zoneview.ZoneView;
import de.neitzel.dnscheck.core.zoneview.ZoneViewRecord;
import de.neitzel.dnscheck.service.api.generated.model.ZoneViewRecordStatus;
import de.neitzel.dnscheck.service.api.generated.model.ZoneViewResponse;

public final class ZoneViewApiMapper {

    private ZoneViewApiMapper() {}

    public static ZoneViewResponse toResponse(ZoneView view) {
        ZoneViewResponse response = new ZoneViewResponse();
        response.setZone(view.zone());
        response.setDefaultTtl(view.defaultTtl());
        response.setRecords(view.records().stream().map(ZoneViewApiMapper::toRecord).toList());
        return response;
    }

    private static de.neitzel.dnscheck.service.api.generated.model.ZoneViewRecord toRecord(ZoneViewRecord record) {
        de.neitzel.dnscheck.service.api.generated.model.ZoneViewRecord apiRecord =
                new de.neitzel.dnscheck.service.api.generated.model.ZoneViewRecord();
        apiRecord.setName(record.nameFqdn());
        apiRecord.setType(record.type());
        apiRecord.setStatus(toApiStatus(record.status()));
        apiRecord.setRdata(record.rdata());
        apiRecord.setTtl(record.ttl());
        apiRecord.setExpectedRdata(record.expectedRdata());
        apiRecord.setExpectedTtl(record.expectedTtl());
        return apiRecord;
    }

    private static ZoneViewRecordStatus toApiStatus(RecordStatus status) {
        return switch (status) {
            case ENFORCED -> ZoneViewRecordStatus.ENFORCED;
            case AD_HOC -> ZoneViewRecordStatus.AD_HOC;
            case DRIFTED -> ZoneViewRecordStatus.DRIFTED;
            case MISSING -> ZoneViewRecordStatus.MISSING;
            case MARKED_FOR_DELETION -> ZoneViewRecordStatus.MARKED_FOR_DELETION;
        };
    }
}
