/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the view of an inn that the innkeeper NPC and the settlement morale use.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An inn (the worksite block of the Automation module). It looks at the blocks in a radius around itself: it counts as complete when it has at least one
 * bed and a table (a crafting table or a barrel) there. An innkeeper NPC reports in with {@link #staff}; while it does the inn is open. The pantry holds
 * the food and drink the couriers bring from the settlement's storage and the coins the inn earns. Implemented by the block entity; the NPC module finds
 * inns through {@link WorksiteRegistry}. Server thread only.
 */
public interface IInn {
    /** Ticks after the last report of the innkeeper until the inn counts as closed. */
    int STAFF_TIMEOUT = 60;

    /**
     * Whether an item is food or drink an inn serves: a food with at least 3 nutrition and no harmful effect (so no rotten flesh or poisonous potato), or a
     * drinkable item (milk, a honey bottle, a potion).
     */
    static boolean isMeal(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            if (food.nutrition() < 3) {
                return false;
            }
            for (FoodProperties.PossibleEffect effect : food.effects()) {
                if (effect.effect().getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                    return false;
                }
            }
            return true;
        }
        return stack.getUseAnimation() == UseAnim.DRINK;
    }

    /** The block of the inn. */
    BlockPos getInnPos();

    /** Where the innkeeper stands: the block in front of the inn. */
    BlockPos getStandPos();

    /** The radius (blocks) in which the inn looks for beds and a table. */
    int getRadius();

    /** The beds (foot part) found in the radius at the last scan. */
    List<BlockPos> getBeds();

    /** Whether a crafting table or a barrel was found in the radius at the last scan. */
    boolean hasTable();

    /** At least one bed and a table. */
    default boolean isComplete() {
        return hasTable() && !getBeds().isEmpty();
    }

    /** The pantry: food and drink for the guests, and the coins earned. */
    IItemHandler getPantry();

    /** True while an innkeeper stands at the inn (it has told the inn in the last 3 seconds) and the inn is complete. */
    boolean isOpen();

    Optional<UUID> getKeeper();

    /**
     * An innkeeper reports in; call at least every 2 seconds while it stays.
     *
     * @return false if another innkeeper holds the inn
     */
    boolean staff(UUID keeper, long gameTime);

    void release(UUID keeper);

    /**
     * Rents a free bed of the inn to a guest until the game time {@code until}. A guest that has a bed keeps it (the time is extended).
     *
     * @return the bed, or empty if the inn is closed or has no free bed
     */
    Optional<BlockPos> rentBed(UUID guest, long gameTime, long until);

    /** The bed rented to the guest right now, if any. */
    Optional<BlockPos> getRentedBed(UUID guest, long gameTime);

    /** How many beds are not rented right now. */
    int freeBeds(long gameTime);
}
