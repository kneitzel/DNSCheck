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

public final class ZoneDefinitionStore {

    private final ObjectMapper objectMapper;
    private final ZoneNameNormalizer nameNormalizer;

    public ZoneDefinitionStore() {
        this(new ZoneNameNormalizer(), new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT));
    }

    ZoneDefinitionStore(ZoneNameNormalizer nameNormalizer, ObjectMapper objectMapper) {
        this.nameNormalizer = nameNormalizer;
        this.objectMapper = objectMapper;
    }

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

    public void write(ZoneDefinition definition) throws IOException {
        validateFilename(definition.sourcePath(), definition.zone());
        ZoneDefinitionJson json = new ZoneDefinitionJson();
        json.zone = definition.zone();
        json.nameserver = definition.nameserver();
        json.keyFile = definition.keyFile();
        json.records = definition.records().stream().map(this::toRecordJson).toList();
        objectMapper.writeValue(definition.sourcePath().toFile(), json);
    }

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

    private static void validateFilename(Path zoneFile, String zone) {
        String expectedStem = zone;
        String actualStem = stripJsonSuffix(zoneFile.getFileName().toString());
        if (!expectedStem.equals(actualStem)) {
            throw new IllegalArgumentException(
                    "Zone name '%s' does not match filename '%s'".formatted(zone, zoneFile.getFileName()));
        }
    }

    private static String stripJsonSuffix(String filename) {
        if (filename.toLowerCase().endsWith(".json")) {
            return filename.substring(0, filename.length() - 5);
        }
        return filename;
    }

    private static void validateRecordType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("record type is required");
        }
        if (Type.value(type, true) < 0) {
            throw new IllegalArgumentException("Unsupported record type: " + type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ZoneDefinitionJson {
        public String zone;
        public String nameserver;
        public String keyFile;
        public List<RecordJson> records;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class RecordJson {
        public String name;
        public String type;
        public String rdata;

        @JsonProperty("rdataPattern")
        public String rdataPattern;

        public Integer ttl;

        @JsonProperty("delete")
        public boolean delete;
    }
}
