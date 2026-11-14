package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.dns.LiveZoneClient;
import de.neitzel.dnscheck.core.dns.NsupdateClient;
import de.neitzel.dnscheck.core.model.*;
import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.core.util.GlobMatcher;
import de.neitzel.dnscheck.core.zone.LocalZoneDirectory;
import de.neitzel.dnscheck.core.zone.ZoneNameNormalizer;
import de.neitzel.dnscheck.fx.model.*;
import jakarta.inject.Singleton;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Name;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Desktop file access and explicit, record-scoped live DNS operations. */
@Singleton
public class ZoneFiles {
    private final LiveZoneClient liveClient;
    private final NsupdateClient updater;

    public ZoneFiles() { this(new LiveZoneClient(), new NsupdateClient()); }
    ZoneFiles(LiveZoneClient liveClient, NsupdateClient updater) {
        this.liveClient = liveClient;
        this.updater = updater;
    }

    public List<CatalogEntryModel> load(Path directory) throws IOException {
        return new LocalZoneDirectory(directory).entries().stream().map(CatalogEntryModel::from).toList();
    }
    public ZoneEditor open(Path directory, CatalogEntryModel entry) throws IOException {
        return ZoneEditor.from(new LocalZoneDirectory(directory).read(entry.getZone()));
    }
    public void save(ZoneEditor editor) throws IOException {
        var definition = editor.toDefinition();
        directory(definition).write(definition);
        editor.markSaved();
    }
    public ZoneReconciliationResult reconcile(Path directory, String zone, boolean dryRun) {
        return new LocalZoneDirectory(directory).reconcile(zone, dryRun);
    }
    public LiveZone readLive(ZoneDefinition definition) throws IOException {
        return liveClient.loadIncludingSoa(definition.zone(), definition.nameserver(),
                directory(definition).readKey(definition.keyFile()));
    }

    /** Validate and canonicalize one edited record without accessing the network. */
    public static RecordSpec validateRecord(String zone, RecordSpec record) throws IOException {
        ZoneNameNormalizer normalizer = new ZoneNameNormalizer();
        String name = normalizer.normalize(record.nameFqdn(), zone);
        Name dnsName = normalizer.toDnsName(name);
        if (!dnsName.subdomain(normalizer.toDnsName(normalizer.normalize("@", zone)))) {
            throw new IllegalArgumentException("Record name must belong to " + zone);
        }
        int type = Type.value(record.type().toUpperCase(), true);
        if (type < 0 || !Type.isRR(type) || type == Type.SOA) {
            throw new IllegalArgumentException("Unsupported editable record type: " + record.type());
        }
        String rdata = record.rdata();
        if (!record.deletionMarker()) {
            var parsed = org.xbill.DNS.Record.fromString(dnsName, type, DClass.IN, 3600, rdata, Name.root);
            rdata = parsed.rdataToString();
        }
        return new RecordSpec(name, Type.string(type), rdata, record.rdataPattern(),
                record.deletionMarker(), record.ttlPolicy());
    }

