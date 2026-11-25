package io.github.capttracker.spectrefactions.api;

import io.github.capttracker.spectrefactions.data.Faction;
import net.neoforged.bus.api.Event;

import java.util.UUID;

/**
 * Events posted on {@code NeoForge.EVENT_BUS}, on the server thread, after the change was made. None can be cancelled.
 */
public abstract class FactionEvent extends Event {
    /** A player faction was created. */
    public static class Created extends FactionEvent {
        private final Faction faction;

        public Created(Faction faction) {
            this.faction = faction;
        }

        public Faction getFaction() {
            return faction;
        }
    }

    /** A faction was disbanded. The faction object still has its last members. */
    public static class Disbanded extends FactionEvent {
        private final Faction faction;

        public Disbanded(Faction faction) {
            this.faction = faction;
        }

        public Faction getFaction() {
            return faction;
        }
    }

    public static class MemberJoined extends FactionEvent {
        private final Faction faction;
        private final UUID player;

        public MemberJoined(Faction faction, UUID player) {
            this.faction = faction;
            this.player = player;
        }

        public Faction getFaction() {
            return faction;
        }

        public UUID getPlayer() {
            return player;
        }
    }

    /** A member left or was kicked. A disband does not report its members here (see {@link Disbanded}). */
    public static class MemberLeft extends FactionEvent {
        private final Faction faction;
        private final UUID player;

        public MemberLeft(Faction faction, UUID player) {
            this.faction = faction;
            this.player = player;
        }

        public Faction getFaction() {
            return faction;
        }

        public UUID getPlayer() {
            return player;
        }
    }

    /** The effective relation between two actors changed. Fired once per pair. */
    public static class RelationChanged extends FactionEvent {
        private final ActorId a;
        private final ActorId b;
        private final Relation oldRelation;
        private final Relation newRelation;

        public RelationChanged(ActorId a, ActorId b, Relation oldRelation, Relation newRelation) {
            this.a = a;
            this.b = b;
            this.oldRelation = oldRelation;
            this.newRelation = newRelation;
        }

        public ActorId getA() {
            return a;
        }

        public ActorId getB() {
            return b;
        }

        public Relation getOldRelation() {
            return oldRelation;
        }

        public Relation getNewRelation() {
            return newRelation;
        }
    }

    /** Reputation between two actors changed (extra to the specified events). */
    public static class ReputationChanged extends FactionEvent {
        private final ActorId a;
        private final ActorId b;
        private final int oldReputation;
        private final int newReputation;
        private final String reasonKey;

        public ReputationChanged(ActorId a, ActorId b, int oldReputation, int newReputation, String reasonKey) {
            this.a = a;
            this.b = b;
            this.oldReputation = oldReputation;
            this.newReputation = newReputation;
            this.reasonKey = reasonKey;
        }

        public ActorId getA() {
            return a;
        }

        public ActorId getB() {
            return b;
        }

        public int getOldReputation() {
            return oldReputation;
        }

        public int getNewReputation() {
            return newReputation;
        }

        public String getReasonKey() {
            return reasonKey;
        }
    }
}
