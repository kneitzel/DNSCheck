package de.neitzel.dnscheck.fx.view;

import de.neitzel.dnscheck.fx.Desk;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;

/**
 * One row of a zone definition.
 * <p>
 * The class is the root and the Controller of {@code RecordForm.fxml}. Repeat creates one
 * instance per record.
 */
@Singleton
public class RecordForm extends HBox {

    private final Desk desk;
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * Creates a row that can remove itself from the open zone.
     *
     * @param desk the desk
     */
    @Inject
    public RecordForm(Desk desk) {
        this.desk = desk;
    }

    /**
     * Returns the model property Repeat sets.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the record this row edits.
     *
     * @return the record, or {@code null} when none is set
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the record this row edits.
     *
     * @param model the record
     */
    public void setModel(Object model) {
        this.model.set(model);
    }

    /**
     * Removes this record from the open zone.
     */
    @FXML
    public void remove() {
        if (desk.zone() == null || getModel() == null) {
            return;
        }
        desk.zone().getRecords().remove(getModel());
    }
}
