package de.neitzel.dnscheck.core.model;

/**
 * How TTL is enforced for a managed record.
 */
public sealed interface TtlPolicy permits TtlPolicy.Ignore, TtlPolicy.UseZoneDefault, TtlPolicy.Explicit {

    /** TTL is not compared or corrected. */
    record Ignore() implements TtlPolicy {}

    /** Use the zone default TTL from the SOA minimum field. */
    record UseZoneDefault() implements TtlPolicy {}

    /** Enforce an explicit TTL value in seconds. */
    record Explicit(long seconds) implements TtlPolicy {
        public Explicit {
            if (seconds < 0) {
                throw new IllegalArgumentException("TTL must be non-negative");
            }
        }
    }

    static TtlPolicy fromJsonValue(Integer ttl) {
        if (ttl == null) {
            return new Ignore();
        }
        if (ttl == -1) {
            return new UseZoneDefault();
        }
        if (ttl < -1) {
            throw new IllegalArgumentException("TTL must be omitted, -1, or a non-negative value");
        }
        return new Explicit(ttl);
    }

    default long resolveSeconds(long zoneDefaultTtl) {
        return switch (this) {
            case Ignore ignored -> zoneDefaultTtl;
            case UseZoneDefault useZoneDefault -> zoneDefaultTtl;
            case Explicit explicit -> explicit.seconds();
        };
    }

    default boolean isEnforced() {
        return this instanceof Explicit || this instanceof UseZoneDefault;
    }
}
