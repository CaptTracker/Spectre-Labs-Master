/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the grappling hook.
 */
package io.github.capttracker.ancientwarfarevehicle.item;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.GrappleHook;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The grappling hook (rework phase 11). Used while standing or riding on a ship and looking at another ship within {@code grapple_range} blocks, it throws a hook
 * with a line at that ship ({@link GrappleHook}), which pulls the two ships alongside. Used while sneaking it lets go of the lines the player holds (or, for the owner
 * of the ship, every line of the ship). Each throw uses one point of the hook's durability.
 */
public class GrapplingHookItem extends Item {
    /** How near the line of sight must pass to a hull cell (the cosine of the angle) for the ship to count as aimed at. */
    private static final double AIM_COSINE = 0.975D;

    public GrapplingHookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        VehicleBase own = ShipHelper.shipOf(player);
        if (own == null || !own.isShip()) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.grapple.not_aboard"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {
            int released = 0;
            boolean owner = own.getOwner().equals(io.github.capttracker.ancientwarfare.owner.Owner.EMPTY) || own.isOwner(player);
            for (GrappleHook hook : List.copyOf(own.ship.lines())) {
                if (player.getUUID().equals(hook.holder()) || owner) {
                    hook.release(true);
                    released++;
                }
            }
            player.displayClientMessage(Component.translatable(released > 0 ? "message.ancientwarfarevehicle.grapple.released" : "message.ancientwarfarevehicle.grapple.no_line"), true);
            return InteractionResultHolder.success(stack);
        }
        VehicleBase target = aimedShip(player, own);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.grapple.no_target", VehicleSettings.grappleRange), true);
            return InteractionResultHolder.fail(stack);
        }
        GrappleHook.Result result = GrappleHook.tryThrow(own, target, player);
        player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.grapple." + result.name().toLowerCase(java.util.Locale.ROOT)), true);
        if (result != GrappleHook.Result.OK) {
            return InteractionResultHolder.fail(stack);
        }
        stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.success(stack);
    }

    /** The ship (other than {@code own}) whose hull the player's line of sight passes nearest to, within the range and the aim cone; null if none. */
    @Nullable
    public static VehicleBase aimedShip(Player player, VehicleBase own) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = VehicleSettings.grappleRange;
        VehicleBase best = null;
        double bestCosine = AIM_COSINE;
        List<VehicleBase> near = player.level().getEntitiesOfClass(VehicleBase.class, player.getBoundingBox().inflate(range + 12.0D), v -> v != own && GrappleHook.usable(v));
        for (VehicleBase other : near) {
            for (ShipSpec.Cell cell : other.ship.spec().cells()) {
                Vec3 at = other.ship.toWorld(cell.forward(), cell.horizontal(), other.ship.spec().deckTop());
                Vec3 to = at.subtract(eye);
                double distance = to.length();
                if (distance > range + 4.0D || distance < 1.0E-3D) {
                    continue;
                }
                double cosine = to.dot(look) / distance;
                if (cosine > bestCosine) {
                    bestCosine = cosine;
                    best = other;
                }
            }
        }
        return best;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ancientwarfarevehicle.grappling_hook.tooltip"));
        tooltip.add(Component.translatable("item.ancientwarfarevehicle.grappling_hook.tooltip2"));
    }
}
