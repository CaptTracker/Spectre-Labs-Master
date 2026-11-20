/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerNpcBase and core/container/ContainerEntityBase.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfare.menu.AwMenu;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import javax.annotation.Nullable;

/**
 * Base of the menus that edit or use one NPC. The NPC travels as its entity id in the extra data buffer, so it is looked up in the
 * player's level on both sides (1.12.2: the entity id was the first GUI argument).
 *
 * <p>Changes from 1.12.2:
 * <ul>
 *   <li>{@link #npc} is null if this side does not know the entity (the original threw). The server never opens a menu for a missing
 *       NPC, and a client menu without its NPC closes at once ({@link #stillValid}); the screens show nothing meanwhile.</li>
 *   <li>The menu is only valid while the player may use it ({@link #mayUse}), not only while the NPC lives and is within 8 blocks. The
 *       original asked for the right to open the menu once, in the entity's interaction code, and trusted every later packet.</li>
 *   <li>The first state is pushed by the server itself on its first {@link #broadcastChanges()} if the opener did not call
 *       {@link #sendInitData()} (same pattern as the Automation menus).</li>
 * </ul>
 *
 * <p>Everything a client sends reaches {@code handlePacketData} on the server and is untrusted: subclasses check
 * {@link #isRequestAllowed()} first and validate every value.
 *
 * @param <T> the NPC class the menu works with
 */
public abstract class NpcMenuBase<T extends NpcBase> extends AwMenu {
    /** Squared distance within which the menu stays open (1.12.2: {@code getDistanceSq(entity) < 64}). */
    public static final double USE_DISTANCE_SQ = 64.0D;
    /** The longest custom name or template name a client may send. */
    public static final int MAX_NAME_LENGTH = 64;

    /** The NPC, or null if this side does not know the entity. */
    @Nullable
    public final T npc;
    private boolean initSent = false;

    protected NpcMenuBase(@Nullable MenuType<?> type, int containerId, Inventory playerInventory, @Nullable T npc) {
        super(type, containerId, playerInventory);
        this.npc = npc;
    }

    /** The entity with this id in the player's level if it is an instance of the class, else null. */
    @Nullable
    protected static <E extends NpcBase> E find(Player player, int entityId, Class<E> type) {
        Entity found = player.level().getEntity(entityId);
        return type.isInstance(found) ? type.cast(found) : null;
    }

    protected final boolean isClient() {
        return player.level().isClientSide;
    }

    protected final HolderLookup.Provider registries() {
        return player.level().registryAccess();
    }

    /**
     * Whether this player may use the menu. The default is the rule of the NPC inventory: the commanders of a player-owned NPC, and
     * creative players for a faction NPC. Menus that anyone may use (the trade menus) or that need more (the creative controls) override it.
     */
    protected boolean mayUse(Player who) {
        return npc != null && mayEdit(who, npc);
    }

    /** True if the player may edit this NPC (1.12.2: {@code NpcPlayerOwned.tryCommand}: command permissions, {@code NpcFaction.tryCommand}: creative). */
    public static boolean mayEdit(Player who, NpcBase npc) {
        if (npc instanceof NpcFaction) {
            return who.isCreative();
        }
        return npc.hasCommandPermissions(who.getUUID(), who.getGameProfile().getName());
    }

    @Override
    public boolean stillValid(Player who) {
        return npc != null && npc.isAlive() && who.distanceToSqr(npc) < USE_DISTANCE_SQ && mayUse(who);
    }

    /** Server side: whether data that arrived from a client may be acted on (this is the server copy and the player may still use the menu). */
    protected final boolean isRequestAllowed() {
        return !isClient() && stillValid(player);
    }

    /** A custom name a client sent: control characters removed, at most {@value #MAX_NAME_LENGTH} characters. */
    protected static String sanitizeName(String name) {
        StringBuilder clean = new StringBuilder(Math.min(name.length(), MAX_NAME_LENGTH));
        for (int i = 0; i < name.length() && clean.length() < MAX_NAME_LENGTH; i++) {
            char c = name.charAt(i);
            if (c >= ' ' && c != 127) {
                clean.append(c);
            }
        }
        return clean.toString();
    }

    // ---- first state ----

    @Override
    public final void sendInitData() {
        initSent = true;
        sendInitialState();
    }

    /** Server: sends everything the screen needs when it opens. Called once, by the opener or on the first tick. */
    protected void sendInitialState() {
    }

    @Override
    public void broadcastChanges() {
        if (!isClient() && !initSent) {
            sendInitData();
        }
        super.broadcastChanges();
    }

    // ---- requests of the NPC inventory screen (1.12.2: ContainerNpcBase) ----

    public void repack() {
        sendFlag("repack");
    }

    public void setHome() {
        sendFlag("setHome");
    }

    public void clearHome() {
        sendFlag("clearHome");
    }

    public void togglefollow() {
        sendFlag("togglefollow");
    }

    private void sendFlag(String key) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(key, true);
        sendDataToServer(tag);
    }
}
