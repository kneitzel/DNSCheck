package de.neitzel.dnscheck.service.keys;

import de.neitzel.dnscheck.core.key.KeyAlreadyExistsException;
import de.neitzel.dnscheck.core.key.ScopedKeyFileStore;
import de.neitzel.dnscheck.core.ownership.OwnerFolder;
import de.neitzel.dnscheck.core.ownership.SafeNames;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.config.DnsCheckConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class PersonalKeyService {

    @Inject
    DnsCheckConfig config;

    @Inject
    KeyUsageService keyUsageService;

    public List<PersonalKeySummary> listKeys(Operator operator) {
        OwnerFolder ownerFolder = ownerFolderFor(operator);
        try {
            ScopedKeyFileStore store = openStore();
            return store.listBasenames(ownerFolder).stream()
                    .map(name -> toSummary(ownerFolder, name, store))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PersonalKeyDetail getKey(Operator operator, String keyName) {
        OwnerFolder ownerFolder = ownerFolderFor(operator);
        String basename = requireKeyName(keyName);
        try {
            String content = openStore().readContent(ownerFolder, basename);
            return new PersonalKeyDetail(basename, openStore().scopedPath(ownerFolder, basename), content);
        } catch (IOException e) {
            throw new NotFoundException();
        }
    }

    public PersonalKeySummary createKey(Operator operator, String keyName, String content) {
        OwnerFolder ownerFolder = ownerFolderFor(operator);
        String basename = requireKeyName(keyName);
        try {
            openStore().create(ownerFolder, basename, content);
            return toSummary(ownerFolder, basename, openStore());
        } catch (KeyAlreadyExistsException e) {
            throw new WebApplicationException("Key already exists: " + basename, Response.Status.CONFLICT);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PersonalKeySummary updateKey(Operator operator, String keyName, String content) {
        OwnerFolder ownerFolder = ownerFolderFor(operator);
        String basename = requireKeyName(keyName);
        try {
            openStore().replace(ownerFolder, basename, content);
            return toSummary(ownerFolder, basename, openStore());
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IOException e) {
            throw new NotFoundException();
        }
    }

    public PersonalKeySummary replaceKey(Operator operator, String keyName, String content) {
        return updateKey(operator, keyName, content);
    }

    public String downloadKey(Operator operator, String keyName) {
        return getKey(operator, keyName).content();
    }

    public void deleteKey(Operator operator, String keyName) {
        OwnerFolder ownerFolder = ownerFolderFor(operator);
        String basename = requireKeyName(keyName);
        ScopedKeyFileStore store = openStore();
        keyUsageService.assertNotInUse(store.scopedPath(ownerFolder, basename));
        try {
            store.delete(ownerFolder, basename);
        } catch (IOException e) {
            throw new NotFoundException();
        }
    }

    static OwnerFolder ownerFolderFor(Operator operator) {
        return ScopedKeyFileStore.personalFolderFor(operator.username);
    }

    private ScopedKeyFileStore openStore() {
        return new ScopedKeyFileStore(Path.of(config.keys().dir()));
    }

    private static String requireKeyName(String keyName) {
        try {
            return SafeNames.requireKeyBasename(keyName);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        }
    }

    public String requireKeyBasename(String keyName) {
        return requireKeyName(keyName);
    }

    private PersonalKeySummary toSummary(OwnerFolder ownerFolder, String basename, ScopedKeyFileStore store) {
        return new PersonalKeySummary(basename, store.scopedPath(ownerFolder, basename));
    }

    public record PersonalKeySummary(String keyName, String scopedPath) {}

    public record PersonalKeyDetail(String keyName, String scopedPath, String content) {}
}
