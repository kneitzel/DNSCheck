package de.neitzel.dnscheck.fx.model;

import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.TtlPolicy;
import org.xbill.DNS.Type;

import java.util.Locale;

/**
 * One managed record or deletion marker edited in a row.
 * <p>
 * TTL text is empty when TTL is ignored, {@code -1} for the zone default, or a non-negative
 * number of seconds. A deletion marker stores the pattern and does not store rdata.
 */
public class RecordEditor {

    private String name = "";
    private String type = "";
    private String rdata = "";
    private String ttl = "";
    private String rdataPattern = "";
    private boolean deletionMarker;

    /**
     * Creates an empty record.
     */
    public RecordEditor() {
    }

    /**
     * Copies one record from a zone definition.
     *
     * @param spec the record
     * @return the editor row
     */
    public static RecordEditor from(RecordSpec spec) {
        RecordEditor editor = new RecordEditor();
        editor.setName(spec.nameFqdn());
        editor.setType(spec.type());
        editor.setRdata(spec.rdata() == null ? "" : spec.rdata());
        editor.setRdataPattern(spec.rdataPattern() == null ? "" : spec.rdataPattern());
        editor.setDeletionMarker(spec.deletionMarker());
        editor.setTtl(ttlText(spec.ttlPolicy()));
        return editor;
    }

    /**
     * Returns the record this row edits.
     *
     * @return the record
     */
    public RecordSpec toSpec() {
        String recordType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (Type.value(recordType, true) < 0) {
            throw new IllegalArgumentException("Unsupported record type: " + recordType);
        }
        String recordName = name == null ? "" : name.trim();
        if (deletionMarker) {
            return new RecordSpec(
                    recordName,
                    recordType,
                    null,
                    blankToNull(rdataPattern),
                    true,
                    new TtlPolicy.Ignore());
        }
        return new RecordSpec(recordName, recordType, blankToNull(rdata), null, false, ttlPolicy(ttl));
    }

    /**
     * Returns the record name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the record name.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the record type.
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the record type.
     *
     * @param type the type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the rdata.
     *
     * @return the rdata
     */
    public String getRdata() {
        return rdata;
    }

    /**
     * Sets the rdata.
     *
     * @param rdata the rdata
     */
    public void setRdata(String rdata) {
        this.rdata = rdata;
    }

    /**
     * Returns the TTL text.
     *
     * @return the TTL text
     */
    public String getTtl() {
        return ttl;
    }

    /**
     * Sets the TTL text.
     *
     * @param ttl the TTL text
     */
    public void setTtl(String ttl) {
        this.ttl = ttl;
    }

    /**
     * Returns the rdata pattern of a deletion marker.
     *
     * @return the pattern
     */
    public String getRdataPattern() {
        return rdataPattern;
    }

    /**
     * Sets the rdata pattern of a deletion marker.
     *
     * @param rdataPattern the pattern
     */
    public void setRdataPattern(String rdataPattern) {
        this.rdataPattern = rdataPattern;
    }

    /**
     * Returns whether this row is a deletion marker.
     *
     * @return {@code true} when the row is a deletion marker
     */
    public boolean isDeletionMarker() {
        return deletionMarker;
    }

    /**
     * Sets whether this row is a deletion marker.
     *
     * @param deletionMarker {@code true} when the row is a deletion marker
     */
    public void setDeletionMarker(boolean deletionMarker) {
        this.deletionMarker = deletionMarker;
    }

    private static String ttlText(TtlPolicy policy) {
        if (policy instanceof TtlPolicy.Explicit explicit) {
            return Long.toString(explicit.seconds());
        }
        if (policy instanceof TtlPolicy.UseZoneDefault) {
            return "-1";
        }
        return "";
    }

    private static TtlPolicy ttlPolicy(String ttl) {
        if (ttl == null || ttl.isBlank()) {
            return new TtlPolicy.Ignore();
        }
        String text = ttl.trim();
        try {
            return TtlPolicy.fromJsonValue(Integer.valueOf(text));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("TTL must be empty, -1, or a non-negative number: " + text);
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
