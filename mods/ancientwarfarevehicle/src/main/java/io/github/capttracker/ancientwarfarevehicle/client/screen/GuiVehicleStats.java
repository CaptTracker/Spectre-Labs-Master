/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/gui/GuiVehicleStats (and its DummyContainer).
 */
package io.github.capttracker.ancientwarfarevehicle.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.menu.ScreenOnlyMenu;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The vehicle stats screen (1.12.2: {@code GUI_VEHICLE_STATS}, 52): about 20 lines of the vehicle's current numbers, opened by
 * the client from {@link GuiVehicleInventory} with no server involved. It has an empty client-only menu (1.12.2: the
 * {@code DummyContainer}) and returns to the inventory screen when closed.
 *
 * <p>The values are read once when the screen is built, as in the original. The strings are lang keys now (the original had
 * them in English in the code).
 *
 * <p>Left out: the three empty labels at the bottom of the original (its own TODO doubted they were needed).
 */
public class GuiVehicleStats extends GuiContainerBase<ScreenOnlyMenu> {
    private static final String KEY = "gui.ancientwarfarevehicles.stats.";

    private final VehicleBase vehicle;
    private final GuiVehicleInventory parent;
    private int nextLineY = 4;

    public GuiVehicleStats(GuiVehicleInventory parent, VehicleBase vehicle) {
        super(new ScreenOnlyMenu(parent.getContainer().player.getInventory()), 256, 240);
        this.vehicle = vehicle;
        this.parent = parent;
        this.shouldCloseOnVanillaKeys = true;
    }

    /** Adds one line of 10 pixels: the lang key of the label and its values. */
    private void line(String key, Object... values) {
        addGuiElement(new Label(10, nextLineY, Component.translatable(KEY + key, values).getString()));
        nextLineY += 10;
    }

    @Override
    public void initElements() {
        Vec3 motion = vehicle.getDeltaMovement();
        line("vehicle_type", vehicle.vehicleType.getDisplayComponent().getString());
        line("material_level", vehicle.vehicleMaterialLevel);
        line("health", vehicle.getHealth(), vehicle.baseHealth);
        line("weight", vehicle.currentWeight, vehicle.baseWeight);
        line("speed", Trig.getVelocity(motion.x, motion.y, motion.z) * 20, vehicle.currentForwardSpeedMax * 20);
        line("missile_velocity", vehicle.localLaunchPower, vehicle.currentLaunchSpeedPowerMax);
        line("resists", vehicle.currentFireResist, vehicle.currentExplosionResist, vehicle.currentGenericResist);
        line("mountable", vehicle.isMountable());
        line("drivable", vehicle.isDrivable());
        line("combat_vehicle", vehicle.isAimable());
        line("rider_sits", vehicle.shouldRiderSit());
        line("rider_on_turret", vehicle.vehicleType.moveRiderWithTurret());
        line("adjustable_yaw", vehicle.canAimRotate());
        line("adjustable_pitch", vehicle.canAimPitch());
        line("adjustable_power", vehicle.canAimPower());
        line("pitch_min", vehicle.currentTurretPitchMin);
        line("pitch_max", vehicle.currentTurretPitchMax);
        line("yaw_min", vehicle.localTurretRotationHome - vehicle.currentTurretRotationMax);
        line("yaw_max", vehicle.localTurretRotationHome + vehicle.currentTurretRotationMax);
        // the original left a 10 pixel gap (an empty label) before the team line
        nextLineY += 10;
        line("team", vehicle.getTeam() == null ? "" : vehicle.getTeam().getName());
    }

    @Override
    public void setupElements() {
    }

    /** Escape and the inventory key return to the inventory screen, which shares the vehicle's menu with the server. */
    @Override
    protected boolean onGuiCloseRequested() {
        parent.bindMenu();
        showScreen(parent);
        return false;
    }
}
