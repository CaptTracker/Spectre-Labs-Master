/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gamedata/AWGameData (the 1.12.2 MapStorage wrapper).
 */
package io.github.capttracker.ancientwarfare.gamedata;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Entry point for the mod's saved data. Each data class supplies its own {@link SavedData.Factory} and
 * file name; this class only picks which storage it lives in.
 *
 * <ul>
 *   <li>{@link #getData}: one copy for the whole world, saved with the overworld (was {@code getData},
 *       the global MapStorage).</li>
 *   <li>{@link #getPerLevelData}: one copy per dimension (was {@code getPerWorldData}).</li>
 * </ul>
 *
 * <p>The 1.12.2 version derived the file name from the class name with an "AW" prefix; the name is now
 * passed in, because 1.21 data classes are not created by reflection. Use {@link #fileName(Class)} for
 * the old naming scheme.
 */
public final class AWGameData {
    public static final AWGameData INSTANCE = new AWGameData();

    private AWGameData() {}

    /** The 1.12.2 file naming scheme: "AW" plus the simple class name. */
    public static String fileName(Class<? extends SavedData> clazz) {
        return "AW" + clazz.getSimpleName();
    }

    public <T extends SavedData> T getData(MinecraftServer server, SavedData.Factory<T> factory, String name) {
        return getPerLevelData(server.overworld(), factory, name);
    }

    public <T extends SavedData> T getPerLevelData(ServerLevel level, SavedData.Factory<T> factory, String name) {
        return level.getDataStorage().computeIfAbsent(factory, name);
    }
}
