/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): the tool that marks building plots.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The building zone tool. Use it on a block inside the land of one of your settlements for the first corner of a plot and on another block for the opposite
 * corner: the rectangle between them (the full height) becomes a building plot, saved in the settlement. Builders only build inside plots. Sneak and use on a
 * block inside a plot removes that plot (builds that are only planned or queued on it are cancelled; a plot with a build under construction stays). Sneak
 * and use in the air drops a first corner. While the tool is in a hand the owner sees the plots and the planned buildings of the settlements nearby
 * (drawn by the client from {@link BuildingNetwork.PlotsPayload}).
 */
public class BuildingZoneItem extends Item {
    private static final String KEY = "zone_tool";

    public BuildingZoneItem(Properties properties) {
        super(properties);
    }

    /** The first corner marked on a tool (null when none) and the dimension it is in. */
    public record Selection(@Nullable ResourceLocation dimension, @Nullable BlockPos corner) {}

    public static Selection read(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(KEY)) {
            return new Selection(null, null);
        }
        CompoundTag tag = root.getCompound(KEY);
        int[] v = tag.getIntArray("a");
        return new Selection(ResourceLocation.tryParse(tag.getString("dim")), v.length == 3 ? new BlockPos(v[0], v[1], v[2]) : null);
    }

    private static void write(ItemStack stack, @Nullable ResourceLocation dimension, @Nullable BlockPos corner) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            if (dimension == null || corner == null) {
                root.remove(KEY);
                return;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", dimension.toString());
            tag.putIntArray("a", new int[]{corner.getX(), corner.getY(), corner.getZ()});
            root.put(KEY, tag);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ancientwarfarenpc.building_zone_tool.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.ancientwarfarenpc.building_zone_tool.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.sidedSuccess(true);
        }
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            removeOrClear(serverPlayer, level, stack, context.getClickedPos());
        } else {
            click(serverPlayer, level, stack, context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player.isShiftKeyDown() && read(stack).corner() != null) {
            write(stack, null, null);
            serverPlayer.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.cleared"), true);
        } else {
            serverPlayer.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.hint"), true);
        }
        return InteractionResultHolder.success(stack);
    }

    @Nullable
    private static Settlement managedSettlement(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Settlement settlement = SettlementApi.findByPosition(level, pos).orElse(null);
        if (settlement == null || !SettlementApi.mayManage(settlement, player)) {
            return null;
        }
        return settlement;
    }

    private static void removeOrClear(ServerPlayer player, ServerLevel level, ItemStack stack, BlockPos pos) {
        Settlement settlement = managedSettlement(player, level, pos);
        BuildingPlot plot = settlement == null ? null : settlement.getBuilding().plotAt(pos.getX(), pos.getZ()).orElse(null);
        if (plot == null) {
            write(stack, null, null);
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.cleared"), true);
            return;
        }
        if (settlement.getBuilding().removePlot(plot.getId())) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.removed", plot.getNumber()), true);
        } else {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.remove_busy", plot.getNumber()), true);
        }
    }

    private static void click(ServerPlayer player, ServerLevel level, ItemStack stack, BlockPos pos) {
        Settlement settlement = managedSettlement(player, level, pos);
        if (settlement == null) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.outside"), true);
            return;
        }
        Selection selection = read(stack);
        ResourceLocation dim = level.dimension().location();
        if (selection.corner() == null || !dim.equals(selection.dimension())) {
            write(stack, dim, pos.immutable());
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.first", pos.toShortString()), true);
            return;
        }
        BlockPos a = selection.corner();
        int minX = Math.min(a.getX(), pos.getX());
        int maxX = Math.max(a.getX(), pos.getX());
        int minZ = Math.min(a.getZ(), pos.getZ());
        int maxZ = Math.max(a.getZ(), pos.getZ());
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        SettlementBuilding building = settlement.getBuilding();
        if (width < SettlementConfig.plotMinSide || depth < SettlementConfig.plotMinSide) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.too_small", width, depth, SettlementConfig.plotMinSide), true);
            return;
        }
        if (width > SettlementConfig.plotMaxSide || depth > SettlementConfig.plotMaxSide) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.too_big", width, depth, SettlementConfig.plotMaxSide), true);
            return;
        }
        if (!settlement.contains(a) || !settlement.contains(minX, minZ) || !settlement.contains(maxX, minZ) || !settlement.contains(minX, maxZ)
                || !settlement.contains(maxX, maxZ) || !settlement.contains((minX + maxX) / 2.0D + 0.5D, (minZ + maxZ) / 2.0D + 0.5D)) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.partly_outside"), true);
            return;
        }
        for (BuildingPlot other : building.getPlots()) {
            if (other.intersects(minX, minZ, maxX, maxZ)) {
                player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.overlap", other.getNumber()), true);
                return;
            }
        }
        if (building.getPlots().size() >= SettlementConfig.plotsMaxPerSettlement) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.limit", SettlementConfig.plotsMaxPerSettlement), true);
            return;
        }
        BuildingPlot plot = building.addPlot(minX, minZ, maxX, maxZ, a.getY());
        write(stack, null, null);
        player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.plot.created", plot.getNumber(), width, depth, settlement.getName()), true);
    }
}
