# automation-worksites: the concrete worksites

Slice key `automation-worksites`, module `ancientwarfareautomation`. Ported from `automation/tile/worksite/**` (crop farm,
fruit farm, tree farm, animal farm, fish farm, quarry, auto crafting) and `automation/block/BlockWorksiteBase`,
`BlockAutoCrafting` (report 03). `TileOreProcessor` / `BlockOreProcessor` are dead code and were not ported. Nothing was
compiled or run. Every Minecraft and NeoForge name was checked against `analysis/mc-sources`.

## What was ported

Package `io.github.capttracker.ancientwarfareautomation`:

`block.worksite`
- `WorksiteBlock` (BlockWorksiteBase): quarry, the five farms and the warehouse control block. `(Properties, Supplier<? extends BlockEntityType<?>>)`,
  `FACING` and `ACTIVE` from `WorksiteProperties`, ticker, rotate/mirror, owner / team / friend check on right click.
- `AutoCraftingBlock` (BlockAutoCrafting): same state, opens its screen for every player (as the original).

`block.entity.worksite`
- `QuarryBlockEntity` (WorkSiteQuarry), `AnimalFarmBlockEntity` (WorkSiteAnimalFarm), `FishFarmBlockEntity` (WorkSiteFishFarm),
  `AutoCraftingBlockEntity` (TileAutoCrafting).
- New helpers: `WorksiteActions` (action costs, with the efficiency fix), `WorksiteMenus` (where the menus open, see below),
  `WorksiteEvents` (mod bus and game bus listeners).

`block.entity.worksite.cropfarm`: `CropFarmBlockEntity` (WorkSiteCropFarm), `ICrop`, `CropDefault`, `CropBreakOnly`, `CropGourd`,
`CropKeepBottom`, `CropMatureDefined`, `CropStem`, `CropTall`.

`block.entity.worksite.fruitfarm`: `FruitFarmBlockEntity` (WorkSiteFruitFarm), `IFruit`, `FruitCocoa`, `FruitBreakOnly`, `FruitPicked`,
`FruitPickedDrop`, `FruitPickedRemoveOne`.

`block.entity.worksite.treefarm`: `TreeFarmBlockEntity` (WorkSiteTreeFarm), `ITree`, `Tree`, `Branch`, `HorizontalAABB`, `ITreeScanner`,
`DefaultTreeScanner` (with the five `INextPositionGetter` constants), `ChorusScanner`, `ISapling`, `Sapling`, `IBlockExtraDrop`,
`ChorusFlowerDrop`.

The class names and constructors are the contract written in `automation-base.md`; the three registries (`CropFarmRegistry`,
`FruitFarmRegistry`, `TreeFarmRegistry`) call the handler constructors as written there and compile against these classes.
Assets and data: nothing new. The base slice already wrote the blockstates (facing + active), models, loot tables, mineable tags,
recipes and the registry data files for every block of this slice.

## Wiring needed in shared files

1. `init/AwAutomationBase.java`, imports: the two block classes are in `block.worksite` (the slice was told to write into that package, the
   contract said `block`):
   ```java
   import io.github.capttracker.ancientwarfareautomation.block.worksite.AutoCraftingBlock;
   import io.github.capttracker.ancientwarfareautomation.block.worksite.WorksiteBlock;
   ```
   replacing `...block.AutoCraftingBlock` and `...block.WorksiteBlock`.
2. `init/AwAutomationBase.registerEvents(IEventBus modBus)`: add
   ```java
   WorksiteEvents.registerEvents(modBus);   // import ...block.entity.worksite.WorksiteEvents
   ```
   It registers the auto crafting table's item handler capability (the block entity of the table is registered for torque and energy
   by `AwAutomationBase.registerWorksite`, its item handler is not) and the animal farm's `LivingDropsEvent` handler.
3. Menus (GUI slice): call `WorksiteMenus.setFactory(Kind, Factory)` once per kind with the menu constructor: `QUARRY`, `TREE_FARM`,
   `CROP_FARM`, `FRUIT_FARM`, `ANIMAL_FARM`, `ANIMAL_CONTROL`, `FISH_FARM`, `FISH_CONTROL`, `AUTO_CRAFTING`. The menu is opened with the
   block position as extra data (`BlockEntityMenu.resolve(...)` reads it). The kinds map to the 1.12.2 `GUI_WORKSITE_*` ids (see the class comment).
   Until a factory is set, clicking a worksite does nothing. Menus read and write: `AnimalFarmBlockEntity.foodInventory/toolInventory`,
   `setMaxCounts(...)`, the public `maxPigCount` etc.; `FishFarmBlockEntity.harvestFish()/harvestInk()/setHarvest(...)`;
   `AutoCraftingBlockEntity.craftingRecipeMemory/outputInventory/resourceInventory`; `QuarryBlockEntity.height`.