    /** File intent is committed first. A failed online step is reported separately and never rolls it back. */
    public MutationResult applyRecord(ZoneDefinition expected, RecordChange request) throws IOException {
        LocalZoneDirectory directory = directory(expected);
        ZoneDefinition current = directory.read(expected.zone());
        if (!current.equals(expected)) throw new IOException("The domain file changed on disk. Reload it before editing records.");
        RecordSpec replacement = validateRecord(current.zone(), request.replacement());
        List<RecordSpec> records = new ArrayList<>(current.records());
        RecordSpec original = request.original() == null ? null : request.original().stored();
        int index = original == null ? -1 : records.indexOf(original);
        if (original != null && index < 0) throw new IOException("The file entry no longer exists. Reload the domain.");
        if (index >= 0) records.remove(index);
        if (request.store()) {
            if (records.contains(replacement)) throw new IllegalArgumentException("This entry is already stored in the domain file.");
            records.add(index < 0 ? records.size() : index, replacement);
        }
        ZoneDefinition updated = new ZoneDefinition(current.zone(), current.nameserver(), current.keyFile(), records, current.sourcePath());
        boolean fileChanged = !updated.equals(current);
        if (fileChanged) {
            directory.write(updated);
            // The JSON writer normalizes optional fields; keep the exact new on-disk snapshot for later edits.
            updated = directory.read(updated.zone());
        }
        String fileMessage = fileChanged ? "Domain file saved. " : "Domain file unchanged. ";
        if (!request.online()) return new MutationResult(updated, null, false, false, fileMessage + "No online change requested.");
        try {
            LiveZone live = readLive(updated);
            List<PlannedChange> changes = plan(request, replacement, live);
            updater.apply(updated.zone(), updated.nameserver(), directory.readKey(updated.keyFile()), changes);
            try {
                LiveZone refreshed = readLive(updated);
                return new MutationResult(updated, refreshed, true, true,
                        fileMessage + (changes.isEmpty() ? "Live DNS already matches." : "Live DNS updated."));
            } catch (Exception refreshFailure) {
                return new MutationResult(updated, null, true, true,
                        fileMessage + "Live update completed, but refresh failed: " + text(refreshFailure));
            }
        } catch (Exception onlineFailure) {
            return new MutationResult(updated, null, true, false,
                    fileMessage + "Live update failed or could not be confirmed: " + text(onlineFailure));
        }
    }

    private static List<PlannedChange> plan(RecordChange request, RecordSpec replacement, LiveZone live) {
        List<PlannedChange> changes = new ArrayList<>();
        if (replacement.deletionMarker()) {
            for (DnsRecord record : live.records()) {
                if (record.nameFqdn().equalsIgnoreCase(replacement.nameFqdn())
                        && record.type().equalsIgnoreCase(replacement.type())
                        && GlobMatcher.matches(replacement.rdataPattern(), record.rdata())) {
                    changes.add(change(ChangeAction.DELETE, record, null));
                }
            }
            return List.copyOf(changes);
        }
        DnsRecord previous = request.original() == null ? null : request.original().live();
        if (previous != null) {
            DnsRecord selected = previous;
            previous = live.records().stream().filter(record -> sameRecord(record, selected)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("The selected live record changed. Refresh before applying this edit."));
        }
        long ttl = replacement.ttlPolicy() instanceof TtlPolicy.Ignore && previous != null
                ? previous.ttl() : replacement.ttlPolicy().resolveSeconds(live.defaultTtl());
        DnsRecord desired = new DnsRecord(replacement.nameFqdn(), replacement.type(), replacement.rdata(), ttl);
        if (previous != null && !sameRecord(previous, desired)) changes.add(change(ChangeAction.DELETE, previous, null));
        DnsRecord target = live.records().stream().filter(record -> sameRecord(record, desired)).findFirst().orElse(null);
        if (target == null) changes.add(change(ChangeAction.ADD, desired, ttl));
        else if (target.ttl() != ttl) changes.add(change(ChangeAction.UPDATE, desired, ttl));
        return List.copyOf(changes);
    }

    private static boolean sameRecord(DnsRecord left, DnsRecord right) {
        return left.nameFqdn().equalsIgnoreCase(right.nameFqdn()) && left.type().equalsIgnoreCase(right.type())
                && Objects.equals(left.rdata(), right.rdata());
    }
    private static PlannedChange change(ChangeAction action, DnsRecord record, Long ttl) {
        return new PlannedChange(action, record.nameFqdn(), record.type(), record.rdata(), ttl, "desktop record edit");
    }
    private static LocalZoneDirectory directory(ZoneDefinition definition) {
        return new LocalZoneDirectory(definition.sourcePath().toAbsolutePath().getParent());
    }
    private static String text(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
    public record MutationResult(ZoneDefinition definition, LiveZone live, boolean onlineAttempted,
                                 boolean onlineApplied, String message) {}
}
