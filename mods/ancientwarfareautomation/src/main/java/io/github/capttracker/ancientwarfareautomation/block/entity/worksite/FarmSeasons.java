/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): the season rule of the farms (Serene Seasons).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.compat.season.SeasonCompat;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The one question the farms ask about the seasons: may this item be sown here now. Without Serene Seasons, or with {@code serene_seasons.enabled} off, the
 * answer is always yes. The check itself is in the core module ({@link SeasonCompat}); nothing of the other mod is named here.
 */
public final class FarmSeasons {
    private FarmSeasons() {}

    /** True when the farms follow the seasons (the mod is installed and the switch is on). */
    public static boolean active() {
        return AWAutomationStatics.sereneSeasonsEnabled && SeasonCompat.get().isActive();
    }

    /** Whether the stack (seeds, a sapling, a vegetable that is planted) may be sown at the position in the current season. */
    public static boolean mayPlant(Level level, BlockPos pos, ItemStack stack) {
        return !active() || SeasonCompat.get().isFertile(level, pos, stack);
    }
}
