package de.neitzel.dnscheck.cli.command;

import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;
import de.neitzel.dnscheck.core.reconcile.DefaultReconciliationEngine;
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
        description = "Reconcile live DNS against zone definition JSON files")
public class ReconcileCommand implements Callable<Integer> {

    @Parameters(
            index = "0",
            arity = "0..1",
            description = "Catalog entry to reconcile (e.g. admin.u/example.com). Omit to reconcile all entries.")
    String catalogEntry;

    @Option(
            names = {"--dry-run"},
            description = "Detect drift without applying nsupdate")
    boolean dryRun;

    @Option(
            names = {"--zones-dir"},
            description = "Zone catalog directory",
            defaultValue = "${env:DNSCHECK_ZONES_DIR:-./zones}")
    String zonesDir;

    @Option(
            names = {"--keys-dir"},
            description = "TSIG keys directory",
            defaultValue = "${env:DNSCHECK_KEYS_DIR:-./keys}")
    String keysDir;

    @Override
    public Integer call() {
        var engine = new DefaultReconciliationEngine(Path.of(zonesDir), Path.of(keysDir));
        if (catalogEntry == null) {
            ReconciliationRunResult result = engine.reconcileAll(dryRun);
            result.zoneResults().forEach(this::printResult);
            return result.zoneResults().stream().anyMatch(this::isFailure) ? 1 : 0;
        }

        CatalogEntryIdentity identity = CatalogEntryIdentity.parseCatalogRef(catalogEntry);
        ZoneReconciliationResult result = engine.reconcileCatalogEntry(
                identity.ownerFolder().folderName(), identity.zoneName(), dryRun);
        printResult(result);
        return isFailure(result) ? 1 : 0;
    }

    private void printResult(ZoneReconciliationResult result) {
        System.out.printf("%s: %s%n", result.zoneName(), result.outcome());
        if (!result.plannedChanges().isEmpty()) {
            result.plannedChanges().forEach(change -> System.out.printf("  - %s%n", change));
        }
        if (result.errorMessage() != null) {
            System.err.printf("  error: %s%n", result.errorMessage());
        }
    }

    private boolean isFailure(ZoneReconciliationResult result) {
        return result.outcome() == ZoneOutcome.FAILED;
    }
}
