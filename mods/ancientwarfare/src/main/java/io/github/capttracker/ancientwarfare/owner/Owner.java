/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/owner/Owner.
 */
package io.github.capttracker.ancientwarfare.owner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable owner of a block entity or entity: a player UUID plus the name last seen for it.
 * {@link #EMPTY} (zero UUID, no name) means "no owner".
 */
public final class Owner {
    private static final UUID ZERO = new UUID(0, 0);
    public static final Owner EMPTY = new Owner();

    /** Saved and sent as a UUID plus name; an empty owner round-trips to the {@link #EMPTY} instance. */
    public static final Codec<Owner> CODEC = RecordCodecBuilder.<Owner>create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Owner::getUUID),
            Codec.STRING.fieldOf("name").forGetter(Owner::getName)
    ).apply(instance, Owner::new)).xmap(Owner::normalize, Owner::normalize);

    public static final StreamCodec<RegistryFriendlyByteBuf, Owner> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Owner::getUUID,
            ByteBufCodecs.STRING_UTF8, Owner::getName,
            (id, name) -> normalize(new Owner(id, name)));

    private static final String OWNER_NAME_TAG = "ownerName";
    private static final String OWNER_ID_TAG = "ownerId";
    private final UUID uuid;
    private final String name;

    private Owner() {
        this(ZERO, "");
    }

    private Owner(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    private static Owner normalize(Owner owner) {
        return owner.uuid.equals(ZERO) && owner.name.isEmpty() ? EMPTY : owner;
    }

    public Owner(Player player) {
        this(player.getUUID(), player.getGameProfile().getName());
    }

    /** Reads what {@link #serializeToBuffer} wrote. */
    public Owner(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf());
    }

    /** The owner with the given name; the UUID of an online player of that name, else the zero UUID. */
    public Owner(Level world, String name) {
        UUID found = ZERO;
        for (Player player : world.players()) {
            if (player.getGameProfile().getName().equals(name)) {
                found = player.getUUID();
                break;
            }
        }
        this.uuid = found;
        this.name = name;
    }

    public boolean isOwnerOrSameTeamOrFriend(@Nullable Entity entity) {
        // check our own implementation of the ownable entities
        if (entity instanceof IOwnable ownable) {
            Owner owner = ownable.getOwner();
            return isOwnerOrSameTeamOrFriend(entity.level(), owner.getUUID(), owner.getName());
        }
        // check if entity implements the vanilla interface, is ownable and has an owner
        if (entity instanceof OwnableEntity ownable) {
            LivingEntity owner = ownable.getOwner();
            if (owner != null) {
                return isOwnerOrSameTeamOrFriend(entity.level(), owner.getUUID(), owner.getScoreboardName());
            }
        }
        return entity != null && isOwnerOrSameTeamOrFriend(entity.level(), entity.getUUID(), entity.getScoreboardName());
    }

    public boolean isOwnerOrSameTeamOrFriend(Level world, @Nullable UUID playerId, String playerName) {
        return TeamViewerRegistry.areFriendly(world, uuid, playerId, name, playerName);
    }

    public String getName() {
        return name;
    }

    public UUID getUUID() {
        return uuid;
    }

    public void serializeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUUID(uuid);
        buffer.writeUtf(name);
    }

    public CompoundTag serializeToNBT(CompoundTag tag) {
        if (this == EMPTY) {
            return tag;
        }
        tag.putString(OWNER_NAME_TAG, name);
        tag.putUUID(OWNER_ID_TAG, uuid);

        return tag;
    }

    public static Owner deserializeFromNBT(CompoundTag tag) {
        if (tag.contains(OWNER_NAME_TAG)) {
            UUID id = tag.hasUUID(OWNER_ID_TAG) ? tag.getUUID(OWNER_ID_TAG) : ZERO;
            return new Owner(id, tag.getString(OWNER_NAME_TAG));
        }
        return Owner.EMPTY;
    }

    public boolean playerHasCommandPermissions(Level world, UUID playerId, String playerName) {
        return this != Owner.EMPTY && TeamViewerRegistry.areTeamMates(world, uuid, playerId, name, playerName);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Owner other && uuid.equals(other.uuid) && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, name);
    }

    @Override
    public String toString() {
        return name.isEmpty() ? "Owner[none]" : "Owner[" + name + " " + uuid + "]";
    }
}
