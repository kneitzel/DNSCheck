package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.AccountKeyDetail;
import de.neitzel.dnscheck.service.api.generated.model.AccountKeySummary;
import de.neitzel.dnscheck.service.keys.PersonalKeyService;

final class AccountKeysApiMapper {

    private AccountKeysApiMapper() {}

    static AccountKeySummary toSummary(PersonalKeyService.PersonalKeySummary summary) {
        AccountKeySummary api = new AccountKeySummary();
        api.setKeyName(summary.keyName());
        api.setScopedPath(summary.scopedPath());
        return api;
    }

    static AccountKeyDetail toDetail(PersonalKeyService.PersonalKeyDetail detail) {
        AccountKeyDetail api = new AccountKeyDetail();
        api.setKeyName(detail.keyName());
        api.setScopedPath(detail.scopedPath());
        api.setContent(detail.content());
        return api;
    }
}
