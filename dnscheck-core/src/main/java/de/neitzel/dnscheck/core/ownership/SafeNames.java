package de.neitzel.dnscheck.core.ownership;

import java.util.regex.Pattern;

/**
 * Validation rules for zone names and scoped file basenames.
 */
public final class SafeNames {

    static final Pattern ZONE_NAME = Pattern.compile("^[a-zA-Z0-9.-]+$");
    static final Pattern KEY_BASENAME = Pattern.compile("^[a-zA-Z0-9._-]+\\.key$");

    private SafeNames() {}

    public static String requireZoneName(String zoneName) {
        if (zoneName == null || zoneName.isBlank()) {
            throw new IllegalArgumentException("zone name is required");
        }
        if (!ZONE_NAME.matcher(zoneName).matches()) {
            throw new IllegalArgumentException("Invalid zone name: " + zoneName);
        }
        return zoneName;
    }

    public static String requireKeyBasename(String basename) {
        if (basename == null || basename.isBlank()) {
            throw new IllegalArgumentException("key basename is required");
        }
        if (basename.contains("/") || basename.contains("\\") || basename.contains("..")) {
            throw new IllegalArgumentException("Invalid key basename: " + basename);
        }
        if (!KEY_BASENAME.matcher(basename).matches()) {
            throw new IllegalArgumentException("Invalid key basename: " + basename);
        }
        return basename;
    }
}
