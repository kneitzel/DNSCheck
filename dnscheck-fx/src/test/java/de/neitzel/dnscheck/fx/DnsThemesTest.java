package de.neitzel.dnscheck.fx;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.fx.docking.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

class DnsThemesTest {
    @TempDir Path temp;
    static final Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
    Thread.UncaughtExceptionHandler previous;

    @BeforeAll
    static void start() throws Exception {
        var latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        fx(() -> Platform.setImplicitExit(false));
    }

    static void fx(Runnable work) throws Exception {
        var future = new FutureTask<Void>(work, null);
        Platform.runLater(future);
        future.get(20, TimeUnit.SECONDS);
    }

    @BeforeEach
    void capture() throws Exception {
        fx(
                () -> {
                    previous = Thread.currentThread().getUncaughtExceptionHandler();
                    Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e));
                });
    }

    @AfterEach
    void restore() throws Exception {
        fx(
                () -> {
                    for (var w : List.copyOf(Window.getWindows())) w.hide();
                    Thread.currentThread().setUncaughtExceptionHandler(previous);
                });
        assertTrue(errors.isEmpty(), errors.toString());
        errors.clear();
    }

    @Test
    void settingsCoexistWithDirectoryHistoryAndMalformedFilesAreNotOverwritten() throws Exception {
        Path config = temp.resolve("config.json");
        Files.writeString(config, "{\"unrelated\":42,\"theme\":\"dark\"}");
        var settings = new RecentDirectories(config);
        settings.remember(temp);
        settings.rememberTheme("high-contrast");
        assertEquals("high-contrast", new RecentDirectories(config).theme());
        assertEquals(List.of(temp.toRealPath()), settings.existingDirectories());
        assertTrue(Files.readString(config).contains("42"));
        Files.writeString(config, "broken");
        fx(
                () -> {
                    try (var themes = new DnsThemes(settings)) {
                        assertEquals("light", themes.selected());
                        assertFalse(themes.messageProperty().get().isBlank());
                        assertTrue(themes.select("dark"));
                        assertTrue(themes.messageProperty().get().contains("could not be saved"));
                    }
                });
        assertEquals("broken", Files.readString(config));
    }

    @Test
    void unavailablePreferenceAndInstallFailureRetainApplicationStylesAndTheme() throws Exception {
        Path config = temp.resolve("config.json");
        Files.writeString(config, "{\"theme\":\"removed-theme\"}");
        fx(
                () -> {
                    try (var themes = new DnsThemes(new RecentDirectories(config))) {
                        assertEquals("light", themes.selected());
                        assertTrue(themes.messageProperty().get().contains("Using Light"));
                        var stage = new Stage();
                        var scene =
                                new Scene(
                                        new javafx.scene.layout.VBox(
                                                new Label("Application content")),
                                        400,
                                        200);
                        stage.setScene(scene);
                        String application =
                                DnsThemes.class.getResource("view/catalog.css").toExternalForm();
                        scene.getStylesheets().add(application);
                        themes.install(scene, new Menu());
                        stage.show();
                        assertTrue(themes.select("dark"));
                        var before = List.copyOf(scene.getStylesheets());
                        themes.manager()
                                .setInstallationCheck(
                                        s -> {
                                            throw new IllegalStateException(
                                                    "Injected installation failure");
                                        });
                        assertFalse(themes.select("high-contrast"));
                        assertEquals("dark", themes.selected());
                        assertEquals(before, scene.getStylesheets());
                        assertFalse(themes.messageProperty().get().isBlank());
                        stage.hide();
                    }
                });
        assertEquals("dark", new RecentDirectories(config).theme());
    }

    @Test
    void missingOptionalPaletteFallsBackAndMissingLightFailsClearly() throws Exception {
        for (String saved : List.of("light", "dark")) {
            var settings = new RecentDirectories(temp.resolve(saved + ".json"));
            settings.rememberTheme(saved);
            fx(
                    () -> {
                        try (var themes =
                                new DnsThemes(
                                        settings,
                                        id ->
                                                id.equals("dark")
                                                        ? null
                                                        : DnsThemes.class.getResource(
                                                                "theme/" + id + ".css"))) {
                            assertEquals("light", themes.selected());
                            assertEquals(
                                    saved.equals("light"),
                                    themes.messageProperty().get().isBlank());
                        }
                        var failure =
                                assertThrows(
                                        IllegalStateException.class,
                                        () -> new DnsThemes(settings, id -> null));
                        assertTrue(
                                failure.getMessage()
                                        .contains("Repair the application installation"));
                    });
            assertEquals(saved, settings.theme());
        }
    }

    @Test
    void inheritedWindowFailureIsVisibleWithoutChangingOwnerOrPreference() throws Exception {
        Path config = temp.resolve("window.json");
        var settings = new RecentDirectories(config);
        settings.rememberTheme("dark");
        String beforeConfig = Files.readString(config);
        fx(
                () -> {
                    try (var themes = new DnsThemes(settings)) {
                        var owner = new Stage();
                        owner.setScene(new Scene(new javafx.scene.layout.VBox(new Label("Owner"))));
                        themes.install(owner.getScene(), new Menu());
                        owner.show();
                        var before = List.copyOf(owner.getScene().getStylesheets());
                        var child = new Stage();
                        child.initOwner(owner);
                        child.setScene(new Scene(new javafx.scene.layout.VBox(new Label("Child"))));
                        String application =
                                DnsThemes.class.getResource("view/catalog.css").toExternalForm();
                        child.getScene().getStylesheets().add(application);
                        themes.manager()
                                .setInstallationCheck(
                                        scene -> {
                                            if (scene == child.getScene())
                                                throw new IllegalStateException(
                                                        "Injected child failure");
                                        });
                        child.show();
                        assertTrue(
                                themes.messageProperty()
                                        .get()
                                        .contains("Could not theme new window"));
                        assertEquals("dark", themes.selected());
                        assertEquals(before, owner.getScene().getStylesheets());
                        assertEquals(List.of(application), child.getScene().getStylesheets());
                        child.hide();
                        owner.hide();
                    }
                });
        assertEquals(beforeConfig, Files.readString(config));
    }

    @Test
    void dockingFloatingWindowAndNewDialogReceiveDomainBundleAndKeepDraft() throws Exception {
        fx(
                () -> {
                    try (var themes =
                            new DnsThemes(new RecentDirectories(temp.resolve("config.json")))) {
                        var stage = new Stage();
                        var pane = new DockPane();
                        pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
                        stage.setScene(new Scene(pane, 600, 400));
                        themes.install(stage.getScene(), new Menu());
                        stage.show();
                        var docking = DockingManager.attach(stage, pane, new DockingOptions());
                        var draft = new TextArea("unfinished zone edit");
                        var item = new Dockable(new DockableId("zone"), draft);
                        docking.open(item, DockTarget.area("editors"));
                        themes.select("dark");
                        docking.move(item.getId(), DockTarget.floating());
                        var floating = draft.getScene();
                        assertTrue(
                                floating.getStylesheets().stream()
                                        .anyMatch(s -> s.contains("dnscheck/fx/theme/dark.css")));
                        var dialog =
                                new de.neitzel.dnscheck.fx.view.RecordDialog(
                                        floating.getWindow(), "example.com", null);
                        dialog.show();
                        for (String id : List.of("light", "dark", "high-contrast")) {
                            themes.select(id);
                            assertTrue(
                                    floating.getStylesheets().stream()
                                            .anyMatch(
                                                    s ->
                                                            s.endsWith(
                                                                    "dnscheck/fx/theme/"
                                                                            + id
                                                                            + ".css")));
                            assertTrue(
                                    dialog.getDialogPane()
                                            .getScene()
                                            .getStylesheets()
                                            .containsAll(floating.getStylesheets()));
                            assertEquals("unfinished zone edit", draft.getText());
                        }
                        dialog.close();
                        for (var w : List.copyOf(Window.getWindows())) w.hide();
                    }
                });
    }
}
