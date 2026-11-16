package de.neitzel.dnscheck.fx;

import de.neitzel.fx.theme.Theme;
import de.neitzel.fx.theme.ThemeManager;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;

import java.net.URL;
import java.util.*;
import java.util.function.Function;

/** Application-owned themes and preference persistence; no zone/query operations. */
public final class DnsThemes implements AutoCloseable {
    private final ThemeManager manager = new ThemeManager();
    private final RecentDirectories settings;
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper("");
    private final Map<String, RadioMenuItem> choices = new LinkedHashMap<>();

    private final ChangeListener<ThemeManager.Failure> failures =
            (observable, previous, failure) -> {
                if (failure != null) message.set(failure.message());
            };

    public DnsThemes(RecentDirectories settings) {
        this(settings, id -> DnsThemes.class.getResource("theme/" + id + ".css"));
    }

    DnsThemes(RecentDirectories settings, Function<String, URL> resources) {
        try {
            this.settings = Objects.requireNonNull(settings);
            manager.failureProperty().addListener(failures);
            for (var base : List.of(Theme.light(), Theme.dark(), Theme.highContrast())) {
                URL resource = resources.apply(base.id());
                if (resource == null) continue;
                var sheets = new ArrayList<>(base.stylesheets());
                sheets.add(resource.toExternalForm());
                manager.addTheme(new Theme("dns-" + base.id(), sheets));
            }
            if (!manager.select("dns-light")) {
                throw new IllegalStateException(
                        "Bundled DNSCheck Light theme unavailable. Repair the application"
                            + " installation. "
                                + manager.failureProperty().get().message());
            }
            try {
                String saved = settings.theme();
                if (!manager.select("dns-" + saved)) {
                    String reason = manager.failureProperty().get().message();
                    // A failed selection retains the already installed Light bundle.
                    message.set(reason + " Using Light; choose a theme from View → Theme.");
                }
            } catch (java.io.IOException | RuntimeException failure) {
                message.set(
                        "Could not read theme preference; using Light. " + failure.getMessage());
            }
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    public void install(Scene scene, Menu menu) {
        manager.register(scene);
        var group = new ToggleGroup();
        for (String id : List.of("light", "dark", "high-contrast")) {
            var item =
                    new RadioMenuItem(
                            switch (id) {
                                case "light" -> "Light";
                                case "dark" -> "Dark";
                                default -> "High contrast";
                            });
            item.setToggleGroup(group);
            item.setOnAction(e -> select(id));
            choices.put(id, item);
            menu.getItems().add(item);
        }
        sync();
    }

    public boolean select(String id) {
        boolean installed = manager.select("dns-" + id);
        if (!installed) message.set(manager.failureProperty().get().message());
        else {
            message.set("");
            try {
                settings.rememberTheme(id);
            } catch (java.io.IOException | RuntimeException failure) {
                message.set(
                        "Theme applied, but its preference could not be saved. "
                                + failure.getMessage());
            }
        }
        sync();
        return installed;
    }

    public String selected() {
        String id = manager.selectedThemeProperty().get().id();
        return id.startsWith("dns-") ? id.substring(4) : id;
    }

    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    ThemeManager manager() {
        return manager;
    }

    private void sync() {
        choices.forEach((id, item) -> item.setSelected(id.equals(selected())));
    }

    public void close() {
        manager.failureProperty().removeListener(failures);
        manager.close();
    }
}