4. `assets/ancientwarfareautomation/lang/en_us.json`: merge `automation-worksites.lang.json` (2 keys, the titles of the second screens).
5. Core, `IWorkSite.WorksiteImplementation.getEnergyPerActivation`: still has the original (wrong) formula. The worksites of this slice do not use it
   (they use `WorksiteActions`), but the warehouse slice or any other user should: change it to
   `AWCoreStatics.energyPerWorkUnit * (1.0 - efficiencyBonusFactor)` and let `WorksiteActions.energyPerActivation` delegate, or the reverse.
6. `docs/PORT-STATUS.md`: Automation worksites slice done, not built.

## Fixed bugs of the original (each is also noted in a code comment)

- **Efficiency upgrades**: the action cost was `energyPerWorkUnit * 1.f - bonus` (the bonus was subtracted from 50, saving about 1 percent). It is
  `energyPerWorkUnit * (1 - bonus)` now (`WorksiteActions`). This changes the balance of all worksites: with the strongest upgrades (0.35) an action
  costs 32.5 instead of 49.65.
- **Animal farm drops handler**: `onLivingDrops` carried `@SubscribeEvent` but was never registered. It is registered now. The farm collects the drops
  of the animal it culls through a map of "animals being culled" and cancels the event for them, so the drops go into the main inventory (the loot
  table, not the 1.12.2 `captureDrops` field).
- `sheepToShear` grew forever (never cleared); it is rebuilt at every rescan.
- The pickup test of the animal farm (`a && b || c`) is moot: items have no damage value in 1.21, the config list holds ids and `#tags`.
- `CropGourd` had no matchers and threw a NullPointerException in `matches(ItemStack)`; a break-only crop without an item matcher matches no item.
- Fruit farm: planting a fruit did not use up the item (the cocoa bean stayed in the plantable inventory forever). It is used up now.
- Crop default handler: a replant item that cannot be placed is kept among the drops, it was lost.
- Quarry: the cursor is `BlockPos.ZERO` before the first update; the original forced chunk (0, 0) for the chunk loader then. It does not any more.
- Crop farm fertilising judged the block by the state from before the bone meal; it uses the state after.

## Behaviour changes forced by 1.21 (not bugs of the original)

- **Crops are `CropBlock`s and sweet berry bushes** (`CropDefault`): 1.12.2 treated every `IGrowable` as a crop (fully grown = `!canGrow`, fertilise = `canGrow`).
  In 1.21 `BonemealableBlock` includes grass, flowers, saplings, moss and so on, which would have been broken and bone-mealed. Nether wart, sugar cane, cactus and
  mushrooms come from the data files as before (`crop_blocks`). Stems use `StemBlock` and `AttachedStemBlock`. Gourds are melon, pumpkin, carved pumpkin and
  jack o'lantern (the old `Material.GOURD`).
- The data driven crop handlers (`mature_defined`, `keep_bottom`, `tall`) do not claim items (`matches(ItemStack)` false); in the original they inherited
  "matches every item" and so the order of the data files decided what was plantable.
- **Fruit farm** only uses bone meal on growing fruit blocks (a registered fruit that is not ripe and accepts bone meal), not on every growable block.
- **Tree farm** only uses bone meal on blocks that are plantables of the farm (saplings, mushrooms, ...); trunks and leaves come from the scanners (tags); shearing
  uses the loot of the block with the shears as the tool (leaves, vines): NeoForge 1.21's `IShearable` on blocks only answers `isShearable`.
  Sheared blocks are removed through `BlockTools.breakBlockNoDrops` (one break event), the original used `setBlockToAir`.
- **Quarry** depth: down to the build limit (`level.getMinBuildHeight()`), not to y = 1. Tool tiers use the `needs_*_tool` block tags (level 3 / 2 / 1). Liquids are `LiquidBlock`s
  (a waterlogged block is mined). It reads blocks with `level.getBlockState` like the original, which loads chunks that are not loaded.
- **Fish farm** water is the water fluid tag (flowing, still, waterlogged); the loot is the vanilla `gameplay/fishing` table rolled without a fishing hook, so no
  treasure (its condition needs an "in open water" hook); luck is the fortune of the upgrades; ink is `minecraft:ink_sac`. The `MAX_WATER` constant 1280 and the
  "failed roll = failed action, wait 20 ticks" rule are kept.
