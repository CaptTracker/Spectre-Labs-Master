/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/item/ItemInfoTool.
 */
package io.github.capttracker.ancientwarfare.item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.capttracker.ancientwarfare.init.AwCoreMisc;
import io.github.capttracker.ancientwarfare.network.CoreMiscPayloads;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.parsing.JsonHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A developer tool. Right-click a block to print its state, as JSON (copied to the clipboard), or as a loot
 * table entry; sneak-right-click in the air to cycle the mode; right-click in the air to open the item picker
 * screen, which prints the same for an item (see {@link #printItemInfo}).
 *
 * <p>1.12.2 stored the mode in the item tag {@code mode}; it is the {@code info_tool_mode} component now. The loot
 * entry output is a 1.21 loot entry ({@code minecraft:item} with {@code set_count} and {@code set_components}).
 * The clipboard copy goes through the server to the player's client (1.12.2 used the server's AWT clipboard).
 */
public class InfoToolItem extends Item {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Opens the item picker screen. Installed by the screens slice (1.12.2: GUI_INFO_TOOL); null until then. */
    @Nullable
    private static volatile BiConsumer<ServerPlayer, InteractionHand> guiOpener;

    public static void setGuiOpener(@Nullable BiConsumer<ServerPlayer, InteractionHand> opener) {
        guiOpener = opener;
    }

    public InfoToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(getMode(stack).getDisplayName() + " mode"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = level.getBlockState(hit.getBlockPos());
            switch (getMode(stack)) {
                case INFO -> printSimpleMessage(player, state);
                case JSON -> printAndCopyToClipboard(serverPlayer, BlockTools.serializeToJson(state).toString());
                case LOOT_ENTRY -> printLootEntryJSON(serverPlayer, state.getCloneItemStack(hit, level, hit.getBlockPos(), player));
            }
            return InteractionResultHolder.success(stack);
        }

        if (player.isShiftKeyDown()) {
            Mode mode = getMode(stack).cycle();
            stack.set(AwCoreMisc.INFO_TOOL_MODE.get(), mode);
            player.sendSystemMessage(Component.literal(mode.getDisplayName() + " mode"));
        } else {
            BiConsumer<ServerPlayer, InteractionHand> opener = guiOpener;
            if (opener != null) {
                opener.accept(serverPlayer, hand);
            } else {
                // safe fallback when no picker opener is registered (AwCoreScreens normally sets one)
                player.sendSystemMessage(Component.literal("Info tool: " + getMode(stack).getDisplayName() + " mode. Sneak-use to change mode."));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    private static void printSimpleMessage(Player player, BlockState state) {
        player.sendSystemMessage(Component.literal("Block name: " + BuiltInRegistries.BLOCK.getKey(state.getBlock())));
        if (!state.getValues().isEmpty()) {
            player.sendSystemMessage(Component.literal("Properties:"));
            for (Map.Entry<Property<?>, Comparable<?>> prop : state.getValues().entrySet()) {
                player.sendSystemMessage(Component.literal(prop.getKey().getName() + " : " + prop.getValue()));
            }
        }
    }

    /** Prints information about an item stack, as the mode of the info tool says. Called by the item picker screen's menu. */
    public void printItemInfo(ServerPlayer player, ItemStack infoTool, ItemStack stack) {
        switch (getMode(infoTool)) {
            case INFO -> printSimpleMessage(player, stack);
            case JSON -> printAndCopyToClipboard(player, JsonHelper.serializeItemStack(stack, player.level().registryAccess()).toString());
            case LOOT_ENTRY -> printLootEntryJSON(player, stack);
        }
    }

    private static void printSimpleMessage(Player player, ItemStack stack) {
        player.sendSystemMessage(Component.literal("Item name: " + BuiltInRegistries.ITEM.getKey(stack.getItem())));
        DataComponentPatch patch = stack.getComponentsPatch();
        if (!patch.isEmpty()) {
            JsonObject json = JsonHelper.serializeItemStack(stack, player.level().registryAccess());
            player.sendSystemMessage(Component.literal("Components: " + json.get("components")));
        }
    }

    private static void printAndCopyToClipboard(ServerPlayer player, String json) {
        PacketDistributor.sendToPlayer(player, new CoreMiscPayloads.ClipboardPayload(json));
        player.sendSystemMessage(Component.literal(json));
        player.sendSystemMessage(Component.literal("Copied to clipboard"));
    }

    private static void printLootEntryJSON(ServerPlayer player, ItemStack stack) {
        JsonObject itemJson = JsonHelper.serializeItemStack(stack, player.level().registryAccess());

        JsonArray functions = new JsonArray();
        JsonObject setCount = new JsonObject();
        setCount.addProperty("function", "minecraft:set_count");
        setCount.addProperty("count", 1);
        functions.add(setCount);
        JsonElement components = itemJson.get("components");
        if (components != null) {
            JsonObject setComponents = new JsonObject();
            setComponents.addProperty("function", "minecraft:set_components");
            setComponents.add("components", components);
            functions.add(setComponents);
        }

        JsonObject lootEntry = new JsonObject();
        lootEntry.addProperty("type", "minecraft:item");
        lootEntry.addProperty("name", itemJson.get("name").getAsString());
        lootEntry.addProperty("weight", 1);
        lootEntry.add("functions", functions);
        printAndCopyToClipboard(player, GSON.toJson(lootEntry));
    }

    public static Mode getMode(ItemStack stack) {
        Mode mode = stack.get(AwCoreMisc.INFO_TOOL_MODE.get());
        return mode == null ? Mode.INFO : mode;
    }

    public enum Mode implements StringRepresentable {
        INFO("Info"),
        JSON("JSON"),
        LOOT_ENTRY("Loot Entry");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        public static final StreamCodec<ByteBuf, Mode> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Mode::ordinal);

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        public Mode cycle() {
            return switch (this) {
                case INFO -> JSON;
                case JSON -> LOOT_ENTRY;
                case LOOT_ENTRY -> INFO;
            };
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
