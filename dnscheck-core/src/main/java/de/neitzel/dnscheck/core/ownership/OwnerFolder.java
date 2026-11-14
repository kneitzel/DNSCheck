package de.neitzel.dnscheck.core.ownership;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A scoped namespace directory under the zone catalog or keys directory,
 * named {@code {identifier}.u} for an operator or {@code {identifier}.g} for a group.
 *
 * @param folderName the directory name, {@code {identifier}.u} or {@code {identifier}.g}.
 * @param kind whether the folder belongs to an operator or a group.
 * @param identifier the operator or group name encoded in the folder name, without the suffix.
 */
public record OwnerFolder(String folderName, OwnerFolderKind kind, String identifier) {

    /**
     * Matches an owner folder name and captures the identifier and the {@code u} or {@code g} suffix.
     */
    private static final Pattern FOLDER_PATTERN =
            Pattern.compile("^([a-zA-Z0-9._-]+)\\.(u|g)$");

    /**
     * Requires a folder name, a kind, and an identifier.
     * It does not check that the folder name matches the kind and identifier; {@link #parse(String)} does that.
     *
     * @throws IllegalArgumentException when any component is blank.
     */
    public OwnerFolder {
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("owner folder is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("owner folder kind is required");
        }
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("owner folder identifier is required");
        }
    }

    /**
     * Parses an owner folder name into its kind and identifier.
     *
     * @param folderName the directory name, such as {@code admin.u} or {@code ops.g}.
     * @return the owner folder.
     * @throws IllegalArgumentException when the name is blank or does not match
     *                                  {@code {identifier}.u} or {@code {identifier}.g}.
     */
    public static OwnerFolder parse(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("owner folder is required");
        }
        Matcher matcher = FOLDER_PATTERN.matcher(folderName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid owner folder: " + folderName);
        }
        String identifier = matcher.group(1);
        OwnerFolderKind kind = "u".equals(matcher.group(2)) ? OwnerFolderKind.USER : OwnerFolderKind.GROUP;
        return new OwnerFolder(folderName, kind, identifier);
    }

    /**
     * Reports whether {@code folderName} is an operator or group owner folder name.
     *
     * @param folderName the directory name to test.
     * @return {@code true} when the name matches the owner folder pattern.
     */
    public static boolean isValidFolderName(String folderName) {
        return folderName != null && FOLDER_PATTERN.matcher(folderName).matches();
    }
}
