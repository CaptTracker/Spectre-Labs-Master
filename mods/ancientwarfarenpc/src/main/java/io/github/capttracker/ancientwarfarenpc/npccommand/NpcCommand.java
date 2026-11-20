/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/npc_command/NpcCommand.
 */
package io.github.capttracker.ancientwarfarenpc.npccommand;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.network.NpcNetwork;
import io.github.capttracker.ancientwarfarenpc.network.NpcNetwork.NpcCommandPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The orders of the command baton ({@link CommandType}), the {@link Command} that carries one to an NPC, and the two ends of the baton's
 * network round trip: {@link #handleCommandClient} (the key press on the client) and {@link #handleServerCommand} (the payload on the server).
 *
 * <p>TODO (from the original): turn the commands into proper instances that hold their own logic (it is in {@code NpcAIPlayerOwnedFollowCommand}).
 *
 * <p>Link to the item slice, which does not exist yet: the NPCs bound to the baton in the player's hands come from
 * {@link CommandedEntitiesProvider} (the item slice installs {@code ItemCommandBaton.getCommandedEntities}). They receive the command through
 * {@link NpcPlayerOwned#handlePlayerCommand}. This class lives in {@code npccommand} (1.12.2: {@code npc_command}), where the entity slice imports it from.
 */
public final class NpcCommand {
    @Nullable
    private static volatile CommandedEntitiesProvider commandedEntitiesProvider;

    private NpcCommand() {}

    public enum CommandType {
        MOVE,
        /** Click on an entity. */
        ATTACK,
        /** Click on a block. */
        ATTACK_AREA,
        /** Click on a friendly player or NPC. */
        GUARD,
        SET_HOME, SET_UPKEEP, CLEAR_HOME, CLEAR_UPKEEP, CLEAR_COMMAND, NONE;

        public boolean isPersistent() {
            return this == ATTACK || this == GUARD || this == ATTACK_AREA;
        }
    }

    /** The NPCs a player's command baton is bound to; installed by the item slice. */
    @FunctionalInterface
    public interface CommandedEntitiesProvider {
        /** The entities bound to the command baton in either hand of the player (empty if there is none). */
        List<Entity> getCommandedEntities(Player player);
    }

    public static void setCommandedEntitiesProvider(CommandedEntitiesProvider provider) {
        commandedEntitiesProvider = provider;
    }

    /** Client side: sends the command for what the player is looking at. Misses are ignored. Called from the baton's key handler. */
    public static void handleCommandClient(CommandType type, @Nullable HitResult hit) {
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return;
        }
        if (hit instanceof EntityHitResult entityHit) {
            NpcNetwork.sendToServer(new NpcCommandPayload(type, entityHit.getEntity()));
        } else if (hit instanceof BlockHitResult blockHit) {
            NpcNetwork.sendToServer(new NpcCommandPayload(type, blockHit.getBlockPos()));
        }
    }

    /** Server side: gives the command to every NPC bound to the sender's baton. */
    public static void handleServerCommand(Player player, CommandType type, boolean block, BlockPos pos, int entityId) {
        CommandedEntitiesProvider provider = commandedEntitiesProvider;
        if (provider == null) {
            return;
        }
        Command cmd = block ? new Command(type, pos) : new Command(type, entityId);
        for (Entity entity : provider.getCommandedEntities(player)) {
            if (entity instanceof NpcPlayerOwned npc) {
                npc.handlePlayerCommand(cmd.copy());
            }
        }
    }

    public static class Command {
        public static final Command NONE = new Command(CommandType.NONE, BlockPos.ZERO);
        private static final String IDMSB_TAG = "idmsb";
        private static final String IDLSB_TAG = "idlsb";

        public CommandType type;
        public BlockPos pos = BlockPos.ZERO;
        private boolean blockTarget;
        @Nullable
        private UUID entityUUID;
        private int entityID;
        @Nullable
        private Entity entity;

        public Command() {
        }

        public Command(CompoundTag tag) {
            readFromNBT(tag);
        }

        public Command(CommandType type, BlockPos pos) {
            this.type = type;
            this.pos = pos;
            blockTarget = true;
        }

        public Command(CommandType type, int entityID) {
            this.type = type;
            this.entityID = entityID;
            blockTarget = false;
        }

        public Command copy() {
            Command cmd = new Command();
            cmd.type = type;
            cmd.pos = pos;
            cmd.entity = entity;
            cmd.entityID = entityID;
            cmd.entityUUID = entityUUID;
            cmd.blockTarget = blockTarget;
            return cmd;
        }

        public final void readFromNBT(CompoundTag tag) {
            CommandType[] types = CommandType.values();
            int ordinal = tag.getInt("type");
            type = ordinal >= 0 && ordinal < types.length ? types[ordinal] : CommandType.NONE;
            blockTarget = tag.getBoolean("block");
            pos = BlockPos.of(tag.getLong("pos"));
            if (tag.contains(IDMSB_TAG) && tag.contains(IDLSB_TAG)) {
                entityUUID = new UUID(tag.getLong(IDMSB_TAG), tag.getLong(IDLSB_TAG));
            }
            entityID = tag.getInt("entityid");
        }

        public CompoundTag writeToNBT(CompoundTag tag) {
            tag.putInt("type", type.ordinal());
            tag.putBoolean("block", blockTarget);
            tag.putLong("pos", pos.asLong());
            if (entityUUID != null) {
                tag.putLong(IDMSB_TAG, entityUUID.getMostSignificantBits());
                tag.putLong(IDLSB_TAG, entityUUID.getLeastSignificantBits());
            }
            tag.putInt("entityid", entityID);
            return tag;
        }

        public boolean isBlockTarget() {
            return blockTarget;
        }

        /** Looks the entity target up: by id the first time (and then remembers its UUID), by UUID after a reload. */
        private void findEntity(Level world) {
            if (blockTarget || entity != null) {
                return;
            }
            if (entityUUID == null) {
                entity = world.getEntity(entityID);
                if (entity != null) {
                    entityUUID = entity.getUUID();
                }
            } else if (world instanceof ServerLevel serverLevel) {
                entity = serverLevel.getEntity(entityUUID);
            } else {
                entity = world.getPlayerByUUID(entityUUID);
            }
        }

        /** The entity the command targets, or null for a block target or an entity that cannot be found. */
        @Nullable
        public Entity getEntityTarget(Level world) {
            if (blockTarget) {
                return null;
            }
            if (entity != null && entity.isRemoved()) {
                entity = null;
            }
            if (entity == null) {
                findEntity(world);
            }
            return entity;
        }
    }

    /** For callers that need an immutable empty list of commanded entities. */
    public static List<Entity> noEntities() {
        return Collections.emptyList();
    }
}
