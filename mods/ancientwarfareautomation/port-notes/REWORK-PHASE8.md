# Rework phase 8: production chains

## Stage: processing worksites, sawmill, bakery

Code: Automation `block/entity/worksite/ProcessingWorksiteBlockEntity` (shared base), `ProcessingRecipe`, `SawmillBlockEntity`, `BakeryBlockEntity`, `WoodRecipes`;
`menu/ProcessingMenu`, `SawmillMenu`, `BakeryMenu`, `ProcessingRecipeMenu`; `client/screen/GuiWorksiteProcessing`, `GuiWorksiteProcessingRecipes`; Core `api/WorksiteStock`,
`WorkAction.SAW/BAKE`, `IWorksiteStatus` keys; NPC `Skill.ofTask`, `SettlementApi.countItems`, lang.

### What changed
- **Processing base.** Extends `InventoryWorksiteBlockEntity`: input store (9), optional fuel store (3, burn pool like a furnace), main inventory as output. A list of `ProcessingRecipe`s, each with an
  on/off switch and a target stock (keep N, 0 = no limit), saved by recipe id. One staffed task: the batch at the block (`WorkTask` at the site, base ticks of the batch); on completion the input is
  consumed, fuel burnt and the products go into the main inventory. Machine mode (`machines_work_alone`) crafts one batch per work unit. Next recipe: the one furthest below its target, then the
  unlimited ones in turn. Couriers: `getLogisticsNeeds` stocks the inputs of the recipes that are on (and coal or charcoal for fuel); outputs are the main inventory. Status (`IWorksiteStatus`):
  `needs_input`, `needs_fuel`, `no_recipe`, `storage_full`, shown by Jade/WTHIT through the existing tooltip, and in the recipe screen.
- **Stock.** The stock of a recipe is its product in the site's main inventory plus the settlement storages around it, through the new Core hook `WorksiteStock` (the NPC module sets the counter, as
  Automation cannot see settlements). Without a settlement only the site's own inventory counts.
- **Sawmill.** Recipes planks, slabs, stairs, sticks, fences, doors. Log (`#minecraft:logs`) to planks of its wood type, `sawmill_planks_per_log` (6) per log; the others are crafted from those planks using the
  vanilla crafting recipes (so modded woods work); whole crafts only, a batch uses enough logs for one craft, leftover planks are given as planks. Planks is on by default.
- **Bakery.** Bread (6 wheat to 2 loaves, on by default), cake (buckets come back), pumpkin pie, cookies. With `bakery_needs_fuel` (true) each batch burns 100 to 200 ticks of fuel.
- **Screens.** The site screen (inventories, side button, Recipes button) and the recipe screen (switch, icon, target number box, stock, status), as `openAltGui` like the animal and fish farm.
- Blocks `sawmill` and `bakery`: blockstate, block and item model (vanilla stonecutter, stripped oak, planks and smoker textures), loot table, research recipe (construction, agriculture), axe tag, lang.
- New actions `SAW` and `BAKE` (non-breaking): census text, thought lines, crafting skill XP.

### Config (`ancientwarfareautomation-worksites.toml`, section `production`)
`sawmill_planks_per_log` (6), `bakery_needs_fuel` (true), `production_input_target` (64, raw inputs couriers keep in the input store).

### Deviations
- **Roles:** the roles follow the work type of the site (job board, `NpcWorker` roles), which is fixed. Both sites are work type crafting, so the existing craftsman works them; no new "sawyer" or "baker" role.
  The crafting skill gets the XP.
- **Smelter not refactored** onto the base: it works linked furnaces and a fire, a different shape; left untouched.
- **Work area:** the sites have none (like the stable); the worker stands at the block.
- **Cake** is not an edible item in vanilla, so the settlement does not count it as food; bread, pie and cookies are.
- Bread is made two loaves at a time (same ratio as vanilla). One worker at a time per site.

### Not play-tested
Built only. No test with a worker, couriers or the screens in game.

## Stage: smithy and tannery

Code: Automation `SmithyBlockEntity`, `TanneryBlockEntity`, `SmithyMenu`, `TanneryMenu`, hooks `missingCondition` and `consumedFor` in `ProcessingWorksiteBlockEntity`, paged `GuiWorksiteProcessingRecipes`;
Core `WorkType.SMITHING/TANNING`, `WorkAction.SMITH/TAN`, `IWorker.getCraftingRank`, `IWorksiteStatus.WATER`; NPC roles `smith` and `tanner` (`NpcWorker`), `Skill`, `StaffedWork.swingEffects`,
`NpcAIPlayerOwnedFetchTool`, lang, skin pack entries (the craftsman skins).

