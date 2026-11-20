/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/input/VehicleKeyConflictContext.
 */
package io.github.capttracker.ancientwarfarevehicle.client;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;

/**
 * Active only in the world with no screen open while the player rides a {@link VehicleBase}, so the driving keys (W, A, S, D,
 * space and so on) can share keys with the ordinary bindings the rest of the time.
 */
public final class VehicleKeyConflictContext implements IKeyConflictContext {
    public static final VehicleKeyConflictContext INSTANCE = new VehicleKeyConflictContext();

    private VehicleKeyConflictContext() {}

    @Override
    public boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.player != null && mc.level != null && mc.player.getVehicle() instanceof VehicleBase;
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return this == other;
    }
}
