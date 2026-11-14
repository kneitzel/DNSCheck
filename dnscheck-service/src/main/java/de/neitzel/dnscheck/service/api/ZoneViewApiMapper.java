package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.core.zoneview.RecordStatus;
import de.neitzel.dnscheck.core.zoneview.ZoneView;
import de.neitzel.dnscheck.core.zoneview.ZoneViewRecord;
import de.neitzel.dnscheck.service.api.generated.model.ZoneViewRecordStatus;
import de.neitzel.dnscheck.service.api.generated.model.ZoneViewResponse;

/**
 * Maps a zone view, including each record status, onto the zone view API models.
 */
public final class ZoneViewApiMapper {

    /**
     * Prevents instantiation of this mapper.
     */
    private ZoneViewApiMapper() {}

    /**
     * Maps a zone view onto the API payload, including the zone default TTL and each record status.
     *
     * @param view Supplies the merged live zone and managed records.
     * @return Zone view returned to the operator.
     */
    public static ZoneViewResponse toResponse(ZoneView view) {
        ZoneViewResponse response = new ZoneViewResponse();
        response.setZone(view.zone());
        response.setDefaultTtl(view.defaultTtl());
        response.setRecords(view.records().stream().map(ZoneViewApiMapper::toRecord).toList());
        return response;
    }

    /**
     * Maps one zone view record, including live rdata, expected rdata, and record status.
     *
     * @param record Supplies one merged record from the zone view.
     * @return API record inside the zone view.
     */
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

    /**
     * Maps a domain record status onto the zone view status enum.
     *
     * @param status Supplies enforced, ad-hoc, drifted, missing, or marked for deletion status.
     * @return Matching API record status.
     */
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
