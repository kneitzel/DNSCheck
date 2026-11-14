package de.neitzel.dnscheck.core.zone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.TtlPolicy;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes zone definition files, normalizing owner names to FQDN on load.
 */
public final class ZoneDefinitionStore {

    /**
     * Jackson mapper used to read and write zone definition files.
     * Null properties are omitted and output is indented.
     */
    private final ObjectMapper objectMapper;

    /**
     * Normalizes record names to FQDN when a zone definition is loaded.
     */
    private final ZoneNameNormalizer nameNormalizer;

    /**
     * Creates a store that normalizes names and writes indented JSON, omitting null properties.
     */
    public ZoneDefinitionStore() {
        this(new ZoneNameNormalizer(), new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT));
    }

    /**
     * Creates a store that uses the supplied normalizer and object mapper.
     *
     * @param nameNormalizer the normalizer applied to record names on load.
     * @param objectMapper the mapper used to read and write zone definition JSON.
     */
    ZoneDefinitionStore(ZoneNameNormalizer nameNormalizer, ObjectMapper objectMapper) {
        this.nameNormalizer = nameNormalizer;
        this.objectMapper = objectMapper;
    }

    /**
     * Loads a zone definition and normalizes record names to FQDN.
     * The zone name must match the file name.
     *
     * @param zoneFile the zone definition file.
     * @return the loaded zone definition, with {@code sourcePath} set to {@code zoneFile}.
     * @throws IOException when the file cannot be read or parsed.
     * @throws IllegalArgumentException when the zone name does not match the file name or a record entry is invalid.
     */
    public ZoneDefinition read(Path zoneFile) throws IOException {
        ZoneDefinitionJson json = objectMapper.readValue(zoneFile.toFile(), ZoneDefinitionJson.class);
        validateFilename(zoneFile, json.zone);
        List<RecordSpec> records = new ArrayList<>();
        if (json.records != null) {
            for (RecordJson record : json.records) {
                records.add(toRecordSpec(json.zone, record));
            }
        }
        return new ZoneDefinition(json.zone, json.nameserver, json.keyFile, records, zoneFile);
    }

    /**
     * Writes the zone definition as indented JSON.
     * The file name must match the zone name.
     *
     * @param definition the zone definition to write. {@code sourcePath} selects the file.
     * @throws IOException when the file cannot be written.
     * @throws IllegalArgumentException when the file name does not match the zone name.
     */
    public void write(ZoneDefinition definition) throws IOException {
        validateFilename(definition.sourcePath(), definition.zone());
        ZoneDefinitionJson json = new ZoneDefinitionJson();
        json.zone = definition.zone();
        json.nameserver = definition.nameserver();
        json.keyFile = definition.keyFile();
        json.records = definition.records().stream().map(this::toRecordJson).toList();
        objectMapper.writeValue(definition.sourcePath().toFile(), json);
    }

    /**
     * Reads only the zone name from a file that failed a full load, so catalog introspection can still name it.
     *
     * @param zoneFile the zone definition file.
     * @return the zone name, or empty when the file has no zone name or cannot be parsed.
     */
    public Optional<String> peekZoneName(Path zoneFile) {
        try {
            ZoneDefinitionJson json = objectMapper.readValue(zoneFile.toFile(), ZoneDefinitionJson.class);
            if (json.zone == null || json.zone.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(json.zone);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Converts one JSON record into a managed record or a deletion marker.
     * A deletion marker does not carry a TTL policy.
     *
     * @param zone the DNS zone the owner name is relative to.
     * @param record the JSON record from the zone definition.
     * @return the normalized record entry.
     * @throws IllegalArgumentException when the type is missing or unsupported, or the name cannot be normalized.
     */
    private RecordSpec toRecordSpec(String zone, RecordJson record) {
        validateRecordType(record.type);
        String nameFqdn = nameNormalizer.normalize(record.name, zone);
        boolean deletionMarker = record.delete;
        TtlPolicy ttlPolicy = deletionMarker ? new TtlPolicy.Ignore() : TtlPolicy.fromJsonValue(record.ttl);
        return new RecordSpec(
                nameFqdn,
                record.type.toUpperCase(),
                record.rdata,
                record.rdataPattern,
                deletionMarker,
                ttlPolicy);
    }

    /**
     * Converts a managed record or deletion marker back to the JSON fields that {@link #read(Path)} accepts.
     * An explicit TTL is stored as seconds, and the zone default TTL is stored as {@code -1}. An ignored TTL is omitted.
     *
     * @param record the record entry to write.
     * @return the JSON record.
     */
    private RecordJson toRecordJson(RecordSpec record) {
        RecordJson json = new RecordJson();
        json.name = record.nameFqdn();
        json.type = record.type();
        json.delete = record.deletionMarker();
        if (record.deletionMarker()) {
            json.rdataPattern = record.rdataPattern();
        } else {
            json.rdata = record.rdata();
            TtlPolicy ttlPolicy = record.ttlPolicy();
            if (ttlPolicy instanceof TtlPolicy.Explicit explicit) {
                json.ttl = (int) explicit.seconds();
            } else if (ttlPolicy instanceof TtlPolicy.UseZoneDefault) {
                json.ttl = -1;
            }
        }
        return json;
    }

    /**
     * Checks that the zone name equals the file name without the {@code .json} suffix.
     *
     * @param zoneFile the zone definition file.
     * @param zone the zone name from the file contents.
     * @throws IllegalArgumentException when the zone name and the file name differ.
     */
    private static void validateFilename(Path zoneFile, String zone) {
        String expectedStem = zone;
        String actualStem = stripJsonSuffix(zoneFile.getFileName().toString());
        if (!expectedStem.equals(actualStem)) {
            throw new IllegalArgumentException(
                    "Zone name '%s' does not match filename '%s'".formatted(zone, zoneFile.getFileName()));
        }
    }

    /**
     * Removes a trailing {@code .json} suffix, ignoring case. Other names are returned unchanged.
     *
     * @param filename the file name, which may include the suffix.
     * @return the file name without that suffix.
     */
    private static String stripJsonSuffix(String filename) {
        if (filename.toLowerCase().endsWith(".json")) {
            return filename.substring(0, filename.length() - 5);
        }
        return filename;
    }

    /**
     * Checks that {@code type} is a DNS record type dnsjava recognizes.
     *
     * @param type the record type mnemonic from the zone definition.
     * @throws IllegalArgumentException when the type is blank or unsupported.
     */
    private static void validateRecordType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("record type is required");
        }
        if (Type.value(type, true) < 0) {
            throw new IllegalArgumentException("Unsupported record type: " + type);
        }
    }

    /**
     * JSON shape of a zone definition file.
     * Unknown properties are ignored so older files still load.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ZoneDefinitionJson {
        /**
         * DNS zone name. It must match the file name without the {@code .json} suffix.
         */
        public String zone;

        /**
         * Authoritative nameserver contacted for this zone.
         */
        public String nameserver;

        /**
         * Scoped path of the TSIG key file referenced by the zone definition.
         */
        public String keyFile;

        /**
         * Managed records and deletion markers. May be null when the file lists none.
         */
        public List<RecordJson> records;
    }

    /**
     * JSON shape of one managed record or deletion marker inside a zone definition.
     * Unknown properties are ignored.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class RecordJson {
        /**
         * Owner name as written in the file, either relative to the zone or an FQDN.
         * It is normalized to FQDN before comparison.
         */
        public String name;

        /**
         * DNS record type mnemonic.
         */
        public String type;

        /**
         * Expected rdata of a managed record. Not set on a deletion marker.
         */
        public String rdata;

        /**
         * Rdata pattern for a deletion marker.
         * Omitted means every rdata value for the name and type matches.
         */
        @JsonProperty("rdataPattern")
        public String rdataPattern;

        /**
         * TTL policy for a managed record.
         * Omitted ignores TTL, {@code -1} uses the zone default TTL, and a positive value is enforced.
         */
        public Integer ttl;

        /**
         * Whether this entry is a deletion marker rather than a managed record.
         */
        @JsonProperty("delete")
        public boolean delete;
    }
}
