/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the meal shop of an innkeeper.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfare.api.IMarketStall.TradeResult;
import io.github.capttracker.ancientwarfarenpc.entity.NpcInnkeeper;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.inn.InnApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The meal shop of an innkeeper at an open inn: each meal in the pantry with the price this player pays and a Buy button. Anyone may use it. The client only
 * asks ({@code buy} with the pantry slot); the server finds the inn of the keeper, checks everything again and does the whole deal through
 * {@link InnApi#buyMeal}, then sends the list again with the result code.
 */
public class InnShopMenu extends NpcMenuBase<NpcInnkeeper> {
    private static final String SHOP_TAG = "shop";
    private static final String BUY_TAG = "buy";

    /** A meal as the client shows it: the pantry slot, the item, how many the pantry holds. */
    public record Meal(int slot, ItemStack item, int count) {}

    public final List<Meal> meals = new ArrayList<>();
    public ItemStack price = ItemStack.EMPTY;
    public boolean open = false;
    public boolean refused = false;
    /** The {@link TradeResult} of the last purchase on this screen, or -1. */
    public int lastResult = -1;

    /** Client side: the entity id of the NPC comes from the buffer written by {@link NpcMenuOpener}. */
    public InnShopMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcInnkeeper.class));
    }

    public InnShopMenu(int containerId, Inventory inventory, @Nullable NpcInnkeeper npc) {
        super(AwNpcMenus.INN_SHOP_MENU.get(), containerId, inventory, npc);
        addPlayerSlots(inventory, 8, 240 - 4 - 8 - 4 * 18, 4);
    }

    /** Anyone may buy. */
    @Override
    protected boolean mayUse(Player who) {
        return true;
    }

    @Override
    protected void sendInitialState() {
        sendShop(-1);
    }

    private void sendShop(int result) {
        CompoundTag tag = new CompoundTag();
        CompoundTag shop = new CompoundTag();
        Optional<IInn> found = npc == null ? Optional.empty() : InnApi.innOf(npc).filter(IInn::isOpen);
        shop.putBoolean("open", found.isPresent());
        shop.putInt("result", result);
        ListTag list = new ListTag();
        if (found.isPresent() && player.level() instanceof ServerLevel level) {
            IInn inn = found.get();
            ItemStack quoted = InnApi.quote(level, player, inn);
            shop.putBoolean("refused", quoted.isEmpty());
            if (!quoted.isEmpty()) {
                shop.put("price", quoted.save(registries()));
            }
            IItemHandler pantry = inn.getPantry();
            for (int slot = 0; slot < pantry.getSlots(); slot++) {
                ItemStack stack = pantry.getStackInSlot(slot);
                if (IInn.isMeal(stack)) {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt("slot", slot);
                    entry.put("item", stack.copyWithCount(1).save(registries()));
                    entry.putInt("count", stack.getCount());
                    list.add(entry);
                }
            }
        }
        shop.put("meals", list);
        tag.put(SHOP_TAG, shop);
        sendDataToClient(tag);
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            if (tag.contains(SHOP_TAG, Tag.TAG_COMPOUND)) {
                readShop(tag.getCompound(SHOP_TAG));
            }
            refreshGui();
            return;
        }
        if (!isRequestAllowed() || npc == null || !tag.contains(BUY_TAG, Tag.TAG_INT)) {
            return;
        }
        int result = InnApi.innOf(npc)
                .map(inn -> InnApi.buyMeal(player, npc, inn, tag.getInt(BUY_TAG)))
                .orElse(TradeResult.CLOSED).ordinal();
        sendShop(result);
    }

    private void readShop(CompoundTag shop) {
        open = shop.getBoolean("open");
        refused = shop.getBoolean("refused");
        lastResult = shop.contains("result") ? shop.getInt("result") : -1;
        price = shop.contains("price") ? ItemStack.parseOptional(registries(), shop.getCompound("price")) : ItemStack.EMPTY;
        meals.clear();
        ListTag list = shop.getList("meals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            meals.add(new Meal(entry.getInt("slot"), ItemStack.parseOptional(registries(), entry.getCompound("item")), entry.getInt("count")));
        }
    }

    /** Client side: asks the server to sell one meal from this pantry slot. */
    public void buy(int pantrySlot) {
        sendDataToServer(BUY_TAG, IntTag.valueOf(pantrySlot));
    }
}
