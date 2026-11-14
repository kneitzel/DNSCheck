package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.AccountKeyDetail;
import de.neitzel.dnscheck.service.api.generated.model.AccountKeySummary;
import de.neitzel.dnscheck.service.keys.PersonalKeyService;

/**
 * Maps personal TSIG key summaries and details onto account key API models.
 */
final class AccountKeysApiMapper {

    /**
     * Prevents instantiation of this mapper.
     */
    private AccountKeysApiMapper() {}

    /**
     * Maps a personal TSIG key summary onto the account key list model.
     *
     * @param summary Supplies the personal key name and scoped path from the key service.
     * @return API summary used by account key list and upload responses.
     */
    static AccountKeySummary toSummary(PersonalKeyService.PersonalKeySummary summary) {
        AccountKeySummary api = new AccountKeySummary();
        api.setKeyName(summary.keyName());
        api.setScopedPath(summary.scopedPath());
        return api;
    }

    /**
     * Maps a personal TSIG key, including its file content, onto the account key detail model.
     *
     * @param detail Supplies the personal key name, scoped path, and file content.
     * @return API detail returned when an operator reads one personal key.
     */
    static AccountKeyDetail toDetail(PersonalKeyService.PersonalKeyDetail detail) {
        AccountKeyDetail api = new AccountKeyDetail();
        api.setKeyName(detail.keyName());
        api.setScopedPath(detail.scopedPath());
        api.setContent(detail.content());
        return api;
    }
}
