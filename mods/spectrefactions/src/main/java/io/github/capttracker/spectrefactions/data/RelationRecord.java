package io.github.capttracker.spectrefactions.data;

import io.github.capttracker.spectrefactions.api.Treaty;
import net.minecraft.nbt.CompoundTag;

/** The saved state between one unordered pair of actors. Read-only from outside the {@code data} package. */
public final class RelationRecord {
    int reputation;
    Treaty treaty = Treaty.NONE;
    boolean war;
    long warSince;

    RelationRecord(int reputation) {
        this.reputation = reputation;
    }

    public int getReputation() {
        return reputation;
    }

    public Treaty getTreaty() {
        return treaty;
    }

    public boolean isWar() {
        return war;
    }

    public long getWarSince() {
        return warSince;
    }

    boolean isDefault(int defaultReputation) {
        return reputation == defaultReputation && treaty == Treaty.NONE && !war;
    }

    void save(CompoundTag t) {
        t.putInt("rep", reputation);
        t.putString("treaty", treaty.name());
        t.putBoolean("war", war);
        t.putLong("warSince", warSince);
    }

    static RelationRecord load(CompoundTag t) {
        RelationRecord r = new RelationRecord(t.getInt("rep"));
        try {
            r.treaty = Treaty.valueOf(t.getString("treaty"));
        } catch (IllegalArgumentException e) {
            r.treaty = Treaty.NONE;
        }
        r.war = t.getBoolean("war");
        r.warSince = t.getLong("warSince");
        return r;
    }
}
