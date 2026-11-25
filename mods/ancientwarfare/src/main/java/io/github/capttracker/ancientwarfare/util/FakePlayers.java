/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Replaces Ancient Warfare 2: core/entity/AWFakePlayer (a FakePlayer singleton cleared on world unload).
 */
package io.github.capttracker.ancientwarfare.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The fake player Ancient Warfare uses to break and place blocks and to post block events on behalf of
 * automation and vehicles. NeoForge's {@link FakePlayerFactory} caches one per level and clears them when
 * the level unloads, which is what the original's unload handler did by hand.
 */
public final class FakePlayers {
    private static final String PLAYER_NAME = "AncientWarfareFakePlayer";
    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes(PLAYER_NAME.getBytes(StandardCharsets.UTF_8)), PLAYER_NAME);

    private FakePlayers() {}

    public static FakePlayer get(ServerLevel level) {
        return FakePlayerFactory.get(level, PROFILE);
    }
}
