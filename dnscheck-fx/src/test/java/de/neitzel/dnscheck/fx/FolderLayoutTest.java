package de.neitzel.dnscheck.fx;

import de.neitzel.fx.docking.*;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FolderLayoutTest {
    @TempDir Path folder;

    @Test void roundTripsASplitAndFloatingTabWithItsReturnPosition() throws Exception {
        var zone = new DockableId("example.com");
        var second = new DockableId("example.net");
        var third = new DockableId("example.org");
        var area = new DockAreaId("editors");
        var group = new DockGroupId("left");
        var settings = new LayoutSnapshot.GroupSettings(Side.TOP, DockGroupMode.DOCKED,
                200, 150, 80, 80, true, true, false, false, SplitDirections.ALL);
        var first = new LayoutSnapshot.Group(List.of(area), group, area, settings, false, 200, 150, List.of(zone), zone);
        var other = new LayoutSnapshot.Group(List.of(), new DockGroupId("right"), area, settings,
                false, 200, 150, List.of(second), second);
        var floating = new LayoutSnapshot.Group(List.of(), new DockGroupId("floating"), area, settings,
                false, 200, 150, List.of(third), third);
        var split = new LayoutSnapshot.Split(List.of(), Orientation.HORIZONTAL, 0.4, first, other);
        var main = new LayoutSnapshot.Window(new DockWindowId("main"), true, null,
                List.of(new LayoutSnapshot.Root(LayoutSnapshot.Placement.CENTER, 200, 150, split)));
        var window = new LayoutSnapshot.Window(new DockWindowId("float"), false, new Rectangle2D(20, 30, 600, 400),
                List.of(new LayoutSnapshot.Root(LayoutSnapshot.Placement.CENTER, 200, 150, floating)));
        var snapshot = new LayoutSnapshot(LayoutSnapshot.CURRENT_VERSION, List.of(main, window), third,
                Map.of(third, new LayoutSnapshot.ReturnPosition(DockTarget.group(group, TabPosition.after(zone)), area)),
                Map.of(zone, area, second, area, third, area));
        var expected = new FolderLayout.Saved(snapshot, 216, new Rectangle2D(40, 50, 960, 640));

        var files = new FolderLayout();
        files.write(folder, expected);
        assertEquals(expected, files.read(folder).orElseThrow());
        try (var filesInFolder = Files.list(folder)) {
            assertEquals(1, filesInFolder.count());
        }
    }
}
