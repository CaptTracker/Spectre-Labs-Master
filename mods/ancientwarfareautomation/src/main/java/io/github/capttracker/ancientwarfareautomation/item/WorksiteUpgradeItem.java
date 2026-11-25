/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/item/ItemWorksiteUpgrade (an ItemMulti with 11 damage values).
 */
package io.github.capttracker.ancientwarfareautomation.item;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.upgrade.WorksiteUpgrade;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.HashSet;

/**
 * One worksite upgrade (the 11 items replace the damage values of the original {@code worksite_upgrade} item).
 * Using it on a worksite installs the upgrade, if the worksite accepts it, does not have it yet and none of its
 * upgrades excludes it; upgrades it overrides are removed and dropped as items. The item is used up, except in
 * creative mode.
 *
 * <p>1.12.2 did this in {@code onItemRightClick} on the block the player looked at; {@code useOn} is the 1.21 call
 * for "used on a block". It runs after the block's own right click handling, so sneaking is needed on worksites
 * whose right click opens a screen, as before.
 */
public class WorksiteUpgradeItem extends Item {
    private final WorksiteUpgrade upgrade;

    public WorksiteUpgradeItem(Properties properties, WorksiteUpgrade upgrade) {
        super(properties);
        this.upgrade = upgrade;
    }

    public WorksiteUpgrade getUpgrade() {
        return upgrade;
    }

    /** The item stack of an upgrade (1.12.2: {@code new ItemStack(WORKSITE_UPGRADE, 1, ordinal)}); empty for null. */
    public static ItemStack getStack(@Nullable WorksiteUpgrade upgrade) {
        return upgrade == null ? ItemStack.EMPTY : new ItemStack(AwAutomationBase.UPGRADE_ITEMS.get(upgrade).get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof IWorkSite ws)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!ws.getValidUpgrades().contains(upgrade)) {
            return InteractionResult.FAIL;
        }
        HashSet<WorksiteUpgrade> wsug = new HashSet<>(ws.getUpgrades());
        if (wsug.contains(upgrade)) {
            return InteractionResult.FAIL;
        }
        for (WorksiteUpgrade ug : wsug) {
            if (ug.exclusive(upgrade)) {
                return InteractionResult.FAIL;//exclusive upgrade present, exit early
            }
        }
        for (WorksiteUpgrade ug : wsug) {
            if (upgrade.overrides(ug)) {
                InventoryTools.dropItemInWorld(level, getStack(ug), pos);
                ws.removeUpgrade(ug);
            }
        }
        ws.addUpgrade(upgrade);
        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
