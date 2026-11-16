package de.neitzel.dnscheck.fx;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.*;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

final class ThemeEvidence {
    static final List<String> ratios = new ArrayList<>();

    static Color background(Node node) {
        for (Node n = node; n != null; n = n.getParent())
            if (n instanceof Region r && r.getBackground() != null)
                for (int i = r.getBackground().getFills().size() - 1; i >= 0; i--) {
                    var p = r.getBackground().getFills().get(i).getFill();
                    if (p instanceof Color c && c.getOpacity() == 1) return c;
                }
        throw new AssertionError("No opaque background for " + node);
    }

    static double linear(double n) {
        return n <= .04045 ? n / 12.92 : Math.pow((n + .055) / 1.055, 2.4);
    }

    static double luminance(Color c) {
        return .2126 * linear(c.getRed())
                + .7152 * linear(c.getGreen())
                + .0722 * linear(c.getBlue());
    }

    static void check(String label, Labeled node, double target) {
        Color foreground = (Color) node.getTextFill(), back = background(node);
        double a = luminance(foreground), b = luminance(back);
        double ratio = (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
        double opacity = 1;
        for (Node n = node; n != null; n = n.getParent()) opacity *= n.getOpacity();
        assertEquals(1, opacity, .001, "Opacity changes effective contrast: " + label);
        ratios.add(label + " | " + foreground + " | " + back + " | " + ratio);
        assertTrue(ratio >= target, label + " contrast=" + ratio);
    }

    static void paint(String label, Color foreground, Color back, double target) {
        double a = luminance(foreground), b = luminance(back);
        double ratio = (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
        ratios.add(label + " | " + foreground + " | " + back + " | " + ratio);
        assertTrue(ratio >= target, label + " contrast=" + ratio);
    }

    static void snapshot(String name, Parent root) throws Exception {
        root.applyCss();
        root.layout();
        var image = root.snapshot(null, null);
        var out =
                new BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++)
                out.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        Files.createDirectories(Path.of("target/theme-evidence"));
        ImageIO.write(out, "png", Path.of("target/theme-evidence", name + ".png").toFile());
        Files.write(Path.of("target/theme-evidence/contrast.txt"), ratios);
    }
}
