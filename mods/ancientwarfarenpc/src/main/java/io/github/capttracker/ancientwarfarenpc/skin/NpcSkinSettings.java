/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/skin/NpcSkinSettings.
 */
package io.github.capttracker.ancientwarfarenpc.skin;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Optional;

/**
 * Which skin an NPC wears and which arm model it uses: the default skin of its type, the skin of a player (by name) or a skin of another NPC
 * type (a random one or a chosen one). Saved in the NPC's tag as {@code skinSettings}, sent to the client in the NPC's spawn data.
 *
 * <p>Changes from 1.12.2:
 * <ul>
 *   <li>{@link #getTexture} and {@link #renderFemaleModel} take the NPC's full type, skin seed and gender instead of the NPC
 *       ({@code getNpcFullType()}, {@code getIDForSkin()}, {@code isFemale()}), so this class does not depend on the entity classes.</li>
 *   <li>Player skins: the client resolves a name to a texture through the {@link PlayerSkinResolver} that the client setup installs (it uses
 *       the vanilla skin manager). The server no longer resolves the profile and sends it in an entity packet ({@code NpcCommonProxy.cacheProfile}).</li>
 *   <li>Fix: the buffer reader read the skin string with a limit of 60 where the writer allows 100 characters.</li>
 * </ul>
 */
public class NpcSkinSettings {
    /** The texture of an NPC without a usable skin. */
    public static final ResourceLocation BASE_DEFAULT_TEXTURE = Ids.of(AncientWarfareNPC.MOD_ID, "textures/entity/npc/npc_default.png");
    public static final String TAG_NAME = "skinSettings";
    private static final String SKIN_TYPE_TAG = "skinTypeSelected";
    private static final String PLAYER_NAME_TAG = "playerName";
    private static final String RANDOM_TAG = "random";
    private static final String NPC_TYPE_NAME_TAG = "npcTypeName";
    private static final String NPC_TYPE_SKIN_TAG = "npcTypeSkin";
    private static final String IS_ALEX_MODEL_TAG = "isAlexModel";

    @Nullable
    private static volatile PlayerSkinResolver playerSkinResolver;

    private SkinType skinTypeSelected = SkinType.DEFAULT;
    private boolean playerSkinLoaded = false;
    @Nullable
    private ResourceLocation playerSkin = null;
    private String playerName = "";
    private boolean random = true;
    private String npcTypeName = "custom";
    @Nullable
    private ResourceLocation npcTypeSkin = null;
    private boolean isAlexModel = false;

    /** Client setup installs this: the texture of a player's skin by name, if it is known (loaded) yet. */
    @FunctionalInterface
    public interface PlayerSkinResolver {
        Optional<ResourceLocation> resolve(String playerName);
    }

    public static void setPlayerSkinResolver(PlayerSkinResolver resolver) {
        playerSkinResolver = resolver;
    }

    public boolean isAlexModel() {
        return isAlexModel;
    }

    public void setAlexModel(boolean alexModel) {
        isAlexModel = alexModel;
    }

    /** Whether to draw the slim (female) model: the NPC's own gender for the default skin, else the setting. */
    public boolean renderFemaleModel(boolean npcIsFemale) {
        if (skinTypeSelected == SkinType.DEFAULT) {
            return npcIsFemale;
        }
        return isAlexModel;
    }

    /**
     * The texture to draw.
     *
     * @param npcFullType the NPC's full type, for example {@code orc.soldier} or {@code worker.farmer}
     * @param skinSeed    the seed that makes the random skin the same on server and client (the least significant bits of the NPC's UUID)
     */
    public ResourceLocation getTexture(String npcFullType, long skinSeed) {
        switch (skinTypeSelected) {
            case PLAYER:
                loadPlayerSkin();
                return playerSkin == null ? BASE_DEFAULT_TEXTURE : playerSkin;
            case NPC_TYPE:
                return random || npcTypeSkin == null ? NpcSkinManager.getNpcTexture(npcTypeName, skinSeed).orElse(BASE_DEFAULT_TEXTURE) : npcTypeSkin;
            case DEFAULT:
            default:
                return NpcSkinManager.getNpcTexture(npcFullType, skinSeed).orElse(BASE_DEFAULT_TEXTURE);
        }
    }

    // ---- NBT ----

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString(SKIN_TYPE_TAG, skinTypeSelected.getSerializedName());

