package io.github.capttracker.spectrefactions.data;

import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionType;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.Rank;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A faction. Read-only from outside the {@code data} package; all changes go through {@link FactionsSavedData}
 * (through the faction service for players), which keeps the indexes and the sync flag up to date.
 */
public final class Faction {
    final UUID id;
    String name;
    String tag;
    ChatFormatting color;
    final FactionType type;
    boolean open;
    /** Insertion order is join order, which decides the succession. */
    final LinkedHashMap<UUID, Rank> members = new LinkedHashMap<>();
    GuardMode guardMode;
    final LinkedHashMap<UUID, Long> invites = new LinkedHashMap<>();
    final long createdAt;
    /** NPC factions only: the registered ResourceLocation string. */
    @Nullable
    String key;
    /** NPC factions only: starting reputation with other actors. */
    int defaultReputation;

    Faction(UUID id, String name, String tag, ChatFormatting color, FactionType type, boolean open, GuardMode guardMode, long createdAt) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.color = color;
        this.type = type;
        this.open = open;
        this.guardMode = guardMode;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public ActorId actor() {
        return ActorId.faction(id);
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public FactionType getType() {
        return type;
    }

    public boolean isNpc() {
        return type == FactionType.NPC;
    }

    public boolean isOpen() {
        return open;
    }

    public GuardMode getGuardMode() {
        return guardMode;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /** NPC factions: the registered key as a string such as {@code ancientwarfarenpc:bandit}; otherwise null. */
    @Nullable
    public String getKey() {
        return key;
    }

    public int getDefaultReputation() {
        return defaultReputation;
    }

    /** Member UUID to rank, in join order. Unmodifiable view. */
    public Map<UUID, Rank> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    /** Invited player UUID to the game time the invite expires. Unmodifiable view. */
    public Map<UUID, Long> getInvites() {
        return Collections.unmodifiableMap(invites);
    }

    @Nullable
    public Rank getRank(UUID player) {
        return members.get(player);
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }

    @Nullable
    public UUID getLeader() {
        for (Map.Entry<UUID, Rank> e : members.entrySet()) {
            if (e.getValue() == Rank.LEADER) {
                return e.getKey();
            }
        }
        return null;
    }

    public int memberCount() {
        return members.size();
    }

    /** The name in the faction colour. */
    public MutableComponent displayName() {
        return Component.literal(name).withStyle(color);
    }

    /** {@code [TAG]} in the faction colour. */
    public MutableComponent tagComponent() {
        return Component.literal("[" + tag + "]").withStyle(color);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("tag", tag);
        t.putString("color", color.getName());
        t.putString("type", type.name());
        t.putBoolean("open", open);
        t.putString("guard", guardMode.name());
        t.putLong("createdAt", createdAt);
        t.putInt("defaultRep", defaultReputation);
        if (key != null) {
            t.putString("key", key);
        }
        ListTag ms = new ListTag();
        members.forEach((u, r) -> {
            CompoundTag m = new CompoundTag();
            m.putUUID("id", u);
            m.putString("rank", r.name());
            ms.add(m);
        });
        t.put("members", ms);
        ListTag is = new ListTag();
        invites.forEach((u, exp) -> {
            CompoundTag m = new CompoundTag();
            m.putUUID("id", u);
            m.putLong("expiry", exp);
            is.add(m);
        });
        t.put("invites", is);
        return t;
    }

    static Faction load(CompoundTag t) {
        ChatFormatting color = ChatFormatting.getByName(t.getString("color"));
        if (color == null || !color.isColor()) {
            color = ChatFormatting.WHITE;
        }
        FactionType type = "NPC".equals(t.getString("type")) ? FactionType.NPC : FactionType.PLAYER;
        Faction f = new Faction(t.getUUID("id"), t.getString("name"), t.getString("tag"), color, type, t.getBoolean("open"),
                GuardMode.byName(t.getString("guard"), GuardMode.GUARD), t.getLong("createdAt"));
        f.defaultReputation = t.getInt("defaultRep");
        if (t.contains("key")) {
            f.key = t.getString("key");
        }
        ListTag ms = t.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < ms.size(); i++) {
            CompoundTag m = ms.getCompound(i);
            f.members.put(m.getUUID("id"), Rank.byName(m.getString("rank")));
        }
        ListTag is = t.getList("invites", Tag.TAG_COMPOUND);
        for (int i = 0; i < is.size(); i++) {
            CompoundTag m = is.getCompound(i);
            f.invites.put(m.getUUID("id"), m.getLong("expiry"));
        }
        return f;
    }
}
