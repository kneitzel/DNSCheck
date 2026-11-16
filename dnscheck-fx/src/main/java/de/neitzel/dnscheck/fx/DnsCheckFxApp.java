package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.fx.model.CatalogSession;
import de.neitzel.dnscheck.fx.view.CatalogController;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Opens the zone catalog and edits zone definitions with the fx library.
 */
public class DnsCheckFxApp extends Application {

    /**
     * Creates the JavaFX application.
     */
    public DnsCheckFxApp() {
    }

    /**
     * Launches the desktop application.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Loads the catalog view and shows it.
     *
     * @param stage the primary stage
     */
    @Override
    public void start(Stage stage) {
        ApplicationContext context = new ApplicationContext(DnsCheckFxApp.class);
        Desk desk = context.getComponent(Desk.class);
        desk.prepare(session(context.getComponent(RecentDirectories.class)), context);
        LoadedView loaded = ViewLoader.load(
                CatalogController.class.getResource("Catalog.fxml"), desk.catalog(), context);
        stage.setTitle("DNSCheck");
        stage.setScene(new Scene(loaded.getRoot()));
        stage.show();
        context.getComponent(CatalogController.class).start(stage);
    }

    /** A blank directory asks the main window to open the folder chooser. */
    static CatalogSession session(RecentDirectories recent) {
        CatalogSession session = new CatalogSession();
        try {
            session.setDirectory(recent.lastDirectory().map(Path::toString).orElse(""));
        } catch (IOException | RuntimeException unavailableHistory) {
            // The Recent menu reports configuration errors after the window loads.
        }
        return session;
    }
}
