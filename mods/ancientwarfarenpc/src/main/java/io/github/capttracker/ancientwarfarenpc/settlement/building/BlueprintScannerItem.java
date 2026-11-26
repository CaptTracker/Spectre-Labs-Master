/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import com.google.gson.JsonObject;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The blueprint scanner. Use it on a block for the first corner, on a block for the opposite corner, and on a block at the entrance for the front
 * (the side of the box nearest to that block). That opens a screen to name the blueprint, pick its category and style and mark a worksite block.
 * Sneak and use clears the selection; use again (on a block or in the air) after the third click to reopen the screen. The selection is kept in
 * the item (so the client can draw it).
 */
public class BlueprintScannerItem extends Item {
    private static final String KEY = "blueprint_scan";

    public BlueprintScannerItem(Properties properties) {
        super(properties);
    }

    /** The corners and entrance marked on a scanner. */
    public record Selection(@Nullable ResourceLocation dimension, @Nullable BlockPos a, @Nullable BlockPos b, @Nullable BlockPos front,
                           @Nullable Direction frontDir) {
        public boolean complete() {
            return a != null && b != null && front != null && frontDir != null;
        }

        public BlockPos min() {
            BlockPos second = b == null ? a : b;
            return new BlockPos(Math.min(a.getX(), second.getX()), Math.min(a.getY(), second.getY()), Math.min(a.getZ(), second.getZ()));
        }

        public BlockPos max() {
            BlockPos second = b == null ? a : b;
            return new BlockPos(Math.max(a.getX(), second.getX()), Math.max(a.getY(), second.getY()), Math.max(a.getZ(), second.getZ()));
        }

    }

