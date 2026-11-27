/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the Jade look-at tooltip of the siege works. Only Jade loads this class.
 */
package io.github.capttracker.ancientwarfarenpc.compat.jade;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The pushers and the state of the bridge of a siege tower, the pushers of a mantlet, and the health of both. Everything it shows is on the client already (the seats and the bridge
 * are synced entity data), so it has no server data part. The provider id is also the config toggle of Jade.
 */
public enum VehicleJadeProvider implements IEntityComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(AncientWarfareNPC.MOD_ID, "siege_info");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof VehicleBase vehicle) || !vehicle.vehicleType.isPushed()) {
            return;
        }
        tooltip.add(Component.translatable("jade.ancientwarfarenpc.siege.pushers", vehicle.getPushCount(), vehicle.vehicleType.getPushersForFullSpeed()));
        if (vehicle.siege != null) {
            String state = vehicle.siege.isBridgeFullyDown() ? "down" : vehicle.isBridgeDown() ? "moving" : "up";
            tooltip.add(Component.translatable("jade.ancientwarfarenpc.siege.bridge." + state));
        }
        tooltip.add(Component.translatable("jade.ancientwarfarenpc.siege.health", Math.round(vehicle.getHealth()), Math.round(vehicle.baseHealth)));
    }
}
