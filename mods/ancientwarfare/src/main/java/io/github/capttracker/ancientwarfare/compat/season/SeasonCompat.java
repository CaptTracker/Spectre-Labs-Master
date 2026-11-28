/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): what the Ancient Warfare modules may ask about the seasons of another mod (Serene Seasons).
 */
package io.github.capttracker.ancientwarfare.compat.season;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The seasons of the world, as far as the Ancient Warfare modules care: whether a crop is fertile in the current season at a position, which season it is, and
 * how long the year is. The default ({@link #NONE}) is used when no seasons mod is installed: every crop is fertile all year, and there is no season.
 *
 * <p>The real implementation is in {@code compat.sereneseasons} and is put in place by the {@link io.github.capttracker.ancientwarfare.compat.CompatLoader}
 * only when Serene Seasons is loaded. Nothing outside that package names a class of the other mod; every caller goes through {@link #get()}. Server side.
 */
public interface SeasonCompat {
    /** No seasons: everything is fertile, no season is known. */
    SeasonCompat NONE = new SeasonCompat() {};

    /**
     * The season at a place: its key (spring, summer, autumn, winter; dry or wet in tropical biomes), the key of the part of the season (early_spring and so
     * on), the day of the year and the number of days of the year.
     */
    record Info(String season, String subSeason, boolean tropical, int dayOfYear, int yearDays) {
        /** True in winter of a biome with the four seasons. */
        public boolean isWinter() {
            return !tropical && season.equals("winter");
        }
    }

    /** Holder of the implementation in use (a nested class, because an interface cannot hold a mutable field). */
    final class Holder {
        private static volatile SeasonCompat current = NONE;

        private Holder() {}
    }

    /** The implementation to call: never null. */
    static SeasonCompat get() {
        return Holder.current;
    }

    /** Puts an implementation in place ({@link #NONE} switches the integration off again). */
    static void set(SeasonCompat compat) {
        Holder.current = compat == null ? NONE : compat;
    }

    /** True when a seasons mod is in use. */
    default boolean isActive() {
        return false;
    }

    /** Whether the crop (the registry id of its block) grows at the position in the current season. Always true without a seasons mod. */
    default boolean isFertile(Level level, BlockPos pos, ResourceLocation block) {
        return true;
    }

    /** Whether the item, if it places a block (seeds, a sapling, a root vegetable), may be sown at the position in the current season. Other items always may. */
    default boolean isFertile(Level level, BlockPos pos, ItemStack stack) {
        if (!isActive() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return true;
        }
        return isFertile(level, pos, BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()));
    }

    /** The season at the position, or null when there is none (no seasons mod, or a dimension without seasons). */
    @Nullable
    default Info info(Level level, BlockPos pos) {
        return null;
    }

    /** The length of the whole year of the seasons mod, in ticks; 0 when unknown. */
    default long yearTicks(Level level) {
        return 0L;
    }
}
