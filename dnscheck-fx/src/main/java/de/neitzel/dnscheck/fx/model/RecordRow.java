package de.neitzel.dnscheck.fx.model;

import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.util.GlobMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A file entry, its matching live record, or a record found only in live DNS. */
public record RecordRow(RecordSpec stored, DnsRecord live, String status) {
    public enum Kind { ENFORCED, REMOVAL, LIVE }

    public Kind kind() {
        return stored == null ? Kind.LIVE : stored.deletionMarker() ? Kind.REMOVAL : Kind.ENFORCED;
    }

    public String entry() {
        return switch (kind()) {
            case ENFORCED -> "✓ Enforced entry";
            case REMOVAL -> "− Enforced removal";
            case LIVE -> "● Live only";
        };
    }

    public String name() { return stored == null ? live.nameFqdn() : stored.nameFqdn(); }
    public String type() { return stored == null ? live.type() : stored.type(); }
    public String value() {
        return stored == null ? live.rdata() : stored.deletionMarker()
                ? "Pattern: " + (stored.rdataPattern() == null ? "*" : stored.rdataPattern()) : stored.rdata();
    }
    public String ttl() {
        if (stored == null) return Long.toString(live.ttl());
        if (stored.deletionMarker()) return "—";
        String policy = RecordEditor.from(stored).getTtl();
        return policy.isBlank() ? "Ignore" : policy.equals("-1") ? "Zone default" : policy;
    }
    public boolean readOnly() { return type().equalsIgnoreCase("SOA"); }

    public static List<RecordRow> merge(ZoneDefinition definition, LiveZone snapshot) {
        List<DnsRecord> live = snapshot == null ? List.of() : snapshot.records();
        List<RecordRow> rows = new ArrayList<>();
        Set<DnsRecord> claimed = new HashSet<>();
        for (RecordSpec spec : definition.records()) {
            List<DnsRecord> matches = live.stream().filter(record -> matches(spec, record)).toList();
            if (matches.isEmpty()) {
                rows.add(new RecordRow(spec, null, snapshot == null ? "Live not loaded"
                        : spec.deletionMarker() ? "Removal enforced · no live match" : "Missing in live DNS"));
            } else {
                for (DnsRecord record : matches) {
                    claimed.add(record);
                    String status = spec.deletionMarker() ? "Marked for removal"
                            : spec.ttlPolicy().isEnforced()
                                && record.ttl() != spec.ttlPolicy().resolveSeconds(snapshot.defaultTtl())
                                    ? "TTL drift" : "Live matches";
                    rows.add(new RecordRow(spec, record, status));
                }
            }
        }
        live.stream().filter(record -> !claimed.contains(record))
                .map(record -> new RecordRow(null, record, "Live only")).forEach(rows::add);
        rows.sort(Comparator.comparing(RecordRow::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RecordRow::type).thenComparing(RecordRow::value));
        return List.copyOf(rows);
    }

    private static boolean matches(RecordSpec spec, DnsRecord record) {
        return spec.nameFqdn().equalsIgnoreCase(record.nameFqdn()) && spec.type().equalsIgnoreCase(record.type())
                && (spec.deletionMarker() ? GlobMatcher.matches(spec.rdataPattern(), record.rdata())
                    : Objects.equals(spec.rdata(), record.rdata()));
    }
}
