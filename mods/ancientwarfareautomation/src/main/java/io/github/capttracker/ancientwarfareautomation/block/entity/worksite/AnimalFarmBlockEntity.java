/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/WorkSiteAnimalFarm.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfare.util.EntityTools;
import io.github.capttracker.ancientwarfare.util.FakePlayers;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The animal farm ({@code WorkSiteAnimalFarm}, work type farming). Every 200 ticks it looks at the animals in its
 * area (cows including mooshrooms, chickens, sheep and pigs) and works out which adults can breed (pairs that are
 * fully grown and not in love), which are too many (more adult animals than the limit of the species; the excess is
 * culled), which sheep can be sheared and how many cows can be milked. Priority: shear, milk, breed sheep (2 wheat),
 * breed cows (2 wheat), breed chickens (2 seeds), breed pigs (2 carrots, potatoes or beetroots), cull; each
 * action costs one work unit. Inventories: food (3 slots), tools (3 slots: buckets and shears) and the main
 * inventory (output).
 *
 * <p>Milking is a game of chance as in the original: with {@code n} adult cows the chance is {@code (n - 1) / (4 n)},
 * so a single cow is never milked. Shears are not damaged. The area is picked clean of the configured drops
 * ({@code animal_farm_pickups}) every 128 ticks.
 *
 * <p><b>Fixed bugs.</b>
 * <ul>
 * <li>The drop handler of culled animals ({@code onLivingDrops}) was never registered in 1.12.2, so a culled animal's
 * drops fell on the ground (and were only picked up later by the pickup list). It is registered now (through
 * {@code WorksiteEvents}) and it takes over the drops of the animal that is being culled, which then go into the main
 * inventory like the original code intended.</li>
 * <li>{@code sheepToShear} was never cleared between rescans, so it grew forever; it is rebuilt at every rescan
 * now.</li>
 * <li>The pickup test of the original ({@code a && b || c}) matched any stack whose damage equalled the configured
 * one; items have no damage value to compare in 1.21, the test is just "is one of the configured items or tags".</li>
 * </ul>
 */
