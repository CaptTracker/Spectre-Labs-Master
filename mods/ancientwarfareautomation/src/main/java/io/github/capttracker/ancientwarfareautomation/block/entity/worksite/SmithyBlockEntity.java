/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): the smithy worksite. No 1.12.2 original.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IWorker;
import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The smithy (work type smithing, so a smith works it). A worker makes tools, weapons and armour of iron, gold and diamond (and leather armour) from
 * the input store at the vanilla costs (a pickaxe is 3 ingots and 2 sticks, a chestplate 8 ingots, and so on), one item per task. Each recipe has its
 * own switch and "keep N in storage" target, so "keep 4 iron pickaxes" is the target of the iron pickaxe. With {@code smithy_needs_fuel} (true) every
 * item burns {@code smithy_fuel_per_item} ticks of fuel from the fuel store.
 *
 * <p><b>Repair.</b> The last recipe, on by default, repairs the damaged tools, weapons and armour in the input store (a courier brings them from the
 * storages of the settlement with {@code smithy_couriers_fetch_damaged}, or a player puts them in): one matching repair item (iron ingot for iron, diamond
 * for diamond, leather, gold, netherite and so on, as the item itself says) per {@code smithy_repair_percent_per_ingot} percent of lost durability, and the
 * item comes out fully repaired into the main inventory, where the couriers take it to a storage. Enchantments and names stay.
 *
 * <p><b>Master smith.</b> A worker of master rank in the crafting skill saves one ingot of a batch with the chance {@code smithy_master_save_chance}
 * (the other bonus that was offered, extra durability, has nothing to add to a new item at full durability or to a repair that gives back everything).
 *
 * <p>Netherite gear is not made: it needs a smithing template that cannot be crafted, so it is left to the player.
 */
public class SmithyBlockEntity extends ProcessingWorksiteBlockEntity {
    /** The crafting rank of a master (the top of {@code Rank} in the NPC module). */
    private static final int MASTER_RANK = 4;
    private static final int TOOL_TICKS = 80;
    private static final int ARMOUR_TICKS = 120;
    /** The most damaged items the couriers keep in the input store. */
    private static final int DAMAGED_TARGET = 3;
    /** The repair items the couriers keep in the input store for the damaged gear it holds. */
    private static final int REPAIR_STOCK = 8;

    private static final Predicate<ItemStack> IRON = stack -> stack.is(Items.IRON_INGOT);
    private static final Predicate<ItemStack> GOLD = stack -> stack.is(Items.GOLD_INGOT);
    private static final Predicate<ItemStack> DIAMOND = stack -> stack.is(Items.DIAMOND);
    private static final Predicate<ItemStack> LEATHER = stack -> stack.is(Items.LEATHER);
    private static final Predicate<ItemStack> STICK = stack -> stack.is(Items.STICK);

    private static final ProcessingRecipe.Input IRON_INPUT = new ProcessingRecipe.Input(IRON, 32, new ItemStack(Items.IRON_INGOT));
    private static final ProcessingRecipe.Input GOLD_INPUT = new ProcessingRecipe.Input(GOLD, 32, new ItemStack(Items.GOLD_INGOT));
    private static final ProcessingRecipe.Input DIAMOND_INPUT = new ProcessingRecipe.Input(DIAMOND, 16, new ItemStack(Items.DIAMOND));
    private static final ProcessingRecipe.Input LEATHER_INPUT = new ProcessingRecipe.Input(LEATHER, 24, new ItemStack(Items.LEATHER));
    private static final ProcessingRecipe.Input STICK_INPUT = new ProcessingRecipe.Input(STICK, 16, new ItemStack(Items.STICK));

