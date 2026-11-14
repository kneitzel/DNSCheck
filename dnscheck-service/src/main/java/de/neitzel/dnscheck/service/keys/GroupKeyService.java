package de.neitzel.dnscheck.service.keys;

import de.neitzel.dnscheck.core.key.KeyAlreadyExistsException;
import de.neitzel.dnscheck.core.key.ScopedKeyFileStore;
import de.neitzel.dnscheck.core.ownership.OwnerFolder;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import de.neitzel.dnscheck.service.auth.entity.Group;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.config.DnsCheckConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Manages TSIG key files in a group's owner folder.
 * Zone definitions reference those key files when a reconciliation run authenticates AXFR and nsupdate.
 */
@ApplicationScoped
public class GroupKeyService {

    /**
     * Service configuration that supplies the keys directory containing owner folders and key files.
     */
    @Inject
    DnsCheckConfig config;

    /**
     * Checks the admin global right and group membership before a group key file is listed or changed.
     */
    @Inject
    OperatorRightsService operatorRightsService;

    /**
     * Validates key file basenames and supplies the summary and detail types used for group key files.
     */
    @Inject
    PersonalKeyService personalKeyService;

    /**
     * Checks whether a zone definition still references a key file before that key file is deleted.
     */
    @Inject
    KeyUsageService keyUsageService;

    /**
     * Lists the TSIG key files in the group's owner folder.
     *
     * @param operator Operator requesting the list. A group member or an operator with the admin global right may list the key files.
     * @param groupId Identifier of the group whose owner folder is scanned.
     * @return Summaries of the key files in that owner folder.
     * @throws NotFoundException When no group exists for {@code groupId}.
     * @throws ForbiddenException When the operator is neither a member of the group nor an admin.
     * @throws UncheckedIOException When the owner folder cannot be read.
     */
    public List<PersonalKeyService.PersonalKeySummary> listKeys(Operator operator, Long groupId) {
        Group group = requireMemberOrAdmin(operator, groupId);
        return listKeysInFolder(groupFolderFor(group));
    }

    /**
     * Reads one TSIG key file from the group's owner folder, including its secret material.
     *
     * @param operator Operator requesting the key file. The admin global right is required.
     * @param groupId Identifier of the group whose owner folder holds the key file.
     * @param keyName Basename of the key file.
     * @return Key file name, scoped path, and BIND native contents.
     * @throws ForbiddenException When the operator lacks the admin global right.
     * @throws NotFoundException When the group or key file does not exist.
     * @throws WebApplicationException When {@code keyName} is rejected as a key file basename.
     */
    public PersonalKeyService.PersonalKeyDetail getKey(Operator operator, Long groupId, String keyName) {
        requireAdmin();
        Group group = findGroup(groupId);
        String basename = personalKeyService.requireKeyBasename(keyName);
        OwnerFolder ownerFolder = groupFolderFor(group);
        try {
            ScopedKeyFileStore store = openStore();
            String content = store.readContent(ownerFolder, basename);
            return new PersonalKeyService.PersonalKeyDetail(
                    basename, store.scopedPath(ownerFolder, basename), content);
        } catch (IOException e) {
            throw new NotFoundException();
        }
    }

