/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: NpcClientProxy.getPlayerSkin / NpcCommonProxy.cacheProfile (the player-name skin of an NPC).
 */
package io.github.capttracker.ancientwarfarenpc.client.skin;

import com.mojang.authlib.GameProfile;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up the skin of a player by name for NPCs whose skin type is "player" (installed with
 * {@code NpcSkinSettings.setPlayerSkinResolver(NpcPlayerSkins::resolveTexture)} by {@code NpcRenderClient}).
 *
 * <p>1.12.2 let the server resolve the game profile and sent it to the clients, which then called {@code SkinManager.loadSkinFromCache} and
 * {@code loadSkin}. 1.21.1 has one call for it: the profile (from the connected player list when the player is online, else fetched by name with
 * {@link SkullBlockEntity#fetchGameProfile(String)}, which also fills in the texture property) goes to {@code SkinManager.getOrLoad(GameProfile)},
 * which downloads and registers the textures. All of it runs in the background; until the future is done the lookup returns empty and the NPC
 * shows its fallback texture, and the settings ask again on the next frame. A failed lookup (unknown name, no network) is retried after
 * {@link #RETRY_MILLIS}.
 *
 * <p>Client only. The cache is cleared when the player leaves the world.
 */
@EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT)
public final class NpcPlayerSkins {
    /** How long a failed lookup is kept before it is tried again. */
    public static final long RETRY_MILLIS = 30_000L;
    private static final int MAX_ENTRIES = 256;

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private record Entry(CompletableFuture<Optional<PlayerSkin>> future, long created) {}

    private NpcPlayerSkins() {}

    /** The texture of the player's skin, once it is loaded. Matches {@code NpcSkinSettings.PlayerSkinResolver}. */
    public static Optional<ResourceLocation> resolveTexture(String playerName) {
        return lookup(playerName).map(PlayerSkin::texture);
    }

    /**
     * Whether the player's skin is the slim ("Alex") model, once it is loaded. The skin settings keep an explicit flag
     * ({@code isAlexModel}), the skin screen can use this to preset it.
     */
    public static Optional<Boolean> isSlimModel(String playerName) {
        return lookup(playerName).map(skin -> skin.model() == PlayerSkin.Model.SLIM);
    }

    /** The loaded skin of the player, or empty while it is loading or if it could not be found. */
    public static Optional<PlayerSkin> lookup(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }
        String key = playerName.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Entry entry = CACHE.get(key);
        if (entry != null && now - entry.created() > RETRY_MILLIS && isFailed(entry)) {
            entry = null;
        }
        if (entry == null) {
            if (CACHE.size() >= MAX_ENTRIES) {
                CACHE.clear();
            }
            entry = new Entry(start(playerName), now);
            CACHE.put(key, entry);
        }
        CompletableFuture<Optional<PlayerSkin>> future = entry.future();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            return future.join();
        }
        return Optional.empty();
    }

    /** Forgets everything that was looked up. */
    public static void clear() {
        CACHE.clear();
    }

    private static boolean isFailed(Entry entry) {
        CompletableFuture<Optional<PlayerSkin>> future = entry.future();
        return future.isDone() && (future.isCompletedExceptionally() || future.join().isEmpty());
    }

    private static CompletableFuture<Optional<PlayerSkin>> start(String playerName) {
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture<Optional<GameProfile>> profile;
        ClientPacketListener connection = minecraft.getConnection();
        PlayerInfo online = connection == null ? null : connection.getPlayerInfo(playerName);
        if (online != null) {
            profile = CompletableFuture.completedFuture(Optional.of(online.getProfile()));
        } else {
            profile = SkullBlockEntity.fetchGameProfile(playerName);
        }
        // Minecraft is an executor of the main thread: the skin manager registers its textures there
        return profile.thenComposeAsync(found -> loadSkin(minecraft, found), minecraft)
                .exceptionally(error -> Optional.empty());
    }

    private static CompletableFuture<Optional<PlayerSkin>> loadSkin(Minecraft minecraft, Optional<GameProfile> profile) {
        if (profile.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return minecraft.getSkinManager().getOrLoad(profile.get()).thenApply(Optional::of);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
