/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/event/EventHandler.
 */
package io.github.capttracker.ancientwarfarenpc.event;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.event.IStructureProtection.StructureInfo;
import io.github.capttracker.ancientwarfarenpc.registry.FactionRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.NpcDefaultsRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.OwnedNpcDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;

/**
 * Module-wide game event handling:
 * <ul>
 *   <li>{@code EntityJoinLevelEvent} (lowest priority): every mob that already hunts players (it has a
 *       {@link NearestAttackableTargetGoal} for {@code Player}, or a goal that a compat {@linkplain #registerAdditionalHostileAICheck check}
 *       accepts) gets a second target goal at the same priority that makes it hunt NPCs that its faction or the owned-NPC defaults list as enemies.
 *       Hostile mobs inside a structure or town are not let into the level, unless tagged {@value #NO_SPAWN_PREVENTION_TAG}.</li>
 *   <li>{@code PlayerInteractEvent.RightClickBlock}: survival players cannot open the generated loot chests of a structure whose protection
 *       flag protects against them, or while a hostile faction NPC is inside the structure (the NPC glows for 10 seconds).</li>
 *   <li>{@code PlayerEvent.BreakSpeed}: in a protected structure digging is slowed to 1 percent, except for mob spawners.</li>
 * </ul>
 *
 * <p>The structure rules need the Structures module and run through {@link IStructureProtection}; without one installed they are off.
 * 1.21 differences: the goal lists are read through {@code GoalSelector#getAvailableGoals}, and the target class of a goal is a protected field
 * that is read by reflection; the target goal takes a {@code Predicate<LivingEntity>}.
 *
 * <p>Wiring: {@code NeoForge.EVENT_BUS.register(NpcEventHandler.INSTANCE)} (done by {@code AwNpcs.registerEvents}).
 */
