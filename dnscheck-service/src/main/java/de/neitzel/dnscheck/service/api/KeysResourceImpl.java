package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.MissingKeyEntry;
import de.neitzel.dnscheck.service.api.generated.model.MissingKeysResponse;
import de.neitzel.dnscheck.service.api.generated.model.SelectableKey;
import de.neitzel.dnscheck.service.api.generated.model.SelectableKeysResponse;
import de.neitzel.dnscheck.service.api.generated.resources.KeysResource;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.keys.KeyUsageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class KeysResourceImpl implements KeysResource {

    @Inject
    OperatorRightsService operatorRightsService;

    @Inject
    KeyUsageService keyUsageService;

    @Override
    @Transactional
    public SelectableKeysResponse listSelectableKeys() {
        Operator operator = operatorRightsService.requireCurrentOperator();
        SelectableKeysResponse response = new SelectableKeysResponse();
        response.setKeys(keyUsageService.listSelectableKeys(operator).stream()
                .map(KeysResourceImpl::toSelectable)
                .toList());
        return response;
    }

    @Override
    public MissingKeysResponse listMissingKeys() {
        operatorRightsService.requireCurrentOperator();
        MissingKeysResponse response = new MissingKeysResponse();
        response.setMissing(keyUsageService.listMissingKeys().stream()
                .map(KeysResourceImpl::toMissing)
                .toList());
        return response;
    }

    private static SelectableKey toSelectable(KeyUsageService.SelectableKey key) {
        SelectableKey api = new SelectableKey();
        api.setScopedPath(key.scopedPath());
        api.setDisplayLabel(key.displayLabel());
        return api;
    }

    private static MissingKeyEntry toMissing(KeyUsageService.MissingKeySummary summary) {
        MissingKeyEntry api = new MissingKeyEntry();
        api.setScopedKeyPath(summary.scopedKeyPath());
        api.setAffectedEntryCount(summary.affectedEntryCount());
        return api;
    }
}
