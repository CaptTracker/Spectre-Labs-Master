# structure-decor: coin stacks, fire pits, totem parts

Module `ancientwarfarestructure`, package `io.github.capttracker.ancientwarfarestructure`. Nothing was compiled; every
Minecraft and NeoForge name was checked against `analysis/mc-sources` (list of unconfirmed names at the end).

## What was ported

Java (all under `src/main/java/io/github/capttracker/ancientwarfarestructure`):

- `decor/CoinMetal` (the 4 metals; the NPC module owns the coin items, they are found by registry id `ancientwarfarenpc:coin_<metal>`)
- `decor/CoinStackBlock` (size 1 to 8 groups of 8 coins as `IntegerProperty size`; heights, add/take 8 coins, drops, tooltip, `placeFromCoins` for the NPC coin item)
- `decor/FirePitVariant`, `decor/FirePitBlock` (3 blocks `fire_pit`, `fire_pit_log`, `fire_pit_square` with `lit` state, light 15, smoke particles and sound, flint/torch to light, water bucket to extinguish, 4/16 high, no collision, needs sturdy floor)
- `decor/TotemVariant` (16 variants, shapes, extra parts of the multi-block variants), `decor/TotemPartBlock` (one block, properties `facing`, `variant`, `visible`), `decor/entity/TotemPartBlockEntity` (main position and drop variant, break handler)
- `decor/item/FirePitItem`, `decor/item/TotemPartItem` (16 items, one per variant, with the room check of the multi-block variants)
- `init/AwDecor` (all registrations, coin stack `SoundType`, creative tab contents)
- `client/DecorRenderClient`, `client/render/decor/DecorItemProperties` (item model predicates `size` and `lit`)

Assets and data (script `analysis/port/structure_decor_assets.py`, run with python -I; safe to re-run):

- 8 blockstates (4 coin stacks, 3 fire pits, totem_part with all 128 facing/variant/visible combinations), 8 coin stack height models plus 32 per-metal models, 6 fire pit models, 16 totem models, 26 item models, 19 textures (+ 4 mcmeta), 2 sounds
- loot tables for the 3 fire pits (`loot_table/blocks/`); coin stacks and totem parts drop through `getDrops` (they need no table)
- the 11 recipes of `recipes/structure` in `data/ancientwarfarestructure/recipe/`: ore-dictionary names became tags (`c:rods/wooden`, `c:cobblestones/normal`, `c:dyes/*`, `c:feathers`, `minecraft:logs_that_burn`), results with NBT became the flattened items (`totem_part_<variant>`, `fire_pit`, `fire_pit_log`, `fire_pit_square`, coin stack with `minecraft:block_state` size). The 4 coin stack recipes carry `neoforge:conditions` mod_loaded `ancientwarfarenpc` and use `ancientwarfarenpc:coin_<metal>`.
- `data/minecraft/tags/block/mineable/{pickaxe,axe,shovel}.json`
- `port-notes/structure-decor.lang.json`, `port-notes/structure-decor.sounds.json`

## Wiring needed in shared files

1. `AncientWarfareStructure.java` constructor, before `REG.register(modBus)`:
   ```java
   AwDecor.load();
   AwDecor.registerEvents(modBus);
   ```
   (import `io.github.capttracker.ancientwarfarestructure.init.AwDecor`)
2. The mod class (or the gates slice) must create the creative tab once: `REG.creativeTab(() -> new ItemStack(AwDecor.firePitItem(FirePitVariant.DEFAULT).get()))` or another icon. `AwDecor` fills it through `BuildCreativeModeTabContentsEvent` for the tab key `ancientwarfarestructure:main` (the id `ModRegistrar.creativeTab` uses). None of the decor items go through `REG.item`, so nothing is listed twice.
3. `assets/ancientwarfarestructure/lang/en_us.json`: merge `port-notes/structure-decor.lang.json`.
4. `assets/ancientwarfarestructure/sounds.json`: merge `port-notes/structure-decor.sounds.json` (keys `coin_stack_break` and `coin_stack_interact`; the oggs are already in `assets/ancientwarfarestructure/sounds/`).
5. NPC module (`ItemCoin` port): in `useOn`, when the stack has 8 or more coins, place a pile. The NPC module may not depend on this one, so look the block up by registry id: `BuiltInRegistries.BLOCK.getOptional(Ids.of("ancientwarfarestructure", "coin_stack_" + metal))`, place its default state on the clicked or adjacent replaceable position (check `canBeReplaced` and `isUnobstructed`), shrink the stack by 8 (also in creative, like 1.12.2) and play `ancientwarfarestructure:coin_stack_interact` (PLAYERS, 0.5, 1). `CoinStackBlock.placeFromCoins(UseOnContext, CoinMetal)` does exactly this for any caller that can see this module.
6. `build.gradle` already depends on `ancientwarfare` and `ancientwarfarevehicle` (for gates). Nothing to add for decor.

