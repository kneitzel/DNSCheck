package de.neitzel.dnscheck.fx;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Moves a completed temporary file into place. */
final class AtomicFiles {
    private AtomicFiles() {}

    static void replace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
