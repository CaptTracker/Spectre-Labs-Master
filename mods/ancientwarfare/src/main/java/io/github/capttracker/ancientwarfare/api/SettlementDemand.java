/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): a hook for what the settlement around a production worksite is short of.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Lets a production worksite (the automation module cannot see settlements) ask whether the settlement it stands in is short of what a recipe makes
 * (a pickaxe for a worker without one, food, planks for the builders), and tell the settlement that it made something. The NPC module sets the
 * {@link Provider} when it loads; without one nothing is ever needed. Server thread only.
 */
public final class SettlementDemand {
    /**
     * How much the settlement is short of a product: {@code count} items still missing (0 = not needed) and the {@code priority} of the most urgent need
     * it fills (higher is more urgent; tools 100, food 95, weapons 90, armour 70, fuel 60, raw inputs 50, building stock 40).
     */
    public record Demand(int count, int priority) {
        public static final Demand NONE = new Demand(0, 0);

        public boolean needed() {
            return count > 0;
        }
    }

    public interface Provider {
        /** The demand of the settlement that holds the site for a product (one item is enough to tell the kind). */
        Demand demandFor(ServerLevel level, BlockPos site, ItemStack product);

        /** The site made this product: the settlement counts it against its needs until it recounts. */
        void fulfilled(ServerLevel level, BlockPos site, ItemStack product);
    }

    @Nullable
    private static Provider provider;

    private SettlementDemand() {}

    public static void setProvider(@Nullable Provider value) {
        provider = value;
    }

    /** What the settlement of the site is short of that this product fills; {@link Demand#NONE} without a settlement or provider. */
    public static Demand demandFor(ServerLevel level, BlockPos site, ItemStack product) {
        Provider active = provider;
        return active == null || product.isEmpty() ? Demand.NONE : active.demandFor(level, site, product);
    }

    /** Tells the settlement that the site made the product. */
    public static void fulfilled(ServerLevel level, BlockPos site, ItemStack product) {
        Provider active = provider;
        if (active != null && !product.isEmpty()) {
            active.fulfilled(level, site, product);
        }
    }
}