## Behaviour notes and decisions

- Coin stack `size` is 1 to 8 (8 to 64 coins). Items carry `minecraft:block_state` `size`; the item's default component is size 1 so crafted, dropped and tab stacks of one size stack together. The item models choose the pile model through the predicate `ancientwarfarestructure:size`.
- Fire pit items carry `lit` the same way (default `false`); lit items are listed in the creative tab and place lit. The item name ends in `.lit` or `.unlit`, as in 1.12.2. Breaking a pit always drops the unlit item (as the original). Pick-block keeps the lit state.
- Material.ROCK pits (default, square) have `requiresCorrectToolForDrops` plus the pickaxe tag, as the rock material needed a pickaxe in 1.12.2. If that feels bad in play, remove it in `AwDecor` (rework candidate).
- Resistance values were converted (1.12.2 `setResistance(0.4)` is 0.24 in 1.21 units).
- Totem part: facing is the horizontal direction the placer faces (same as 1.12.2). The 5 filler/side variants exist as items (for `/give`) but are not in the creative tab. Breaking any part removes the whole totem and drops the main block's variant once. `Block.asItem()` of `totem_part` is arbitrary (16 block items share one block); the code never uses it (pick-block and drops are overridden).
- The `dropVariant` is saved in the block entity instead of being re-read from the main tile on load.
- Fixes of small original issues (commented in code): the water-bucket click on a lit pit now consumes the click instead of falling through; totem break only clears positions that still hold totem blocks.
- Not ported on purpose: the fire pit's random tick and tick rate 30 (they did nothing), `BlockFaceShape` overrides, the 1.12.2 render layer methods (models use `render_type: cutout`).

## Other player-craftable blocks of the original Structures module

- `recipes/structure/` holds only the 11 recipes of this slice.
- The Structures module also has research-gated recipes in `research_recipes/structure/`: `gate_single_wood`, `gate_double_wood`, `gate_vertical_wooden`, `gate_single_iron`, `gate_double_iron`, `gate_vertical_iron`, `gate_drawbridge` (all produce `gate_spawner` items, the 7 gate types) and `drafting_station` (left out of the port, template only). **The gates are not part of this slice**; they must be ported by the gates slice (`13-structure-items-gui-gates.md`) and their research recipes converted with `analysis/port/research_recipes_convert.py --module structure`. Until that slice lands, gates are not in the game. This matters because they are player craftable for building.
- All other Structures blocks (seats, tables, coffins, altars, braziers, totem cubes, statues, flags and so on) have no crafting recipe and are creative or world-generation only.

## Not ported / open items

- Creative-only list order differs from 1.12.2 (variants are grouped per block).
- Totem blocks of totem cubes (`orc_totem_*`, `goblin_totem_*`) are `BlockTotemCube`, not totem parts, and have no recipe; not included.
- JEI display of the NBT results: none in 1.21 (not needed with flattened items).

## API names I could not confirm in the extracted sources

- `EventBusSubscriber.Bus.MOD` and `@EventBusSubscriber(bus=...)` (the annotation source is not in `analysis/mc-sources`; copied from the already-ported Core `CoreMiscClient`).
- `neoforge:conditions` / `neoforge:mod_loaded` in recipe JSON (from memory of NeoForge 21.1; the condition classes were not grepped).
- `c:rods/wooden`, `c:cobblestones/normal`, `c:dyes/<color>`, `c:feathers` existing as NeoForge 21.1 convention tags (data tags, not code).

## Rework candidates

- Build the coin stack and fire pit variants in a data generator instead of the script.
- Cobblestone tag `c:cobblestones/normal` excludes mossy and deepslate variants; the original `#COBBLESTONE` ore name was the same set plus nothing else, so this should match.
