/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/input/VehicleInputHandler, IVehicleMovementHandler and the key binding setup of
 * vehicle/proxy/ClientProxy.
 */
package io.github.capttracker.ancientwarfarevehicle.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.capttracker.ancientwarfare.client.input.InputHandler;
import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.entity.IVehicleCrew;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.input.VehicleInputData;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleNetwork;
import net.minecraft.util.Mth;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Client side of the driving controls: the 13 key mappings (category {@code keybind.category.awVehicles}, active only while
 * riding a vehicle with no screen open), the callbacks of the action keys, the periodic input packet and the mouse aim.
 * Nothing outside the client package may reference this class.
 *
 * <p>Movement is analog (rework phase 4): every tick the held keys and the camera are turned into a {@link VehicleInputData} (throttle and steer
 * in -1..1; opposite keys cancel). It is applied to the local vehicle at once (the rider's own vehicle is predicted, see {@code VehicleMoveHelper})
 * and sent to the server whenever it changes, and once a second otherwise. Fire, aim and ammo key presses call the
 * vehicle helpers directly; they send their own payloads. The default keys are the ones of 1.12.2: W S A D drive, R F aim up and
 * down, Z X turn the turret, space fires, T G cycle the ammo, V opens the ammo selection, C toggles the steering mode (mouse steering or classic keys, which
 * also sets the mouse aim). With mouse steering the vehicle turns towards where the camera points and W/S is the throttle; on a vehicle with a turret the
 * mouse aims and A/D turns the hull. The number keys 1 to 9 switch seats while riding a vehicle with more than one seat.
 *
 * <p>Changes from 1.12.2: the release of a movement key is detected by watching the mappings, not by the key code that was
 * current when the game started (so rebound keys work); the mouse aim ray starts at {@code Player#getEyePosition} instead of the
 * CodeChickenLib corrected head position.
 */
@EventBusSubscriber(modid = AncientWarfareVehicles.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VehicleInputClient {
    private static final String CATEGORY = "keybind.category.awVehicles";

    public static final KeyMapping FORWARD = key(AWVehicleStatics.KEY_VEHICLE_FORWARD, InputConstants.KEY_W);
    public static final KeyMapping REVERSE = key(AWVehicleStatics.KEY_VEHICLE_REVERSE, InputConstants.KEY_S);
    public static final KeyMapping LEFT = key(AWVehicleStatics.KEY_VEHICLE_LEFT, InputConstants.KEY_A);
    public static final KeyMapping RIGHT = key(AWVehicleStatics.KEY_VEHICLE_RIGHT, InputConstants.KEY_D);
    public static final KeyMapping ASCEND_AIM_UP = key(AWVehicleStatics.KEY_VEHICLE_ASCEND_AIM_UP, InputConstants.KEY_R);
    public static final KeyMapping DESCEND_AIM_DOWN = key(AWVehicleStatics.KEY_VEHICLE_DESCEND_AIM_DOWN, InputConstants.KEY_F);
    public static final KeyMapping FIRE = key(AWVehicleStatics.KEY_VEHICLE_FIRE, InputConstants.KEY_SPACE);
    public static final KeyMapping AMMO_PREV = key(AWVehicleStatics.KEY_VEHICLE_AMMO_PREV, InputConstants.KEY_T);
    public static final KeyMapping AMMO_NEXT = key(AWVehicleStatics.KEY_VEHICLE_AMMO_NEXT, InputConstants.KEY_G);
    public static final KeyMapping TURRET_LEFT = key(AWVehicleStatics.KEY_VEHICLE_TURRET_LEFT, InputConstants.KEY_Z);
    public static final KeyMapping TURRET_RIGHT = key(AWVehicleStatics.KEY_VEHICLE_TURRET_RIGHT, InputConstants.KEY_X);
    public static final KeyMapping MOUSE_AIM = key(AWVehicleStatics.KEY_VEHICLE_MOUSE_AIM, InputConstants.KEY_C);
    public static final KeyMapping AMMO_SELECT = key(AWVehicleStatics.KEY_VEHICLE_AMMO_SELECT, InputConstants.KEY_V);
    /** Rework phase 12: lowers and raises the drop bridge of a siege tower (the driver, the ladder and the platform riders). */
    public static final KeyMapping BRIDGE = key(AWVehicleStatics.KEY_VEHICLE_BRIDGE, InputConstants.KEY_B);

    /** Furthest point the mouse aim looks at, in blocks. */
    private static final float MAX_RANGE = 140;

    /** How fast a held movement key ramps the throttle and the key steering up, per tick (so the input is analog, from 0 to 1 in four ticks). */
    private static final float KEY_RAMP = 0.25F;
    /** The angle between the camera and the vehicle at which the mouse steering turns at full rate, and the angle under which it does not turn. */
    private static final float MOUSE_STEER_FULL_ANGLE = 35.0F;
    private static final float MOUSE_STEER_DEAD_ANGLE = 2.0F;

    private static float throttle = 0.0F;
    private static float keySteer = 0.0F;
    @Nullable
    private static VehicleInputData lastSent = null;
    private static int ticksSinceSent = 0;
    /** The climb direction last sent for the ladder of a siege tower: -1, 0 or 1. */
    private static int lastClimb = 0;

    @Nullable
    private static AmmoSelectionOpener ammoSelectionOpener;

    static {
        InputHandler.registerCallBack(MOUSE_AIM, VehicleInputClient::toggleSteerMode);
        InputHandler.registerCallBack(FIRE, new VehicleCallback(VehicleInputClient::handleFireAction));
        InputHandler.registerCallBack(ASCEND_AIM_UP, new VehicleCallback(v -> v.firingHelper.handleAimKeyInput(-1, 0)));
        InputHandler.registerCallBack(DESCEND_AIM_DOWN, new VehicleCallback(v -> v.firingHelper.handleAimKeyInput(1, 0)));
        InputHandler.registerCallBack(TURRET_LEFT, new VehicleCallback(v -> v.firingHelper.handleAimKeyInput(0, -1)));
        InputHandler.registerCallBack(TURRET_RIGHT, new VehicleCallback(v -> v.firingHelper.handleAimKeyInput(0, 1)));
        InputHandler.registerCallBack(AMMO_NEXT, new VehicleCallback(v -> v.ammoHelper.setNextAmmo()));
        InputHandler.registerCallBack(AMMO_PREV, new VehicleCallback(v -> v.ammoHelper.setPreviousAmmo()));
        InputHandler.registerCallBack(AMMO_SELECT, new VehicleCallback(VehicleInputClient::handleAmmoSelectAction));
        InputHandler.registerCallBack(BRIDGE, VehicleInputClient::handleBridgeKey);
    }

    private VehicleInputClient() {}

    private static KeyMapping key(String name, int keyCode) {
        return new KeyMapping(name, VehicleKeyConflictContext.INSTANCE, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }

    /** Opens the ammo selection screen of a vehicle (1.12.2: {@code GUI_VEHICLE_AMMO_SELECTION}). Installed by the vehicle screens slice. */
    @FunctionalInterface
    public interface AmmoSelectionOpener {
        void open(VehicleBase vehicle);
    }

    /** Client only: called once by the code that registers the vehicle screens. */
    public static void setAmmoSelectionOpener(AmmoSelectionOpener opener) {
        ammoSelectionOpener = opener;
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(FORWARD);
        event.register(REVERSE);
        event.register(LEFT);
        event.register(RIGHT);
        event.register(ASCEND_AIM_UP);
        event.register(DESCEND_AIM_DOWN);
        event.register(FIRE);
        event.register(AMMO_PREV);
        event.register(AMMO_NEXT);
        event.register(TURRET_LEFT);
        event.register(TURRET_RIGHT);
        event.register(MOUSE_AIM);
        event.register(AMMO_SELECT);
        event.register(BRIDGE);
    }

    private static void toggleSteerMode() {
        if (VehicleKeyConflictContext.INSTANCE.isActive()) {
            boolean mouse = !AWVehicleStatics.clientSettings.mouseSteer;
            AWVehicleStatics.setMouseSteer(mouse);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable(mouse ? "message.ancientwarfarevehicle.steer_mouse" : "message.ancientwarfarevehicle.steer_classic"), true);
            }
        }
    }

    /** True if the mouse steers this vehicle: the mode is on and the vehicle has no turret that the mouse aims (A/D turn the hull of those). */
    private static boolean isMouseSteering(VehicleBase vehicle) {
        return AWVehicleStatics.clientSettings.mouseSteer && vehicle.isDrivable() && !vehicle.canAimRotate() && vehicle.currentForwardSpeedMax > 0;
    }

    /** The bridge key: asks the siege tower the player rides to lower or raise its bridge (the server decides, see {@code SiegeCommand}). */
    private static void handleBridgeKey() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && VehicleKeyConflictContext.INSTANCE.isActive() && player.getVehicle() instanceof VehicleBase vehicle && vehicle.siege != null) {
            VehicleNetwork.sendToServer(new VehicleEntityPayloads.SiegeCommand(vehicle.getId(), VehicleEntityPayloads.SiegeCommand.BRIDGE));
        }
    }

    /** A rider on the ladder of a siege tower climbs with the forward and reverse keys; the direction is sent when it changes. */
    private static void handleClimbInput(LocalPlayer player, VehicleBase vehicle) {
        int seat = vehicle.seatOf(player);
        int direction = 0;
        if (vehicle.siege != null && seat >= 0 && seat < vehicle.getSeatCount() && vehicle.getSeat(seat).role() == SeatRole.LADDER) {
            direction = VehicleInputData.axis(FORWARD.isDown(), REVERSE.isDown());
        }
        if (direction != lastClimb) {
            lastClimb = direction;
            if (vehicle.siege != null) {
                byte action = direction > 0 ? VehicleEntityPayloads.SiegeCommand.CLIMB_UP
                        : direction < 0 ? VehicleEntityPayloads.SiegeCommand.CLIMB_DOWN : VehicleEntityPayloads.SiegeCommand.CLIMB_STOP;
                VehicleNetwork.sendToServer(new VehicleEntityPayloads.SiegeCommand(vehicle.getId(), action));
            }
        }
    }

    private static void handleAmmoSelectAction(VehicleBase vehicle) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!vehicle.isAmmoLoaded()) {
            player.displayClientMessage(Component.translatable("gui.ancientwarfarevehicles.ammo.no_ammo"), true);
            return;
        }

        if (!vehicle.vehicleType.getValidAmmoTypes().isEmpty()) {
            AmmoSelectionOpener opener = ammoSelectionOpener;
            if (opener != null) {
                opener.open(vehicle);
            }
        }
    }

    private static void handleFireAction(VehicleBase vehicle) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (vehicle.ship != null) {
            // a ship's weapons are its turret mounts, fired from the gunner seats
            ShipGunnerClient.fire(player, vehicle);
            return;
        }
        if (vehicle.vehicleType.isPushed()) {
            return; // the siege works have no weapon
        }
        String configName = vehicle.vehicleType.getConfigName();
        if (!vehicle.isAmmoLoaded() && !(configName.equals("battering_ram") || configName.equals("boat_transport") || configName.equals("chest_cart"))) {
            player.displayClientMessage(Component.translatable("gui.ancientwarfarevehicles.ammo.no_ammo"), true);
        }
        if (vehicle.isAimable()) {
            vehicle.firingHelper.handleFireInput();
        }
    }

    /** The point the player is looking at, from the closest block or entity within {@link #MAX_RANGE}; null if there is none. */
    @Nullable
    static Vec3 getPlayerLookTargetClient(LocalPlayer player, Entity excludedEntity) {
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eyes.add(look.x * MAX_RANGE, look.y * MAX_RANGE, look.z * MAX_RANGE);
        BlockHitResult blockHit = player.level().clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        Entity hitEntity = null;
        double entityDistance = Double.MAX_VALUE;
        Minecraft mc = Minecraft.getInstance();
        Entity viewEntity = mc.getCameraEntity() != null ? mc.getCameraEntity() : player;
        AABB searchBox = viewEntity.getBoundingBox().expandTowards(look.x * MAX_RANGE, look.y * MAX_RANGE, look.z * MAX_RANGE).inflate(1, 1, 1);
        for (Entity candidate : player.level().getEntities(viewEntity, searchBox, e -> e != excludedEntity && e.isPickable())) {
            float borderSize = candidate.getPickRadius();
            Optional<Vec3> intercept = candidate.getBoundingBox().inflate(borderSize).clip(eyes, end);
            if (intercept.isPresent()) {
                double distance = eyes.distanceTo(intercept.get());
                if (distance < entityDistance) {
                    entityDistance = distance;
                    hitEntity = candidate;
                }
            }
        }

        boolean blockWasHit = blockHit.getType() != HitResult.Type.MISS;
        if (hitEntity != null && (!blockWasHit || entityDistance < blockHit.getLocation().distanceTo(eyes))) {
            // aim at 65% of the height of the target
            return new Vec3(hitEntity.getX(), hitEntity.getY() + hitEntity.getBbHeight() * 0.65D, hitEntity.getZ());
        }
        return blockWasHit ? blockHit.getLocation() : null;
    }

    private static void handleMouseAimUpdate(LocalPlayer player, VehicleBase vehicle) {
        if (vehicle.tickCount % 5 == 0) {
            return;
        }
        Vec3 target = getPlayerLookTargetClient(player, vehicle);
        if (target != null) {
            vehicle.firingHelper.handleAimInput(target);
        }
    }

    /** Ramps the value towards the target; a released key (target 0) drops it at once, so the vehicle stops asking for speed when the key is let go. */
    private static float ramp(float value, float target) {
        if (target == 0.0F) {
            return 0.0F;
        }
        return value < target ? Math.min(target, value + KEY_RAMP) : Math.max(target, value - KEY_RAMP);
    }

    private static void handleTickInput(LocalPlayer player, VehicleBase vehicle) {
        if (vehicle.getControllingPassenger() != player) {
            // a gunner or passenger: no movement input (the weapon keys act through their callbacks); on a ladder the movement keys climb
            throttle = 0.0F;
            keySteer = 0.0F;
            lastSent = null;
            handleClimbInput(player, vehicle);
            return;
        }
        lastClimb = 0;
        byte forwardKeys = VehicleInputData.axis(FORWARD.isDown(), REVERSE.isDown());
        byte turnKeys = VehicleInputData.axis(RIGHT.isDown(), LEFT.isDown());
        throttle = ramp(throttle, forwardKeys);
        keySteer = ramp(keySteer, turnKeys);

        boolean mouseSteering = isMouseSteering(vehicle);
        float steer = keySteer;
        if (mouseSteering && turnKeys == 0) {
            boolean moving = throttle != 0.0F || Math.abs(vehicle.moveHelper.forwardMotion) > 0.02F;
            if (moving) {
                // the vehicle's heading in the player's yaw convention is 180 - its yaw
                float diff = Mth.wrapDegrees(player.getYRot() - (180.0F - vehicle.getYRot()));
                if (Math.abs(diff) > MOUSE_STEER_DEAD_ANGLE) {
                    steer = Mth.clamp(diff / MOUSE_STEER_FULL_ANGLE, -1.0F, 1.0F);
                }
            }
        }
        vehicle.moveHelper.riderViewFollows = !mouseSteering;

        VehicleInputData input = new VehicleInputData(
                forwardKeys,
                Math.abs(steer) > 0.02F ? (byte) (steer > 0 ? 1 : -1) : 0,
                VehicleInputData.axis(ASCEND_AIM_UP.isDown(), DESCEND_AIM_DOWN.isDown()),
                VehicleInputData.axis(TURRET_RIGHT.isDown(), TURRET_LEFT.isDown()),
                throttle, steer, player.getYRot(), player.getXRot());
        // the local vehicle uses the input at once (it is predicted), the server gets it when it changed, and once a second so a lost packet does not stick
        vehicle.moveHelper.handleInputData(input.clamped());
        VehicleInputData last = lastSent;
        if (last == null || input.movementDiffers(last) || ++ticksSinceSent >= 20) {
            lastSent = input;
            ticksSinceSent = 0;
            VehicleNetwork.sendToServer(new VehicleEntityPayloads.VehicleInput(vehicle, input));
        }
    }

    /** The number keys 1 to 9 pick a seat of a vehicle with more than one; the clicks are eaten here so they do not also change the hotbar slot. */
    private static void handleSeatKeys(Minecraft mc, LocalPlayer player, VehicleBase vehicle) {
        if (mc.screen != null || vehicle.getSeatCount() < 2) {
            return;
        }
        for (int i = 0; i < 9 && i < vehicle.getSeatCount(); i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                Entity occupant = vehicle.occupantAt(i);
                if (occupant == player) {
                    continue;
                }
                boolean npcHelm = occupant instanceof IVehicleCrew && vehicle.isShip() && vehicle.getSeat(i).role() == SeatRole.DRIVER;
                if (occupant != null && !npcHelm) {
                    player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.seat_taken", i + 1), true);
                    continue;
                }
                player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.seat", i + 1,
                        Component.translatable("ancientwarfarevehicle.seat_role." + vehicle.getSeat(i).role().name().toLowerCase(java.util.Locale.ROOT))), true);
                VehicleNetwork.sendToServer(new VehicleEntityPayloads.SeatSwitch(vehicle.getId(), i));
            }
        }
    }

    /** The per-tick part. A separate class because it listens on the game bus while the key registration above uses the mod bus. */
    @EventBusSubscriber(modid = AncientWarfareVehicles.MOD_ID, value = Dist.CLIENT)
    public static final class TickEvents {
        private TickEvents() {}

        /** Before the tick (and before the key handling of the game, so the number keys can be taken) so the input is in place when the vehicle ticks. */
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null && player.getVehicle() instanceof VehicleBase vehicle) {
                handleSeatKeys(mc, player, vehicle);
                handleTickInput(player, vehicle);
                if (vehicle.ship != null) {
                    ShipGunnerClient.tick(player, vehicle);
                }
                if (AWVehicleStatics.clientSettings.enableMouseAim && vehicle.isWeaponCrew(player)) {
                    handleMouseAimUpdate(player, vehicle);
                }
            } else {
                throttle = 0.0F;
                keySteer = 0.0F;
                lastSent = null;
                lastClimb = 0;
            }
        }
    }
}
