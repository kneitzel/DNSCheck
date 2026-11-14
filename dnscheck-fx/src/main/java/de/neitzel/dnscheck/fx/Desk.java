package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.fx.model.CatalogSession;
import de.neitzel.dnscheck.fx.model.ZoneEditor;
import de.neitzel.fx.view.ViewModel;
import de.neitzel.injection.ApplicationContext;
import jakarta.inject.Singleton;

import java.nio.file.Path;

/**
 * Holds the catalog Model, the zone Model currently open, and the container that loads Views.
 */
@Singleton
public class Desk {

    /**
     * The catalog Model the catalog View binds to.
     */
    private CatalogSession catalog;

    /**
     * The zone Model currently open, or {@code null} when none is open.
     */
    private ZoneEditor zone;

    /**
     * The container that loads Views.
     */
    private ApplicationContext context;

    /**
     * The ViewModel of the catalog View, or {@code null} before that View has loaded.
     */
    private ViewModel catalogView;

    private Path loadedDirectory;

    /** Capture pending configuration before loading. */
    public Path requestedDirectory() {
        return Path.of(catalog.getDirectory()).toAbsolutePath().normalize();
    }

    /** Apply only after a successful load. */
    public void catalogLoaded(Path directory) {
        loadedDirectory = directory;
    }

    public Path requireLoadedDirectory() {
        if (loadedDirectory == null || !loadedDirectory.equals(requestedDirectory())) {
            throw new IllegalStateException("Reload the catalog before using the changed directory.");
        }
        return loadedDirectory;
    }

    /**
     * Creates an empty desk. The container calls this constructor.
     */
    public Desk() {
    }

    /**
     * Installs the catalog Model and the container before the catalog View loads.
     *
     * @param catalog the catalog Model
     * @param context the container that loads Views
     */
    public void prepare(CatalogSession catalog, ApplicationContext context) {
        this.catalog = catalog;
        this.context = context;
    }

    /**
     * Returns the catalog Model.
     *
     * @return the catalog Model
     */
    public CatalogSession catalog() {
        return catalog;
    }

    /**
     * Returns the container that loads Views.
     *
     * @return the container
     */
    public ApplicationContext context() {
        return context;
    }

    /**
     * Records the ViewModel of the catalog View.
     *
     * @param catalogView the catalog ViewModel
     */
    public void catalogView(ViewModel catalogView) {
        this.catalogView = catalogView;
    }

    /**
     * Returns the ViewModel of the catalog View.
     *
     * @return the catalog ViewModel, or {@code null} before the catalog View has loaded
     */
    public ViewModel catalogView() {
        return catalogView;
    }

    /**
     * Records the zone Model about to be shown.
     *
     * @param zone the zone Model
     */
    public void edit(ZoneEditor zone) {
        this.zone = zone;
    }

    /**
     * Returns the zone Model currently open.
     *
     * @return the zone Model, or {@code null} when none is open
     */
    public ZoneEditor zone() {
        return zone;
    }
}
