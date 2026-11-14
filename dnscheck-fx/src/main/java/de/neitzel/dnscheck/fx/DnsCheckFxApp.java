package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.fx.model.CatalogSession;
import de.neitzel.dnscheck.fx.view.CatalogController;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.ArrayList;

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
        desk.prepare(session(), context);
        LoadedView loaded = ViewLoader.load(
                CatalogController.class.getResource("Catalog.fxml"), desk.catalog(), context);
        stage.setTitle("DNSCheck");
        stage.setScene(new Scene(loaded.getRoot()));
        stage.show();
    }

    /**
     * Builds the catalog Model from the environment, falling back to {@code ./zones} and {@code ./keys}.
     *
     * @return the catalog Model
     */
    private static CatalogSession session() {
        CatalogSession session = new CatalogSession();
        session.setZonesDirectory(directory("DNSCHECK_ZONES_DIR", "./zones"));
        session.setKeysDirectory(directory("DNSCHECK_KEYS_DIR", "./keys"));
        session.setEntries(new ArrayList<>());
        session.setOutcome("");
        return session;
    }

    /**
     * Returns the directory named by an environment variable, or the fallback when it is unset or blank.
     *
     * @param env      the environment variable
     * @param fallback the directory used when the variable is unset or blank
     * @return the directory
     */
    private static String directory(String env, String fallback) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
