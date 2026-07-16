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

@ApplicationScoped
public class GroupKeyService {

    @Inject
    DnsCheckConfig config;

    @Inject
    OperatorRightsService operatorRightsService;

    @Inject
    PersonalKeyService personalKeyService;

    @Inject
    KeyUsageService keyUsageService;

    public List<PersonalKeyService.PersonalKeySummary> listKeys(Operator operator, Long groupId) {
        Group group = requireMemberOrAdmin(operator, groupId);
        return listKeysInFolder(groupFolderFor(group));
    }

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

    public PersonalKeyService.PersonalKeySummary replaceKey(
            Operator operator, Long groupId, String keyName, String content) {
        return updateKey(operator, groupId, keyName, content);
    }

    public String downloadKey(Operator operator, Long groupId, String keyName) {
        requireAdmin();
        return getKey(operator, groupId, keyName).content();
    }

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

    private Group requireMemberOrAdmin(Operator operator, Long groupId) {
        Group group = findGroup(groupId);
        if (operatorRightsService.isAdmin() || group.members.contains(operator)) {
            return group;
        }
        throw new ForbiddenException();
    }

    private void requireAdmin() {
        operatorRightsService.requireAdmin();
    }

    private Group findGroup(Long groupId) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new NotFoundException();
        }
        return group;
    }

    private static OwnerFolder groupFolderFor(Group group) {
        return ScopedKeyFileStore.groupFolderFor(group.name);
    }

    private ScopedKeyFileStore openStore() {
        return new ScopedKeyFileStore(Path.of(config.keys().dir()));
    }
}
