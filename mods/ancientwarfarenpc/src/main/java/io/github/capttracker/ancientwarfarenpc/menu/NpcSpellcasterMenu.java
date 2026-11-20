/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerNpcFactionSpellcasterWizardry.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.registry.FactionNpcDefault;
import io.github.capttracker.ancientwarfarenpc.registry.NpcDefaultsRegistry;
import io.github.capttracker.ancientwarfarenpc.skin.NpcSkinSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The spell editor of a spellcaster NPC (1.12.2: {@code GUI_NPC_FACTION_SPELLCASTER_WIZARDRY}, 62; registered only with Electroblob's Wizardry).
 * It edits the maximum health, the skin and the list of assigned spells, and can apply one of the five presets {@code spellcaster.1} to
 * {@code spellcaster.5} of the NPC defaults. The menu works with any NPC that implements {@link ISpellcasterNpc} and a {@link NpcSpellSupport.Catalog}
 * (the 1.12.2 version used Wizardry's {@code Spell} directly, with the spell's metadata number in the NBT; spells are registry names now).
 *
 * <p>Changes from 1.12.2: the preset is applied by the server (a remote client has no NPC defaults, they are server data), which sends the new
 * state back; the preset name is applied to the NPC too (the original set it on the client's copy of the NPC, where it was lost); creative players
 * only; the server checks the spell ids against the catalog (at most {@value #MAX_SPELLS}), the health and the skin.
 *
 * <p>Messages: server to client {@code assignedSpells} (list of strings), {@code maxHealth}, {@code skinSettings}, {@code presets} (ints);
 * client to server {@code assignedSpells}, {@code maxHealth}, {@code skinSettings} or {@code preset} (int 1 to 5).
 */
public class NpcSpellcasterMenu extends NpcMenuBase<NpcBase> implements ISkinSettingsMenu {
    public static final int MAX_SPELLS = 64;
    private static final int MAX_HEALTH = 100_000;

    private final List<ResourceLocation> assignedSpells = new ArrayList<>();
    private final List<Integer> availablePresets = new ArrayList<>();
    private int maxHealth;
    private NpcSkinSettings skinSettings;
    /** A name to give the NPC when the menu closes (set by a preset). */
    @Nullable
    private String presetName;
    /** If set, the values are written to the NPC when the menu closes. */
    private boolean hasChanged;

    /** Client side: the NPC's entity id comes from the buffer written by {@link NpcMenuOpener}. */
    public NpcSpellcasterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcBase.class));
    }

    public NpcSpellcasterMenu(int containerId, Inventory inventory, @Nullable NpcBase npc) {
        super(AwNpcMenus.SPELLCASTER_MENU.get(), containerId, inventory, npc);
        if (npc != null) {
            skinSettings = npc.getSkinSettings();
            maxHealth = npc.getMaxHealthOverride();
            if (npc instanceof ISpellcasterNpc caster) {
                assignedSpells.addAll(caster.getSpells());
            }
        } else {
            skinSettings = new NpcSkinSettings();
        }
    }

    @Override
    protected boolean mayUse(Player who) {
        return who.isCreative() && npc instanceof ISpellcasterNpc;
    }

    // ---- state the screen reads and edits ----

    public List<ResourceLocation> getAssignedSpells() {
        return assignedSpells;
    }

    /** Every spell that can be assigned (empty without a catalog). */
    public List<ResourceLocation> getAllSpells() {
        NpcSpellSupport.Catalog catalog = NpcSpellSupport.getCatalog();
        return catalog == null ? List.of() : catalog.allSpells();
    }

    public String getSpellName(ResourceLocation spell) {
        NpcSpellSupport.Catalog catalog = NpcSpellSupport.getCatalog();
        return catalog == null ? spell.toString() : catalog.displayName(spell);
    }

    /** The presets (1 to 5) that the faction of the NPC has enabled; the server tells the client. */
    public List<Integer> getAvailablePresets() {
        return availablePresets;
    }

    public void addSpell(ResourceLocation spell) {
        assignedSpells.add(spell);
    }

    public void removeSpell(ResourceLocation spell) {
        assignedSpells.remove(spell);
    }

    public String getFactionNpcFullType() {
        return npc == null ? "" : npc.getNpcFullType();
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

    // ---- client requests ----

    /** Sends the spells, the health and the skin. */
    public void sendChangesToServer() {
        CompoundTag tag = new CompoundTag();
        tag.put("skinSettings", skinSettings.serializeNBT());
        tag.putInt("maxHealth", maxHealth);
        tag.put("assignedSpells", spellList());
        sendDataToServer(tag);
    }

    /** Asks the server to apply a preset (1.12.2: {@code setNameAndPresetDefaults}). */
    public void applyPreset(int preset) {
        sendDataToServer("preset", net.minecraft.nbt.IntTag.valueOf(preset));
    }

    private ListTag spellList() {
        ListTag list = new ListTag();
        for (ResourceLocation spell : assignedSpells) {
            list.add(StringTag.valueOf(spell.toString()));
        }
        return list;
    }

    // ---- sync ----

    @Override
    protected void sendInitialState() {
        CompoundTag tag = new CompoundTag();
        tag.put("skinSettings", skinSettings.serializeNBT());
        tag.putInt("maxHealth", maxHealth);
        tag.put("assignedSpells", spellList());
        if (npc instanceof NpcFaction faction) {
            List<Integer> presets = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                FactionNpcDefault preset = NpcDefaultsRegistry.getFactionNpcDefault(faction.getFaction(), "spellcaster." + i);
                if (preset != null && preset.isEnabled()) {
                    presets.add(i);
                }
            }
            tag.put("presets", new IntArrayTag(presets));
        }
        sendDataToClient(tag);
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            readSpells(tag, false);
            if (tag.contains("maxHealth", Tag.TAG_INT)) {
                maxHealth = tag.getInt("maxHealth");
            }
            if (tag.contains("skinSettings", Tag.TAG_COMPOUND)) {
                skinSettings = NpcSkinSettings.deserializeNBT(tag.getCompound("skinSettings"));
            }
            if (tag.contains("presets", Tag.TAG_INT_ARRAY)) {
                availablePresets.clear();
                for (int preset : tag.getIntArray("presets")) {
                    availablePresets.add(preset);
                }
            }
            refreshGui();
            return;
        }
        if (!isRequestAllowed() || npc == null) {
            return;
        }
        if (tag.contains("preset", Tag.TAG_INT)) {
            applyPresetOnServer(tag.getInt("preset"));
            return;
        }
        if (tag.contains("maxHealth", Tag.TAG_INT)) {
            maxHealth = Mth.clamp(tag.getInt("maxHealth"), -1, MAX_HEALTH);
        }
        if (tag.contains("skinSettings", Tag.TAG_COMPOUND)) {
            NpcSkinSettings sent = NpcSkinInput.read(tag.getCompound("skinSettings"));
            if (sent != null) {
                skinSettings = sent;
                npc.setSkinSettings(sent);
            }
        }
        readSpells(tag, true);
        hasChanged = true;
    }

    /** Reads the spell list of the tag; the server only takes ids the catalog knows. */
    private void readSpells(CompoundTag tag, boolean validate) {
        if (!tag.contains("assignedSpells", Tag.TAG_LIST)) {
            return;
        }
        NpcSpellSupport.Catalog catalog = NpcSpellSupport.getCatalog();
        if (validate && catalog == null) {
            return;
        }
        List<ResourceLocation> known = validate ? catalog.allSpells() : List.of();
        List<ResourceLocation> read = new ArrayList<>();
        ListTag list = tag.getList("assignedSpells", Tag.TAG_STRING);
        for (int i = 0; i < list.size() && read.size() < MAX_SPELLS; i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null && (!validate || known.contains(id))) {
                read.add(id);
            }
        }
        assignedSpells.clear();
        assignedSpells.addAll(read);
    }

    private void applyPresetOnServer(int preset) {
        if (!(npc instanceof NpcFaction faction) || preset < 1 || preset > 5) {
            return;
        }
        String presetSubtypeName = "spellcaster." + preset;
        FactionNpcDefault defaults = NpcDefaultsRegistry.getFactionNpcDefault(faction.getFaction(), presetSubtypeName);
        NpcSpellSupport.Catalog catalog = NpcSpellSupport.getCatalog();
        if (defaults == null || !defaults.isEnabled() || catalog == null) {
            return;
        }
        // name
        presetName = Component.translatable("entity.ancientwarfarenpc." + faction.getFaction() + "." + presetSubtypeName + ".name").getString();
        // spells: added to the ones the NPC has, as the original did
        for (ResourceLocation spell : catalog.presetSpells(faction.getFaction(), presetSubtypeName)) {
            if (assignedSpells.size() < MAX_SPELLS) {
                assignedSpells.add(spell);
            }
        }
        // health
        maxHealth = (int) defaults.getBaseHealth();
        // skin
        skinSettings.setSkinType(NpcSkinSettings.SkinType.NPC_TYPE);
        skinSettings.setRandom(true);
        skinSettings.setNpcTypeName(faction.getFaction() + "." + presetSubtypeName);
        npc.setSkinSettings(skinSettings);
        hasChanged = true;
        sendInitData();
    }

    /** Server side: writes the values to the NPC (1.12.2: {@code onContainerClosed}). */
    @Override
    public void removed(Player who) {
        if (hasChanged && !isClient() && npc != null && npc.isAlive() && mayUse(who)) {
            hasChanged = false;
            npc.setSkinSettings(skinSettings.minimizeData());
            ((ISpellcasterNpc) npc).setSpells(new ArrayList<>(assignedSpells));
            npc.setMaxHealthOverride(maxHealth);
            if (presetName != null) {
                npc.setCustomName(Component.literal(presetName));
            }
        }
        super.removed(who);
    }
}
