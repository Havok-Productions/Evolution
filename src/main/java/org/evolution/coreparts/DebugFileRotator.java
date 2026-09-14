package org.evolution.coreparts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * ## Bounded rotation for generated diagnostic snapshots.
 */
public final class DebugFileRotator {
    private DebugFileRotator() {
    }

    public static void rotateIfOversized(
            EvolutionPlugin plugin, File file, long maximumBytes,
            int archiveCount) {
        if (file == null || !file.isFile()
                || file.length() <= maximumBytes || archiveCount <= 0) {
            return;
        }
        try {
            for (int index = archiveCount; index >= 1; index--) {
                File source = index == 1
                        ? file
                        : new File(file.getParentFile(),
                                file.getName() + "." + (index - 1));
                if (!source.isFile()) {
                    continue;
                }
                File target = new File(file.getParentFile(),
                        file.getName() + "." + index);
                Files.move(source.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not rotate diagnostic file " + file.getName(),
                    exception);
        }
    }
}
