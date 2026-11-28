/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (optional integration): the class of the tree farm that fells Dynamic Trees trees (with the seed plantable, the only one that names the
 * classes of the mod). It is created by {@link DynamicTreesFarmCompat} only when the mod is loaded.
 */
package io.github.capttracker.ancientwarfareautomation.compat.dynamictrees;

import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.block.branch.BasicRootsBlock;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.soil.SoilBlock;
import com.dtteam.dynamictrees.entity.FallingTreeEntity;
import com.dtteam.dynamictrees.systems.nodemapper.NetVolumeNode;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.dtteam.dynamictrees.tree.species.Species;
import io.github.capttracker.ancientwarfare.util.FakePlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Felling by the rules of Dynamic Trees. The steps are those of {@code BranchBlock.futureBreak}, the call Dynamic Trees makes when a player's axe breaks a
 * branch, with the direction of the blow given (the face of the trunk that the worker or the farm is on, so the tree falls away from it) instead of read from
 * where the entity looks:
 * <ol>
 *   <li>the break effect, then {@code destroyBranchFromNode}: the branches and leaves are taken out of the world and kept as the data of the falling tree;</li>
 *   <li>the logs and sticks of the wood volume (with the fortune of the axe) as the payload of the falling tree entity, which Dynamic Trees animates (or not,
 *       by its own config) and which spawns the drops as item entities; {@link FallenTreeDrops} collects those into the farm;</li>
 *   <li>the wear of the axe in the hand of the worker (a virtual iron axe with the fortune of the farm for a machine).</li>
 * </ol>
 */
final class DynamicTreesAdapter implements DynamicTreesSupport.Adapter {
    @Override
    public boolean isTrunkBranch(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BranchBlock && !(block instanceof BasicRootsBlock);
    }

    /** The block a player would cut to fell the whole tree: the branch right above the rooty soil, or the given one if the tree has no root. */
    private BlockPos footOf(ServerLevel level, BlockPos pos) {
        BlockPos root = TreeHelper.findRootNode(level, pos);
        if (!BlockPos.ZERO.equals(root) && level.getBlockState(root).getBlock() instanceof SoilBlock soil) {
            BlockPos above = root.relative(soil.getTrunkDirection(level, root));
            if (isTrunkBranch(level.getBlockState(above))) {
                return above;
            }
        }
        return pos;
    }

    @Nullable
    @Override
    public DynamicTreesSupport.Felled fell(ServerLevel level, BlockPos pos, @Nullable LivingEntity actor, int fortune, BlockEntity site, IItemHandler collector) {
        BlockPos cut = footOf(level, pos);
        BlockState state = level.getBlockState(cut);
        if (!isTrunkBranch(state)) {
            return null;
        }
        BranchBlock branch = (BranchBlock) state.getBlock();

        FakePlayer machine = null;
        ItemStack previous = ItemStack.EMPTY;
        LivingEntity user = actor;
        if (user == null) {
            machine = FakePlayers.get(level);
            previous = machine.getMainHandItem();
            machine.setItemInHand(InteractionHand.MAIN_HAND, virtualAxe(level, fortune));
            user = machine;
        }
        try {
            ItemStack held = user.getMainHandItem();
            Vec3 towards = actor != null ? actor.position() : Vec3.atCenterOf(site.getBlockPos());
            Direction toolDir = faceTowards(cut, towards);

            level.levelEvent(2001, cut, Block.getId(state));
            BranchDestructionData data = branch.destroyBranchFromNode(level, cut, toolDir, false, user);
            if (data.getNumBranches() == 0) {
                return null;
            }
            int fortuneLevel = Math.max(fortune, enchantLevel(level, held, Enchantments.FORTUNE));
            NetVolumeNode.Volume volume = data.woodVolume;
            volume.multiplyVolume(1.0F + 0.25F * fortuneLevel);
            List<ItemStack> wood = data.species.getBranchesDrops(level, volume, held);
            FallingTreeEntity.dropTree(level, data, wood, FallingTreeEntity.DestroyType.HARVEST);
            // the block break of a player already costs the axe one point, which damageAxe(..., true) subtracts; no block was broken by the worker here
            branch.damageAxe(user, held, branch.getRadius(state), volume, false);

            watchDrops(level, cut, data, site, collector);
            if (level.getBlockState(cut).getBlock() instanceof BranchBlock) {
                return null; // something stopped the felling: the tree is still there
            }
            return new DynamicTreesSupport.Felled(cut.immutable(), seedOf(data.species));
        } finally {
            if (machine != null) {
                machine.setItemInHand(InteractionHand.MAIN_HAND, previous);
            }
        }
    }

    private static void watchDrops(ServerLevel level, BlockPos cut, BranchDestructionData data, BlockEntity site, IItemHandler collector) {
        int reach = 0;
        int height = 0;
        for (int i = 0; i < data.getNumBranches(); i++) {
            BlockPos rel = data.getBranchRelPos(i);
            reach = Math.max(reach, Math.max(Math.abs(rel.getX()), Math.abs(rel.getZ())));
            height = Math.max(height, rel.getY());
        }
        for (int i = 0; i < data.getNumLeaves(); i++) {
            BlockPos rel = data.getLeavesRelPos(i);
            reach = Math.max(reach, Math.max(Math.abs(rel.getX()), Math.abs(rel.getZ())));
            height = Math.max(height, rel.getY());
        }
        // the crown falls as far from the cut as the tree is tall
        FallenTreeDrops.watch(level, FallenTreeDrops.boxAround(cut, Math.max(reach, height), height), site, collector,
                (serverLevel, box) -> !serverLevel.getEntitiesOfClass(FallingTreeEntity.class, box, tree -> tree.isAlive()).isEmpty());
    }

    /** The side face of the trunk on the side of {@code towards}: where a player standing there would hit it. */
    private static Direction faceTowards(BlockPos cut, Vec3 towards) {
        double dx = towards.x - (cut.getX() + 0.5D);
        double dz = towards.z - (cut.getZ() + 0.5D);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private static int enchantLevel(ServerLevel level, ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> enchantment) {
        if (stack.isEmpty()) {
            return 0;
        }
        Holder<Enchantment> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    /** The axe of a machine: an iron axe that carries the fortune of the farm. It exists only for the one felling. */
    private static ItemStack virtualAxe(ServerLevel level, int fortune) {
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        if (fortune > 0) {
            level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.FORTUNE).ifPresent(holder -> axe.enchant(holder, fortune));
        }
        return axe;
    }

    private static ItemStack seedOf(Species species) {
        ItemStack seed = species.getSeedStack(1);
        if (seed.isEmpty()) {
            seed = species.getCommonSpecies().getSeedStack(1);
        }
        return seed;
    }
}