        if (!playerName.isEmpty()) {
            tag.putString(PLAYER_NAME_TAG, playerName);
        }
        if (!random) {
            tag.putBoolean(RANDOM_TAG, false);
        }
        if (!npcTypeName.isEmpty()) {
            tag.putString(NPC_TYPE_NAME_TAG, npcTypeName);
        }
        if (npcTypeSkin != null) {
            tag.putString(NPC_TYPE_SKIN_TAG, npcTypeSkin.toString());
        }
        if (isAlexModel) {
            tag.putBoolean(IS_ALEX_MODEL_TAG, true);
        }
        return tag;
    }

    public static NpcSkinSettings deserializeNBT(CompoundTag tag) {
        NpcSkinSettings skinSettings = new NpcSkinSettings();
        skinSettings.skinTypeSelected = SkinType.byName(tag.getString(SKIN_TYPE_TAG));

        if (tag.contains(PLAYER_NAME_TAG)) {
            skinSettings.playerName = tag.getString(PLAYER_NAME_TAG);
        }
        if (tag.contains(RANDOM_TAG)) {
            skinSettings.random = tag.getBoolean(RANDOM_TAG);
        }
        if (tag.contains(NPC_TYPE_NAME_TAG)) {
            skinSettings.npcTypeName = tag.getString(NPC_TYPE_NAME_TAG);
        }
        if (tag.contains(NPC_TYPE_SKIN_TAG)) {
            skinSettings.npcTypeSkin = ResourceLocation.tryParse(tag.getString(NPC_TYPE_SKIN_TAG));
        }
        skinSettings.isAlexModel = tag.getBoolean(IS_ALEX_MODEL_TAG);
        return skinSettings;
    }

    // ---- network buffer (the NPC's spawn data) ----

    public void serializeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUtf(skinTypeSelected.getSerializedName(), 20);
        buffer.writeUtf(playerName, 40);
        buffer.writeBoolean(random);
        buffer.writeUtf(npcTypeName, 30);
        String skin = npcTypeSkin == null ? "" : npcTypeSkin.toString();
        buffer.writeUtf(skin.substring(0, Math.min(skin.length(), 100)), 100);
        buffer.writeBoolean(isAlexModel);
    }

    public static NpcSkinSettings deserializeFromBuffer(FriendlyByteBuf buffer) {
        NpcSkinSettings skinSettings = new NpcSkinSettings();
        skinSettings.skinTypeSelected = SkinType.byName(buffer.readUtf(20));
        String player = buffer.readUtf(40);
        if (skinSettings.skinTypeSelected == SkinType.PLAYER) {
            skinSettings.playerName = player;
        }
        boolean randomSkin = buffer.readBoolean();
        String typeName = buffer.readUtf(30);
        // the original read this with a limit of 60 outside of the NPC_TYPE branch, below the 100 the writer allows
        String skin = buffer.readUtf(100);
        if (skinSettings.skinTypeSelected == SkinType.NPC_TYPE) {
            skinSettings.random = randomSkin;
            skinSettings.npcTypeName = typeName;
            if (!skinSettings.random) {
                skinSettings.npcTypeSkin = skin.isEmpty() ? null : ResourceLocation.tryParse(skin);
            }
        }
        skinSettings.isAlexModel = buffer.readBoolean();
        return skinSettings;
    }

    /** A copy without the fields that do not matter for the selected skin type (saves space in the spawner item). */
    public NpcSkinSettings minimizeData() {
        NpcSkinSettings skinSettings = new NpcSkinSettings();
        skinSettings.skinTypeSelected = skinTypeSelected;
        if (skinTypeSelected == SkinType.PLAYER) {
            skinSettings.playerName = playerName;
        } else if (skinTypeSelected == SkinType.NPC_TYPE) {
            skinSettings.random = random;
            skinSettings.npcTypeName = npcTypeName;
            if (!random) {
                skinSettings.npcTypeSkin = npcTypeSkin;
            }
        }
        if (skinTypeSelected != SkinType.DEFAULT) {
            skinSettings.isAlexModel = isAlexModel;
        }
        return skinSettings;
    }

    private void loadPlayerSkin() {
        if (!playerSkinLoaded) {
            PlayerSkinResolver resolver = playerSkinResolver;
            if (resolver != null) {
                Optional<ResourceLocation> found = resolver.resolve(playerName);
                if (found.isPresent()) {
                    playerSkin = found.get();
                    playerSkinLoaded = true;
                }
            }
        }
    }

    /** A short description for the skin screen. */
    public Component getDescription() {
        switch (skinTypeSelected) {
            case PLAYER:
                return Component.translatable("gui.ancientwarfarenpc.skin_info.player", playerName);
            case NPC_TYPE:
                if (random || npcTypeSkin == null) {
                    return Component.literal(npcTypeName + " ").append(Component.translatable("gui.ancientwarfarenpc.skin_info.npc_type_random"));
                }
                return Component.literal(npcTypeSkin.getPath().replace("textures/skin_pack/", "").replace(".png", ""));
            case DEFAULT:
            default:
                return Component.translatable("gui.ancientwarfarenpc.skin_info.default");
        }
    }

    public void setSkinType(SkinType skinType) {
        this.skinTypeSelected = skinType;
    }

    public SkinType getSkinType() {
        return skinTypeSelected;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
        playerSkinLoaded = false;
        playerSkin = null;
    }

    public String getNpcTypeName() {
        return npcTypeName;
    }

    public void setNpcTypeName(String npcTypeName) {
        this.npcTypeName = npcTypeName;
    }

    public Optional<ResourceLocation> getNpcTypeSkin() {
        return Optional.ofNullable(npcTypeSkin);
    }

    public void resetNpcTypeSkin() {
        this.npcTypeSkin = null;
    }

    public void setNpcTypeSkin(ResourceLocation npcTypeSkin) {
        this.npcTypeSkin = npcTypeSkin;
    }

    public void setRandom(boolean random) {
        this.random = random;
    }

    public boolean isRandom() {
        return random;
    }

    public enum SkinType implements StringRepresentable {
        DEFAULT,
        PLAYER,
        NPC_TYPE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static SkinType byName(String name) {
            for (SkinType type : values()) {
                if (type.getSerializedName().equals(name)) {
                    return type;
                }
            }
            return DEFAULT;
        }
    }
}
