/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/compat/TwilightForestCompat.
 */
package io.github.capttracker.ancientwarfarenpc.compat;

import io.github.capttracker.ancientwarfare.compat.ICompat;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The Twilight Forest has a day and a night like the overworld although its sky does not show it, so the NPCs there (workers sleep or work by
 * the time of day) use the day time of the world. 1.12.2 read the dimension id from the mod's config by reflection; in 1.21 the dimension has a fixed id.
 */
public class TwilightForestCompat implements ICompat {
    private static final ResourceKey<Level> TWILIGHT_FOREST = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("twilightforest:twilight_forest"));

    @Override
    public String getModId() {
        return "twilightforest";
    }

    @Override
    public void init() {
        WorldTools.registerDimensionDaytimeLogic(TWILIGHT_FOREST, world -> world.getDayTime() % 24000 < 12000);
    }
}
