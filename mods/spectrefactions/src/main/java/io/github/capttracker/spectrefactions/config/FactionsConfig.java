package io.github.capttracker.spectrefactions.config;

import io.github.capttracker.spectrefactions.api.GuardMode;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code spectrefactions-server.toml}. Values are copied into the public static fields when the config loads or
 * reloads, so they are safe to read at any time (defaults until the config loads).
 *
 * <p>Penalty values are positive magnitudes; they are subtracted from reputation.
 */
public final class FactionsConfig {
    public static final String FILE_NAME = "spectrefactions-server.toml";

    // General
    public static volatile boolean defaultOpen = true;
    public static volatile int maxMembers = 0;
    public static volatile int inviteTimeoutTicks = 6000;
    public static volatile int proposalTimeoutTicks = 6000;
    public static volatile boolean chatTags = true;
    // Relations
    public static volatile int hostileBelow = -300;
    public static volatile int friendlyAbove = 300;
    public static volatile int alliedAbove = 700;
    public static volatile boolean reputationCanAlly = true;
    public static volatile int defaultReputation = 0;
    // Reputation
    public static volatile int attackPenalty = 5;
    public static volatile int killPlayerPenalty = 100;
    public static volatile int killNpcPenalty = 50;
    public static volatile int defendBonus = 20;
    public static volatile int warDeclarePenalty = 200;
    public static volatile int treatyBreakPenalty = 100;
    public static volatile int decayIntervalTicks = 24000;
    public static volatile int decayAmount = 10;
    // Rules
    public static volatile boolean friendlyFireProtection = true;
    public static volatile boolean officersCanDiplomacy = true;
    public static volatile boolean allowOrderedAttackOnFriends = false;
    public static volatile int guardRadius = 32;
    public static volatile GuardMode defaultPlayerGuardMode = GuardMode.GUARD;

    private static final ModConfigSpec.BooleanValue DEFAULT_OPEN;
    private static final ModConfigSpec.IntValue MAX_MEMBERS;
    private static final ModConfigSpec.IntValue INVITE_TIMEOUT;
    private static final ModConfigSpec.IntValue PROPOSAL_TIMEOUT;
    private static final ModConfigSpec.BooleanValue CHAT_TAGS;
    private static final ModConfigSpec.IntValue HOSTILE_BELOW;
    private static final ModConfigSpec.IntValue FRIENDLY_ABOVE;
    private static final ModConfigSpec.IntValue ALLIED_ABOVE;
    private static final ModConfigSpec.BooleanValue REP_CAN_ALLY;
    private static final ModConfigSpec.IntValue DEFAULT_REP;
    private static final ModConfigSpec.IntValue ATTACK_PENALTY;
    private static final ModConfigSpec.IntValue KILL_PLAYER_PENALTY;
    private static final ModConfigSpec.IntValue KILL_NPC_PENALTY;
    private static final ModConfigSpec.IntValue DEFEND_BONUS;
    private static final ModConfigSpec.IntValue WAR_PENALTY;
    private static final ModConfigSpec.IntValue TREATY_BREAK_PENALTY;
    private static final ModConfigSpec.IntValue DECAY_INTERVAL;
    private static final ModConfigSpec.IntValue DECAY_AMOUNT;
    private static final ModConfigSpec.BooleanValue FRIENDLY_FIRE;
    private static final ModConfigSpec.BooleanValue OFFICERS_DIPLOMACY;
    private static final ModConfigSpec.BooleanValue ORDERED_ATTACK_FRIENDS;
    private static final ModConfigSpec.IntValue GUARD_RADIUS;
    private static final ModConfigSpec.EnumValue<GuardMode> PLAYER_GUARD_MODE;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("General").push("general");
        DEFAULT_OPEN = b.comment("New factions are open (anyone can join without an invite).").define("defaultOpen", true);
        MAX_MEMBERS = b.comment("Maximum members per faction. 0 means no limit.").defineInRange("maxMembers", 0, 0, 10000);
        INVITE_TIMEOUT = b.comment("Ticks until an invite expires.").defineInRange("inviteTimeoutTicks", 6000, 20, Integer.MAX_VALUE);
        PROPOSAL_TIMEOUT = b.comment("Ticks until a peace or alliance proposal expires (6000 = 5 minutes).")
                .defineInRange("proposalTimeoutTicks", 6000, 20, Integer.MAX_VALUE);
        CHAT_TAGS = b.comment("Show [TAG] in chat, the tab list and nameplates.").define("chatTags", true);
        b.pop();

