package de.neitzel.dnscheck.fx.model;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

/** The selected local directory and its displayed entries. */
@Getter
@Setter
public class CatalogSession {
    private String directory = "";
    private List<CatalogEntryModel> entries = new ArrayList<>();
    private CatalogEntryModel selected;
    private String outcome = "";
}
