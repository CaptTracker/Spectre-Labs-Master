/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/render/RenderOverlayAdvanced.
 */
package io.github.capttracker.ancientwarfarevehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoHwachaRocket;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleTypeIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The aim preview drawn in the world while riding a vehicle (the {@code renderAdvOverlay} client option): a white line 20 blocks
 * ahead of the vehicle, the ballistic path of the selected ammo (or the flight path of a rocket, with burn time and acceleration)
 * from the missile offset down to the ground, and for the battering ram the three boxes it hits.
 *
 * <p>1.12.2 drew this with immediate mode {@code GL_LINES} in {@code RenderWorldLastEvent}; this is a {@link RenderLevelStageEvent}
 * after the particles with the vanilla lines render type. The path is capped at {@link #MAX_STEPS} segments (the original looped
 * until the path met the floor, which never ends for a zero launch speed) and every access is null-safe.
 *
 * <p>Game-bus listener, client only.
 */
@EventBusSubscriber(modid = AncientWarfareVehicles.MOD_ID, value = Dist.CLIENT)
public final class VehicleAimOverlay {
    /** The most segments of one path. A catapult at full power lands after about 130 steps, a rocket after a few hundred. */
    public static final int MAX_STEPS = 512;

    private static final double GRAVITY = 9.81d * 0.05d * 0.05d;

    private VehicleAimOverlay() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !AWVehicleStatics.clientSettings.renderAdvOverlay) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) {
            return;
        }
        if (!(mc.player.getVehicle() instanceof VehicleBase vehicle) || event.getPoseStack() == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z); // everything below is in world coordinates
        try {
            if (vehicle.vehicleType.getGlobalVehicleType() == VehicleTypeIds.BATTERING_RAM) {
                renderBatteringRam(vehicle, poseStack, lines, partialTick);
            } else {
                renderTrajectory(vehicle, poseStack, lines, partialTick);
            }
        } finally {
            poseStack.popPose();
            buffers.endBatch(RenderType.lines());
        }
    }

    private static void renderTrajectory(VehicleBase vehicle, PoseStack poseStack, VertexConsumer lines, float partialTick) {
        Vec3 origin = vehicle.getPosition(partialTick);
        float rotationSpeed = vehicle.moveHelper.getRotationSpeed();
        drawStraightLine(poseStack, lines, origin, vehicle.getYRot() + partialTick * rotationSpeed);

        double speed = vehicle.localLaunchPower * 0.05d;
        if (speed <= 0) {
            return;
        }
        double angle = 90 - vehicle.localTurretPitch - vehicle.getXRot();
        double yaw = vehicle.localTurretRotation + partialTick * rotationSpeed;
        double vH = -Trig.sinDegrees((float) angle) * speed;
        Vec3 acceleration = new Vec3(Trig.sinDegrees((float) yaw) * vH, Trig.cosDegrees((float) angle) * speed, Trig.cosDegrees((float) yaw) * vH);

        IAmmo ammo = vehicle.ammoHelper.getCurrentAmmoType();
        boolean rocket = ammo != null && ammo.isRocket();
        drawFlightPath(vehicle, poseStack, lines, origin, speed, acceleration, rocket);
    }

    private static void drawStraightLine(PoseStack poseStack, VertexConsumer lines, Vec3 origin, float yaw) {
        double x2 = origin.x - 20 * Trig.sinDegrees(yaw);
        double z2 = origin.z - 20 * Trig.cosDegrees(yaw);
        line(poseStack, lines, origin.x, origin.y + 0.12d, origin.z, x2, origin.y + 0.12d, z2, 1.f, 1.f, 1.f, 0.6f);
    }

    /**
     * The path of a missile fired now: the normal ballistic arc, or for a rocket the arc with the burn phase (it accelerates
     * along the launch direction for {@code speed * 20 * BURN_TIME_FACTOR} steps, then falls).
     */
    private static void drawFlightPath(VehicleBase vehicle, PoseStack poseStack, VertexConsumer lines, Vec3 origin, double speed,
                                       Vec3 launchVector, boolean rocket) {
        Vec3 offset = vehicle.getMissileOffset();
        double x = origin.x + offset.x;
        double y = origin.y + offset.y;
        double z = origin.z + offset.z;
        double floorY = origin.y;

        VehicleMovementType movement = vehicle.vehicleType.getMovementType();
        Vec3 step = launchVector;
        if (movement == VehicleMovementType.AIR1 || movement == VehicleMovementType.AIR2) {
            step = step.add(vehicle.getDeltaMovement());
            floorY = 0; // 1.12.2 used world y 0 as the floor of air vehicles
        }

        double accX = 0;
        double accY = 0;
        double accZ = 0;
        int burnTime = 0;
        if (rocket) {
            accX = step.x / speed * AmmoHwachaRocket.ACCELERATION_FACTOR;
            accY = step.y / speed * AmmoHwachaRocket.ACCELERATION_FACTOR;
            accZ = step.z / speed * AmmoHwachaRocket.ACCELERATION_FACTOR;
            step = new Vec3(accX, accY, accZ);
            burnTime = (int) (speed * 20.f * AmmoHwachaRocket.BURN_TIME_FACTOR);
        }

        for (int i = 0; i < MAX_STEPS && y >= floorY; i++) {
            double nx = x + step.x;
            double ny = y + step.y;
            double nz = z + step.z;
            line(poseStack, lines, x, y, z, nx, ny, nz, 1.f, 0.4f, 0.4f, 0.4f);
            x = nx;
            y = ny;
            z = nz;
            if (rocket && burnTime > 0) {
                burnTime--;
                step = step.add(accX, accY, accZ);
            } else {
                step = step.add(0, -GRAVITY, 0);
            }
        }
    }

    /** The ram: the straight line and the three crossing boxes (3 wide, 3 deep, 3 high) around the block in front of it. */
    private static void renderBatteringRam(VehicleBase vehicle, PoseStack poseStack, VertexConsumer lines, float partialTick) {
        Vec3 origin = vehicle.getPosition(partialTick);
        drawStraightLine(poseStack, lines, origin, vehicle.getYRot());

        Vec3 offset = vehicle.getMissileOffset();
        BlockPos hit = BlockPos.containing(origin.x + offset.x, origin.y + offset.y, origin.z + offset.z);
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(hit.getX() - 1, hit.getY(), hit.getZ(), hit.getX() + 2, hit.getY() + 1, hit.getZ() + 1), 1.f, 0.2f, 0.2f, 1.f);
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(hit.getX(), hit.getY(), hit.getZ() - 1, hit.getX() + 1, hit.getY() + 1, hit.getZ() + 2), 1.f, 0.2f, 0.2f, 1.f);
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(hit.getX(), hit.getY() - 1, hit.getZ(), hit.getX() + 1, hit.getY() + 2, hit.getZ() + 1), 1.f, 0.2f, 0.2f, 1.f);
    }

    /** One segment of the vanilla lines render type: the vertex normal is the direction of the line, which its shader needs. */
    private static void line(PoseStack poseStack, VertexConsumer lines, double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6f || Float.isNaN(length)) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        PoseStack.Pose pose = poseStack.last();
        lines.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(pose, dx, dy, dz);
        lines.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(pose, dx, dy, dz);
    }
}
