package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.key.BindKeyFileParser;
import de.neitzel.dnscheck.core.ownership.SafeNames;
import de.neitzel.dnscheck.core.key.TsigKey;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.reconcile.DefaultReconciliationEngine;
import de.neitzel.dnscheck.core.reconcile.ReconciliationRunResult;
import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** One flat, portable directory of .domain definitions and colocated BIND key files. */
public final class LocalZoneDirectory {
    private final Path directory;
    private final ZoneDefinitionStore store = ZoneDefinitionStore.localDirectoryStore();
    private final BindKeyFileParser keyParser = new BindKeyFileParser();
    private final DefaultReconciliationEngine engine;

    public LocalZoneDirectory(Path directory) {
        this(directory, new DefaultReconciliationEngine(directory, directory));
    }

    LocalZoneDirectory(Path directory, DefaultReconciliationEngine engine) {
        this.directory = directory.toAbsolutePath().normalize();
        this.engine = engine;
    }

    /** Invalid files remain visible. An empty or unreadable directory is an error. */
    public List<Entry> entries() throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Directory does not exist or is not a directory: " + directory);
        }
        List<Entry> entries = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".domain"))
                    .sorted().toList()) {
                String zone = file.getFileName().toString().replaceFirst("\\.domain$", "");
                try {
                    ZoneDefinition definition = read(zone);
                    entries.add(new Entry(zone, definition.nameserver(), definition.keyFile(), null));
                } catch (IOException | RuntimeException failure) {
                    entries.add(new Entry(zone, "", "", message(failure)));
                }
            }
        }
        if (entries.isEmpty()) {
            throw new IOException("No .domain definitions found in " + directory);
        }
        return List.copyOf(entries);
    }

    public ZoneDefinition read(String zone) throws IOException {
        ZoneDefinition definition = store.read(containedFile(SafeNames.requireZoneName(zone) + ".domain"));
        key(definition);
        return definition;
    }

    /** Validate before writing, including the current on-disk schema and file boundary. */
    public void write(ZoneDefinition definition) throws IOException {
        Path file = containedFile(definition.zone() + ".domain");
        if (!file.equals(definition.sourcePath().toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Definition must be saved to its original .domain file");
        }
        store.read(file);
        key(definition);
        store.write(definition);
    }

    public ReconciliationRunResult reconcileAll(boolean dryRun) {
        List<ZoneReconciliationResult> results = new ArrayList<>();
        try {
            for (Entry entry : entries()) {
                results.add(entry.valid() ? reconcile(entry.zone(), dryRun)
                        : ZoneReconciliationResult.failed(entry.zone(), entry.error()));
            }
        } catch (IOException | RuntimeException failure) {
            results.add(ZoneReconciliationResult.failed("*", message(failure)));
        }
        return new ReconciliationRunResult(results);
    }

    public ZoneReconciliationResult reconcile(String zone, boolean dryRun) {
        try {
            ZoneDefinition definition = read(zone);
            return engine.reconcileDefinition(definition, key(definition), dryRun);
        } catch (IOException | RuntimeException failure) {
            return ZoneReconciliationResult.failed(zone, message(failure));
        }
    }

    private static String message(Exception failure) {
        return failure.getMessage() == null ? "Could not load definition or key file" : failure.getMessage();
    }

    /** Reads a colocated key, enforcing the same path and symlink boundary as definitions. */
    public TsigKey readKey(String filename) throws IOException {
        return keyParser.parse(containedFile(filename));
    }

    private TsigKey key(ZoneDefinition definition) throws IOException {
        return readKey(definition.keyFile());
    }

    private Path containedFile(String filename) throws IOException {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")
                || filename.equals(".") || filename.equals("..") || Path.of(filename).isAbsolute()) {
            throw new IllegalArgumentException("Expected a filename inside the selected directory");
        }
        Path file = directory.resolve(filename);
        Path real = file.toRealPath();
        if (!real.startsWith(directory.toRealPath()) || !Files.isRegularFile(real)) {
            throw new IOException("File must stay inside the selected directory: " + filename);
        }
        return file;
    }

    public record Entry(String zone, String nameserver, String keyFile, String error) {
        public boolean valid() {
            return error == null;
        }
    }
}
