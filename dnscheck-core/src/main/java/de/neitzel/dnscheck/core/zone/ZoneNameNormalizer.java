package de.neitzel.dnscheck.core.zone;

import org.xbill.DNS.Name;

public final class ZoneNameNormalizer {

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

    private static String ensureTrailingDot(String value) {
        return value.endsWith(".") ? value : value + ".";
    }

    private static String stripTrailingDot(String value) {
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

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

    public Name toDnsName(String nameFqdn) {
        try {
            return Name.fromString(nameFqdn, Name.root);
        } catch (org.xbill.DNS.TextParseException e) {
            throw new IllegalArgumentException("Invalid DNS name: " + nameFqdn, e);
        }
    }
}
