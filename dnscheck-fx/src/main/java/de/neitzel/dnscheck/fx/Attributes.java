package de.neitzel.dnscheck.fx;

import de.neitzel.fx.view.ViewModel;
import javafx.beans.property.Property;

/**
 * Writes a ViewModel attribute from application code.
 */
public final class Attributes {

    private Attributes() {
    }

    /**
     * Sets the JavaFX property for an attribute name.
     * <p>
     * Setting the property writes the Model and updates a control bound to it.
     *
     * @param viewModel the ViewModel
     * @param name      the attribute name
     * @param value     the value
     */
    @SuppressWarnings("unchecked")
    public static void set(ViewModel viewModel, String name, Object value) {
        Property<Object> property = (Property<Object>) viewModel.property(name);
        property.setValue(value);
    }
}
