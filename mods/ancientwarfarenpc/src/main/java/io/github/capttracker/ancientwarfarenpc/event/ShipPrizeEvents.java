/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): taking a ship of an NPC faction as a prize once its crew is gone.
 */
package io.github.capttracker.ancientwarfarenpc.event;

import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.ShipCombat;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.faction.AwFactions;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipPart;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import io.github.capttracker.spectrefactions.config.FactionsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Rework phase 11: a ship of an NPC faction (the trade ships, marked with the faction's name by {@code SeaCaravanSpawner}) that has nobody of its faction alive aboard
 * (the crew is dead or fled) is captured: the first player to use it takes it as a prize. The ship becomes the player's ({@code Owner}) and the reputation with the faction
 * drops by the attack penalty of Spectre Factions (as for an attack). Normal use of the ship goes on as before. Needs {@code naval_prize_enabled}.
 */
public final class ShipPrizeEvents {
    public static final ShipPrizeEvents INSTANCE = new ShipPrizeEvents();
    /** The entity data tag (in the ship's persistent data) that holds the name of the faction a trade ship belongs to. */
    public static final String FACTION_TAG = "awnpc_sea_faction";
    /** The persistent data tag (game time) set on a trade ship whose trader was killed: it is the players' prize, but an empty wreck of nobody does not stay for ever. */
    public static final String ABANDONED_TAG = "awnpc_sea_abandoned";
    /** Ticks an abandoned trade ship stays (when no player is near) before it vanishes. */
    private static final long ABANDONED_TICKS = 24000L;

    private ShipPrizeEvents() {}

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity target = event.getTarget();
        VehicleBase ship = target instanceof ShipPart part ? part.getParent() : target instanceof VehicleBase vehicle ? vehicle : null;
        if (ship == null || !ship.isShip()) {
            return;
        }
        String faction = ship.getPersistentData().getString(FACTION_TAG);
        if (faction.isEmpty()) {
            return;
        }
        if (!ship.getOwner().equals(Owner.EMPTY)) {
            ship.getPersistentData().remove(FACTION_TAG); // somebody has it already
            return;
        }
        if (!SettlementConfig.navalPrize || ship.ship.isSinking() || hasCrew(ship)) {
            return;
        }
        ship.setOwner(player);
        ship.getPersistentData().remove(FACTION_TAG);
        ship.getPersistentData().remove(ABANDONED_TAG);
        FactionsApi.adjustReputation(AwFactions.actorOfPlayer(player.level(), player.getUUID()), AwFactions.actorOfFaction(faction), -FactionsConfig.attackPenalty, "attack");
        player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.prize.claimed"), true);
    }

    /** Marks a trade ship whose leader was killed, so that it vanishes after a day if nobody takes it. */
    public static void markAbandoned(VehicleBase ship) {
        ship.getPersistentData().putLong(ABANDONED_TAG, ship.level().getGameTime());
    }

    /** An abandoned trade ship that nobody has taken vanishes after a day once no player is near (a hold's coins went when the trader died). Checked every 10 seconds. */
    @SubscribeEvent
    public void onVehicleTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof VehicleBase ship) || ship.level().isClientSide || ship.tickCount % 200 != 0) {
            return;
        }
        var data = ship.getPersistentData();
        if (!data.contains(ABANDONED_TAG)) {
            return;
        }
        if (!ship.getOwner().equals(Owner.EMPTY)) {
            data.remove(ABANDONED_TAG);
            return;
        }
        if (ship.level().getGameTime() - data.getLong(ABANDONED_TAG) > ABANDONED_TICKS && !hasCrew(ship)
                && ship.level().getNearestPlayer(ship, SettlementConfig.caravanDespawnRange) == null) {
            ship.discard();
        }
    }

    /** True if somebody of an NPC faction is alive aboard the ship: riding it or standing on its deck. */
    private static boolean hasCrew(VehicleBase ship) {
        for (LivingEntity aboard : ShipCombat.aboard(ship)) {
            if (aboard instanceof NpcFaction) {
                return true;
            }
        }
        return false;
    }
}