        b.comment("Relations: reputation thresholds").push("relations");
        HOSTILE_BELOW = b.comment("Reputation at or below this is HOSTILE.").defineInRange("hostileBelow", -300, -1000, 1000);
        FRIENDLY_ABOVE = b.comment("Reputation at or above this is FRIENDLY.").defineInRange("friendlyAbove", 300, -1000, 1000);
        ALLIED_ABOVE = b.comment("Reputation at or above this is ALLIED.").defineInRange("alliedAbove", 700, -1000, 1000);
        REP_CAN_ALLY = b.comment("Reputation alone can reach FRIENDLY and ALLIED. If false those bands need a treaty.")
                .define("reputationCanAlly", true);
        DEFAULT_REP = b.comment("Starting reputation between two actors.").defineInRange("defaultReputation", 0, -1000, 1000);
        b.pop();

        b.comment("Reputation changes (penalties are positive numbers that are subtracted)").push("reputation");
        ATTACK_PENALTY = b.comment("Lost when a player hurts a member or NPC of a NEUTRAL or better actor (at most once per 5 seconds per pair).")
                .defineInRange("attackPenalty", 5, 0, 2000);
        KILL_PLAYER_PENALTY = b.defineInRange("killPlayerPenalty", 100, 0, 2000);
        KILL_NPC_PENALTY = b.defineInRange("killNpcPenalty", 50, 0, 2000);
        DEFEND_BONUS = b.comment("Gained for killing an entity that was attacking a member or NPC of another actor.")
                .defineInRange("defendBonus", 20, 0, 2000);
        WAR_PENALTY = b.defineInRange("warDeclarePenalty", 200, 0, 2000);
        TREATY_BREAK_PENALTY = b.defineInRange("treatyBreakPenalty", 100, 0, 2000);
        DECAY_INTERVAL = b.comment("Ticks between reputation decay steps (0 disables decay).")
                .defineInRange("decayIntervalTicks", 24000, 0, Integer.MAX_VALUE);
        DECAY_AMOUNT = b.comment("How far reputation moves towards its default each step (not while at war).")
                .defineInRange("decayAmount", 10, 0, 2000);
        b.pop();

        b.comment("Rules").push("rules");
        FRIENDLY_FIRE = b.comment("Block player-versus-player damage between FRIENDLY or ALLIED actors.").define("friendlyFireProtection", true);
        OFFICERS_DIPLOMACY = b.comment("Officers may declare war and handle treaties.").define("officersCanDiplomacy", true);
        ORDERED_ATTACK_FRIENDS = b.comment("Owners may order NPCs to attack FRIENDLY or ALLIED targets (used by Ancient Warfare).")
                .define("allowOrderedAttackOnFriends", false);
        GUARD_RADIUS = b.comment("GUARD mode: radius around home or work position (used by Ancient Warfare).")
                .defineInRange("guardRadius", 32, 1, 512);
        PLAYER_GUARD_MODE = b.comment("Guard mode of new player factions and of factionless players.")
                .defineEnum("defaultPlayerGuardMode", GuardMode.GUARD);
        b.pop();
        SPEC = b.build();
    }

    private FactionsConfig() {}

    public static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, FILE_NAME);
        modBus.addListener((ModConfigEvent.Loading e) -> {
            if (e.getConfig().getSpec() == SPEC) {
                bake();
            }
        });
        modBus.addListener((ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getSpec() == SPEC) {
                bake();
            }
        });
    }

    /** Copies the spec's values into the static fields. Returns false while the config is not loaded. */
    public static boolean bake() {
        try {
            defaultOpen = DEFAULT_OPEN.get();
            maxMembers = MAX_MEMBERS.get();
            inviteTimeoutTicks = INVITE_TIMEOUT.get();
            proposalTimeoutTicks = PROPOSAL_TIMEOUT.get();
            chatTags = CHAT_TAGS.get();
            hostileBelow = HOSTILE_BELOW.get();
            friendlyAbove = FRIENDLY_ABOVE.get();
            alliedAbove = ALLIED_ABOVE.get();
            reputationCanAlly = REP_CAN_ALLY.get();
            defaultReputation = DEFAULT_REP.get();
            attackPenalty = ATTACK_PENALTY.get();
            killPlayerPenalty = KILL_PLAYER_PENALTY.get();
            killNpcPenalty = KILL_NPC_PENALTY.get();
            defendBonus = DEFEND_BONUS.get();
            warDeclarePenalty = WAR_PENALTY.get();
            treatyBreakPenalty = TREATY_BREAK_PENALTY.get();
            decayIntervalTicks = DECAY_INTERVAL.get();
            decayAmount = DECAY_AMOUNT.get();
            friendlyFireProtection = FRIENDLY_FIRE.get();
            officersCanDiplomacy = OFFICERS_DIPLOMACY.get();
            allowOrderedAttackOnFriends = ORDERED_ATTACK_FRIENDS.get();
            guardRadius = GUARD_RADIUS.get();
            defaultPlayerGuardMode = PLAYER_GUARD_MODE.get();
            return true;
        } catch (IllegalStateException e) {
            // the config is not loaded yet
            return false;
        }
    }
}
