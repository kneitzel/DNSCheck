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

/**
 * HTTP API for TSIG keys an operator may attach to a zone and for key files missing from the zone
 * catalog.
 */
@ApplicationScoped
public class KeysResourceImpl implements KeysResource {

    /**
     * Requires an authenticated operator before key summaries are returned.
     */
    @Inject
    OperatorRightsService operatorRightsService;

    /**
     * Lists selectable TSIG keys and key files missing from readable zone catalog entries.
     */
    @Inject
    KeyUsageService keyUsageService;

    /**
     * Handles GET /api/v1/keys/selectable by listing TSIG keys the current operator may attach to a
     * zone definition. Requires an authenticated operator. The keys come from that operator's owner
     * folder and from groups the operator belongs to.
     *
     * @return Selectable keys the operator may reference from a zone definition.
     */
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

    /**
     * Handles GET /api/v1/keys/missing by summarizing key files that readable zone catalog entries
     * reference and that are absent on disk. Requires an authenticated operator.
     *
     * @return Missing key files limited to zone catalog entries the operator may read.
     */
    @Override
    public MissingKeysResponse listMissingKeys() {
        operatorRightsService.requireCurrentOperator();
        MissingKeysResponse response = new MissingKeysResponse();
        response.setMissing(keyUsageService.listMissingKeys().stream()
                .map(KeysResourceImpl::toMissing)
                .toList());
        return response;
    }

    /**
     * Maps one selectable TSIG key onto the API model.
     *
     * @param key Supplies the scoped path and display label the operator may attach to a zone.
     * @return API key choice for zone create and update.
     */
    private static SelectableKey toSelectable(KeyUsageService.SelectableKey key) {
        SelectableKey api = new SelectableKey();
        api.setScopedPath(key.scopedPath());
        api.setDisplayLabel(key.displayLabel());
        return api;
    }

    /**
     * Maps one missing key file onto the API model.
     *
     * @param summary Supplies the scoped key path and how many zone catalog entries reference it.
     * @return API entry for the missing-keys response.
     */
    private static MissingKeyEntry toMissing(KeyUsageService.MissingKeySummary summary) {
        MissingKeyEntry api = new MissingKeyEntry();
        api.setScopedKeyPath(summary.scopedKeyPath());
        api.setAffectedEntryCount(summary.affectedEntryCount());
        return api;
    }
}