    public static Selection read(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(KEY)) {
            return new Selection(null, null, null, null, null);
        }
        CompoundTag tag = root.getCompound(KEY);
        return new Selection(ResourceLocation.tryParse(tag.getString("dim")), pos(tag, "a"), pos(tag, "b"), pos(tag, "f"), Direction.byName(tag.getString("fd")));
    }

    @Nullable
    private static BlockPos pos(CompoundTag tag, String key) {
        int[] v = tag.getIntArray(key);
        return v.length == 3 ? new BlockPos(v[0], v[1], v[2]) : null;
    }

    private static void write(ItemStack stack, @Nullable Selection selection) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            if (selection == null || selection.dimension() == null) {
                root.remove(KEY);
                return;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", selection.dimension().toString());
            put(tag, "a", selection.a());
            put(tag, "b", selection.b());
            put(tag, "f", selection.front());
            if (selection.frontDir() != null) {
                tag.putString("fd", selection.frontDir().getName());
            }
            root.put(KEY, tag);
        });
    }

    private static void put(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) {
            tag.putIntArray(key, new int[]{pos.getX(), pos.getY(), pos.getZ()});
        }
    }

    /** The side of the box (min to max) that a block is nearest to (a block outside the box counts by how far outside it is). */
    public static Direction frontOf(BlockPos min, BlockPos max, BlockPos click, Direction clickedFace) {
        int north = click.getZ() - min.getZ();
        int south = max.getZ() - click.getZ();
        int west = click.getX() - min.getX();
        int east = max.getX() - click.getX();
        int best = Math.min(Math.min(north, south), Math.min(west, east));
        if (clickedFace.getAxis().isHorizontal()) {
            int face = switch (clickedFace) {
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                default -> east;
            };
            if (face == best) {
                return clickedFace;
            }
        }
        if (north == best) {
            return Direction.NORTH;
        }
        if (south == best) {
            return Direction.SOUTH;
        }
        return west == best ? Direction.WEST : Direction.EAST;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ancientwarfarenpc.blueprint_scanner.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.ancientwarfarenpc.blueprint_scanner.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
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
            clear(serverPlayer, stack);
            return InteractionResult.SUCCESS;
        }
        click(serverPlayer, level, stack, context.getClickedPos(), context.getClickedFace());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            clear(serverPlayer, stack);
        } else if (read(stack).complete()) {
            openScreen(serverPlayer, serverLevel, read(stack));
        } else {
            serverPlayer.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.hint"), true);
        }
        return InteractionResultHolder.success(stack);
    }

    private static void clear(ServerPlayer player, ItemStack stack) {
        write(stack, null);
        player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.cleared"), true);
    }

    private static void click(ServerPlayer player, ServerLevel level, ItemStack stack, BlockPos pos, Direction face) {
        Selection current = read(stack);
        ResourceLocation dim = level.dimension().location();
        if (current.dimension() == null || !current.dimension().equals(dim) || current.complete()) {
            current = new Selection(dim, null, null, null, null);
        }
        if (current.a() == null) {
            write(stack, new Selection(dim, pos.immutable(), null, null, null));
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.first", pos.toShortString()), true);
        } else if (current.b() == null) {
            Selection next = new Selection(dim, current.a(), pos.immutable(), null, null);
            int sx = next.max().getX() - next.min().getX() + 1;
            int sy = next.max().getY() - next.min().getY() + 1;
            int sz = next.max().getZ() - next.min().getZ() + 1;
            if (sx > SettlementConfig.blueprintMaxWidth || sz > SettlementConfig.blueprintMaxWidth || sy > SettlementConfig.blueprintMaxHeight) {
                player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.too_big", sx, sy, sz,
                        SettlementConfig.blueprintMaxWidth, SettlementConfig.blueprintMaxHeight, SettlementConfig.blueprintMaxWidth), true);
                return;
            }
            write(stack, next);
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.second", sx, sy, sz), true);
        } else {
            Selection box = new Selection(dim, current.a(), current.b(), null, null);
            Selection next = new Selection(dim, current.a(), current.b(), pos.immutable(), frontOf(box.min(), box.max(), pos, face));
            write(stack, next);
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.third",
                    Component.translatable("direction.ancientwarfarenpc." + next.frontDir().getName())), true);
            openScreen(player, level, next);
        }
    }

    private static void openScreen(ServerPlayer player, ServerLevel level, Selection selection) {
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        if (!BlueprintCapture.isLoaded(level, min, max)) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.unloaded"), true);
            return;
        }
        BlueprintCapture.Info info = BlueprintCapture.inspect(level, min, max);
        Direction front = selection.frontDir();
        PacketDistributor.sendToPlayer(player, new DesignNetwork.OpenScannerPayload(min, max, front, info.beds(), info.storage(), info.worksiteBlocks()));
    }

    /** Called from the screen's answer: captures the scanned box of the held scanner and saves it as a blueprint of the player. */
    static void save(ServerPlayer player, String name, DesignCategory category, DesignStyle style, @Nullable ResourceLocation worksite) {
        ItemStack stack = player.getMainHandItem().getItem() instanceof BlueprintScannerItem ? player.getMainHandItem() : player.getOffhandItem();
        if (!(stack.getItem() instanceof BlueprintScannerItem) || !(player.level() instanceof ServerLevel level)) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.no_scanner"), true);
            return;
        }
        Selection selection = read(stack);
        if (!selection.complete() || !level.dimension().location().equals(selection.dimension())) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.no_selection"), true);
            return;
        }
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        int sx = max.getX() - min.getX() + 1;
        int sy = max.getY() - min.getY() + 1;
        int sz = max.getZ() - min.getZ() + 1;
        if (sx > SettlementConfig.blueprintMaxWidth || sz > SettlementConfig.blueprintMaxWidth || sy > SettlementConfig.blueprintMaxHeight) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.too_big", sx, sy, sz,
                    SettlementConfig.blueprintMaxWidth, SettlementConfig.blueprintMaxHeight, SettlementConfig.blueprintMaxWidth), true);
            return;
        }
        if (!BlueprintCapture.isLoaded(level, min, max)) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.unloaded"), true);
            return;
        }
        try {
            Direction front = selection.frontDir();
            JsonObject json = BlueprintCapture.capture(level, min, max, front, name, category, style, worksite);
            BuildingDesign design = BlueprintStore.save(player.server, player.getUUID(), name, json);
            write(stack, null);
            BuildingDesigns.sync(player);
            player.sendSystemMessage(Component.translatable("message.ancientwarfarenpc.blueprint.saved", design.getName(), sx, sy, sz,
                    design.getBeds(), design.getStorage()));
        } catch (DesignException e) {
            player.sendSystemMessage(Component.translatable("message.ancientwarfarenpc.blueprint.failed", e.getMessage()));
        }
    }
}
