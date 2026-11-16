package de.neitzel.dnscheck.fx.view;

import de.neitzel.dnscheck.fx.LocalFileImport;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Modal forms for adding files to the current working folder. */
@RequiredArgsConstructor
final class FolderDialogs {
    private final Window owner;
    private final LocalFileImport imports;

    @FunctionalInterface
    interface KeyInstaller {
        boolean install(LocalFileImport.Pending key, Window owner) throws IOException;
    }

    String createZone(Path directory) throws IOException {
        List<String> keys = imports.keyFiles(directory);
        TextField zone = new TextField();
        zone.setId("newZoneName");
        TextField nameserver = new TextField();
        nameserver.setId("newZoneNameserver");
        ComboBox<String> key = new ComboBox<>();
        key.setId("newZoneKey");
        key.getItems().setAll(keys);
        if (keys.size() == 1) key.setValue(keys.getFirst());
        Dialog<ButtonType> dialog = dialog("New zone");
        GridPane fields = fields();
        field(fields, 0, "Zone name", zone);
        field(fields, 1, "Nameserver", nameserver);
        field(fields, 2, "Key file", key);
        if (keys.isEmpty()) fields.add(new Label("Add a valid BIND key to the working folder first."), 0, 3, 2, 1);
        dialog.getDialogPane().setContent(fields);
        Button create = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        create.setText("Create");
        create.disableProperty().bind(zone.textProperty().isEmpty()
                .or(nameserver.textProperty().isEmpty()).or(key.valueProperty().isNull()));
        create.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                imports.createZone(directory, zone.getText(), nameserver.getText(), key.getValue());
            } catch (IOException | RuntimeException failure) {
                showError(dialog, failure);
                event.consume();
            }
        });
        return dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK ? zone.getText().strip() : null;
    }

    void addKey(Path directory, KeyInstaller install) {
        TextArea content = new TextArea();
        content.setId("bindKeyText");
        content.setPromptText("Paste BIND key text");
        content.setPrefRowCount(6);
        TextField filename = new TextField();
        filename.setId("bindKeyFilename");
        content.textProperty().addListener((observable, old, current) -> {
            if (filename.getText().isBlank() || filename.getText().equals(imports.keyName(old))) {
                filename.setText(imports.keyName(current));
            }
        });
        Dialog<ButtonType> dialog = dialog("Add key");
        GridPane fields = fields();
        field(fields, 0, "Filename", filename);
        field(fields, 1, "BIND key", content);
        dialog.getDialogPane().setContent(fields);
        Button add = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        add.setText("Add");
        add.disableProperty().bind(filename.textProperty().isEmpty().or(content.textProperty().isEmpty()));
        add.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (!install.install(imports.pastedKey(directory, filename.getText(), content.getText()),
                        dialog.getDialogPane().getScene().getWindow())) event.consume();
            } catch (IOException | RuntimeException failure) {
                showError(dialog, failure);
                event.consume();
            }
        });
        dialog.showAndWait();
    }

    private Dialog<ButtonType> dialog(String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        return dialog;
    }

    private static GridPane fields() {
        GridPane fields = new GridPane(8, 8);
        fields.setStyle("-fx-padding: 12;");
        return fields;
    }

    private static void field(GridPane fields, int row, String text, javafx.scene.Node control) {
        Label label = new Label(text);
        label.setLabelFor(control);
        fields.addRow(row, label, control);
    }

    private static void showError(Dialog<ButtonType> dialog, Exception failure) {
        String text = failure.getMessage();
        Alert error = new Alert(Alert.AlertType.ERROR,
                text == null || text.isBlank() ? "Could not write the file." : text, ButtonType.OK);
        error.initOwner(dialog.getDialogPane().getScene().getWindow());
        error.showAndWait();
    }
}
