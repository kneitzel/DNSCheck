package de.neitzel.dnscheck.core.ownership;

/**
 * Whether an owner folder belongs to an operator or a group.
 */
public enum OwnerFolderKind {
    /**
     * An operator owner folder, named with the {@code .u} suffix.
     */
    USER,
    /**
     * A group owner folder, named with the {@code .g} suffix.
     */
    GROUP
}
