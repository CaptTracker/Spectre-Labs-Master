package io.github.capttracker.spectrefactions.data;

import io.github.capttracker.spectrefactions.api.ProposalType;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** A pending peace or alliance proposal from one player faction to another. */
public record Proposal(UUID from, UUID to, ProposalType type, long expiry) {
    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("from", from);
        t.putUUID("to", to);
        t.putString("type", type.name());
        t.putLong("expiry", expiry);
        return t;
    }

    static Proposal load(CompoundTag t) {
        ProposalType type = "ALLIANCE".equals(t.getString("type")) ? ProposalType.ALLIANCE : ProposalType.PEACE;
        return new Proposal(t.getUUID("from"), t.getUUID("to"), type, t.getLong("expiry"));
    }
}
