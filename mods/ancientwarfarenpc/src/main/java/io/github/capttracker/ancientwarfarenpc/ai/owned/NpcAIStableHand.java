/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): a stable hand staffs a stable and looks after the horses.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IStable;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcStableHand;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command;
import io.github.capttracker.ancientwarfarenpc.settlement.Activity;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.hitch.CartHitch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The stable hand of a settlement staffs a stable in the work band: it picks the nearest stable that no other stable hand holds, stays within the
 * stable radius and reports in every half second. Every {@code stable_hand_interval} ticks, when it has nothing to do, it picks the first of these
 * jobs among the horses, donkeys and mules within the radius of the stable:
 * <ol>
 *   <li><b>tame</b>: a wild adult or foal (a foal is born wild): it stays with the animal for {@code stable_tame_ticks}, then the animal is tame and
 *       belongs to the owner of the stable hand;</li>
 *   <li><b>feed</b>: a tame animal that is hurt, or a foal, gets one feed item from the feed store of the stable (a heal, or faster growth);</li>
 *   <li><b>breed</b>: while there are fewer than {@code stable_max_equines} animals, two tame, healthy adults that can mate (horses and donkeys, not
 *       mules) are given a golden carrot or apple each and fall in love; the animals mate by themselves;</li>
 *   <li><b>hitch</b>: a cart or wagon of the owner that stands without a horse gets the nearest ready horse (tame, grown up, healthy, not in love):
 *       the horse is walked to the drawbar and hitched with the hitch API of the vehicles.</li>
 * </ol>
 * With nothing to do it walks back to the block in front of the stable.
 */
public class NpcAIStableHand extends NpcAI<NpcStableHand> {
    private enum Job { NONE, TAME, FEED, BREED, HITCH }

    private static final int SEARCH_INTERVAL = 40;
    private static final int REPORT_INTERVAL = 10;
    private static final double ACT_RANGE_SQ = 2.8D * 2.8D;
    private static final int JOB_TIMEOUT = 1200;
    private static final long BREED_COOLDOWN = 6000L;

    private long nextSearch = 0;
    private long nextJob = 0;
    @Nullable
    private BlockPos stablePos;
    @Nullable
    private BlockPos standPos;
    private int reportDelay = 0;

    private Job job = Job.NONE;
    @Nullable
    private UUID targetId;
    @Nullable
    private UUID partnerId;
    private int jobTicks;
    private int jobAge;
    /** When each animal was last fed or bred (game time); not saved. */
    private final Map<UUID, Long> fedAt = new HashMap<>();
    private final Map<UUID, Long> bredAt = new HashMap<>();

    public NpcAIStableHand(NpcStableHand npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    private boolean mayWork() {
        return !npc.isAlarmed && npc.getTarget() == null && !npc.isPassenger() && npc.getActivity() == Activity.WORK && !npc.shouldBeAtHome()
                && npc.getCurrentCommand() == Command.NONE && npc.level() instanceof ServerLevel;
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.tickCount < nextSearch || !mayWork()) {
            return false;
        }
        nextSearch = npc.tickCount + SEARCH_INTERVAL;
        ServerLevel level = (ServerLevel) npc.level();
        Optional<Settlement> settlement = npc.getSettlement().filter(s -> s.getDimension().equals(level.dimension()));
        if (settlement.isEmpty()) {
            return false;
        }
        IStable best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockEntity be : WorksiteRegistry.inLevel(level, settlement.get()::contains)) {
            if (!(be instanceof IStable stable) || !(be instanceof IWorkSite site) || !JobBoard.mayUse(settlement.get(), site)) {
                continue;
            }
            if (stable.getKeeper().filter(id -> !id.equals(npc.getUUID())).isPresent()) {
                continue; // another stable hand has it
            }
            double distance = npc.distanceToSqr(be.getBlockPos().getX() + 0.5D, be.getBlockPos().getY(), be.getBlockPos().getZ() + 0.5D);
            if (distance < bestDistance) {
                best = stable;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return false;
        }
        stablePos = best.getStablePos();
        standPos = best.getStandPos();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canUse() && stablePos != null && mayWork() && stableAt() != null;
    }

    @Nullable
    private IStable stableAt() {
        if (stablePos != null && npc.level().isLoaded(stablePos) && npc.level().getBlockEntity(stablePos) instanceof IStable stable) {
            return stable;
        }
        return null;
    }