public class AnimalFarmBlockEntity extends InventoryWorksiteBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FOOD_INVENTORY_SIZE = 3;
    private static final int TOOL_INVENTORY_SIZE = 3;
    private int workerRescanDelay;
    private boolean shouldCountResources;

    public int maxPigCount = 6;
    public int maxCowCount = 6;
    public int maxChickenCount = 6;
    public int maxSheepCount = 6;

    private int wheatCount;
    private int bucketCount;
    private int carrotCount;
    private int potatoCount;
    private int beetrootCount;
    private int seedCount;
    private int shearsSlot = -1;

    private final List<EntityPair> pigsToBreed = new ArrayList<>();
    private final List<EntityPair> chickensToBreed = new ArrayList<>();
    private final List<EntityPair> cowsToBreed = new ArrayList<>();
    private int cowsToMilk;
    private final List<EntityPair> sheepToBreed = new ArrayList<>();
    private final List<Integer> sheepToShear = new ArrayList<>();
    private final List<Integer> entitiesToCull = new ArrayList<>();
    private final List<Integer> adultCows = new ArrayList<>();
    private final List<Integer> itemsToCollect = new ArrayList<>();

    public final ItemStackHandler foodInventory;
    public final ItemStackHandler toolInventory;

    /** The drops of the animals that are being culled right now, by entity (server thread only). */
    private static final Map<UUID, List<ItemStack>> CULL_DROPS = new HashMap<>();

    public AnimalFarmBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.ANIMAL_FARM_BE.get(), pos, state);
        shouldCountResources = true;

        foodInventory = new ItemStackHandler(FOOD_INVENTORY_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                shouldCountResources = true;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isFood(stack.getItem()) ? super.insertItem(slot, stack, simulate) : stack;
            }
        };

        toolInventory = new ItemStackHandler(TOOL_INVENTORY_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                shouldCountResources = true;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isTool(stack.getItem()) ? super.insertItem(slot, stack, simulate) : stack;
            }
        };

        setSideInventory(RelativeSide.FRONT, foodInventory, RelativeSide.FRONT);
        setSideInventory(RelativeSide.BOTTOM, toolInventory, RelativeSide.TOP);
    }

    /** Wheat, seeds and the root crops for breeding, and a bucket and shears, are kept stocked. */
    @Override
    public List<Need> getLogisticsNeeds() {
        List<Need> needs = new ArrayList<>();
        for (Item item : new Item[]{Items.WHEAT, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT}) {
            needs.add(new Need(foodInventory, stack -> stack.is(item), 16));
        }
        needs.add(new Need(toolInventory, stack -> stack.is(Items.BUCKET), 1));
        needs.add(new Need(toolInventory, stack -> stack.getItem() instanceof ShearsItem, 1));
        return needs;
    }

    private boolean isFood(Item item) {
        return item == Items.WHEAT_SEEDS || item == Items.WHEAT || item == Items.CARROT || item == Items.POTATO || item == Items.BEETROOT;
    }

    private boolean isTool(Item item) {
        return item == Items.BUCKET || item instanceof ShearsItem;
    }

    @Override
    public boolean userAdjustableBlocks() {
        return false;
    }

    /** Sets the four species limits (from the animal control screen). */
    public void setMaxCounts(int pigs, int cows, int chickens, int sheep) {
        maxPigCount = pigs;
        maxCowCount = cows;
        maxChickenCount = chickens;
        maxSheepCount = sheep;
        setChanged();
    }

    private boolean canShearSheep() {
        return shearsSlot >= 0 && !sheepToShear.isEmpty();
    }

    private boolean canMilkCow() {
        return bucketCount > 0 && cowsToMilk > 0;
    }

    private boolean canBreedSheep() {
        return wheatCount > 1 && !sheepToBreed.isEmpty();
    }

    private boolean canBreedCows() {
        return wheatCount > 1 && !cowsToBreed.isEmpty();
    }

    private boolean canBreedChicken() {
        return seedCount > 1 && !chickensToBreed.isEmpty();
    }

    private boolean canBreedPigs() {
        return (carrotCount > 1 && !pigsToBreed.isEmpty()) || (potatoCount > 1 && !pigsToBreed.isEmpty()) || (beetrootCount > 1 && !pigsToBreed.isEmpty());
    }

    private boolean canCull() {
        return !entitiesToCull.isEmpty();
    }

    private static final IWorksiteAction SHEAR_ACTION = WorksiteActions.full();
    private static final IWorksiteAction MILK_COW_ACTION = WorksiteActions.full();
    private static final IWorksiteAction BREED_SHEEP_ACTION = WorksiteActions.full();
    private static final IWorksiteAction BREED_COWS_ACTION = WorksiteActions.full();
    private static final IWorksiteAction BREED_CHICKEN_ACTION = WorksiteActions.full();
    private static final IWorksiteAction BREED_PIGS_ACTION = WorksiteActions.full();
    private static final IWorksiteAction CULL_ACTION = WorksiteActions.full();

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        if (canShearSheep()) {
            return Optional.of(SHEAR_ACTION);
        } else if (canMilkCow()) {
            return Optional.of(MILK_COW_ACTION);
        } else if (canBreedSheep()) {
            return Optional.of(BREED_SHEEP_ACTION);
        } else if (canBreedCows()) {
            return Optional.of(BREED_COWS_ACTION);
        } else if (canBreedChicken()) {
            return Optional.of(BREED_CHICKEN_ACTION);
        } else if (canBreedPigs()) {
            return Optional.of(BREED_PIGS_ACTION);
        } else if (canCull()) {
            return Optional.of(CULL_ACTION);
        }
        return Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        if (action == SHEAR_ACTION) {
            return tryShearing();
        } else if (action == MILK_COW_ACTION) {
            if (tryMilking()) {
                InventoryTools.removeItems(toolInventory, new ItemStack(Items.BUCKET), 1);
                InventoryTools.insertOrDropItem(mainInventory, new ItemStack(Items.MILK_BUCKET), level, worldPosition);
                return true;
            }
        } else if (action == BREED_SHEEP_ACTION) {
            if (tryBreeding(sheepToBreed)) {
                wheatCount -= 2;
                InventoryTools.removeItems(foodInventory, new ItemStack(Items.WHEAT), 2);
                return true;
            }
        } else if (action == BREED_COWS_ACTION) {
            if (tryBreeding(cowsToBreed)) {
                wheatCount -= 2;
                InventoryTools.removeItems(foodInventory, new ItemStack(Items.WHEAT), 2);
                return true;
            }
        } else if (action == BREED_CHICKEN_ACTION) {
            if (tryBreeding(chickensToBreed)) {
                seedCount -= 2;
                InventoryTools.removeItems(foodInventory, new ItemStack(Items.WHEAT_SEEDS), 2);
                return true;
            }
        } else if (action == BREED_PIGS_ACTION) {
            if (tryBreeding(pigsToBreed)) {
                if (carrotCount > 1) {
                    carrotCount -= 2;
                    InventoryTools.removeItems(foodInventory, new ItemStack(Items.CARROT), 2);
                    return true;
                } else if (potatoCount > 1) {
                    potatoCount -= 2;
                    InventoryTools.removeItems(foodInventory, new ItemStack(Items.POTATO), 2);
                    return true;
                } else if (beetrootCount > 1) {
                    beetrootCount -= 2;
                    InventoryTools.removeItems(foodInventory, new ItemStack(Items.BEETROOT), 2);
                    return true;
                }
            }
        } else if (action == CULL_ACTION) {
            return tryCulling();
        }
        return false;
    }

    @Override
    protected void updateWorksite() {
        level.getProfiler().push("Count Resources");
        if (shouldCountResources) {
            countResources();
            this.shouldCountResources = false;
            if (isStaffed()) {
                rebuildTasks();
            }
        }
        level.getProfiler().popPush("Animal Rescan");
        if (workerRescanDelay-- <= 0) {
            rescan();
            workerRescanDelay = 200;
        }
        level.getProfiler().popPush("ItemPickup");
        if (level.getGameTime() % 128 == 0 && !isStaffed()) {
            pickupDrops();
        }
        level.getProfiler().pop();
    }

    @Override
    public void onBlockBroken(BlockState state) {
        super.onBlockBroken(state);
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, foodInventory, worldPosition);
            InventoryTools.dropItemsInWorld(level, toolInventory, worldPosition);
        }
    }

    private void countResources() {
        carrotCount = InventoryTools.getCountOf(foodInventory, s -> s.is(Items.CARROT));
        potatoCount = InventoryTools.getCountOf(foodInventory, s -> s.is(Items.POTATO));
        beetrootCount = InventoryTools.getCountOf(foodInventory, s -> s.is(Items.BEETROOT));
        seedCount = InventoryTools.getCountOf(foodInventory, s -> s.is(Items.WHEAT_SEEDS));
        wheatCount = InventoryTools.getCountOf(foodInventory, s -> s.is(Items.WHEAT));
        bucketCount = InventoryTools.getCountOf(toolInventory, s -> s.is(Items.BUCKET));
        shearsSlot = InventoryTools.findItemSlot(toolInventory, s -> s.getItem() instanceof ShearsItem);
    }

    private void rescan() {
        pigsToBreed.clear();
        cowsToBreed.clear();
        cowsToMilk = 0;
        sheepToBreed.clear();
        sheepToShear.clear(); // fixed bug: the original never cleared this list
        chickensToBreed.clear();
        entitiesToCull.clear();
        adultCows.clear();
        itemsToCollect.clear();

        List<Animal> entityList = EntityTools.getEntitiesWithinBounds(level, Animal.class, getWorkBoundsMin(), getWorkBoundsMax());

        List<Animal> cows = new ArrayList<>();
        List<Animal> pigs = new ArrayList<>();
        List<Animal> sheep = new ArrayList<>();
        List<Animal> chickens = new ArrayList<>();

        for (Animal animal : entityList) {
            if (animal instanceof Cow) {
                cows.add(animal);
            } else if (animal instanceof Chicken) {
                chickens.add(animal);
            } else if (animal instanceof Sheep) {
                sheep.add(animal);
            } else if (animal instanceof Pig) {
                pigs.add(animal);
            }
        }

        scanForCows(cows);
        scanForSheep(sheep);
        scanForAnimals(chickens, chickensToBreed, maxChickenCount);
        scanForAnimals(pigs, pigsToBreed, maxPigCount);
        if (isStaffed()) {
            scanItems();
            rebuildTasks();
        }
    }

    private void scanForAnimals(List<Animal> animals, List<EntityPair> targets, int maxCount) {
        Animal animal1;
        Animal animal2;
        EntityPair breedingPair;

        int age;

        for (int i = 0; i < animals.size(); i++) {
            animal1 = animals.get(i);
            age = animal1.getAge();
            if (age != 0 || animal1.isInLove()) {
                continue;
            }//unbreedable first-target, skip
            while (i + 1 < animals.size())//loop through remaining animals to find a breeding partner
            {
                i++;
                animal2 = animals.get(i);
                age = animal2.getAge();
                if (age == 0 && !animal2.isInLove())//found a second breedable animal, add breeding pair, exit to outer loop
                {
                    breedingPair = new EntityPair(animal1, animal2);
                    targets.add(breedingPair);
                    break;
                }
            }
        }

        int grownCount = 0;
        for (Animal animal : animals) {
            if (animal.getAge() >= 0) {
                grownCount++;
            }
        }

        if (grownCount > maxCount) {
            for (int i = 0, cullCount = grownCount - maxCount; i < animals.size() && cullCount > 0; i++) {
                if (animals.get(i).getAge() >= 0) {
                    entitiesToCull.add(animals.get(i).getId());
                    cullCount--;
                }
            }
        }
    }

    private void scanForSheep(List<Animal> sheep) {
        scanForAnimals(sheep, sheepToBreed, maxSheepCount);
        for (Animal animal : sheep) {
            if (animal.getAge() >= 0) {
                Sheep sheep1 = (Sheep) animal;
                if (!sheep1.isSheared()) {
                    sheepToShear.add(sheep1.getId());
                }
            }
        }
    }

    private void scanForCows(List<Animal> animals) {
        scanForAnimals(animals, cowsToBreed, maxCowCount);
        for (Animal animal : animals) {
            if (animal.getAge() >= 0) {
                cowsToMilk++;
                adultCows.add(animal.getId());
            }
        }
    }

    private boolean tryBreeding(List<EntityPair> targets) {
        Entity animalA;
        Entity animalB;
        EntityPair pair;
        if (!targets.isEmpty()) {
            pair = targets.remove(0);
            animalA = pair.getEntityA(level);
            animalB = pair.getEntityB(level);
            if (!(animalA instanceof Animal first) || !(animalB instanceof Animal second)) {
                return false;
            }
            if (animalA.isAlive() && animalB.isAlive() && level instanceof ServerLevel serverLevel) {
                Player fakePlayer = FakePlayers.get(serverLevel);
                first.setInLove(fakePlayer);
                second.setInLove(fakePlayer);
                return true;
            }
        }
        return false;
    }

    private boolean tryMilking() {
        return cowsToMilk > 0 && level.random.nextInt(cowsToMilk * 4) > (cowsToMilk * 3);
    }

    private boolean tryShearing() {
        if (shearsSlot < 0 || sheepToShear.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Entity found = level.getEntity(sheepToShear.remove(0));
        ItemStack shears = toolInventory.getStackInSlot(shearsSlot);
        if (!(found instanceof Sheep sheep) || !sheep.isShearable(FakePlayers.get(serverLevel), shears, level, worldPosition)) {
            return false;
        }
        //shears do not get damaged, if they did this would need clone of the stack and additional setStackInSlot call
        List<ItemStack> items = sheep.onSheared(FakePlayers.get(serverLevel), shears, level, worldPosition);
        for (ItemStack item : items) {
            InventoryTools.insertOrDropItem(mainInventory, item, level, worldPosition);
        }
        return true;
    }

    private boolean tryCulling() {
        int fortune = getFortune();
        while (canCull()) {
            Entity entity = level.getEntity(entitiesToCull.remove(0));
            if (entity instanceof Animal animal && animal.isAlive()) {
                if (animal.isInLove() || animal.getAge() < 0) {
                    continue;
                }

                List<ItemStack> captured = new ArrayList<>();
                CULL_DROPS.put(animal.getUUID(), captured);
                try {
                    animal.invulnerableTime = 0; // a hit in the last half second would reduce the damage
                    animal.hurt(level.damageSources().generic(), animal.getHealth() + 1);
                } finally {
                    CULL_DROPS.remove(animal.getUUID());
                }
                for (ItemStack stack : captured) {
                    if (!stack.isEmpty()) {
                        if (fortune > 0) {
                            stack.grow(level.random.nextInt(fortune));
                        }
                        InventoryTools.insertOrDropItem(mainInventory, stack, level, worldPosition);
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Takes the drops of an animal the farm is culling (fixed bug: the original handler was never registered).
     * Registered on the game event bus by {@code WorksiteEvents}.
     */
    public static void onLivingDrops(LivingDropsEvent evt) {
        List<ItemStack> sink = CULL_DROPS.get(evt.getEntity().getUUID());
        if (sink != null) {
            for (ItemEntity drop : evt.getDrops()) {
                sink.add(drop.getItem().copy());
            }
            evt.setCanceled(true);
        }
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.ANIMAL_FARM, this);
        }
        return true;
    }

    // ************************************** HERDING (rework phase 3, staffed) ***************************************//

    private static final int HERD_TICKS = 30;
    private static final int MILK_COOLDOWN_TICKS = 3000;
    private static final int MAX_ITEM_TARGETS = 32;

    private List<WorkTask> staffTasks = List.of();
    private Map<Long, WorkTask> taskCache = new HashMap<>();
    /** task to the entity id it is about (the first animal of a breeding pair); not saved, rebuilt with the tasks */
    private final Map<WorkTask, Integer> taskEntity = new HashMap<>();
    /** cow id to the game time it can be milked again (not saved) */
    private final Map<Integer, Long> milkedUntil = new HashMap<>();

    @Override
    protected boolean supportsStaffedWork() {
        return true;
    }

    @Override
    protected List<WorkTask> listTasks() {
        return staffTasks;
    }

    private void scanItems() {
        List<Predicate<ItemStack>> matchers = getPickupMatchers();
        for (ItemEntity item : EntityTools.getEntitiesWithinBounds(level, ItemEntity.class, getWorkBoundsMin(), getWorkBoundsMax())) {
            if (itemsToCollect.size() >= MAX_ITEM_TARGETS) {
                break;
            }
            if (item.isAlive() && matchesPickup(matchers, item.getItem())) {
                itemsToCollect.add(item.getId());
            }
        }
    }

    private static boolean matchesPickup(List<Predicate<ItemStack>> matchers, ItemStack stack) {
        for (Predicate<ItemStack> matcher : matchers) {
            if (matcher.test(stack)) {
                return true;
            }
        }
        return false;
    }

    private void addTask(List<WorkTask> list, Map<Long, WorkTask> newCache, int id, WorkAction action) {
        Entity entity = level.getEntity(id);
        if (entity == null || !entity.isAlive()) {
            return;
        }
        long key = ((long) id << 8) | action.ordinal();
        BlockPos pos = entity.blockPosition();
        WorkTask old = taskCache.get(key);
        // an animal that only wandered a little keeps its task, so a worker on the way does not lose the claim
        WorkTask task = old != null && old.pos().distSqr(pos) < 16 ? old : new WorkTask(pos, action, HERD_TICKS);
        if (!list.contains(task)) {
            list.add(task);
            newCache.put(key, task);
            taskEntity.put(task, id);
        }
    }

    /** The tasks in the order of the machine's priorities: shear, milk, collect, breed sheep, cows, chickens, pigs, cull. */
    private void rebuildTasks() {
        List<WorkTask> list = new ArrayList<>();
        Map<Long, WorkTask> newCache = new HashMap<>();
        taskEntity.clear();
        if (level == null || !hasWorkBounds()) {
            staffTasks = list;
            return;
        }
        long now = level.getGameTime();
        if (shearsSlot >= 0) {
            for (int id : sheepToShear) {
                addTask(list, newCache, id, WorkAction.SHEAR);
            }
        }
        int buckets = bucketCount;
        for (int id : adultCows) {
            if (buckets <= 0) {
                break;
            }
            if (milkedUntil.getOrDefault(id, 0L) <= now) {
                addTask(list, newCache, id, WorkAction.MILK);
                buckets--;
            }
        }
        for (int id : itemsToCollect) {
            addTask(list, newCache, id, WorkAction.COLLECT);
        }
        int wheatPairs = wheatCount / 2;
        wheatPairs -= addBreedTasks(list, newCache, sheepToBreed, wheatPairs);
        addBreedTasks(list, newCache, cowsToBreed, wheatPairs);
        addBreedTasks(list, newCache, chickensToBreed, seedCount / 2);
        addBreedTasks(list, newCache, pigsToBreed, carrotCount / 2 + potatoCount / 2 + beetrootCount / 2);
        for (int id : entitiesToCull) {
            addTask(list, newCache, id, WorkAction.CULL);
        }
        taskCache = newCache;
        staffTasks = list;
    }

    /** Adds a breed task per pair while the food lasts. @return the number of tasks added */
    private int addBreedTasks(List<WorkTask> list, Map<Long, WorkTask> newCache, List<EntityPair> pairs, int budget) {
        int added = 0;
        for (EntityPair pair : pairs) {
            if (added >= budget) {
                break;
            }
            int before = list.size();
            addTask(list, newCache, pair.idA, WorkAction.BREED);
            added += list.size() - before;
        }
        return added;
    }

    @Override
    protected boolean performTask(WorkTask task, IItemHandler drops) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Integer id = taskEntity.get(task);
        if (id == null) {
            return false;
        }
        countResources();
        boolean done = switch (task.action()) {
            case SHEAR -> shearAt(serverLevel, id, drops);
            case MILK -> milkAt(serverLevel, id, drops);
            case COLLECT -> collectAt(task.pos(), id, drops);
            case BREED -> breedAt(serverLevel, id);
            case CULL -> cullAt(serverLevel, id, drops);
            default -> false;
        };
        if (done) {
            countResources();
        }
        rebuildTasks();
        return done;
    }

    private void give(IItemHandler drops, ItemStack stack) {
        ItemStack left = ItemHandlerHelper.insertItemStacked(drops, stack, false);
        if (!left.isEmpty()) {
            InventoryTools.insertOrDropItem(mainInventory, left, level, worldPosition);
        }
    }

    private boolean shearAt(ServerLevel serverLevel, int id, IItemHandler drops) {
        sheepToShear.remove((Integer) id);
        if (shearsSlot < 0 || !(level.getEntity(id) instanceof Sheep sheep)) {
            return false;
        }
        ItemStack shears = toolInventory.getStackInSlot(shearsSlot);
        if (sheep.getAge() < 0 || !sheep.isShearable(FakePlayers.get(serverLevel), shears, level, sheep.blockPosition())) {
            return false;
        }
        for (ItemStack item : sheep.onSheared(FakePlayers.get(serverLevel), shears, level, sheep.blockPosition())) {
            give(drops, item);
        }
        return true;
    }

    private boolean milkAt(ServerLevel serverLevel, int id, IItemHandler drops) {
        if (bucketCount <= 0 || !(level.getEntity(id) instanceof Cow cow) || cow.getAge() < 0 || !cow.isAlive()) {
            return false;
        }
        if (milkedUntil.getOrDefault(id, 0L) > level.getGameTime()) {
            return false;
        }
        milkedUntil.put(id, level.getGameTime() + MILK_COOLDOWN_TICKS);
        if (milkedUntil.size() > 256) {
            long now = level.getGameTime();
            milkedUntil.values().removeIf(until -> until <= now);
        }
        InventoryTools.removeItems(toolInventory, new ItemStack(Items.BUCKET), 1);
        give(drops, new ItemStack(Items.MILK_BUCKET));
        serverLevel.playSound(null, cow.blockPosition(), net.minecraft.sounds.SoundEvents.COW_MILK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        return true;
    }

    private boolean collectAt(BlockPos pos, int id, IItemHandler drops) {
        itemsToCollect.remove((Integer) id);
        List<Predicate<ItemStack>> matchers = getPickupMatchers();
        boolean any = false;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3.0D))) {
            ItemStack stack = item.getItem();
            if (item.isAlive() && !stack.isEmpty() && matchesPickup(matchers, stack)) {
                ItemStack left = ItemHandlerHelper.insertItemStacked(drops, stack.copy(), false);
                if (left.getCount() != stack.getCount()) {
                    any = true;
                }
                if (left.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(left);
                }
            }
        }
        return any;
    }

    /** The food a pair list takes, or null if the farm has fewer than 2 of it. */
    @Nullable
    private Item foodFor(List<EntityPair> pairs) {
        if (pairs == sheepToBreed || pairs == cowsToBreed) {
            return wheatCount > 1 ? Items.WHEAT : null;
        }
        if (pairs == chickensToBreed) {
            return seedCount > 1 ? Items.WHEAT_SEEDS : null;
        }
        if (carrotCount > 1) {
            return Items.CARROT;
        }
        if (potatoCount > 1) {
            return Items.POTATO;
        }
        return beetrootCount > 1 ? Items.BEETROOT : null;
    }

    private boolean breedAt(ServerLevel serverLevel, int id) {
        for (List<EntityPair> pairs : List.of(sheepToBreed, cowsToBreed, chickensToBreed, pigsToBreed)) {
            for (Iterator<EntityPair> it = pairs.iterator(); it.hasNext(); ) {
                EntityPair pair = it.next();
                if (pair.idA != id) {
                    continue;
                }
                it.remove();
                Item food = foodFor(pairs);
                if (food == null || !(level.getEntity(pair.idA) instanceof Animal first) || !(level.getEntity(pair.idB) instanceof Animal second)) {
                    return false;
                }
                if (!first.isAlive() || !second.isAlive() || first.getAge() != 0 || second.getAge() != 0 || first.isInLove() || second.isInLove()) {
                    return false;
                }
                Player fakePlayer = FakePlayers.get(serverLevel);
                first.setInLove(fakePlayer);
                second.setInLove(fakePlayer);
                InventoryTools.removeItems(foodInventory, new ItemStack(food), 2);
                // the worker visibly feeds both animals
                for (Animal animal : new Animal[]{first, second}) {
                    serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(food)), animal.getX(), animal.getEyeY(), animal.getZ(),
                            6, 0.2D, 0.1D, 0.2D, 0.05D);
                }
                serverLevel.playSound(null, first.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EAT, net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 1.0F);
                return true;
            }
        }
        return false;
    }

    private boolean cullAt(ServerLevel serverLevel, int id, IItemHandler drops) {
        entitiesToCull.remove((Integer) id);
        if (!(level.getEntity(id) instanceof Animal animal) || !animal.isAlive() || animal.isInLove() || animal.getAge() < 0) {
            return false;
        }
        int fortune = getFortune();
        List<ItemStack> captured = new ArrayList<>();
        CULL_DROPS.put(animal.getUUID(), captured);
        try {
            animal.invulnerableTime = 0;
            animal.hurt(level.damageSources().generic(), animal.getHealth() + 1);
        } finally {
            CULL_DROPS.remove(animal.getUUID());
        }
        for (ItemStack stack : captured) {
            if (!stack.isEmpty()) {
                if (fortune > 0) {
                    stack.grow(level.random.nextInt(fortune));
                }
                give(drops, stack);
            }
        }
        return true;
    }

    // ************************************** ITEM PICKUP ***************************************//

    /** The pickup matchers built from {@code animal_farm_pickups}; rebuilt when the config array is replaced. */
    private static String[] pickupSource = null;
    private static List<Predicate<ItemStack>> pickupMatchers = List.of();

    private static synchronized List<Predicate<ItemStack>> getPickupMatchers() {
        String[] source = AWAutomationStatics.animal_farm_pickups;
        if (source != pickupSource) {
            pickupSource = source;
            pickupMatchers = buildPickupMatchers(source);
        }
        return pickupMatchers;
    }

    /** Entries are item ids ({@code modid:item}) or item tags ({@code #modid:tag}); 1.12.2 damage suffixes are ignored. */
    private static List<Predicate<ItemStack>> buildPickupMatchers(String[] entries) {
        List<Predicate<ItemStack>> matchers = new ArrayList<>();
        for (String entry : entries) {
            String name = entry.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(name.substring(1));
                if (tagId == null) {
                    LOGGER.warn("Animal farm pickups: invalid tag {}", entry);
                    continue;
                }
                TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                matchers.add(stack -> stack.is(tag));
            } else {
                String[] parts = name.split(":");
                ResourceLocation id = parts.length >= 2 ? ResourceLocation.tryParse(parts[0] + ":" + parts[1]) : ResourceLocation.tryParse(name);
                Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
                if (item.isEmpty()) {
                    LOGGER.warn("Animal farm pickups: skipping missing item {}", entry);
                    continue;
                }
                Item wanted = item.get();
                matchers.add(stack -> stack.is(wanted));
            }
        }
        return matchers;
    }

    private void pickupDrops() {
        List<Predicate<ItemStack>> matchers = getPickupMatchers();
        List<ItemEntity> items = EntityTools.getEntitiesWithinBounds(level, ItemEntity.class, getWorkBoundsMin(), getWorkBoundsMax());
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            if (item.isAlive() && !stack.isEmpty()) {
                for (Predicate<ItemStack> matcher : matchers) {
                    if (matcher.test(stack)) {
                        stack = InventoryTools.mergeItemStack(mainInventory, stack);
                        if (!stack.isEmpty()) {
                            item.setItem(stack);
                        } else {
                            item.discard();
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.FARMING;
    }

    @Override
    public void openAltGui(Player player) {
        WorksiteMenus.open(player, WorksiteMenus.Kind.ANIMAL_CONTROL, this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        maxChickenCount = tag.contains("maxChickens") ? tag.getInt("maxChickens") : 6;
        maxCowCount = tag.contains("maxCows") ? tag.getInt("maxCows") : 6;
        maxPigCount = tag.contains("maxPigs") ? tag.getInt("maxPigs") : 6;
        maxSheepCount = tag.contains("maxSheep") ? tag.getInt("maxSheep") : 6;
        foodInventory.deserializeNBT(registries, tag.getCompound("foodInventory"));
        toolInventory.deserializeNBT(registries, tag.getCompound("toolInventory"));
        shouldCountResources = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("maxChickens", maxChickenCount);
        tag.putInt("maxCows", maxCowCount);
        tag.putInt("maxPigs", maxPigCount);
        tag.putInt("maxSheep", maxSheepCount);
        tag.put("foodInventory", foodInventory.serializeNBT(registries));
        tag.put("toolInventory", toolInventory.serializeNBT(registries));
    }

    /** Two animals that breed together, by entity id (ids are only valid for the loaded level, the lists are rebuilt by every rescan). */
    private static class EntityPair {

        private final int idA;
        private final int idB;

        private EntityPair(Entity a, Entity b) {
            idA = a.getId();
            idB = b.getId();
        }

        @Nullable
        private Entity getEntityA(Level level) {
            return level.getEntity(idA);
        }

        @Nullable
        private Entity getEntityB(Level level) {
            return level.getEntity(idB);
        }
    }

}