- **Animal farm**: entities are `Animal`s (cow incl. mooshroom, chicken, sheep, pig); `getGrowingAge` is `getAge`; shearing uses `Sheep.onSheared` (the `IShearable` of 1.21). The
  milk chance is the original's `(n-1)/(4n)`, so a single cow never gives milk (kept; looks unintended, see rework candidates).
- **Auto crafting**: ingredient matching uses `AWCraftingManager.matchIngredients` (pooled, exact template items first) and builds the real-item grid in the recipe's own
  layout for the result and the remaining items. The recipe memory is not synced through the update tag (the menu syncs it, like the engineering bench).
- **Open-menu hook**: `WorksiteMenus` replaces `NetworkHandler.openGui`.
- A worksite upgrade in the hand is passed to the item instead of opening the block's screen, so applying an upgrade needs no sneaking.
- Saved data: the same keys as the original where they exist (`targetList`, `targetLeafList` as a list of longs, `maxChickens/Cows/Pigs/Sheep`, `foodInventory`,
  `toolInventory`, `fish`, `ink`, `current`, `validate`, `finished`, `init`, `height`, `resourceInventory`, `outputInventory` and the recipe memory keys). Saves of 1.12.2 do not load anyway.

## Not ported (and why)

- `TileOreProcessor`, `BlockOreProcessor`: dead code (as told).
- The `ItemBlockWorksiteStatic` / `ItemBlockRotatableMetaTile` item behaviour: the base slice has `WorksiteBlockItem` and `OwnedBlockItem`.
- `BlockBaseAutomation` (creative tab, state mapper): the registrar and JSON assets do that.
- `TileAutoCrafting.shouldRenderInPass`, the unlisted facing property: the block state replaces them. Renderers belong to the render slice.
- AgriCraft crop (`CropAgricraftCrop`): compat was not ported by the base slice either.
- The 1.12.2 compat registry files of the other mods: see `automation-base.md`.

## Unconfirmed API / things to check at the first build

- All names were found in the sources. Points that rest on inference rather than a call that exists elsewhere in the repo:
  - `WorksiteBlock.getTicker` returns `WorksiteBlockEntity.ticker(level)` with the type check `type != blockEntityType.get()` (generic wildcard comparison).
  - `Sheep.isShearable(...)` / `onSheared(...)` are the `IShearable` default methods reached through `Shearable`; `onSheared` relies on NeoForge's `captureDrops` in
    `Entity.spawnAtLocation` for the sheared wool (as `IShearable.onSheared` does itself).
  - The cull path relies on `LivingEntity.die` running `dropAllDeathLoot` (and so `LivingDropsEvent`) synchronously inside `hurt`.
  - `LootParams` without `THIS_ENTITY` for the fishing set: the entity-property condition of the treasure branch evaluates to false (checked by reading the loot context code, not run).
  - `level.playSound(null, pos, ...)` picks the `Player` overload (most specific).
- Class loading: `FruitFarmRegistry`, `TreeFarmRegistry` and `CropFarmRegistry` create handler objects in static initialisers; `TreeFarmRegistry` refers to `AwAutomationBase.TREE_FARM` inside a lambda only.

## Rework candidates

- Efficiency: the strongest upgrade combination now saves 35 percent; the balance of upgrade costs vs. NPC crank output was not looked at.
- Animal farm: the milk chance `(n - 1) / (4 n)`, entity ids instead of UUIDs, `Animal` species hard-coded (goat, llama, ... could be data driven), no cap per species for modded animals.
- Fruit farm: sweet berries, glow berries (cave vines), bamboo, kelp as built-in or data fruits; the crop farm for torchflower and pitcher crops (2 tall) and bamboo.
- Tree farm: mangrove propagule, cherry, azalea and fungus trees (nether) use the default log/leaf tags; the hanging soil rule ("replaceable below, soil above") is the original's guess.
- Quarry: modded blocks that use only `incorrect_for_*_tool` tags; loading chunks when scanning; a per-tick budget for blocks that are slow to break.
- Fish farm: its own loot table in `data/` (fish, junk, treasure) instead of the vanilla one without a hook.
- Auto crafting: sync of the memory to clients if a renderer wants to show the recipe; a stack of reusable-tool recipes needs a test with real recipes.
- Protection: all breaking and placing is done by the Ancient Warfare fake player, not by the owner (report open question 7).
