package de.neitzel.dnscheck.fx.view;

import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.HBox;

/**
 * One catalog entry in the catalog table.
 * <p>
 * The class is the root and the Controller of {@code CatalogRow.fxml}. The table sets
 * {@link #modelProperty()} once. The fields are labels and a read-only valid mark, in column order.
 */
@Singleton
public class CatalogRow extends HBox {

    /**
     * The catalog entry this row shows. The table sets it once.
     */
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * Creates an empty row. The container calls this constructor.
     */
    public CatalogRow() {
    }

    /**
     * Returns the model property the table sets.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the catalog entry this row shows.
     *
     * @return the entry, or {@code null} when none is set
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the catalog entry this row shows.
     *
     * @param model the entry
     */
    public void setModel(Object model) {
        this.model.set(model);
    }
}
