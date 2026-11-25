/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/research/ResearchTracker.
 */
package io.github.capttracker.ancientwarfare.research;

import io.github.capttracker.ancientwarfare.network.ResearchSyncPayload;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Entry point for reading and changing research. Server code changes research only through
 * {@link #change}, which saves and re-syncs the owner if they are online.
 */
public final class Research {
    /** The local player's research on a client; replaced whenever the server syncs. Unused on a server. */
    private static ResearchEntry clientEntry = new ResearchEntry();

    private Research() {}

    /** Works on both sides. On a client it answers for the local player only. */
    public static ResearchEntry entry(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return ResearchData.get(serverPlayer.server).entry(serverPlayer.getUUID());
        }
        return clientEntry;
    }

    public static boolean hasCompleted(Player player, ResourceLocation goal) {
        return entry(player).knows(goal);
    }

    /** Client side, for tooltips: whether the local player knows a goal, as of the last sync. */
    public static boolean localPlayerKnows(ResourceLocation goal) {
        return clientEntry.knows(goal);
    }

    public static Registry<ResearchGoal> goals(MinecraftServer server) {
        return ResearchGoal.registry(server.registryAccess());
    }

    /** Applies a change to a player's research, marks it for saving and syncs the player if online. */
    public static void change(MinecraftServer server, UUID player, Consumer<Mutator> change) {
        ResearchData data = ResearchData.get(server);
        change.accept(new Mutator(data.entry(player), goals(server)));
        data.setDirty();
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            sync(online);
        }
    }

    /** @return false if the player already knew it */
    public static boolean learnFromNotes(ServerPlayer player, ResourceLocation goal) {
        if (hasCompleted(player, goal)) {
            return false;
        }
        change(player.server, player.getUUID(), m -> m.add(goal));
        return true;
    }

    /**
     * Notes for a goal the player already knows add a quarter of that goal's time to whatever is
     * in progress.
     *
     * @return false if nothing is in progress
     */
    public static boolean addProgressFromNotes(ServerPlayer player, ResourceLocation goal) {
        ResearchGoal known = goals(player.server).get(goal);
        if (known == null || entry(player).current().isEmpty()) {
            return false;
        }
        change(player.server, player.getUUID(), m -> m.addProgress(known.time() / 4));
        return true;
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ResearchSyncPayload(entry(player).save()));
    }

    /** Client side: called when a sync arrives. */
    public static void acceptSync(ResearchSyncPayload payload) {
        clientEntry = ResearchEntry.load(payload.entry());
    }

    /** The changes that can be made to an entry. Only {@link #change} hands one out. */
    public static final class Mutator {
        private final ResearchEntry entry;
        private final Registry<ResearchGoal> goals;

        private Mutator(ResearchEntry entry, Registry<ResearchGoal> goals) {
            this.entry = entry;
            this.goals = goals;
        }

        public void add(ResourceLocation goal) {
            entry.add(goal);
        }

        public void remove(ResourceLocation goal) {
            entry.remove(goal);
        }

        public void clear() {
            entry.clear();
        }

        public void fill() {
            entry.fill(goals.keySet());
        }

        public void enqueue(ResourceLocation goal) {
            entry.enqueue(goal);
        }

        public void dequeue(ResourceLocation goal) {
            entry.dequeue(goals, goal);
        }

        public void start(ResourceLocation goal) {
            entry.start(goal);
        }

        public void setProgress(int progress) {
            entry.setProgress(progress);
        }

        public boolean addProgress(int amount) {
            return entry.addProgress(goals, amount);
        }

        public void finish(ResourceLocation goal) {
            entry.finish(goal);
        }
    }
}
