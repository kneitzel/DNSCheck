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

/**
 * Manages TSIG key files in an operator's personal owner folder.
 * Zone definitions reference those key files when a reconciliation run authenticates AXFR and nsupdate.
 */
@ApplicationScoped
public class PersonalKeyService {

    /**
     * Service configuration that supplies the keys directory containing owner folders and key files.
     */
    @Inject
    DnsCheckConfig config;

    /**
     * Checks whether a zone definition still references a key file before that key file is deleted.
     */
    @Inject
    KeyUsageService keyUsageService;

    /**
     * Lists the TSIG key files in the operator's personal owner folder.
     *
     * @param operator Operator whose owner folder is scanned.
     * @return Summaries of the key files in that owner folder.
     * @throws UncheckedIOException When the owner folder cannot be read.
     */
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

    /**
     * Reads one TSIG key file from the operator's personal owner folder, including its secret material.
     *
     * @param operator Operator whose owner folder holds the key file.
     * @param keyName Basename of the key file.
     * @return Key file name, scoped path, and BIND native contents.
     * @throws NotFoundException When the key file does not exist.
     * @throws WebApplicationException When {@code keyName} is rejected as a key file basename.
     */
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

    /**
     * Creates a TSIG key file in the operator's personal owner folder so zone definitions can reference it.
     *
     * @param operator Operator whose owner folder receives the key file.
     * @param keyName Basename of the new key file.
     * @param content BIND native text of the key file, including the secret material.
     * @return Summary of the created key file.
     * @throws WebApplicationException When the key file already exists, or when the basename or contents are rejected.
     * @throws UncheckedIOException When the key file cannot be written.
     */
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

    /**
     * Replaces the contents of a TSIG key file in the operator's personal owner folder.
     * Later reconciliation runs use the updated secret material for that key file.
     *
     * @param operator Operator whose owner folder holds the key file.
     * @param keyName Basename of the key file to replace.
     * @param content Replacement BIND native text of the key file.
     * @return Summary of the replaced key file.
     * @throws NotFoundException When the key file does not exist.
     * @throws WebApplicationException When the basename or contents are rejected.
     */
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

    /**
     * Replaces the contents of a TSIG key file in the operator's personal owner folder by delegating to {@link #updateKey}.
     *
     * @param operator Operator whose owner folder holds the key file.
     * @param keyName Basename of the key file to replace.
     * @param content Replacement BIND native text of the key file.
     * @return Summary of the replaced key file.
     * @throws NotFoundException When the key file does not exist.
     * @throws WebApplicationException When the basename or contents are rejected.
     */
    public PersonalKeySummary replaceKey(Operator operator, String keyName, String content) {
        return updateKey(operator, keyName, content);
    }

    /**
     * Returns the BIND native contents of a TSIG key file in the operator's personal owner folder.
     *
     * @param operator Operator whose owner folder holds the key file.
     * @param keyName Basename of the key file.
     * @return BIND native text of the key file, including the secret material.
     * @throws NotFoundException When the key file does not exist.
     * @throws WebApplicationException When {@code keyName} is rejected as a key file basename.
     */
    public String downloadKey(Operator operator, String keyName) {
        return getKey(operator, keyName).content();
    }

    /**
     * Deletes a TSIG key file from the operator's personal owner folder when no zone definition references it.
     *
     * @param operator Operator whose owner folder holds the key file.
     * @param keyName Basename of the key file to delete.
     * @throws NotFoundException When the key file does not exist.
     * @throws WebApplicationException When {@code keyName} is rejected as a key file basename.
     * @throws de.neitzel.dnscheck.core.key.KeyInUseException When a zone definition still references the key file.
     * @throws UncheckedIOException When the zone catalog cannot be scanned for key file references.
     */
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

    /**
     * Returns the owner folder that stores the operator's personal key files.
     *
     * @param operator Operator whose owner folder is required.
     * @return Personal owner folder for that operator.
     */
    static OwnerFolder ownerFolderFor(Operator operator) {
        return ScopedKeyFileStore.personalFolderFor(operator.username);
    }

    /**
     * Opens the scoped key file store rooted at the configured keys directory.
     *
     * @return Store used to list, read, write, and delete key files.
     */
    private ScopedKeyFileStore openStore() {
        return new ScopedKeyFileStore(Path.of(config.keys().dir()));
    }

    /**
     * Requires a key file basename that is safe to store in an owner folder.
     *
     * @param keyName Requested basename of the key file.
     * @return Accepted basename.
     * @throws WebApplicationException When {@code keyName} is rejected as a key file basename.
     */
    private static String requireKeyName(String keyName) {
        try {
            return SafeNames.requireKeyBasename(keyName);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        }
    }

    /**
     * Requires a key file basename that is safe to store in an owner folder.
     *
     * @param keyName Requested basename of the key file.
     * @return Accepted basename.
     * @throws WebApplicationException When {@code keyName} is rejected as a key file basename.
     */
    public String requireKeyBasename(String keyName) {
        return requireKeyName(keyName);
    }

    /**
     * Builds a summary for one key file in an owner folder.
     *
     * @param ownerFolder Owner folder that contains the key file.
     * @param basename Basename of the key file.
     * @param store Store that resolves the scoped path of the key file.
     * @return Summary containing the basename and scoped path.
     */
    private PersonalKeySummary toSummary(OwnerFolder ownerFolder, String basename, ScopedKeyFileStore store) {
        return new PersonalKeySummary(basename, store.scopedPath(ownerFolder, basename));
    }

    /**
     * Summary of a TSIG key file in an owner folder, without the secret material.
     *
     * @param keyName Basename of the key file.
     * @param scopedPath Scoped path of the key file relative to the keys directory.
     */
    public record PersonalKeySummary(String keyName, String scopedPath) {}

    /**
     * TSIG key file contents from an owner folder, including the secret material.
     *
     * @param keyName Basename of the key file.
     * @param scopedPath Scoped path of the key file relative to the keys directory.
     * @param content BIND native text of the key file.
     */
    public record PersonalKeyDetail(String keyName, String scopedPath, String content) {}
}
