/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 10, children): births and coming of age.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.family;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcNames;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.skill.Skill;
import io.github.capttracker.ancientwarfarenpc.trait.NpcTraits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The births and the coming of age of the children (rework phase 10). Once a day a household that passes {@link FamilyApi#canHaveChild} has a child with the
 * chance {@code birth_chance_per_day}; the admin command can force one. The child is a worker in the child state ({@link NpcWorker#makeChild}): it appears at the
 * home of the household (else next to a parent), gets a first name from the name pool of the culture of its parents, the surname of the household, traits from
 * {@link FamilyApi#childTraits} and a head start in one of the best skills of its parents.
 */
public final class FamilyChildren {
    /** The result of a birth: the child, or the reason there is none (a blocker key of {@link FamilyApi#childBlocker}, {@code unloaded} or {@code failed}). */
    public record Birth(@Nullable NpcWorker child, String blocker) {}

    private FamilyChildren() {}

    // ---------------------------------------------------------------- the daily chance

    /** Once a day: every household that may have a child has one with the chance of the config. */
    static void dailyBirths(ServerLevel level, Settlement settlement, SettlementFamilies families) {
        if (SettlementConfig.birthChancePerDay <= 0.0D) {
            return;
        }
        for (Household household : new ArrayList<>(families.households())) {
            if (level.getRandom().nextDouble() < SettlementConfig.birthChancePerDay && FamilyApi.canHaveChild(level, settlement, household)) { // the cheap roll first
                birth(level, settlement, household, false);
            }
        }
    }

    // ---------------------------------------------------------------- a birth

    /**
     * Makes a child of the household. Without {@code force} the household must pass {@link FamilyApi#canHaveChild}; forced (the admin command) any household with
     * an adult will do. At least one parent must be loaded (the child appears next to it when the home is not loaded).
     */
    public static Birth birth(ServerLevel level, Settlement settlement, Household household, boolean force) {
        if (!force) {
            Optional<String> blocker = FamilyApi.childBlocker(level, settlement, household);
            if (blocker.isPresent()) {
                return new Birth(null, blocker.get());
            }
        } else if (household.getAdults().isEmpty()) {
            return new Birth(null, "no_couple");
        }
        UUID idA = household.getAdults().get(0);
        UUID idB = household.getAdults().size() > 1 ? household.getAdults().get(1) : null;
        NpcPlayerOwned parentA = SettlementFamilies.loaded(level, idA);
        NpcPlayerOwned parentB = idB == null ? null : SettlementFamilies.loaded(level, idB);
        NpcPlayerOwned near = parentA != null ? parentA : parentB;
        if (near == null) {
            return new Birth(null, "unloaded");
        }
        NpcBase made = AwNpcs.createNpc(level, "worker", "", "");
        if (!(made instanceof NpcWorker child)) {
            return new Birth(null, "failed");
        }
        child.setOwner(near.getOwner());

        // name: the culture of the parents, the surname of the household
        List<String> parentNames = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        for (UUID id : household.members()) {
            String given = settlement.nameOf(id);
            if (!given.isEmpty()) {
                taken.add(given);
                if (household.getAdults().contains(id)) {
                    parentNames.add(given);
                }
            }
        }
        String given = NpcNames.pickGivenLike(level.getRandom(), parentNames, taken);
        child.setGivenName(given);
        child.setSurname(FamilyApi.surnameFor(settlement, household));

        // traits from the two parents, a head start in one of their best skills
        NpcTraits traits = FamilyApi.childTraits(level, settlement, idA, idB, level.getRandom());
        child.setTraits(traits.list());
        giveHeadStart(level, child, parentA, parentB);

        child.makeChild(0L);
        child.setFoodRemaining(child.getMaxFood());
        BlockPos spot = findSpot(level, settlement, household, near);
        child.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
        child.setHomeAreaAtCurrentPosition();
        level.addFreshEntity(child);
        child.joinSettlement(settlement);
        if (!FamilyApi.registerChild(settlement, idA, idB, child.getUUID(), level.getGameTime())) {
            child.discard();
            return new Birth(null, "failed");
        }
        child.refreshMemberRecord();
        child.recomputeMorale(level);

        Component name = Component.literal(given);
        for (NpcPlayerOwned parent : new NpcPlayerOwned[]{parentA, parentB}) {
            if (parent != null) {
                parent.think("child_born", name);
                parent.recomputeMorale(level);
            }
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(settlement.getFounderId());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.ancientwarfarenpc.family.birth", name, child.getFullName(), settlement.getName()));
        }
        level.sendParticles(ParticleTypes.HEART, child.getX(), child.getY() + child.getBbHeight() + 0.2D, child.getZ(), 5, 0.3D, 0.2D, 0.3D, 0.02D);
        return new Birth(child, "");
    }

    /** One of the best skills of the loaded parents (a random parent's best skill) gives the child some experience. */
    private static void giveHeadStart(ServerLevel level, NpcWorker child, @Nullable NpcPlayerOwned parentA, @Nullable NpcPlayerOwned parentB) {
        if (SettlementConfig.childSkillHeadStart <= 0) {
            return;
        }
        List<Skill> best = new ArrayList<>(2);
        for (NpcPlayerOwned parent : new NpcPlayerOwned[]{parentA, parentB}) {
            if (parent == null) {
                continue;
            }
            Skill top = null;
            int topXp = 0;
            for (Skill skill : Skill.ALL) {
                if (parent.getSkills().getXp(skill) > topXp) {
                    topXp = parent.getSkills().getXp(skill);
                    top = skill;
                }
            }
            if (top != null) {
                best.add(top);
            }
        }
        if (!best.isEmpty()) {
            child.getSkills().add(best.get(level.getRandom().nextInt(best.size())), SettlementConfig.childSkillHeadStart);
        }
    }

    /** A free spot for the newborn: next to the home of the household if its chunk is loaded, else where the parent stands. */
    private static BlockPos findSpot(ServerLevel level, Settlement settlement, Household household, NpcPlayerOwned parent) {
        Optional<FamilyApi.Home> home = FamilyApi.homeOf(settlement, household);
        if (home.isPresent() && level.isLoaded(home.get().centre())) {
            BlockPos around = home.get().centre();
            for (int i = 0; i < 16; i++) {
                BlockPos p = around.offset(level.getRandom().nextInt(7) - 3, level.getRandom().nextInt(3) - 1, level.getRandom().nextInt(7) - 3);
                if (isFree(level, p)) {
                    return p;
                }
            }
        }
        return parent.blockPosition();
    }

    private static boolean isFree(ServerLevel level, BlockPos p) {
        return level.isLoaded(p) && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP);
    }

    // ---------------------------------------------------------------- coming of age

    /**
     * A child came of age (called by {@link NpcWorker#growUp}): out of the children of the household, a thought for it and for its parents, a message to the
     * owner. It is a grown worker without a role now: the job board and the owner give it work.
     */
    public static void onGrownUp(ServerLevel level, Settlement settlement, NpcWorker npc) {
        List<UUID> parents = settlement.getFamilies().parentsOf(npc.getUUID());
        FamilyApi.markGrownUp(settlement, npc.getUUID());
        Component name = Component.literal(npc.getGivenName());
        npc.think("grew_up");
        for (UUID id : parents) {
            if (SettlementFamilies.loaded(level, id) instanceof NpcPlayerOwned parent) {
                parent.think("child_grew_up", name);
            }
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(settlement.getFounderId());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.ancientwarfarenpc.family.grew_up", npc.getFullName(), settlement.getName()));
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, npc.getX(), npc.getY() + npc.getBbHeight() + 0.2D, npc.getZ(), 10, 0.4D, 0.3D, 0.4D, 0.0D);
    }
}
