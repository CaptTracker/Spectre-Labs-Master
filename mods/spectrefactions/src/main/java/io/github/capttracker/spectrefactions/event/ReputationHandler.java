package io.github.capttracker.spectrefactions.event;

import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import io.github.capttracker.spectrefactions.api.Relation;
import io.github.capttracker.spectrefactions.config.FactionsConfig;
import io.github.capttracker.spectrefactions.data.FactionsSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reputation from combat: attack, kill and defend; friendly-fire protection between players. Kills by an owned NPC
 * are attributed to its owner's actor through the registered actor resolvers.
 */
public final class ReputationHandler {
    private static final int ATTACK_COOLDOWN_TICKS = 100;
    private static final int DEFEND_WINDOW_TICKS = 200;

    private record AttackEntry(ActorId victimActor, long tick) {}

    /** Attacker entity to the actor it last hurt. */
    private static final Map<UUID, AttackEntry> ATTACKS = new HashMap<>();
    private static final Map<FactionsSavedData.PairKey, Long> LAST_PENALTY = new HashMap<>();

    private ReputationHandler() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide() || !FactionsConfig.friendlyFireProtection) {
            return;
        }
        if (e.getSource().getEntity() instanceof ServerPlayer attacker && victim instanceof ServerPlayer target && attacker != target) {
            Relation r = FactionsApi.getRelation(FactionsApi.actorOf(attacker.getUUID()), FactionsApi.actorOf(target.getUUID()));
            if (r.isFriendlyOrBetter()) {
                e.setCanceled(true);
                attacker.displayClientMessage(Component.translatable("message.spectrefactions.friendly_fire_blocked"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        Entity attacker = e.getSource().getEntity();
        if (attacker == null || attacker == victim) {
            return;
        }
        FactionsSavedData d = FactionsSavedData.current();
        Optional<ActorId> victimActor = FactionsApi.actorOf(victim);
        if (d == null || victimActor.isEmpty()) {
            return;
        }
        long now = d.now();
        if (ATTACKS.size() > 1024) {
            ATTACKS.values().removeIf(a -> now - a.tick > DEFEND_WINDOW_TICKS);
            LAST_PENALTY.values().removeIf(t -> now - t > ATTACK_COOLDOWN_TICKS);
        }
        ATTACKS.put(attacker.getUUID(), new AttackEntry(victimActor.get(), now));

        if (attacker instanceof ServerPlayer player) {
            ActorId a = d.actorOfPlayer(player.getUUID());
            ActorId b = victimActor.get();
            if (!a.equals(b) && d.relation(a, b) != Relation.HOSTILE) {
                FactionsSavedData.PairKey key = FactionsSavedData.PairKey.of(a, b);
                Long last = LAST_PENALTY.get(key);
                if (last == null || now - last >= ATTACK_COOLDOWN_TICKS) {
                    LAST_PENALTY.put(key, now);
                    d.adjustReputation(a, b, -FactionsConfig.attackPenalty, "attack");
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        Entity killer = e.getSource().getEntity();
        FactionsSavedData d = FactionsSavedData.current();
        if (killer == null || d == null) {
            return;
        }
        Optional<ActorId> killerActor = FactionsApi.actorOf(killer);
        Optional<ActorId> victimActor = FactionsApi.actorOf(victim);
        if (killerActor.isEmpty()) {
            return;
        }
        ActorId ka = killerActor.get();

        if (victimActor.isPresent() && !ka.equals(victimActor.get()) && d.relation(ka, victimActor.get()) != Relation.HOSTILE) {
            int penalty = victim instanceof Player ? FactionsConfig.killPlayerPenalty : FactionsConfig.killNpcPenalty;
            d.adjustReputation(ka, victimActor.get(), -penalty, victim instanceof Player ? "kill_player" : "kill_npc");
        }

        // The victim was attacking a member or an NPC of some actor: that actor is grateful.
        Set<ActorId> defended = new HashSet<>();
        AttackEntry entry = ATTACKS.remove(victim.getUUID());
        if (entry != null && d.now() - entry.tick <= DEFEND_WINDOW_TICKS) {
            defended.add(entry.victimActor);
        }
        if (victim instanceof Mob mob && mob.getTarget() != null) {
            FactionsApi.actorOf(mob.getTarget()).ifPresent(defended::add);
        }
        for (ActorId c : defended) {
            if (!c.equals(ka) && !(victimActor.isPresent() && c.equals(victimActor.get()))) {
                d.adjustReputation(ka, c, FactionsConfig.defendBonus, "defend");
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        ATTACKS.clear();
        LAST_PENALTY.clear();
    }
}