### What changed
- **Smithy** (blocks `smithy`, stone/pickaxe): iron, gold, diamond pickaxe, axe, shovel, hoe, sword, helmet, chestplate, leggings, boots at vanilla costs, plus leather armour. One recipe each with switch and
  "keep N" target. Fuel per item. Last recipe "Repair worn gear" (on by default): damaged repairable gear in the input gets repaired with 1 repair item per 25% lost durability (rounded up), comes out
  fully repaired into the main inventory and couriers take it to storage. Couriers bring damaged gear from storages (up to 3) and the matching repair items. Anvil sound and sparks on each swing and on completion.
- **Tannery** (block `tannery`, wood/axe): rotten flesh (4 per leather, 600 ticks, needs water) and rabbit hide (4 per leather, a fifth of the time, no water). Both on by default.
- **New roles** `smith` (WorkType SMITHING, tool hammer) and `tanner` (TANNING, tool shears), in the hall Role button, names, skins, spawner subtypes. Crafting skill XP; work speed uses the crafting skill.
  Craftsmen still work sawmill and bakery only; smiths and tanners only their own site.
- **Fetch tool flow**: it already matched vanilla tools by item tag and ability, so smithy output is taken. It now takes the best tool in storage (tier, wear counts) instead of the first.
- Recipe screen shows 10 recipes per page with page buttons.

### Config (section `production`)
`smithy_needs_fuel` (true), `smithy_fuel_per_item` (200 ticks), `smithy_repair_percent_per_ingot` (25), `smithy_master_save_chance` (0.15), `smithy_couriers_fetch_damaged` (true),
`tannery_flesh_per_leather` (4), `tannery_flesh_ticks` (600), `tannery_needs_water` (true).

### Deviations
- **Master bonus:** chose "save an ingot" (a master smith, crafting rank 4, does not use up one material item of a batch); extra durability cannot apply to fresh or fully repaired items.
- **Water:** a water source block (or waterlogged block) or water cauldron within 2 blocks; not consumed. Buckets not supported.
- **Copper:** vanilla 1.21.1 has no copper tools or armour, so none; copper ingots only count as a repair item for modded gear. **Netherite** skipped (template cannot be crafted).
- **Tannery bundles/saddles** skipped (bundle is experimental in 1.21.1, saddle has no recipe).
- Spawner icons for the new subtypes use the base icon (no art). Block textures are vanilla (smithing table, barrel). Research recipes: smithy needs refining, tannery animal husbandry.
- Stock counts include damaged items in storage.

### Not play-tested
Built only. No test with smith/tanner NPCs, couriers, repair or the paged screen in game.

## Stage: settlement demand

Code: Core `api/SettlementDemand` (hook), `api/IWorksiteProduction`, `IWorksiteLogistics.Need` priority; Automation `ProcessingWorksiteBlockEntity` (need-first select, priorities,
starved inputs), `ProcessingRecipe.Input.sample`, `SmelterBlockEntity` (starved fuel), `ProcessingRecipeMenu`, `GuiWorksiteProcessingRecipes`; NPC `settlement/SettlementNeed`,
`SettlementNeeds`, `Settlement`, `SettlementApi`, `SettlementConfig`, `NpcAIPlayerOwnedLogistics`, `TownHallMenu`, `GuiTownHallInventory`, `NpcThinking`, lang.

### What changed
- **Needs list.** Every 10 s (`demand_interval_ticks`) a settlement counts: tools for workers flagged no-tool by tool type (hoe, axe, pickaxe, hammer, quill, shears) less what is in stock; swords for soldiers
  without a weapon; armour per slot for loaded soldiers; food below `demand_food_days` NPC-days per member; a reserve of planks, stone, glass for phase 9; raw inputs and fuel a production site is starved of
  while the settlement has none; needs posted through the API. Stock = linked storages, worksite output stores and loaded couriers' backpacks (items on their way count). Priorities: tool 100, food 95, weapon 90,
  armour 70, fuel 60, input 50, building stock 40.
