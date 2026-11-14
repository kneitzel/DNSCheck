package de.neitzel.dnscheck.fx.model;

import de.neitzel.dnscheck.core.zone.LocalZoneDirectory;
import lombok.Getter;
import lombok.Setter;

/** One local .domain file, including its validation error if it cannot be used. */
@Getter
@Setter
public class CatalogEntryModel {
    private String zone = "";
    private String nameserver = "";
    private String keyFile = "";
    private boolean valid;
    private String error = "";

    public static CatalogEntryModel from(LocalZoneDirectory.Entry entry) {
        CatalogEntryModel model = new CatalogEntryModel();
        model.setZone(entry.zone());
        model.setNameserver(entry.nameserver());
        model.setKeyFile(entry.keyFile());
        model.setValid(entry.valid());
        model.setError(entry.error() == null ? "" : entry.error());
        return model;
    }

    public String getCatalogRef() {
        return zone;
    }

    @Override
    public String toString() {
        return zone;
    }
}
