package de.neitzel.dnscheck.core.ownership;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A scoped namespace directory under the zone catalog or keys directory,
 * named {@code {identifier}.u} for an operator or {@code {identifier}.g} for a group.
 */
public record OwnerFolder(String folderName, OwnerFolderKind kind, String identifier) {

    private static final Pattern FOLDER_PATTERN =
            Pattern.compile("^([a-zA-Z0-9._-]+)\\.(u|g)$");

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

    public static boolean isValidFolderName(String folderName) {
        return folderName != null && FOLDER_PATTERN.matcher(folderName).matches();
    }
}
