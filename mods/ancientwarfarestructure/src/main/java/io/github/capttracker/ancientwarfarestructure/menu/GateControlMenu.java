/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/container/ContainerGateControl.
 */
package io.github.capttracker.ancientwarfarestructure.menu;

import io.github.capttracker.ancientwarfare.menu.AwMenu;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import io.github.capttracker.ancientwarfarestructure.init.AwGateMenus;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * The control menu of a gate: a repack button and, for creative players, an editable owner name (1.12.2:
 * {@code ContainerGateControl}). The gate travels as its entity id and the creative flag in the extra data buffer.
 *
 * <p>Changes from 1.12.2: the server re-checks that the player may still use the gate on every request, and only a
 * creative player may change the owner (the original trusted the packet).
 */
public class GateControlMenu extends AwMenu {
    private static final String REPACK_TAG = "repack";
    private static final String OWNER_TAG = "owner";
    private static final int MAX_NAME_LENGTH = 64;

    /** Null if this side does not know the entity. */
    @Nullable
    public final EntityGate gate;
    public final boolean creative;
    /** Client side: the owner name sent by the server in {@link #sendInitData}. */
    public String ownerName = "";

    /** Client side: reads the buffer written by {@link #open}. */
    public GateControlMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readInt()), buf.readBoolean());
    }

    public GateControlMenu(int containerId, Inventory inventory, @Nullable EntityGate gate, boolean creative) {
        super(AwGateMenus.GATE_CONTROL.get(), containerId, inventory);
        this.gate = gate;
        this.creative = creative;
        if (gate != null && !inventory.player.level().isClientSide) {
            ownerName = gate.getOwner().getName();
        }
    }

    @Nullable
    private static EntityGate find(Player player, int id) {
        Entity found = player.level().getEntity(id);
        return found instanceof EntityGate g ? g : null;
    }

    /** Server side: opens the menu for the player (installed as the {@code GateControl.Opener}). */
    public static void open(ServerPlayer player, EntityGate gate, boolean creative) {
        MenuProvider provider = new SimpleMenuProvider((id, inv, p) -> new GateControlMenu(id, inv, gate, creative), Component.empty());
        if (player.openMenu(provider, buf -> {
            buf.writeInt(gate.getId());
            buf.writeBoolean(creative);
        }).isPresent() && player.containerMenu instanceof GateControlMenu menu) {
            menu.sendInitData();
        }
    }

    @Override
    public void sendInitData() {
        if (gate != null) {
            CompoundTag tag = new CompoundTag();
            tag.putString(OWNER_TAG, gate.getOwner().getName());
            sendDataToClient(tag);
        }
    }

    // ---- client requests ----

    public void sendRepack() {
        sendDataToServer(REPACK_TAG, ByteTag.ONE);
    }

    public void sendOwner(String name) {
        sendDataToServer(OWNER_TAG, StringTag.valueOf(name));
    }

    @Override
    protected void handlePacketData(CompoundTag data) {
        if (player.level().isClientSide) {
            if (data.contains(OWNER_TAG)) {
                ownerName = data.getString(OWNER_TAG);
                refreshGui();
            }
            return;
        }
        // from a client: untrusted
        if (gate == null || !stillValid(player)) {
            return;
        }
        if (data.contains(REPACK_TAG)) {
            gate.repackEntity();
        }
        if (data.contains(OWNER_TAG) && player.isCreative()) {
            String name = data.getString(OWNER_TAG);
            if (name.length() > MAX_NAME_LENGTH) {
                return;
            }
            gate.setOwner(name.isEmpty() ? Owner.EMPTY : new Owner(gate.level(), name));
        }
    }

    @Override
    public boolean stillValid(Player who) {
        if (gate == null || !gate.isAlive() || gate.distanceToSqr(who) >= 64.0D) {
            return false;
        }
        return who.isCreative() || gate.getOwner() == Owner.EMPTY || gate.getOwner().isOwnerOrSameTeamOrFriend(who);
    }
}
