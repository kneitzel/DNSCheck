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

    /**
     * The record name, written as an FQDN or relative to the zone.
     */
    private String name = "";

    /**
     * The record type, such as {@code A}.
     */
    private String type = "";

    /**
     * The record data. A deletion marker does not store this.
     */
    private String rdata = "";

    /**
     * The TTL text. Empty ignores TTL, {@code -1} uses the zone default, and a number is seconds.
     */
    private String ttl = "";

    /**
     * The rdata glob pattern used by a deletion marker.
     */
    private String rdataPattern = "";

    /**
     * Whether this row stores a deletion marker instead of rdata.
     */
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

    /**
     * Returns the TTL text for a policy: seconds, {@code -1} for the zone default, or empty when TTL is ignored.
     *
     * @param policy the TTL policy
     * @return the text shown in the TTL field
     */
    private static String ttlText(TtlPolicy policy) {
        if (policy instanceof TtlPolicy.Explicit explicit) {
            return Long.toString(explicit.seconds());
        }
        if (policy instanceof TtlPolicy.UseZoneDefault) {
            return "-1";
        }
        return "";
    }

    /**
     * Parses TTL text into a policy.
     *
     * @param ttl empty, {@code -1}, or a non-negative number of seconds
     * @return the policy
     * @throws IllegalArgumentException when the text is not empty, {@code -1}, or an integer
     */
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

    /**
     * Returns the trimmed value, or {@code null} when it is null or blank.
     *
     * @param value the field text
     * @return the trimmed value, or {@code null}
     */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
