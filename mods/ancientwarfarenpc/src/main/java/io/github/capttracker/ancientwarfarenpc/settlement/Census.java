/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): the census of the settlement, the member list of the town hall screen.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.NpcThinking;
import io.github.capttracker.ancientwarfarenpc.menu.TownHallMenu;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The census: one compact {@link Entry} for each member of a settlement (who, what role, how it feels, where, what it does, what is wrong). The server
 * builds the snapshot for a player who has the Census tab of the town hall open ({@link TownHallMenu}) and sends it as a {@link Payload} when the tab opens
 * and again every {@code census_refresh_ticks}. A member whose chunk is loaded is read live; any other member is read from its record in the settlement
 * (the last known position, morale, task and flags, see {@link Settlement.Member}).
 *
 * <p>The problems are a bit mask ({@code FLAG_*}); the task is a short key such as {@code work.chop} that the screen turns into a few words.
 */
public final class Census {
    public static final int FLAG_HUNGRY = 1;
    public static final int FLAG_STARVING = 2;
    public static final int FLAG_NO_BED = 4;
    public static final int FLAG_NO_TOOL = 8;
    public static final int FLAG_NO_JOB = 16;
    public static final int FLAG_HURT = 32;
    public static final int FLAG_NO_PATH = 64;
    public static final int FLAG_ALARM = 128;
    /** The flags in the order the screen draws them. */
    public static final int[] FLAGS = {FLAG_STARVING, FLAG_HUNGRY, FLAG_NO_BED, FLAG_NO_TOOL, FLAG_NO_JOB, FLAG_HURT, FLAG_NO_PATH, FLAG_ALARM};
    /** The lang key suffix of each flag in {@link #FLAGS}: {@code guistrings.census.flag.<name>}. */
    public static final String[] FLAG_NAMES = {"starving", "hungry", "no_bed", "no_tool", "no_job", "hurt", "no_path", "alarm"};

    /** The most entries a snapshot carries (the decoder drops the rest, so the sender must not exceed it). */
    public static final int MAX_ENTRIES = 1024;
    private static final int MAX_STRING = 64;

    private Census() {}

    /**
     * One member. {@code name} is the custom name (may be empty), {@code given} the generated first name, {@code type} the full type such as
     * {@code worker.farmer}; {@code morale} is -1 when unknown; {@code rank} is the rank (0 to 4) of the main skill for the name, -1 for none; {@code traits} is the bit mask of the traits ({@code Trait#bit}); {@code loaded} is true when the data was read from the live NPC.
     */
    public record Entry(UUID id, String name, String given, String type, int morale, BlockPos pos, boolean loaded, int flags, String task, int rank, int traits) {
        public boolean hasProblem() {
            return flags != 0;
        }
    }

    /** Server side: the snapshot of all members of the settlement. */
    public static List<Entry> snapshot(ServerLevel viewerLevel, Settlement settlement) {
        ServerLevel level = viewerLevel.getServer().getLevel(settlement.getDimension());
        if (level == null) {
            level = viewerLevel;
        }
        List<Entry> entries = new ArrayList<>();
        for (Settlement.Member member : settlement.getMembers().values()) {
            if (entries.size() >= MAX_ENTRIES) {
                break;
            }
            int bed = settlement.isHomeless(member.id) ? FLAG_NO_BED : 0;
            Entity found = level.getEntity(member.id);
            if (found instanceof NpcPlayerOwned npc && npc.isAlive()) {
                NpcThinking.CensusInfo info = NpcThinking.census(npc, settlement);
                Component custom = npc.getCustomName();
                entries.add(new Entry(member.id, custom == null ? "" : custom.getString(), npc.getGivenName(), npc.getNpcFullType(), npc.getPersonalMorale(),
                        npc.blockPosition(), true, info.flags() | bed, info.task(), npc.getMainRank(), npc.getTraitMask()));
            } else {
                entries.add(new Entry(member.id, member.name, member.given, member.type, member.morale, member.pos, false, (member.flags & ~FLAG_NO_BED) | bed,
                        member.task.isEmpty() ? "idle" : member.task, member.rank, member.traits));
            }
        }
        return entries;
    }

    /** Cuts a string to the length the codec allows (a very long custom name must not break the packet). */
    private static String clip(String text) {
        return text.length() <= MAX_STRING ? text : text.substring(0, MAX_STRING);
    }

    /** Server to client: the snapshot for the open Census tab. */
    public record Payload(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "census"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    int count = Math.min(payload.entries.size(), MAX_ENTRIES);
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        Entry e = payload.entries.get(i);
                        buf.writeUUID(e.id);
                        buf.writeUtf(clip(e.name), MAX_STRING);
                        buf.writeUtf(clip(e.given), MAX_STRING);
                        buf.writeUtf(clip(e.type), MAX_STRING);
                        buf.writeVarInt(e.morale + 1);
                        buf.writeBlockPos(e.pos);
                        buf.writeBoolean(e.loaded);
                        buf.writeVarInt(e.flags);
                        buf.writeUtf(clip(e.task), MAX_STRING);
                        buf.writeVarInt(e.rank + 1);
                        buf.writeVarInt(e.traits);
                    }
                },
                buf -> {
                    int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
                    List<Entry> list = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        list.add(new Entry(buf.readUUID(), buf.readUtf(MAX_STRING), buf.readUtf(MAX_STRING), buf.readUtf(MAX_STRING), buf.readVarInt() - 1,
                                buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(MAX_STRING), buf.readVarInt() - 1, buf.readVarInt()));
                    }
                    return new Payload(list);
                });

        @Override
        public Type<Payload> type() {
            return TYPE;
        }

        /** Client side: hands the snapshot to the town hall menu the player has open (anything else is dropped). */
        public static void handle(Payload payload, IPayloadContext context) {
            if (context.player().containerMenu instanceof TownHallMenu menu) {
                menu.receiveCensus(payload.entries);
            }
        }
    }
}