    /** What can repair something: the items an armour or tool material takes (the item decides which fits, {@code Item.isValidRepairItem}). */
    private static final Item[] REPAIR_ITEMS = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.LEATHER, Items.NETHERITE_INGOT, Items.COPPER_INGOT};

    /** True for a damaged item that one of the repair items can mend. */
    private static boolean isRepairable(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamaged()) {
            return false;
        }
        for (Item item : REPAIR_ITEMS) {
            if (stack.getItem().isValidRepairItem(stack, new ItemStack(item))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRepairItem(ItemStack stack) {
        for (Item item : REPAIR_ITEMS) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    /** The one instance the courier need of damaged gear is recognised by (so the config can drop it again). */
    private static final Predicate<ItemStack> DAMAGED = SmithyBlockEntity::isRepairable;
    private static final ProcessingRecipe.Input DAMAGED_INPUT = new ProcessingRecipe.Input(DAMAGED, DAMAGED_TARGET);

    private static int count(IItemHandler input, Item item) {
        int total = 0;
        for (int slot = 0; slot < input.getSlots(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(item) && !stack.isDamaged()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int fuelCost() {
        return AWAutomationStatics.smithyNeedsFuel ? AWAutomationStatics.smithyFuelPerItem : 0;
    }

    /** One tool, weapon or piece of armour: its material count and sticks, as in the vanilla crafting recipe. */
    private static final class GearRecipe implements ProcessingRecipe {
        private final String id;
        private final Item product;
        private final Item material;
        private final int materialCount;
        private final int sticks;
        private final int ticks;
        private final ProcessingRecipe.Input materialInput;

        GearRecipe(String id, Item product, Item material, ProcessingRecipe.Input materialInput, int materialCount, int sticks, int ticks) {
            this.id = id;
            this.product = product;
            this.material = material;
            this.materialInput = materialInput;
            this.materialCount = materialCount;
            this.sticks = sticks;
            this.ticks = ticks;
        }

        @Override
        public String id() {
            return id;
        }

        /** The name of the item itself, so there is no extra text for each of them. */
        @Override
        public String nameKey() {
            return product.getDescriptionId();
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(product);
        }

        @Override
        public Predicate<ItemStack> product() {
            return stack -> stack.is(product);
        }

        @Override
        public List<Input> inputs() {
            return sticks > 0 ? List.of(materialInput, STICK_INPUT) : List.of(materialInput);
        }

        @Override
        public boolean usesInput(ItemStack stack) {
            return stack.is(material) || (sticks > 0 && stack.is(Items.STICK));
        }

        @Nullable
        @Override
        public Batch plan(Level level, IItemHandler input) {
            if (count(input, material) < materialCount || (sticks > 0 && count(input, Items.STICK) < sticks)) {
                return null;
            }
            List<ItemStack> take = new ArrayList<>();
            take.add(new ItemStack(material, materialCount));
            if (sticks > 0) {
                take.add(new ItemStack(Items.STICK, sticks));
            }
            return new Batch(take, List.of(new ItemStack(product)), ticks, fuelCost());
        }
    }

    /** The repair of the damaged gear in the input store. */
    private static final class RepairRecipe implements ProcessingRecipe {
        @Override
        public String id() {
            return "repair";
        }

        @Override
        public String nameKey() {
            return "guistrings.automation.smithy.repair";
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(Items.ANVIL);
        }

        @Override
        public boolean defaultEnabled() {
            return true;
        }

        /** Nothing is "made", so the "keep N" number has nothing to count. */
        @Override
        public Predicate<ItemStack> product() {
            return stack -> false;
        }

        /** The damaged gear; the repair items are stocked for the gear the smithy holds (see {@link SmithyBlockEntity#getLogisticsNeeds}). */
        @Override
        public List<Input> inputs() {
            return List.of(DAMAGED_INPUT);
        }

        @Override
        public boolean usesInput(ItemStack stack) {
            return isRepairable(stack) || isRepairItem(stack);
        }

        /** The repair items one repair takes: the percent of lost durability over {@code smithy_repair_percent_per_ingot}, rounded up, at least one. */
        private static int cost(ItemStack damaged) {
            double lost = 100.0D * damaged.getDamageValue() / Math.max(1, damaged.getMaxDamage());
            int per = Math.max(1, AWAutomationStatics.smithyRepairPercentPerIngot);
            return Math.max(1, (int) Math.ceil(lost / per - 1.0E-9D));
        }

        @Nullable
        @Override
        public Batch plan(Level level, IItemHandler input) {
            for (int slot = 0; slot < input.getSlots(); slot++) {
                ItemStack gear = input.getStackInSlot(slot);
                if (!isRepairable(gear)) {
                    continue;
                }
                int cost = cost(gear);
                for (Item item : REPAIR_ITEMS) {
                    if (!gear.getItem().isValidRepairItem(gear, new ItemStack(item)) || count(input, item) < cost) {
                        continue;
                    }
                    ItemStack repaired = gear.copyWithCount(1);
                    repaired.setDamageValue(0);
                    return new Batch(List.of(gear.copyWithCount(1), new ItemStack(item, cost)), List.of(repaired), 40 + 20 * cost, fuelCost());
                }
            }
            return null;
        }
    }

    private static final RepairRecipe REPAIR = new RepairRecipe();
    private static final List<ProcessingRecipe> RECIPES = buildRecipes();

    private static List<ProcessingRecipe> buildRecipes() {
        List<ProcessingRecipe> list = new ArrayList<>();
        addSet(list, "iron", Items.IRON_INGOT, IRON_INPUT, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.IRON_SWORD,
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
        addSet(list, "gold", Items.GOLD_INGOT, GOLD_INPUT, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE, Items.GOLDEN_SWORD,
                Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
        addSet(list, "diamond", Items.DIAMOND, DIAMOND_INPUT, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
                Items.DIAMOND_SWORD, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
        addSet(list, "leather", Items.LEATHER, LEATHER_INPUT, null, null, null, null, null, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
                Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
        list.add(REPAIR);
        return List.copyOf(list);
    }

    private static void addSet(List<ProcessingRecipe> list, String prefix, Item material, ProcessingRecipe.Input input, @Nullable Item pickaxe,
                               @Nullable Item axe, @Nullable Item shovel, @Nullable Item hoe, @Nullable Item sword, Item helmet, Item chestplate,
                               Item leggings, Item boots) {
        if (pickaxe != null && axe != null && shovel != null && hoe != null && sword != null) {
            list.add(new GearRecipe(prefix + "_pickaxe", pickaxe, material, input, 3, 2, TOOL_TICKS));
            list.add(new GearRecipe(prefix + "_axe", axe, material, input, 3, 2, TOOL_TICKS));
            list.add(new GearRecipe(prefix + "_shovel", shovel, material, input, 1, 2, TOOL_TICKS));
            list.add(new GearRecipe(prefix + "_hoe", hoe, material, input, 2, 2, TOOL_TICKS));
            list.add(new GearRecipe(prefix + "_sword", sword, material, input, 2, 1, TOOL_TICKS));
        }
        list.add(new GearRecipe(prefix + "_helmet", helmet, material, input, 5, 0, ARMOUR_TICKS));
        list.add(new GearRecipe(prefix + "_chestplate", chestplate, material, input, 8, 0, ARMOUR_TICKS));
        list.add(new GearRecipe(prefix + "_leggings", leggings, material, input, 7, 0, ARMOUR_TICKS));
        list.add(new GearRecipe(prefix + "_boots", boots, material, input, 4, 0, ARMOUR_TICKS));
    }

    public SmithyBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.SMITHY_BE.get(), pos, state, FUEL_SIZE);
    }

    @Override
    protected List<ProcessingRecipe> recipes() {
        return RECIPES;
    }

    @Override
    protected WorkAction craftAction() {
        return WorkAction.SMITH;
    }

    @Override
    public WorksiteMenus.Kind menuKind() {
        return WorksiteMenus.Kind.SMITHY;
    }

    @Override
    protected boolean needsFuel() {
        return fuelInventory != null && AWAutomationStatics.smithyNeedsFuel;
    }

    /**
     * The needs of the base, without the damaged gear if the config keeps the couriers from fetching it, and with the repair items for the damaged
     * gear the input store holds (iron for the iron pickaxe waiting there, and so on), as long as the repair is on.
     */
    @Override
    public List<Need> getLogisticsNeeds() {
        List<Need> needs = new ArrayList<>(super.getLogisticsNeeds());
        if (!AWAutomationStatics.smithyFetchDamaged) {
            needs.removeIf(need -> need.accepts() == DAMAGED);
        }
        if (isRecipeEnabled(RECIPES.indexOf(REPAIR))) {
            for (Item item : REPAIR_ITEMS) {
                if (holdsGearFor(item)) {
                    needs.add(new Need(inputInventory, stack -> stack.is(item) && !stack.isDamaged(), REPAIR_STOCK));
                }
            }
        }
        return needs;
    }

    private boolean holdsGearFor(Item repairItem) {
        ItemStack probe = new ItemStack(repairItem);
        for (int slot = 0; slot < inputInventory.getSlots(); slot++) {
            ItemStack stack = inputInventory.getStackInSlot(slot);
            if (isRepairable(stack) && stack.getItem().isValidRepairItem(stack, probe)) {
                return true;
            }
        }
        return false;
    }

    /** A master smith saves one ingot of a gear batch (never a stick; a repair always costs its repair items, so it is never free). */
    @Override
    protected List<ItemStack> consumedFor(ProcessingRecipe recipe, ProcessingRecipe.Batch batch, @Nullable LivingEntity actor) {
        if (recipe == REPAIR || !(actor instanceof IWorker worker) || worker.getCraftingRank() < MASTER_RANK || AWAutomationStatics.smithyMasterSaveChance <= 0.0D
                || actor.getRandom().nextDouble() >= AWAutomationStatics.smithyMasterSaveChance) {
            return batch.consume();
        }
        List<ItemStack> consume = new ArrayList<>();
        boolean saved = false;
        for (ItemStack stack : batch.consume()) {
            ItemStack copy = stack.copy();
            if (!saved && copy.getMaxStackSize() > 1 && !copy.is(Items.STICK)) {
                saved = true;
                copy.shrink(1);
            }
            if (!copy.isEmpty()) {
                consume.add(copy);
            }
        }
        return consume;
    }

    /** The anvil rings and sparks fly. */
    @Override
    protected void onCrafted(ServerLevel level, ProcessingRecipe recipe) {
        level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.0F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, 12, 0.25D, 0.1D, 0.25D, 0.2D);
    }
}
