package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.reconcile.ZoneOutcome;
import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.fx.view.Message;

/**
 * Turns a zone outcome into text and a Message severity.
 */
public final class OutcomeText {

    /**
     * Prevents instantiation.
     */
    private OutcomeText() {
    }

    /**
     * Returns the catalog ref, the outcome, the planned changes, and any error text.
     *
     * @param result the zone outcome
     * @return the text
     */
    public static String of(ZoneReconciliationResult result) {
        StringBuilder text = new StringBuilder();
        text.append(result.zoneName()).append(": ").append(result.outcome());
        for (String change : result.plannedChanges()) {
            text.append('\n').append("  ").append(change);
        }
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            text.append('\n').append(result.errorMessage());
        }
        return text.toString();
    }

    /**
     * Returns the Message severity for a zone outcome.
     *
     * @param outcome the zone outcome
     * @return the severity
     */
    public static Message.Severity severity(ZoneOutcome outcome) {
        return switch (outcome) {
            case FAILED -> Message.Severity.ERROR;
            case DRIFT_DETECTED -> Message.Severity.WARN;
            case SUCCESS, RECONCILED -> Message.Severity.INFO;
        };
    }
}