    /**
     * Creates a TSIG key file in the group's owner folder so zone definitions can reference it.
     *
     * @param operator Operator creating the key file. The admin global right is required.
     * @param groupId Identifier of the group whose owner folder receives the key file.
     * @param keyName Basename of the new key file.
     * @param content BIND native text of the key file, including the secret material.
     * @return Summary of the created key file.
     * @throws ForbiddenException When the operator lacks the admin global right.
     * @throws NotFoundException When no group exists for {@code groupId}.
     * @throws WebApplicationException When the key file already exists, or when the basename or contents are rejected.
     * @throws UncheckedIOException When the key file cannot be written.
     */
    public PersonalKeyService.PersonalKeySummary createKey(
            Operator operator, Long groupId, String keyName, String content) {
        requireAdmin();
        Group group = findGroup(groupId);
        OwnerFolder ownerFolder = groupFolderFor(group);
        String basename = personalKeyService.requireKeyBasename(keyName);
        try {
            ScopedKeyFileStore store = openStore();
            store.create(ownerFolder, basename, content);
            return new PersonalKeyService.PersonalKeySummary(basename, store.scopedPath(ownerFolder, basename));
        } catch (KeyAlreadyExistsException e) {
            throw new WebApplicationException("Key already exists: " + basename, Response.Status.CONFLICT);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Replaces the contents of a TSIG key file in the group's owner folder.
     * Later reconciliation runs use the updated secret material for that key file.
     *
     * @param operator Operator updating the key file. The admin global right is required.
     * @param groupId Identifier of the group whose owner folder holds the key file.
     * @param keyName Basename of the key file to replace.
     * @param content Replacement BIND native text of the key file.
     * @return Summary of the replaced key file.
     * @throws ForbiddenException When the operator lacks the admin global right.
     * @throws NotFoundException When the group or key file does not exist.
     * @throws WebApplicationException When the basename or contents are rejected.
     */
    public PersonalKeyService.PersonalKeySummary updateKey(
            Operator operator, Long groupId, String keyName, String content) {
        requireAdmin();
        Group group = findGroup(groupId);
        OwnerFolder ownerFolder = groupFolderFor(group);
        String basename = personalKeyService.requireKeyBasename(keyName);
        try {
            ScopedKeyFileStore store = openStore();
            store.replace(ownerFolder, basename, content);
            return new PersonalKeyService.PersonalKeySummary(basename, store.scopedPath(ownerFolder, basename));
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IOException e) {
            throw new NotFoundException();
        }
    }

    /**
     * Replaces the contents of a TSIG key file in the group's owner folder by delegating to {@link #updateKey}.
     *
     * @param operator Operator replacing the key file. The admin global right is required.
     * @param groupId Identifier of the group whose owner folder holds the key file.
     * @param keyName Basename of the key file to replace.
     * @param content Replacement BIND native text of the key file.
     * @return Summary of the replaced key file.
     * @throws ForbiddenException When the operator lacks the admin global right.
     * @throws NotFoundException When the group or key file does not exist.
     * @throws WebApplicationException When the basename or contents are rejected.
     */
    public PersonalKeyService.PersonalKeySummary replaceKey(
            Operator operator, Long groupId, String keyName, String content) {
        return updateKey(operator, groupId, keyName, content);
    }

    /**
     * Returns the BIND native contents of a TSIG key file in the group's owner folder.
     *
     * @param operator Operator downloading the key file. The admin global right is required.
     * @param groupId Identifier of the group whose owner folder holds the key file.
     * @param keyName Basename of the key file.
     * @return BIND native text of the key file, including the secret material.
     * @throws ForbiddenException When the operator lacks the admin global right.
     * @throws NotFoundException When the group or key file does not exist.
     * @throws WebApplicationException When {@code keyName} is rejected as a key file basename.
     */
    public String downloadKey(Operator operator, Long groupId, String keyName) {
        requireAdmin();
        return getKey(operator, groupId, keyName).content();
    }

    /**
     * Deletes a TSIG key file from the group's owner folder when no zone definition references it.
     *
     * @param operator Operator deleting the key file. The admin global right is required.
     * @param groupId Identifier of the group whose owner folder holds the key file.
     * @param keyName Basename of the key file to delete.
     * @throws ForbiddenException When the operator lacks the admin global right.
     * @throws NotFoundException When the group or key file does not exist.
     * @throws WebApplicationException When {@code keyName} is rejected as a key file basename.
     * @throws de.neitzel.dnscheck.core.key.KeyInUseException When a zone definition still references the key file.
     * @throws UncheckedIOException When the zone catalog cannot be scanned for key file references.
     */
    public void deleteKey(Operator operator, Long groupId, String keyName) {
        requireAdmin();
        Group group = findGroup(groupId);
        OwnerFolder ownerFolder = groupFolderFor(group);
        String basename = personalKeyService.requireKeyBasename(keyName);
        ScopedKeyFileStore store = openStore();
        keyUsageService.assertNotInUse(store.scopedPath(ownerFolder, basename));
        try {
            store.delete(ownerFolder, basename);
        } catch (IOException e) {
            throw new NotFoundException();
        }
    }

    /**
     * Lists TSIG key file summaries in one owner folder.
     *
     * @param ownerFolder Owner folder whose key files are listed.
     * @return Summaries of the key files in that owner folder.
     * @throws UncheckedIOException When the owner folder cannot be read.
     */
    private List<PersonalKeyService.PersonalKeySummary> listKeysInFolder(OwnerFolder ownerFolder) {
        try {
            ScopedKeyFileStore store = openStore();
            return store.listBasenames(ownerFolder).stream()
                    .map(name -> new PersonalKeyService.PersonalKeySummary(
                            name, store.scopedPath(ownerFolder, name)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the group when the operator is a member or holds the admin global right.
     *
     * @param operator Operator requesting access to the group's key files.
     * @param groupId Identifier of the group to load.
     * @return Group that owns the owner folder.
     * @throws NotFoundException When no group exists for {@code groupId}.
     * @throws ForbiddenException When the operator is neither a member of the group nor an admin.
     */
    private Group requireMemberOrAdmin(Operator operator, Long groupId) {
        Group group = findGroup(groupId);
        if (operatorRightsService.isAdmin() || group.members.contains(operator)) {
            return group;
        }
        throw new ForbiddenException();
    }

    /**
     * Requires the admin global right before a group key file is read or changed.
     *
     * @throws ForbiddenException When the operator lacks the admin global right.
     */
    private void requireAdmin() {
        operatorRightsService.requireAdmin();
    }

    /**
     * Loads the group for the given identifier.
     *
     * @param groupId Identifier of the group to load.
     * @return Group whose owner folder stores the key files.
     * @throws NotFoundException When no group exists for {@code groupId}.
     */
    private Group findGroup(Long groupId) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new NotFoundException();
        }
        return group;
    }

    /**
     * Returns the owner folder that stores the group's key files.
     *
     * @param group Group whose owner folder is required.
     * @return Owner folder named for that group.
     */
    private static OwnerFolder groupFolderFor(Group group) {
        return ScopedKeyFileStore.groupFolderFor(group.name);
    }

    /**
     * Opens the scoped key file store rooted at the configured keys directory.
     *
     * @return Store used to list, read, write, and delete key files.
     */
    private ScopedKeyFileStore openStore() {
        return new ScopedKeyFileStore(Path.of(config.keys().dir()));
    }
}
