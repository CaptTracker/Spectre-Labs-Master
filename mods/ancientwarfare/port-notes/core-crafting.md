# Core research-gated crafting and recipes (slice key: core-crafting)

Ported from `core/crafting` (+ `wrappers`), `core/tile/CraftingRecipeMemory`, `core/container/ContainerCraftingRecipeMemory`,
`ContainerEngineeringStation`, `ICraftingContainer`, `core/gui/crafting/GuiEngineeringStation` + `ResearchCraftingElement`, and the
data in `research_recipes/core`. Nothing was compiled or run. Class and method names were checked against `analysis/mc-sources`
(`Recipe`, `RecipeSerializer`, `RecipeManager`, `ShapedRecipePattern`, `CraftingInput`, `SizedIngredient`, `RecipeMatcher`,
`DataComponentIngredient`, `SimpleContainer`, `CraftingContainer`, `Slot`, `ItemStackHandler`, wrappers).

## Files (package `io.github.capttracker.ancientwarfare`)

crafting: `ResearchRecipe` (abstract recipe base, `Recipe<CraftingInput>`), `ShapedResearchRecipe` (+ `Pattern`, `Serializer`),
`ShapelessResearchRecipe` (+ `Serializer`), `AWCraftingManager`, `ICraftingRecipe`, `RecipeResourceLocation`, `CraftingRecipeMemory`,
`CraftMatrix`, `CraftingMemoryController` (was `ContainerCraftingRecipeMemory`), `ICraftingContainer`,
`wrappers/NoRecipeWrapper`, `wrappers/RegularCraftingWrapper`, `wrappers/ResearchCraftingWrapper`
init: `AwCoreCrafting` | menu: `EngineeringStationMenu` (rewritten) | block/entity: `EngineeringStationBlockEntity` (rewritten)
client: `EngineeringStationScreen` (new; was the original's GuiEngineeringStation + ResearchCraftingElement)
Data: 15 recipes in `src/main/resources/data/ancientwarfare/recipe/research/*.json` (ids `ancientwarfare:research/<name>`).
Script: `analysis/port/research_recipes_convert.py` (+ `research_recipes_core_items.json`).

## How it works now

- `ancientwarfare:research` is a `RecipeType`; the serializers are `ancientwarfare:research_recipe` (shaped) and
  `ancientwarfare:shapeless_research_recipe`, the same ids the 1.12.2 files used. Recipes are normal datapack recipes under
  `data/<ns>/recipe/`, loaded and synced to clients by the recipe manager. The custom registry, `loadRecipes` (config folder and
  every mod's assets folder), the `JsonContext` constants reflection and the three ingredient factories are gone.
- Ingredients are `SizedIngredient` (item or tag plus `count`, `{"tag": "c:leathers", "count": 5}`); `IngredientCount`,
  `IngredientOreCount`, `IIngredientCount` are dropped. The "relaxed NBT" ingredient is `neoforge:components` with `strict: false`.
  A cell accepts a stack only if it holds at least the count, as before.
- `research` in a recipe file is a goal id; a bare name means `ancientwarfare:<name>`. Unknown goals are not an error at load (the
  original threw); `AWCraftingManager.validateRecipes` logs them (and ingredients with no items) when the server has started.
- Gating (`AWCraftingManager.requiresResearch`): research system on and (the item is "researchable" or the recipe has a counted
  ingredient). The original decided at load time and put the other recipes in the normal recipe registry. Those now stay research-type
  recipes that need no research; they are only usable in the engineering bench, **not in the vanilla crafting table** (see rework).
  `canPlayerCraft(server, researcherUuid, goal)`: research off, or the researcher's `ResearchEntry.knows(goal)`. The researcher is the
  `Researcher` bound in the research book in the bench's book slot (by UUID now, the original used the name); no book, no gated recipes.
- Pooled matching (`AWCraftingManager.matchIngredients/matchIngredientList`): see the class comment for the three fixes of
  original bugs (partial stacks add up to a count, exact stacks are reported, reserved items are not reused, most constrained
  ingredient first). `getRecipeInventoryMatch` (9 slot list / visitor), `getIngredientInventoryMatch`, `getReusableStacks`,
  `findMatchingRecipe(level, stacks, result)` and `fillCraftingMatrixFromInventory` keep their original roles. New:
  `tryTakeIngredients(handler, sizedIngredients)` and `extractExact(handler, stack, count)`.
- Shapeless matching now pairs grid stacks and ingredients properly (`RecipeMatcher`); the original took the first ingredient that
  accepted a stack, so two ingredients accepting the same item could never both match. Shaped matching works on a trimmed or full
  input (offset scan, never mirrored). Patterns are shrunk like vanilla.
- Bench: `EngineeringStationBlockEntity` holds `memory` (`CraftingRecipeMemory`: book slot accepting only bound books, `CraftMatrix`
  grid, chosen recipe id as `kind|ns:path` string, resolved lazily) and `storage` (18 slots). NBT keys kept: `bookSlot`, `craftMatrix`,
  `recipe`, `extraInventory`.
- `EngineeringStationMenu` (signatures unchanged; slot order and positions as in 1.12.2: result 134,26; book 8,26; grid 62,8;
  storage 8,66; player 8,110; gui 176x192). The server (`CraftingMemoryController.serverRefresh`, called from `broadcastChanges`) lists
  the matching recipes (research recipes the crafter may use + ordinary crafting recipes), keeps the chosen one if still possible,
  else the first, and sends `{recipes: [names], selected: n}` with `sendDataToClient`; the client menu stores it, the screen reads
  `getRecipeCount()` / `getSelectedRecipeIndex()` each frame and the arrows call `previousRecipe()/nextRecipe()`, which send
  `{recipe: name}` to the server (accepted only if it is in the server's list). The list is refreshed on grid/book/recipe changes and every 40 ticks (so newly learned
  research appears).
- Taking the result: `takeIngredientsFromStorage` (original `handleOnTake`: exact grid items first, then any matching item, tools that
  survive crafting are not consumed, leftovers to storage or dropped) else `consumeGrid` (original `SlotResearchCrafting.onTake`: each
  grid cell gives its ingredient's count). Crafting events are fired in both cases (the original skipped them on the storage path).
- `ICraftingContainer` is implemented by the menu (`getInventories`, `pushCraftingMatrixToInventories`).

## Wiring needed in shared files

1. `AncientWarfareCore.java`, in the constructor before `REG.register(modBus)`:
   ```java
   AwCoreCrafting.load();
   AwCoreCrafting.registerEvents(modBus);
   ```
   (import `io.github.capttracker.ancientwarfare.init.AwCoreCrafting`). Nothing else changes there: `EngineeringStationMenu::new`,
   `EngineeringStationBlockEntity::new` and the menu constructors have the same signatures.
2. `client/AwClient.java`, in `registerScreens`, replace the `PanelScreen` registration of the bench by
   ```java
   event.<EngineeringStationMenu, EngineeringStationScreen>register(AncientWarfareCore.ENGINEERING_STATION_MENU.get(),
           (menu, inventory, title) -> new EngineeringStationScreen(menu));
   ```
   `EngineeringStationScreen` extends `GuiContainerBase`, which needs the `AwMenu.setRefreshListener` addition named in the gui-toolkit notes.
3. `lang/en_us.json`: merge `core-crafting.lang.json` (only needed once a recipe viewer category exists; the screen uses no strings).
4. `ModRegistrar` (library) has no recipe registries; `AwCoreCrafting` keeps two private `DeferredRegister`s. When the library gets
   `recipeTypes` / `recipeSerializers`, move them (the library version must then be bumped).
5. Other modules' research recipes: run the converter (below) and ship their files; they need the same recipe serializers, which are
   registered by core.
6. `core-util` notes, item 11: the bench does not use `SlotResearchCrafting` (see "Problems in other slices").

## Converting other modules' research recipes

```
python -I D:/Spectre Labs Master/analysis/port/research_recipes_convert.py --module <core|automation|npc|structure|vehicle> [--dry-run] [--lenient]
python -I ...research_recipes_convert.py --source <folder> --namespace <ns> --out <resources dir> [--item-map map.json] [--tag-map map.json]
```
Reads `upstream/.../research_recipes/<module>` and the shared `_constants.json`, writes
`repo/mods/<namespace>/src/main/resources/data/<namespace>/recipe/research/<same relative path>.json` (the module folder
names in the repo must match; use `--out` otherwise). It reads `analysis/port/research_recipes_<module>_items.json` automatically
if present. Mapping tables are at the top of the script: `ORE_TAGS` (ore name to tag; all 79 names the recipes use are covered
for core, the rest was filled in from the usage scan but not run), `VANILLA_META` (wool, planks, logs, coal, stone, stonebrick,
sign), `VANILLA_RENAMES`. Items of the other mods that had metadata (worksite upgrades, torque parts, warehouse storage, flywheels,
bard instruments, gate spawners, vehicle spawners with types 0-20, npc spawners) need an item map from their port: key
`modid:item#data`, value an id, `{"tag": ..}` or `{"id": .., "components": {..}}`. Without one those recipes are reported as
SKIPPED (a dry run on `vehicle` converts 86 and skips 100 for the spawner data values), never silently guessed (`--lenient` writes
a visibly wrong `<item>_<data>` guess). Tags it uses that no tag file defines are listed at the end; `foodBundle` maps to
`ancientwarfarenpc:food_bundles`, which the npc port must create. Core was run: 15 recipes, 0 skipped.

## Not ported / decisions

- Recipe JSON from `config/ancientwarfare/research_recipes` and other mods' assets folders: replaced by datapacks.
- The plain `recipes/` folder (4 core files, `_constants.json`) belongs to the assets/recipes work, not this slice; the `_constants.json`
  files are inlined by the same kind of script (the converter's constant handling can be reused).
- JEI: `AWJEIPlugin`, categories, `MultiRecipeTransferHandler`, `PacketTransferRecipe` are out of scope. The pieces they need exist
  (`ICraftingContainer`, `CraftingMemoryController.setRecipe(recipe, forceSet)`, `matchIngredients` / visitor match,
  `AWCraftingManager.getResearchRecipes`).
- Vanilla crafting table support for count-less, non-gated research recipes (see above).
- The Research Book screen's recipe list per goal: `AWCraftingManager.getRecipesForResearch(recipeManager, goal)` is there for it.
- Dropped: `IIngredientCount`, `IngredientCount`, `IngredientOreCount`, `IngredientNBTRelaxed`, `ResearchRecipeFactory`,
  `ResearchRecipeBase.getCraftingRecipe` and the `RecipeResourceLocation` for `ForgeRegistries.RECIPES` registration.
- `ResearchGoal.tryStart` (research station slice) should call `AWCraftingManager.tryTakeIngredients(handler, goal.resources())`
  (clone-and-check is built in: it takes nothing unless everything can be supplied).

## Problems in other slices (found while porting; not edited)

- `inventory/SlotResearchCrafting.onTake`: `craftMatrix.asCraftInput()` is **trimmed** to the used area in 1.21 (it calls
  `CraftingInput.ofPositioned`), so `remainingItems.get(i)` / `craftMatrix.getItem(i)` indexing is wrong for any grid that does not
  fill the 3x3, and a research recipe's remaining list is only as long as the trimmed input (the loop would stop early and never
  consume the later slots). Map through `CraftingInput.Positioned.left()/top()` as `EngineeringStationMenu.consumeGrid` does, or
  use that class's logic. The bench does not use the slot.
- `InventoryTools.removeItem(handler, stack, simulate)` (used by `removeItems(handler, list)`) asks every matching slot for the
  full `stack.getCount()`, so a count spread over several slots can remove more than asked (5 + 12 removed for a request of 12 when the
  first slot holds 5). The bench uses `AWCraftingManager.extractExact` instead.
- `AwMenu` has no `setRefreshListener`, which `GuiContainerBase.bindMenu` calls (already in the gui-toolkit notes).

## Unconfirmed API / things to check at the first build

- `ExtraCodecs.strictUnboundedMap(...)` returns a `StrictUnboundedMapCodec`; it is used as a `Codec<Map<K,V>>` for `fieldOf`, as vanilla's
  `ShapedRecipePattern.Data` does. `MapCodec.flatXmap` (same as vanilla). `RecipeMatcher.findMatches` signature read from source.
- `RecipeManager.getAllRecipesFor(RecipeType<T>)` generic inference with `ResearchRecipe` (implements `Recipe<CraftingInput>` through a
  superclass); if javac cannot infer, add explicit type witnesses.
- `Ingredient.CONTENTS_STREAM_CODEC` (inside `SizedIngredient.STREAM_CODEC`) sends the resolved item list for tag ingredients, so the client
  sees items, not tags; fine for display, and recipe matching is server-only here.
- The recipe `type` ids `ancientwarfare:research_recipe` / `ancientwarfare:shapeless_research_recipe` are registry names of
  serializers; `RecipeType.simple` needs no registration for matching, it is registered anyway so `getType()` ids show in logs.
- `DeferredHolder<RecipeType<?>, RecipeType<ResearchRecipe>>` from `RecipeType.simple(...)` inference.
- `Slot.onTake` override on the result slot with `ResultContainer`; `Slot.tryRemove` semantics read from source, not run.
- FML classes are not needed by this slice.

## Rework candidates (for the playability pass)

- The grid being a template whose stacks must already hold the ingredient's count (12 paper means a 12 stack in the cell) is
  clumsy; a recipe-picker list with "missing: ..." display, no template items, would be easier. Counts are then free.
- Gated vs plain recipes by config is confusing (item list and per-recipe count rule); one `requires_research` per recipe file is clearer.
- Non-gated research recipes could be registered as vanilla `crafting_shaped` copies by the conversion script, restoring crafting table use.
- Wool in the backpack recipes is white wool only (1.12.2 meta 0); `minecraft:wool` tag would be friendlier. Steel ingot needs plain coal (not charcoal).
- Research book screen should list the recipes a goal unlocks (data is available).
- Recipe viewer support (JEI/EMI) with a category per research recipe type; research recipes are normal recipes so a generic category is cheap.
- The bench could show why nothing is craftable (no book, goal unknown, missing items).
- Client-side prediction of the recipe choice is naive (index change is applied at once, the server reply overwrites it).
