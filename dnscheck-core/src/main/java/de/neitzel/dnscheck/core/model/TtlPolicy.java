package de.neitzel.dnscheck.core.model;

/**
 * How TTL is enforced for a managed record.
 */
public sealed interface TtlPolicy permits TtlPolicy.Ignore, TtlPolicy.UseZoneDefault, TtlPolicy.Explicit {

    /** TTL is not compared or corrected. */
    record Ignore() implements TtlPolicy {}

    /** Use the zone default TTL from the SOA minimum field. */
    record UseZoneDefault() implements TtlPolicy {}

    /**
     * Enforce an explicit TTL value in seconds.
     *
     * @param seconds the TTL to enforce, in seconds. Negative values are rejected.
     */
    record Explicit(long seconds) implements TtlPolicy {
        /**
         * Rejects a negative TTL. Zero is allowed.
         *
         * @throws IllegalArgumentException when {@code seconds} is negative.
         */
        public Explicit {
            if (seconds < 0) {
                throw new IllegalArgumentException("TTL must be non-negative");
            }
        }
    }

    /**
     * Maps the optional {@code ttl} field of a zone definition record to a TTL policy.
     * An omitted value ignores TTL, {@code -1} uses the zone default TTL, and any other
     * non-negative value is enforced.
     *
     * @param ttl the JSON TTL value, or {@code null} when the field is omitted.
     * @return the TTL policy for that value.
     * @throws IllegalArgumentException when {@code ttl} is less than {@code -1}.
     */
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

    /**
     * Resolves the TTL in seconds to use when this policy supplies a concrete value.
     * An ignored TTL and the zone-default policy both return {@code zoneDefaultTtl}.
     * Callers that only correct TTL when {@link #isEnforced()} is true still need that fallback
     * in order to write a record.
     *
     * @param zoneDefaultTtl the zone default TTL from the live zone.
     * @return the TTL in seconds for an add or correction.
     */
    default long resolveSeconds(long zoneDefaultTtl) {
        return switch (this) {
            case Ignore ignored -> zoneDefaultTtl;
            case UseZoneDefault useZoneDefault -> zoneDefaultTtl;
            case Explicit explicit -> explicit.seconds();
        };
    }

    /**
     * Reports whether reconciliation should compare and correct TTL.
     *
     * @return {@code true} for an explicit TTL or the zone default TTL, and {@code false} when TTL is ignored.
     */
    default boolean isEnforced() {
        return this instanceof Explicit || this instanceof UseZoneDefault;
    }
}
