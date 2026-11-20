/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces the access transformer entry for {@code EntityLivingBase.activeItemStackUseCount} that
 * {@code NpcAIBlockWithShield} used to make the shield of an NPC block at once.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * The vanilla shield only blocks after it has been raised for 5 ticks ({@code LivingEntity.isBlocking}). The 1.12.2 NPC shield goal forced the use
 * counter to a fixed value so the NPC's shield blocked from the first tick. The same is done by starting the use of an NPC's shield with 5 ticks
 * already used up. Registered automatically on the game event bus (no wiring needed).
 */
@EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID)
public final class NpcShieldEvents {
    /** The delay of {@code LivingEntity.isBlocking}. */
    private static final int SHIELD_WARM_UP_TICKS = 5;

    private NpcShieldEvents() {
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof NpcBase && event.getItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)
                && event.getDuration() > SHIELD_WARM_UP_TICKS) {
            event.setDuration(event.getDuration() - SHIELD_WARM_UP_TICKS);
        }
    }
}