    @Override
    public void start() {
        reportDelay = 0;
        nextJob = 0;
        endJob();
        npc.setStablePos(stablePos);
        npc.addAITask(TASK_WORK);
    }

    private void endJob() {
        job = Job.NONE;
        targetId = null;
        partnerId = null;
        jobTicks = 0;
        jobAge = 0;
    }

    // ---------------------------------------------------------------- the tick

    @Override
    public void tick() {
        IStable stable = stableAt();
        BlockPos stand = standPos;
        BlockPos at = stablePos;
        if (stable == null || stand == null || at == null || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        double range = stable.getRadius() + 3.0D;
        if (npc.distanceToSqr(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D) <= range * range && reportDelay-- <= 0) {
            reportDelay = REPORT_INTERVAL;
            if (!stable.staff(npc.getUUID(), level.getGameTime())) {
                stablePos = null; // another stable hand got there first: the goal ends, the next search takes another stable
                return;
            }
        }
        if (job == Job.NONE) {
            if (npc.tickCount >= nextJob) {
                nextJob = npc.tickCount + SettlementConfig.stableHandInterval;
                chooseJob(level, stable);
            }
            if (job == Job.NONE) {
                double distance = npc.distanceToSqr(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
                if (distance > 2.0D) {
                    npc.addAITask(TASK_MOVE);
                    moveToPosition(stand.getX(), stand.getY(), stand.getZ(), distance);
                } else {
                    npc.getNavigation().stop();
                    npc.removeAITask(TASK_MOVE);
                }
                return;
            }
        }
        jobAge++;
        if (jobAge > JOB_TIMEOUT) {
            endJob();
            return;
        }
        switch (job) {
            case TAME -> tickTame(level);
            case FEED -> tickFeed(level, stable);
            case BREED -> tickBreed(level, stable);
            case HITCH -> tickHitch(level);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- choosing

    private static boolean isEquine(AbstractHorse horse) {
        return horse instanceof Horse || horse instanceof Donkey || horse instanceof Mule;
    }

    private List<AbstractHorse> equinesAround(ServerLevel level, IStable stable) {
        AABB box = new AABB(stable.getStablePos()).inflate(stable.getRadius());
        return new ArrayList<>(level.getEntitiesOfClass(AbstractHorse.class, box,
                h -> h.isAlive() && isEquine(h) && !h.isVehicle() && !h.isPassenger() && !h.isLeashed() && CartHitch.cartOf(h).isEmpty()));
    }

    private void chooseJob(ServerLevel level, IStable stable) {
        long now = level.getGameTime();
        List<AbstractHorse> equines = equinesAround(level, stable);
        IItemHandler store = stable.getFeedStore();

        // 1. tame
        AbstractHorse wild = nearest(equines, h -> !h.isTamed());
        if (wild != null) {
            start(Job.TAME, wild.getUUID(), null);
            return;
        }
        // 2. feed
        if (findFeed(store, false) >= 0 || findFeed(store, true) >= 0) {
            AbstractHorse hungry = nearest(equines, h -> h.isTamed() && (h.getHealth() < h.getMaxHealth() || h.isBaby()) && now - fedAt.getOrDefault(h.getUUID(), -1_000_000L) > SettlementConfig.stableFeedCooldown);
            if (hungry != null) {
                start(Job.FEED, hungry.getUUID(), null);
                return;
            }
        }
        // 3. breed
        if (equines.size() < stable.getMaxEquines() && countBreedingFeed(store) >= 2) {
            List<AbstractHorse> parents = new ArrayList<>();
            for (AbstractHorse h : equines) {
                if (h.isTamed() && !h.isBaby() && (h instanceof Horse || h instanceof Donkey) && h.getHealth() >= h.getMaxHealth() && h.canFallInLove()
                        && !h.isInLove() && now - bredAt.getOrDefault(h.getUUID(), -1_000_000L) > BREED_COOLDOWN) {
                    parents.add(h);
                }
            }
            for (int i = 0; i < parents.size(); i++) {
                for (int j = i + 1; j < parents.size(); j++) {
                    if (compatible(parents.get(i), parents.get(j))) {
                        start(Job.BREED, parents.get(i).getUUID(), parents.get(j).getUUID());
                        return;
                    }
                }
            }
        }
        // 4. hitch
        VehicleBase cart = nearestCart(level, stable);
        if (cart != null) {
            AbstractHorse ready = null;
            double best = Double.MAX_VALUE;
            for (AbstractHorse h : equines) {
                if (CartHitch.isHitchable(h) && h.getHealth() >= h.getMaxHealth() * 0.8F && !h.isInLove() && h.distanceToSqr(cart) < best) {
                    best = h.distanceToSqr(cart);
                    ready = h;
                }
            }
            if (ready != null) {
                start(Job.HITCH, cart.getUUID(), ready.getUUID());
            }
        }
    }

    private void start(Job next, UUID target, @Nullable UUID partner) {
        job = next;
        targetId = target;
        partnerId = partner;
        jobTicks = 0;
        jobAge = 0;
    }

    private static boolean compatible(AbstractHorse a, AbstractHorse b) {
        return (a instanceof Horse || a instanceof Donkey) && (b instanceof Horse || b instanceof Donkey);
    }

    @Nullable
    private AbstractHorse nearest(List<AbstractHorse> list, java.util.function.Predicate<AbstractHorse> filter) {
        AbstractHorse best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AbstractHorse h : list) {
            if (filter.test(h) && npc.distanceToSqr(h) < bestDistance) {
                best = h;
                bestDistance = npc.distanceToSqr(h);
            }
        }
        return best;
    }

    @Nullable
    private VehicleBase nearestCart(ServerLevel level, IStable stable) {
        Owner mine = npc.getOwner();
        AABB box = new AABB(stable.getStablePos()).inflate(stable.getRadius());
        VehicleBase best = null;
        for (VehicleBase v : level.getEntitiesOfClass(VehicleBase.class, box,
                v -> v.isAlive() && v.vehicleType.isHorseDrawn() && !v.hitch.isHitched() && v.getPassengers().isEmpty())) {
            Owner owner = v.getOwner();
            boolean ok = owner == null || owner == Owner.EMPTY || mine == null || mine == Owner.EMPTY || owner.getUUID().equals(mine.getUUID());
            if (ok && (best == null || v.distanceToSqr(npc) < best.distanceToSqr(npc))) {
                best = v;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- feed store

    /** The slot of a feed item (breeding feed only when {@code breeding}); -1 if none. */
    private static int findFeed(IItemHandler store, boolean breeding) {
        for (int slot = 0; slot < store.getSlots(); slot++) {
            ItemStack stack = store.getStackInSlot(slot);
            if (!stack.isEmpty() && IStable.isFeed(stack) && IStable.isBreedingFeed(stack) == breeding) {
                return slot;
            }
        }
        return -1;
    }

    private static int countBreedingFeed(IItemHandler store) {
        int count = 0;
        for (int slot = 0; slot < store.getSlots(); slot++) {
            ItemStack stack = store.getStackInSlot(slot);
            if (IStable.isBreedingFeed(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ItemStack takeFeed(IItemHandler store) {
        int slot = findFeed(store, false);
        if (slot < 0) {
            slot = findFeed(store, true);
        }
        return slot < 0 ? ItemStack.EMPTY : store.extractItem(slot, 1, false);
    }

    private static ItemStack takeBreedingFeed(IItemHandler store) {
        int slot = findFeed(store, true);
        return slot < 0 ? ItemStack.EMPTY : store.extractItem(slot, 1, false);
    }

    // ---------------------------------------------------------------- the jobs

    @Nullable
    private AbstractHorse horse(ServerLevel level, @Nullable UUID id) {
        return id != null && level.getEntity(id) instanceof AbstractHorse h && h.isAlive() ? h : null;
    }

    /** Walks to the animal; true when within reach. */
    private boolean approach(AbstractHorse horse) {
        double distance = npc.distanceToSqr(horse);
        if (distance > ACT_RANGE_SQ) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(horse.getX(), horse.getY(), horse.getZ(), distance);
            return false;
        }
        npc.getNavigation().stop();
        npc.removeAITask(TASK_MOVE);
        npc.getLookControl().setLookAt(horse, 30.0F, 30.0F);
        return true;
    }

    private void tickTame(ServerLevel level) {
        AbstractHorse horse = horse(level, targetId);
        if (horse == null || horse.isTamed() || horse.isVehicle()) {
            endJob();
            return;
        }
        if (!approach(horse)) {
            return;
        }
        jobTicks++;
        if (jobTicks % 20 == 0) {
            npc.swing(InteractionHand.MAIN_HAND);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, horse.getX(), horse.getY() + 1.0D, horse.getZ(), 3, 0.3D, 0.3D, 0.3D, 0.01D);
        }
        if (jobTicks >= SettlementConfig.stableTameTicks) {
            horse.setTamed(true);
            Owner owner = npc.getOwner();
            if (owner != null && owner != Owner.EMPTY) {
                horse.setOwnerUUID(owner.getUUID());
            }
            level.broadcastEntityEvent(horse, (byte) 7); // hearts
            endJob();
        }
    }

    private void tickFeed(ServerLevel level, IStable stable) {
        AbstractHorse horse = horse(level, targetId);
        if (horse == null || !horse.isTamed()) {
            endJob();
            return;
        }
        if (!approach(horse)) {
            return;
        }
        ItemStack feed = takeFeed(stable.getFeedStore());
        if (!feed.isEmpty()) {
            npc.swing(InteractionHand.MAIN_HAND);
            if (horse.getHealth() < horse.getMaxHealth()) {
                horse.heal(4.0F);
            }
            if (horse.isBaby()) {
                horse.ageUp(Math.max(1, (int) (-horse.getAge() / 20 * 0.1F)), true);
            }
            level.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.HORSE_EAT, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        fedAt.put(horse.getUUID(), level.getGameTime());
        endJob();
    }

    private void tickBreed(ServerLevel level, IStable stable) {
        AbstractHorse first = horse(level, targetId);
        AbstractHorse second = horse(level, partnerId);
        if (first == null || second == null || !first.canFallInLove() || !second.canFallInLove()) {
            endJob();
            return;
        }
        if (!approach(first)) {
            return;
        }
        IItemHandler store = stable.getFeedStore();
        if (countBreedingFeed(store) >= 2) {
            takeBreedingFeed(store);
            takeBreedingFeed(store);
            npc.swing(InteractionHand.MAIN_HAND);
            first.setInLove(null);
            second.setInLove(null);
            long now = level.getGameTime();
            bredAt.put(first.getUUID(), now);
            bredAt.put(second.getUUID(), now);
        }
        endJob();
    }

    private void tickHitch(ServerLevel level) {
        VehicleBase cart = level.getEntity(targetId == null ? UUID.randomUUID() : targetId) instanceof VehicleBase v && v.isAlive() ? v : null;
        AbstractHorse horse = horse(level, partnerId);
        if (cart == null || horse == null || cart.hitch.isHitched() || !CartHitch.isHitchable(horse)) {
            endJob();
            return;
        }
        // the point in front of the drawbar, where the horse stands to be hitched
        double yaw = Math.toRadians(cart.getYRot());
        double length = cart.vehicleType.getHitchOffset();
        double dx = -Math.sin(yaw) * (length + 1.5D);
        double dz = -Math.cos(yaw) * (length + 1.5D);
        Vec3 spot = new Vec3(cart.getX() + dx, cart.getY(), cart.getZ() + dz);
        if (npc.distanceToSqr(cart) > 36.0D) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(cart.getX(), cart.getY(), cart.getZ(), npc.distanceToSqr(cart));
        } else {
            npc.getNavigation().stop();
            npc.removeAITask(TASK_MOVE);
        }
        jobTicks++;
        if (jobTicks % 10 == 1) {
            horse.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.1D);
        }
        if (jobTicks % 10 == 5 || horse.distanceToSqr(spot) < 4.0D) {
            CartHitch.Result result = cart.hitch.hitch(horse);
            if (result == CartHitch.Result.OK) {
                npc.swing(InteractionHand.MAIN_HAND);
                horse.getNavigation().stop();
                endJob();
            } else if (result != CartHitch.Result.TOO_FAR) {
                endJob();
            }
        }
    }

    @Override
    public void stop() {
        IStable stable = stableAt();
        if (stable != null) {
            stable.release(npc.getUUID());
        }
        endJob();
        stablePos = null;
        standPos = null;
        npc.setStablePos(null);
        npc.getNavigation().stop();
        npc.removeAITask(TASK_WORK + TASK_MOVE);
        nextSearch = npc.tickCount + SEARCH_INTERVAL;
    }
}
