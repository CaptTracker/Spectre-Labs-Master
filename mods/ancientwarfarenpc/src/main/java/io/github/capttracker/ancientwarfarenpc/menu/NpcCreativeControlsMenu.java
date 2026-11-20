/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerNpcCreativeControls.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.skin.NpcSkinSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * The creative controls of an NPC (1.12.2: {@code GUI_NPC_CREATIVE}, 44): owner name, skin, wander flag, health, attack damage and armor
 * overrides and the custom equipment flag. The whole state travels as one NBT in both directions; the server keeps it and writes it to the
 * NPC when the menu closes (only if the client sent a change), as before.
 *
 * <p>Changes from 1.12.2: only creative players may use it (the original opened it for anyone who asked and trusted the values), and the
 * server checks the values it receives: the owner name length, the skin settings ({@link NpcSkinInput}) and the numbers (kept within
 * +-{@value #MAX_VALUE}).
 */
public class NpcCreativeControlsMenu extends NpcMenuBase<NpcBase> implements ISkinSettingsMenu {
    private static final int MAX_VALUE = 1_000_000;

    /** Allows editing the owner name for player-owned NPCs, no effect on faction NPCs. */
    public String ownerName;
    /** A temporary flag in all NPCs. */
    public boolean wander;
    /** Faction NPCs only. */
    public boolean hasCustomEquipment;
    public int maxHealth;
    /** Faction NPCs only. */
    public int attackDamage;
    /** Faction NPCs only. */
    public int armorValue;
    public NpcSkinSettings skinSettings;
    /** Set when the client sent a change: then everything is written to the NPC when the menu closes. */
    private boolean hasChanged;

    /** Client side: the NPC's entity id comes from the buffer written by {@link NpcMenuOpener}. */
    public NpcCreativeControlsMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcBase.class));
    }

    public NpcCreativeControlsMenu(int containerId, Inventory inventory, @Nullable NpcBase npc) {
        super(AwNpcMenus.CREATIVE_MENU.get(), containerId, inventory, npc);
        if (npc != null) {
            ownerName = npc.getOwner().getName();
            skinSettings = npc.getSkinSettings();
            wander = npc.getIsAIEnabled();
            maxHealth = npc.getMaxHealthOverride();
            attackDamage = npc.getAttackDamageOverride();
            armorValue = npc.getArmorValueOverride();
            hasCustomEquipment = npc.getCustomEquipmentOverride();
        } else {
            ownerName = "";
            skinSettings = new NpcSkinSettings();
        }
    }

    @Override
    protected boolean mayUse(Player who) {
        return who.isCreative();
    }

    public boolean isFactionNpc() {
        return npc instanceof NpcFaction;
    }

    // ---- client requests ----

    public void sendChangesToServer() {
        sendDataToServer(serializeContainerData());
    }

    private CompoundTag serializeContainerData() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ownerName", ownerName);
        tag.put("skinSettings", skinSettings.serializeNBT());
        tag.putBoolean("wander", wander);
        tag.putInt("maxHealth", maxHealth);
        tag.putInt("attackDamage", attackDamage);
        tag.putInt("armorValue", armorValue);
        tag.putBoolean("hasCustomEquipment", hasCustomEquipment);
        return tag;
    }

    @Override
    public void handleNpcSkinUpdate() {
        sendDataToServer("skinSettings", skinSettings.serializeNBT());
    }

    @Override
    public NpcSkinSettings getSkinSettings() {
        return skinSettings;
    }

    @Override
    public void setSkinSettings(NpcSkinSettings skinSettings) {
        this.skinSettings = skinSettings;
    }

    // ---- sync ----

    @Override
    protected void sendInitialState() {
        sendDataToClient(serializeContainerData());
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            ownerName = tag.getString("ownerName");
            wander = tag.getBoolean("wander");
            hasCustomEquipment = tag.getBoolean("hasCustomEquipment");
            attackDamage = tag.getInt("attackDamage");
            armorValue = tag.getInt("armorValue");
            maxHealth = tag.getInt("maxHealth");
            skinSettings = NpcSkinSettings.deserializeNBT(tag.getCompound("skinSettings"));
            refreshGui();
            return;
        }
        if (!isRequestAllowed() || npc == null) {
            return;
        }
        // the skin screen sends the skin alone, the close of the screen sends everything
        if (tag.contains("skinSettings", Tag.TAG_COMPOUND)) {
            NpcSkinSettings sent = NpcSkinInput.read(tag.getCompound("skinSettings"));
            if (sent == null) {
                return;
            }
            skinSettings = sent;
            npc.setSkinSettings(sent);
        }
        if (tag.contains("ownerName", Tag.TAG_STRING)) {
            String sentName = sanitizeName(tag.getString("ownerName"));
            if (sentName.length() > 32) {
                return;
            }
            ownerName = sentName;
            wander = tag.getBoolean("wander");
            hasCustomEquipment = tag.getBoolean("hasCustomEquipment");
            attackDamage = Mth.clamp(tag.getInt("attackDamage"), -MAX_VALUE, MAX_VALUE);
            armorValue = Mth.clamp(tag.getInt("armorValue"), -MAX_VALUE, MAX_VALUE);
            maxHealth = Mth.clamp(tag.getInt("maxHealth"), -MAX_VALUE, MAX_VALUE);
        }
        hasChanged = true;
    }

    @Override
    public void removed(Player who) {
        if (hasChanged && !isClient() && npc != null && npc.isAlive() && mayUse(who)) {
            hasChanged = false;
            npc.setOwnerName(ownerName);
            npc.setSkinSettings(skinSettings.minimizeData());
            npc.setAttackDamageOverride(attackDamage);
            npc.setArmorValueOverride(armorValue);
            npc.setIsAIEnabled(wander);
            npc.setMaxHealthOverride(maxHealth);
            npc.setCustomEquipmentOverride(hasCustomEquipment);
        }
        super.removed(who);
    }
}
