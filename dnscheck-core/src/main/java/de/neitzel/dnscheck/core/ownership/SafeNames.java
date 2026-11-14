package de.neitzel.dnscheck.core.ownership;

import java.util.regex.Pattern;

/**
 * Validation rules for zone names and scoped file basenames.
 */
public final class SafeNames {

    /**
     * Zone names accepted in catalog paths: letters, digits, dots, and hyphens.
     */
    static final Pattern ZONE_NAME = Pattern.compile("^[a-zA-Z0-9.-]+$");

    /**
     * Key file basenames accepted in scoped paths.
     * The name may contain letters, digits, dots, underscores, and hyphens, and must end in {@code .key}.
     */
    static final Pattern KEY_BASENAME = Pattern.compile("^[a-zA-Z0-9._-]+\\.key$");

    /**
     * Prevents instantiation.
     */
    private SafeNames() {}

    /**
     * Checks that a zone name is present and contains only letters, digits, dots, and hyphens.
     *
     * @param zoneName the DNS zone name.
     * @return the same zone name when it is valid.
     * @throws IllegalArgumentException when the name is blank or contains any other character.
     */
    public static String requireZoneName(String zoneName) {
        if (zoneName == null || zoneName.isBlank()) {
            throw new IllegalArgumentException("zone name is required");
        }
        if (!ZONE_NAME.matcher(zoneName).matches()) {
            throw new IllegalArgumentException("Invalid zone name: " + zoneName);
        }
        return zoneName;
    }

    /**
     * Checks that a key file basename ends in {@code .key} and does not contain a path separator or {@code ..}.
     *
     * @param basename the key file name within an owner folder.
     * @return the same basename when it is valid.
     * @throws IllegalArgumentException when the basename is blank, escapes the owner folder,
     *                                  or does not match the key file pattern.
     */
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
