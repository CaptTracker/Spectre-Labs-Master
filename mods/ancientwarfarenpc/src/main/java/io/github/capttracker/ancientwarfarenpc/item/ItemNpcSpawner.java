/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemNpcSpawner.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcItemComponents;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.ancientwarfarenpc.registry.FactionRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.NpcDefaultsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The NPC spawner: a stack of up to 16 that spawns the NPC of its {@link NpcSpawnerData} when it is used on a block. It can also hold a whole NPC
 * (equipment, levels, name, orders; see {@link #getSpawnerItemForNpc}), which is what repacking an NPC and the town hall's death list produce.
 * The creative tab lists one stack per NPC type, subtype and faction ({@link #getSpawnerSubItems}).
 *
 * <p>The icon is chosen with the {@code custom_model_data} component: {@link AwNpcs#getSpawnerModelIndex} of the model variant of the NPC type
 * or subtype ({@code models/item/npc_spawner.json} switches on it); a stack without data shows the base icon.
 */
public class ItemNpcSpawner extends Item {
    public ItemNpcSpawner(Properties properties) {
        super(properties);
    }

    @Nullable
    public static NpcSpawnerData getData(ItemStack stack) {
        return stack.get(AwNpcItemComponents.NPC_SPAWNER.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        NpcSpawnerData data = getData(stack);
        if (data != null && data.storedName().isPresent()) {
            tooltip.add(Component.translatable(getDescriptionId(stack)));
        }
        tooltip.add(Component.translatable("guistrings.npc.spawner.right_click_to_place"));
    }

    /** The lang key of the NPC: {@code entity.ancientwarfarenpc.[faction.]<type>[.<subtype>].name}, which is the key of the entity's name. */
    @Override
    public String getDescriptionId(ItemStack stack) {
        NpcSpawnerData data = getData(stack);
        if (data != null) {
            String npcName = data.npcType().replace("faction.", "");
            if (!data.npcSubtype().isEmpty()) {
                npcName = npcName + "." + data.npcSubtype();
            }
            return "entity.ancientwarfarenpc." + data.faction().map(s -> s + ".").orElse("") + npcName + ".name";
        }
        return super.getDescriptionId(stack);
    }

    /** A stack that holds an NPC with a custom name shows that name. */
    @Override
    public Component getName(ItemStack stack) {
        NpcSpawnerData data = getData(stack);
        if (data != null) {
            Optional<String> name = data.storedName();
            if (name.isPresent()) {
                return Component.literal(name.get());
            }
        }
        return Component.translatable(getDescriptionId(stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        BlockPos hit = BlockTools.getBlockClickedOn(player, level, true);
        if (hit == null) {
            return InteractionResultHolder.pass(stack);
        }
        NpcBase npc = createNpcFromItem(level, stack);
        if (npc != null) {
            if (npc instanceof NpcPlayerOwned) {
                npc.setOwner(player);
            }
            npc.setPos(hit.getX() + 0.5d, hit.getY(), hit.getZ() + 0.5d);
            npc.setHomeAreaAtCurrentPosition();
            level.addFreshEntity(npc);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    /**
     * Creates an NPC from the spawner stack if it is valid, else returns null. The NPC has type, subtype, equipment, levels, health, food and
     * owner (the owner is set by the caller) from the stack.
     */
    @Nullable
    public static NpcBase createNpcFromItem(Level level, ItemStack stack) {
        NpcSpawnerData data = getData(stack);
        if (data == null) {
            return null;
        }
        NpcBase npc = AwNpcs.createNpc(level, data.npcType(), data.npcSubtype(), data.faction().orElse(""));
        if (npc == null) {
            return null;
        }
        CompoundTag stored = data.storedDataCopy();
        if (stored != null) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                npc.setItemSlot(slot, ItemStack.EMPTY);
            }
            npc.readAdditionalItemData(stored);
        }
        return npc;
    }

    /**
     * A spawner stack that contains the data to spawn the NPC again: type, subtype, faction and everything {@code writeAdditionalItemData}
     * writes (equipment, levels, health, food, orders, owner).
     */
    public static ItemStack getSpawnerItemForNpc(NpcBase npc) {
        String type = npc.getNpcType();
        String sub = npc.getNpcSubType();
        ItemStack stack = npc instanceof NpcFaction factionNpc ? getStackForNpcType("faction." + type, sub, factionNpc.getFaction())
                : getStackForNpcType(type, sub, "");
        CompoundTag tag = new CompoundTag();
        npc.writeAdditionalItemData(tag);
        NpcSpawnerData data = getData(stack);
        if (data != null) {
            stack.set(AwNpcItemComponents.NPC_SPAWNER.get(), data.withStoredData(tag));
        }
        return stack;
    }

    /**
     * The stacks of the creative tab: first one per player owned type and subtype, then the enabled faction NPC types of every faction
     * (1.12.2: {@code getSubItems}).
     */
    public static List<ItemStack> getSpawnerSubItems() {
        List<ItemStack> playerOwned = new ArrayList<>();
        List<ItemStack> factionOwned = new ArrayList<>();

        for (AwNpcs.NpcDeclaration dec : AwNpcs.getNpcMap().values()) {
            if (dec.canSpawnBaseEntity()) {
                if (dec.getNpcType().startsWith("faction.")) {
                    for (String factionName : FactionRegistry.getFactionNames()) {
                        if (NpcDefaultsRegistry.getFactionNpcDefault(factionName, getShortNpcType(dec)).isEnabled()) {
                            factionOwned.add(getStackForNpcType(dec.getNpcType(), "", factionName));
                        }
                    }
                } else {
                    playerOwned.add(getStackForNpcType(dec.getNpcType(), "", ""));
                }
            }
            for (String sub : dec.getSubTypes()) {
                playerOwned.add(getStackForNpcType(dec.getNpcType(), sub, ""));
            }
        }

        List<ItemStack> ret = new ArrayList<>(playerOwned);
        ret.addAll(factionOwned);
        return ret;
    }

    private static String getShortNpcType(AwNpcs.NpcDeclaration dec) {
        return dec.getNpcType().substring("faction.".length());
    }

    private static ItemStack getStackForNpcType(String type, String npcSubtype, String faction) {
        ItemStack stack = new ItemStack(AwNpcs.NPC_SPAWNER.get());
        stack.set(AwNpcItemComponents.NPC_SPAWNER.get(), new NpcSpawnerData(type, npcSubtype, faction));
        AwNpcs.NpcDeclaration dec = AwNpcs.getNpcDeclaration(type);
        if (dec != null) {
            int modelIndex = AwNpcs.getSpawnerModelIndex(dec.getItemModelVariant(npcSubtype));
            if (modelIndex > 0) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelIndex));
            }
        }
        return stack;
    }

    /** The NPC type of the stack ({@code combat}, {@code faction.archer}, ...), or null for a spawner without data. */
    @Nullable
    public static String getNpcType(ItemStack stack) {
        NpcSpawnerData data = getData(stack);
        return data == null ? null : data.npcType();
    }

    /** The faction of the stack's NPC (only faction NPCs have one). */
    public static Optional<String> getFaction(ItemStack stack) {
        NpcSpawnerData data = getData(stack);
        return data == null ? Optional.empty() : data.faction();
    }
}
