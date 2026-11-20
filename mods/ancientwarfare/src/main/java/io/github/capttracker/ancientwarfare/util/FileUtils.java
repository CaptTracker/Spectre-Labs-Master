/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/FileUtils.
 */
package io.github.capttracker.ancientwarfare.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * File export helper for the dev commands. Not ported: {@code findFiles}, which walked the mod jar by hand;
 * data and resource files are found through the {@code ResourceManager} now (see {@code RegistryLoader}).
 */
public final class FileUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FileUtils() {}

    /** Writes the rows as lines of a UTF-8 text file, replacing it, and creating missing folders. */
    public static void exportToFile(Path exportFile, List<String> rows) {
        try {
            Path parent = exportFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(exportFile, rows, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Error exporting file: {}", exportFile.toAbsolutePath(), e);
        }
    }
}