- **Production.** A site asks `SettlementDemand` per recipe (matched by the recipe's icon item). `select()` is now need (priority, then count) > target stock (furthest below) > unlimited recipes. A needed recipe
  is made even when its target is met, and with `demand_enables_recipes` (true) even when its switch is off (then the switch means "only when needed"). Each product made is reported (`fulfilled`) so the count
  drops before the next recount. Inputs and fuel of needed recipes get the need's priority.
- **Couriers.** Inputs are stocked in priority order; outputs that fill a need are taken first; a tool, weapon or armour that fills a need goes to the storage nearest to the worker or soldier who lacks it (the
  storage the fetch-tool goal checks first).
- **Screens.** The recipe screen shows "Need N" in orange on needed recipes. The hall Overview has a Needs section (top 8): name, count, "being made at the X", "the X will make it" or "no site can make this
  (build a Smithy)" with hints per kind.
- **Talk/census.** A worker without a tool whose tool is being made says "waiting for the smith to make a pickaxe" (three phrasings; maker word from the site: smith, sawyer, baker, tanner); the census keeps the
  no-tool flag and shows the task "Waiting for a tool to be made" when the worker is otherwise idle.
- **API for phase 9.** `SettlementApi.getNeeds(settlement)`, `postNeed(level, settlement, id, labelKey, matcher, wanted, priority, ttlTicks)` ("the settlement should hold at least `wanted`"; lapses after
  `ttlTicks`, 0 = until `clearNeed`), `clearNeed`.

### Config
NPC `ancientwarfarenpc-settlements.toml` section `demand`: `demand_enabled`, `demand_interval_ticks` (200), `demand_food_days` (3), `demand_reserve_planks` (64), `demand_reserve_stone` (64),
`demand_reserve_glass` (16), `demand_armour` (true). Automation worksites config: `demand_enables_recipes` (true).

### Deviations
- **Switch semantics:** with `demand_enables_recipes` a switched-off recipe is still made when needed; set it false for a hard off.
- **Soldiers' gear:** soldiers have no fetch-gear goal, so weapons and armour are made and hauled to the storage nearest the soldier; equipping stays with the player. Armour needs only count loaded soldiers.
- **Delivery to the worker:** outputs go to the nearest storage, not hand to hand (the fetch-tool goal does the last step).
- **Fuel/input matching:** a need is matched to a recipe by the recipe's icon item, so a "keep N" recipe whose icon differs from its product would not match; none do today. Smelter only reports starved fuel.
- **Shears, hammer, quill** have no recipe: they show "no site can make this" with a hand-craft hint.
- Posted needs are not saved; posters renew them.

### Not play-tested
Built only. No test of the counts, the need-first choice, courier priority routing, the hall section or the talk lines in game.

## Final check

Reviewed the whole uncommitted diff of phase 8 (all three stages) for item loss and duplication, missing assets, client classes on the server, demand churn and per-tick cost.

### Fixed
- **Free products from a renamed input.** The recipes counted ingots (and the tannery flesh) by item, but the batch took them out only when the components matched too, so a renamed ingot made the product and was not used up. `craft` now refuses a batch whose full input is not in the input store, and for items that cannot be damaged the take matches by item (a damaged gear item still has to match exactly).
- **Free repair by a master smith.** The master bonus saves one ingot of a batch; for a repair costing one ingot that made the repair free. The repair is now exempt; gear batches still save an ingot.
- **Hand-placed junk in the input and fuel slots.** `isItemValid` now matches `insertItem`, so a player cannot place an item that is neither an input nor fuel (the shift-click and hopper paths already refused it).
- **Cost of "keep N in storage".** Each stock check walked every storage of the settlement, once per recipe (32 in the smithy) on each choice. The settlement part is now cached per recipe for 20 ticks; the site's own inventory is counted fresh, so a batch is not made twice.
- **Missing text.** `npc.thought.worktype.smithing` and `.tanning` (used by the job board thought of a smith or tanner without a post).

### Checked and found sound
- Bucket return of the cake (3 buckets are products into the main inventory, none are consumed), repair and demand loops (a need is counted against storages, outputs and courier packs, so it ends when the item exists; a recipe made for a starved input is bounded by that input's missing count; a repaired item is no longer damaged and so is not fetched again), the new screens (the recipe screen has no slots and validates the index and clamps the target on the server), client classes (all screens are in the `client` packages and registered through `AutomationScreensClient`), and the assets of the four blocks (blockstate, block and item model with vanilla textures, loot table, research recipe, lang, mineable tag).
- Data generation: none of the mods use it for these files, so `runData` was not run.

### Not fixed (by design, listed in the stage deviations)
- A damaged tool held in the smithy's input store does not count as stock of that tool, so for a short time a new one may be made as well.
- Soldiers do not equip made gear themselves.

### Verification
`./gradlew build` passes. `./gradlew :ancientwarfarereforged:runServer` reached `Done (` with no errors from the mods; the worksites and settlements config files were corrected for the new keys (expected). Not play-tested.