public final class NpcEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String NO_SPAWN_PREVENTION_TAG = "noSpawnPrevention";
    public static final String GENERATED_INVENTORY_TAG = "generatedInventory";
    public static final NpcEventHandler INSTANCE = new NpcEventHandler();

    private final Set<Predicate<Goal>> additionalHostileAIChecks = new CopyOnWriteArraySet<>();
    @Nullable
    private volatile IStructureProtection structureProtection;
    @Nullable
    private static Field targetTypeField;
    private static boolean targetTypeFieldSearched = false;

    private NpcEventHandler() {}

    /** Lets another mod's mob AI count as "hunts players" (1.12.2: used for Epic Siege and similar). */
    public void registerAdditionalHostileAICheck(Predicate<Goal> aiCheck) {
        additionalHostileAIChecks.add(aiCheck);
    }

    /** Installed by the Structures module. */
    public void setStructureProtection(IStructureProtection protection) {
        this.structureProtection = protection;
    }

    // ---------------------------------------------------------------- entity join

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        injectAi(event.getEntity());
        preventHostileSpawnsInStructures(event);
    }

    private void preventHostileSpawnsInStructures(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        IStructureProtection protection = structureProtection;
        if (protection == null || event.getLevel().isClientSide() || !(entity instanceof Enemy) || entity.getTags().contains(NO_SPAWN_PREVENTION_TAG)) {
            return;
        }
        if (protection.shouldPreventSpawnAt(event.getLevel(), entity.blockPosition())) {
            event.setCanceled(true);
        }
    }

    private void injectAi(Entity entity) {
        if (entity instanceof NpcBase || !(entity instanceof PathfinderMob mob) || mob.level().isClientSide()) {
            return;
        }
        // all mobs that can attack somebody should have target goals
        int targetTaskPriority = getHostileAIPriority(mob);
        if (targetTaskPriority != -1) {
            mob.targetSelector.addGoal(targetTaskPriority,
                    new NearestAttackableTargetGoal<>(mob, NpcBase.class, 0, true, false, e -> entityShouldTarget(mob, e)));
        }
    }

    /** The priority of the mob's goal that hunts players (or that a compat check accepts), or -1. */
    private int getHostileAIPriority(PathfinderMob mob) {
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if ((isPlayerTargetGoal(goal) || anyAdditionalHostileAIChecksMatch(goal)) && wrapped.getPriority() != -1) {
                return wrapped.getPriority();
            }
        }
        return -1;
    }

    private static boolean isPlayerTargetGoal(Goal goal) {
        if (!(goal instanceof NearestAttackableTargetGoal<?> targetGoal)) {
            return false;
        }
        Field field = getTargetTypeField();
        if (field == null) {
            return false;
        }
        try {
            return field.get(targetGoal) == Player.class;
        } catch (IllegalAccessException | RuntimeException e) {
            return false;
        }
    }

    /** The protected {@code targetType} field of {@link NearestAttackableTargetGoal}: its only non-static field of type Class. */
    @Nullable
    private static synchronized Field getTargetTypeField() {
        if (!targetTypeFieldSearched) {
            targetTypeFieldSearched = true;
            for (Field field : NearestAttackableTargetGoal.class.getDeclaredFields()) {
                if (field.getType() == Class.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        targetTypeField = field;
                    } catch (RuntimeException e) {
                        LOGGER.error("Can't read the target class of target goals, mobs will not be made to hunt NPCs", e);
                    }
                    break;
                }
            }
        }
        return targetTypeField;
    }

    private boolean anyAdditionalHostileAIChecksMatch(Goal goal) {
        for (Predicate<Goal> additionalCheck : additionalHostileAIChecks) {
            if (additionalCheck.test(goal)) {
                return true;
            }
        }
        return false;
    }

    private boolean entityShouldTarget(PathfinderMob mob, @Nullable LivingEntity e) {
        if (e instanceof NpcFaction factionNpc) {
            if (!factionNpc.isPassive() && FactionRegistry.getFaction(factionNpc.getFaction()).isTarget(mob)) {
                return true;
            }
        }
        if (e instanceof NpcPlayerOwned owned) {
            OwnedNpcDefault ownedDefault = NpcDefaultsRegistry.getOwnedNpcDefault(owned.getNpcType());
            return ownedDefault != null && ownedDefault.isTarget(mob);
        }
        return false;
    }

    // ---------------------------------------------------------------- loot chests and digging in structures

    @SubscribeEvent
    public void onChestClicked(PlayerInteractEvent.RightClickBlock event) {
        IStructureProtection protection = structureProtection;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        if (protection == null || player.isCreative() || !protection.isLockedContainer(level, pos)) {
            return;
        }
        Optional<StructureInfo> structure = protection.getStructureAt(level, pos);
        if (structure.isEmpty()) {
            return;
        }
        if (protection.shouldProtectAgainst(level, structure.get(), player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (level.isClientSide()) {
                player.displayClientMessage(Component.translatable("gui.ancientwarfarenpc.no_chest_access_flag_not_claimed"), true);
            }
            return;
        }
        List<NpcFaction> factionNpcs = level.getEntitiesOfClass(NpcFaction.class, structure.get().bounds());
        for (NpcFaction factionNpc : factionNpcs) {
            if (!factionNpc.isPassive()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                factionNpc.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200));
                if (level.isClientSide()) {
                    player.displayClientMessage(Component.translatable("gui.ancientwarfarenpc.no_chest_access", capitalize(factionNpc.getFaction())), true);
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public void playerDigSpeed(PlayerEvent.BreakSpeed event) {
        IStructureProtection protection = structureProtection;
        Player player = event.getEntity();
        Optional<BlockPos> position = event.getPosition();
        if (protection == null || position.isEmpty() || player.isCreative() || player.isSpectator()) {
            return;
        }
        Level level = player.level();
        BlockPos pos = position.get();
        protection.getStructureAt(level, pos).ifPresent(structure -> {
            if (protection.shouldProtectAgainst(level, structure, player) && shouldBlockSlowDownDigging(protection, level, pos)) {
                event.setNewSpeed(event.getOriginalSpeed() * 0.01f);
            }
        });
    }

    private boolean shouldBlockSlowDownDigging(IStructureProtection protection, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isCollisionShapeFullBlock(level, pos)) {
            return protection.isLockedContainer(level, pos);
        }
        return !protection.isSpawnerBlock(state);
    }

    private static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
