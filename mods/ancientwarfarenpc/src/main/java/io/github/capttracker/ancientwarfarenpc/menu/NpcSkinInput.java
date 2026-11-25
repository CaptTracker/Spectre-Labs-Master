/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the validation of skin settings that a client sends (1.12.2 read them unchecked in ContainerNpcInventory,
 * ContainerNpcCreativeControls and ContainerNpcFactionSpellcasterWizardry).
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.skin.NpcSkinSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Reads the skin settings a client sent. The skin pack is a resource pack file of the client, so the server cannot check that a skin
 * exists; it only checks that every field has the shape the game itself writes, so that no oversized or odd text is stored in the NPC.
 */
final class NpcSkinInput {
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{0,16}");
    private static final Pattern TYPE_NAME = Pattern.compile("[a-z0-9_.]{0,30}");
    private static final int MAX_SKIN_LENGTH = 100;

    private NpcSkinInput() {}

    /**
     * @return the settings of the tag (reduced to the fields their skin type uses), or null if a field is not valid
     */
    @Nullable
    static NpcSkinSettings read(CompoundTag tag) {
        if (tag.contains("playerName", Tag.TAG_STRING) && !PLAYER_NAME.matcher(tag.getString("playerName")).matches()) {
            return null;
        }
        if (tag.contains("npcTypeName", Tag.TAG_STRING) && !TYPE_NAME.matcher(tag.getString("npcTypeName")).matches()) {
            return null;
        }
        if (tag.contains("npcTypeSkin", Tag.TAG_STRING)) {
            String skin = tag.getString("npcTypeSkin");
            if (skin.length() > MAX_SKIN_LENGTH || ResourceLocation.tryParse(skin) == null) {
                return null;
            }
        }
        return NpcSkinSettings.deserializeNBT(tag).minimizeData();
    }
}
