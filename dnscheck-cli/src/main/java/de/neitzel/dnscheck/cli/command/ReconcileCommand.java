package de.neitzel.dnscheck.cli.command;

import de.neitzel.dnscheck.core.zone.LocalZoneDirectory;
import de.neitzel.dnscheck.core.reconcile.ReconciliationRunResult;
import de.neitzel.dnscheck.core.reconcile.ZoneOutcome;
import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Reconcile one catalog entry or all zones in the catalog.
 */
@Command(
        name = "reconcile",
        mixinStandardHelpOptions = true,
        description = "Reconcile live DNS against .domain JSON files in one directory")
public class ReconcileCommand implements Callable<Integer> {

    /**
     * Bare DNS zone name to reconcile within the selected local directory.
     * When this is omitted, the reconciliation run covers every entry in the zone catalog.
     */
    @Parameters(
            index = "0",
            arity = "0..1",
            description = "Zone to reconcile (e.g. example.com). Omit to reconcile all .domain files.")
    String zone;

    /**
     * Selects a dry-run, so the reconciliation run reports drift without sending nsupdate.
     */
    @Option(
            names = {"--dry-run"},
            description = "Detect drift without applying nsupdate")
    boolean dryRun;

    /** Directory containing both definitions and keys. */
    @Option(names = "--directory", description = "Directory containing .domain and key files",
            defaultValue = "${env:DNSCHECK_DIRECTORY:-.}")
    Path directory;

    /**
     * Runs reconciliation for one zone catalog entry, or for every entry when none is named.
     *
     * @return {@code 0} when no zone outcome failed, otherwise {@code 1}
     */
    @Override
    public Integer call() {
        var engine = new LocalZoneDirectory(directory);
        if (zone == null) {
            ReconciliationRunResult result = engine.reconcileAll(dryRun);
            result.zoneResults().forEach(this::printResult);
            return result.zoneResults().stream().anyMatch(this::isFailure) ? 1 : 0;
        }

        ZoneReconciliationResult result = engine.reconcile(zone, dryRun);
        printResult(result);
        return isFailure(result) ? 1 : 0;
    }

    /**
     * Prints one zone outcome, the planned changes, and any error text.
     *
     * @param result the result for one zone in the reconciliation run
     */
    private void printResult(ZoneReconciliationResult result) {
        System.out.printf("%s: %s%n", result.zoneName(), result.outcome());
        if (!result.plannedChanges().isEmpty()) {
            result.plannedChanges().forEach(change -> System.out.printf("  - %s%n", change));
        }
        if (result.errorMessage() != null) {
            System.err.printf("  error: %s%n", result.errorMessage());
        }
    }

    /**
     * Returns whether the zone outcome failed.
     *
     * @param result the result for one zone
     * @return {@code true} when the outcome is {@link ZoneOutcome#FAILED}
     */
    private boolean isFailure(ZoneReconciliationResult result) {
        return result.outcome() == ZoneOutcome.FAILED;
    }
}
