/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/item/ItemSpawner.
 */
package io.github.capttracker.ancientwarfarevehicle.item;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.IVehicleType;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleType;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleTypeIds;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Places one vehicle. 1.12.2 had a single item {@code spawner} whose damage value was the vehicle type index and whose
 * NBT {@code spawnData:{level, health}} held the material level and saved health. Now every one of the 21 types has its own
 * spawner item ({@code <config name>_spawner}, see {@link VehicleTypeIds}) and the level and health are in the
 * {@link VehicleSpawnData} component ({@link AwVehicles#SPAWN_DATA}).
 */
public class VehicleSpawnerItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final int vehicleTypeId;

    public VehicleSpawnerItem(Properties properties, int vehicleTypeId) {
        super(properties);
        this.vehicleTypeId = vehicleTypeId;
    }

    /** Global vehicle type index (0 to 20) of the vehicle this item places. */
    public int getVehicleTypeId() {
        return vehicleTypeId;
    }

    public static VehicleSpawnData getSpawnData(ItemStack stack) {
        return stack.getOrDefault(AwVehicles.SPAWN_DATA, VehicleSpawnData.DEFAULT);
    }

    /** A spawner stack for the type with the given material level (1.12.2: {@code IVehicleType#getStackForLevel}). */
    public static ItemStack createStack(int vehicleTypeId, int level) {
        return createStack(vehicleTypeId, new VehicleSpawnData(level));
    }

    /** A spawner stack that carries the health of a packed-up vehicle (1.12.2: {@code VehicleBase#getItemForVehicle}). */
    public static ItemStack createStack(int vehicleTypeId, int level, float health) {
        return createStack(vehicleTypeId, new VehicleSpawnData(level, health));
    }

    public static ItemStack createStack(int vehicleTypeId, VehicleSpawnData data) {
        if (!VehicleTypeIds.isValid(vehicleTypeId)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(AwVehicles.SPAWNERS.get(vehicleTypeId).get());
        stack.set(AwVehicles.SPAWN_DATA, data);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (stack.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        return trySpawnVehicle(level, player, stack) ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }

    /** Server side. The 1.12.2 method returned the inverted flag (true meant failure). */
    private boolean trySpawnVehicle(Level level, Player player, ItemStack stack) {
        VehicleSpawnData data = getSpawnData(stack);
        Optional<VehicleBase> created = VehicleType.getVehicleForType(level, vehicleTypeId, data.level());
        if (created.isEmpty()) {
            // the type is disabled in the config
            return false;
        }
        VehicleBase vehicle = created.get();
        data.health().ifPresent(vehicle::setHealth);

        // liquids count as a hit, so boats can be placed on water
        BlockHitResult rayTrace = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (rayTrace.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        spawnVehicle(level, player, vehicle, rayTrace);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return true;
    }

    private static void spawnVehicle(Level level, Player player, VehicleBase vehicle, BlockHitResult rayTrace) {
        Vec3 hitVec = rayTrace.getLocation();
        Direction side = rayTrace.getDirection();
        if (side.getAxis().isHorizontal()) {
            float halfWidth = vehicle.getBbWidth() / 2f;
            hitVec = hitVec.add(side.getStepX() * halfWidth, 0, side.getStepZ() * halfWidth);
        }

        vehicle.setPos(hitVec.x, hitVec.y, hitVec.z);
        float yaw = -player.getYRot() + 180;
        vehicle.setYRot(yaw);
        vehicle.yRotO = yaw;
        vehicle.localTurretDestRot = vehicle.localTurretRotation = vehicle.localTurretRotationHome = yaw;
        if (AWVehicleStatics.generalSettings.useVehicleSetupTime) {
            vehicle.setSetupState(true, 100);
        }
        level.addFreshEntity(vehicle);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        VehicleSpawnData data = getSpawnData(stack);
        tooltip.add(Component.translatable("tooltip.ancientwarfarevehicle.material_level", data.level()));
        data.health().ifPresent(health -> tooltip.add(Component.translatable("tooltip.ancientwarfarevehicle.vehicle_health", health)));

        // The original built a whole vehicle entity to read these lines; the type descriptor has them.
        @Nullable IVehicleType type = VehicleRegistry.getVehicleType(vehicleTypeId);
        if (type == null) {
            LOGGER.debug("No vehicle type with index {} for a spawner tooltip", vehicleTypeId);
            return;
        }
        // 1.12.2 collected the lines in a set (de-duplicated, unordered); the order of the list is kept here
        type.getDisplayTooltip().stream().distinct().forEach(key -> tooltip.add(Component.translatable(key)));
    }
}
