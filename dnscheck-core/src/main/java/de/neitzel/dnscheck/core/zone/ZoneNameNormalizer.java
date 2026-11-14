package de.neitzel.dnscheck.core.zone;

import org.xbill.DNS.Name;

/**
 * Normalizes record owner names to fully qualified form and converts them to dnsjava names.
 */
public final class ZoneNameNormalizer {

    /**
     * Normalizes an owner name relative to {@code zone}.
     * {@code @} means the zone apex. A name that already ends with a dot is kept absolute.
     * A name equal to the zone, ignoring a trailing dot, is the apex. A name that contains a dot
     * is treated as absolute and gains a trailing dot. Any other name is relative to the zone.
     *
     * @param name the owner name as supplied by the operator or stored in a zone definition.
     * @param zone the DNS zone the relative name belongs to.
     * @return the fully qualified owner name, including the trailing dot.
     * @throws IllegalArgumentException when {@code name} or {@code zone} is blank.
     */
    public String normalize(String name, String zone) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("record name is required");
        }
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("zone is required");
        }

        String trimmed = name.trim();
        if ("@".equals(trimmed)) {
            return ensureTrailingDot(zone);
        }

        if (trimmed.endsWith(".")) {
            return trimmed;
        }

        String zoneFqdn = ensureTrailingDot(zone);
        if (trimmed.equalsIgnoreCase(stripTrailingDot(zoneFqdn))) {
            return zoneFqdn;
        }

        if (trimmed.contains(".")) {
            return trimmed + ".";
        }

        return trimmed + "." + stripTrailingDot(zoneFqdn) + ".";
    }

    /**
     * Appends a trailing dot when {@code value} does not already end with one.
     *
     * @param value the zone or owner name.
     * @return {@code value} with a trailing dot.
     */
    private static String ensureTrailingDot(String value) {
        return value.endsWith(".") ? value : value + ".";
    }

    /**
     * Removes one trailing dot when {@code value} ends with one.
     *
     * @param value the zone or owner name.
     * @return {@code value} without a trailing dot.
     */
    private static String stripTrailingDot(String value) {
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * Renders an FQDN the way a zone definition shows it.
     * The zone apex becomes {@code @}. A name inside the zone becomes the relative label.
     * Any other name is left absolute, including its trailing dot.
     *
     * @param nameFqdn the fully qualified owner name.
     * @param zone the DNS zone used to decide whether the name is relative.
     * @return {@code @}, a relative name, or the original absolute name.
     */
    public String toRelativeOrFqdn(String nameFqdn, String zone) {
        String zoneFqdn = ensureTrailingDot(zone);
        if (nameFqdn.equals(zoneFqdn)) {
            return "@";
        }
        String zoneSuffix = stripTrailingDot(zoneFqdn);
        String name = stripTrailingDot(nameFqdn);
        if (name.endsWith("." + zoneSuffix)) {
            return name.substring(0, name.length() - zoneSuffix.length() - 1);
        }
        return nameFqdn;
    }

    /**
     * Parses an FQDN into a dnsjava name relative to the DNS root.
     *
     * @param nameFqdn the fully qualified owner or zone name.
     * @return the dnsjava name.
     * @throws IllegalArgumentException when {@code nameFqdn} is not valid DNS syntax.
     */
    public Name toDnsName(String nameFqdn) {
        try {
            return Name.fromString(nameFqdn, Name.root);
        } catch (org.xbill.DNS.TextParseException e) {
            throw new IllegalArgumentException("Invalid DNS name: " + nameFqdn, e);
        }
    }
}
