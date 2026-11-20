/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the shared shape of npc/container/ContainerWorkOrder, ContainerUpkeepOrder, ContainerCombatOrder,
 * ContainerRoutingOrder and ContainerTradeOrder (an item bound menu that edits the order stored in the held item).
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfare.menu.AwMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Base of the five order menus. The order slip travels as the hand that holds it (1.12.2: the held item was found with
 * {@code EntityTools.getHandHoldingItem}), so a client menu reads the order from the stack in that hand and the server menu from the same hand.
 * The screen edits {@link #order}, a working copy, and sends the whole order as one NBT (the order's codec) when it closes, as the original did.
 *
 * <p>Changes from 1.12.2, where the server took whatever order the client sent and wrote it into the held item when the menu closed:
 * <ul>
 *   <li>The server checks the order against the order in the stack ({@link #sanitize}): the screens only reorder, remove and change settings
 *       of points the slip already has, so a point that is not in the stored order is refused (nobody can invent positions), and every number is
 *       limited. An order that fails is ignored.</li>
 *   <li>The order is written into the stack when the message arrives, not when the menu closes (the screen sends it just before it closes, so
 *       the result is the same, and nothing is lost if the player leaves).</li>
 *   <li>The menu is only valid while the stack in the hand is still that kind of order item.</li>
 * </ul>
 *
 * @param <O> the order class
 */
public abstract class OrderMenu<O> extends AwMenu {
    private static final Logger LOGGER = LogUtils.getLogger();

    protected final InteractionHand hand;
    /** The order being edited (a copy of the one in the stack). */
    public final O order;

    protected OrderMenu(MenuType<?> type, int containerId, Inventory inventory, InteractionHand hand) {
        super(type, containerId, inventory);
        this.hand = hand;
        O read = readOrder(inventory.player.getItemInHand(hand));
        this.order = read != null ? read : newOrder();
    }

    /** The key of the order in the message. */
    protected abstract String dataKey();

    protected abstract Codec<O> codec();

    /** A copy of the order in the stack, null if the stack is not this kind of order item. Called by the constructor: it must not use fields. */
    @Nullable
    protected abstract O readOrder(ItemStack stack);

    protected abstract O newOrder();

    protected abstract void writeOrder(O order, ItemStack stack);

    protected abstract boolean isOrderItem(ItemStack stack);

    /**
     * Checks an order a client sent against the order now in the stack. May fix values in {@code sent} and return it.
     *
     * @return the order to store, or null to refuse it
     */
    @Nullable
    protected abstract O sanitize(O current, O sent);

    /** Checks the raw data before it is decoded (sizes that the decoding would allocate). */
    protected boolean isRawValid(CompoundTag raw) {
        return true;
    }

    // ---- client ----

    /** Client side: sends the order as it is now to the server. */
    public void sendOrder() {
        if (!player.level().isClientSide) {
            return;
        }
        codec().encodeStart(player.level().registryAccess().createSerializationContext(NbtOps.INSTANCE), order)
                .resultOrPartial(error -> LOGGER.error("Couldn't send {}: {}", dataKey(), error))
                .ifPresent(tag -> sendDataToServer(dataKey(), tag));
    }

    // ---- server ----

    @Override
    protected void handlePacketData(CompoundTag data) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer) || !stillValid(player) || !data.contains(dataKey(), Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag raw = data.getCompound(dataKey());
        if (!isRawValid(raw)) {
            LOGGER.warn("Refused {} of {}: it has too many entries", dataKey(), player.getGameProfile().getName());
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        O current = readOrder(stack);
        if (current == null) {
            return;
        }
        O sent = codec().parse(player.level().registryAccess().createSerializationContext(NbtOps.INSTANCE), raw)
                .resultOrPartial(error -> LOGGER.warn("Refused {} of {}: {}", dataKey(), player.getGameProfile().getName(), error)).orElse(null);
        if (sent == null) {
            return;
        }
        O accepted = sanitize(current, sent);
        if (accepted == null) {
            LOGGER.warn("Refused {} of {}: it does not match the order in the item", dataKey(), player.getGameProfile().getName());
            return;
        }
        writeOrder(accepted, stack);
    }

    @Override
    public boolean stillValid(Player who) {
        return isOrderItem(who.getItemInHand(hand));
    }

    /** Server side: opens an order menu for the item in the hand; the hand goes into the extra data. */
    protected static void openMenu(ServerPlayer player, InteractionHand hand, MenuConstructor constructor) {
        player.openMenu(new SimpleMenuProvider(constructor, player.getItemInHand(hand).getHoverName()), buf -> buf.writeEnum(hand));
    }

    // ---- helpers for the checks ----

    /** True if every element of {@code sent} can be matched to its own element of {@code current} (a multiset subset). */
    protected static <K> boolean isSubset(Collection<K> sent, Collection<K> current) {
        Map<K, Integer> available = new HashMap<>();
        for (K key : current) {
            available.merge(key, 1, Integer::sum);
        }
        for (K key : sent) {
            Integer left = available.get(key);
            if (left == null || left == 0) {
                return false;
            }
            available.put(key, left - 1);
        }
        return true;
    }
}
