/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerInfoTool.
 */
package io.github.capttracker.ancientwarfare.menu;

import io.github.capttracker.ancientwarfare.init.AwCoreScreens;
import io.github.capttracker.ancientwarfare.item.InfoToolItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * The info tool's item picker: just the player's inventory. Clicking a slot asks the server to print that stack with the
 * mode of the info tool in the opening hand (see {@link InfoToolItem#printItemInfo}). The slots cannot be used to
 * move items, which the original screen also never allowed.
 */
public class InfoToolMenu extends AwMenu {
    private final InteractionHand hand;

    /** Client side: the hand comes from the buffer written by {@link #open}. */
    public InfoToolMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readEnum(InteractionHand.class));
    }

    public InfoToolMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(AwCoreScreens.INFO_TOOL_MENU.get(), containerId, inventory);
        this.hand = hand;
        addPlayerSlots(inventory, 8, 8, 4);
    }

    /** Opens the picker (1.12.2: GUI_INFO_TOOL, 57). Installed with {@code InfoToolItem.setGuiOpener}. */
    public static void open(ServerPlayer player, InteractionHand hand) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new InfoToolMenu(id, inventory, hand),
                Component.translatable("item.ancientwarfare.info_tool")), buf -> buf.writeEnum(hand));
    }

    /** Client side: asks the server to print the stack in a menu slot. */
    public void printItemInfo(int slotId) {
        if (slotId >= 0 && slotId < slots.size()) {
            sendDataToServer("printItem", IntTag.valueOf(slotId));
        }
    }

    @Override
    protected void handlePacketData(CompoundTag data) {
        // Server side; the client may send anything, so the slot and the tool in hand are checked.
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer) || !data.contains("printItem", Tag.TAG_INT)) {
            return;
        }
        int slotId = data.getInt("printItem");
        ItemStack tool = player.getItemInHand(hand);
        if (slotId < 0 || slotId >= slots.size() || !(tool.getItem() instanceof InfoToolItem infoTool)) {
            return;
        }
        ItemStack stack = slots.get(slotId).getItem();
        if (!stack.isEmpty()) {
            infoTool.printItemInfo(serverPlayer, tool, stack);
        }
    }

    /** No item moves in this menu. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof InfoToolItem;
    }
}
