# Assets and data files

> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Paths are relative to src/main/resources/ in upstream 1.12.x @ 78178170b.

Scope: everything under src/main/resources except `assets/ancientwarfare/template/` (1,495 files, 140 MB uncompressed, covered elsewhere), plus `project_resources/` at the repo root. Class names are relative to `net.shadowmage.ancientwarfare`. 1.21.1 API names marked "(unverified)" were not checked against NeoForge 21.1 sources.

Measured totals for this slice: 5,861 files, 53.1 MiB on disk (55.7 MB decimal; 5,858 asset files plus `AW-logo.png`, `ancientwarfare_at.cfg`, `mcmod.info`). Sizes in this report are MiB unless marked MB. The brief quoted about 69 MB; I could not reproduce that figure. The template folder alone is 140 MB (not re-measured in verification), so 69 MB is probably the compressed jar total or a different cut. `project_resources/` adds 342 files and 207 MiB (217 MB decimal), none of which ships in the jar.

## Overview

| Asset family | Files | Size | Format | Read by | Action | Note |
|---|---|---|---|---|---|---|
| Lang | 8 | 448 KB | `.lang` key=value | Forge 1.12 language loader; keys used via `I18n.translateToLocal`, `TextComponentTranslation` (279 call sites) | b | 1,792 en_US keys; 7 other languages, 4 of them well below full coverage |
| Blockstates | 71 | 99 KB | Forge blockstate v1 (`forge_marker: 1`) | Forge ModelLoader | b/c | Defaults plus property variants; 36 override textures per variant |
| Models: block JSON | 84 | ~1.0 MB | Vanilla model JSON, many Blockbench exports | Forge ModelLoader | b | Texture refs need `blocks/` to `block/` and vanilla name flattening |
| Models: item JSON | 102 | 197 KB | Vanilla item model JSON | Forge ModelLoader | b | 16 have `blocking` overrides (shields) |
| Models: OBJ | 11 | ~0.2 MB | Wavefront OBJ | `core/render/BaseBakery`, `automation/render/FlywheelStorageRenderer`, `WindmillBladeRenderer` via CodeChickenLib `OBJParser` | d | Hand-baked quads in a custom renderer |
| Models: m2f | 14 | 104 KB | AW text model (`part=`, `box=` lines) | No loader found in SRC (grep for "m2f" finds nothing) | a (drop) | Dead legacy files from 1.7-era models |
| Textures | 716 (704 png, 12 mcmeta) | 2.4 MB | PNG, mcmeta animation | Model JSON, `TextureUtils`, renderers | a | Folder renames; 12 animated |
| skin_pack | 3,607 (3,606 png, 1 meta) | 12.9 MB | 64x64 PNG plus `skin_pack.meta` | `npc/skin/NpcSkinManager` | a + d | Assets copy; the runtime texture loader must be rewritten |
| Sounds (ogg) | 587 | 27.1 MB | Ogg Vorbis | `sounds.json` plus `structure/sounds/SoundLoader` | a | 186 are long structure ambience and music clips |
| sounds.json | 3 | 115 KB | Vanilla sounds.json (npc, structure, vehicle) | Minecraft SoundHandler | b | 332 events, 567 sound entries |
| Loot tables | 177 | 1.0 MB | 1.12 loot table JSON | `core/init/AWCoreLoot`, Forge loot manager | b/c | 158 chest, 19 entity |
| Recipes | 40 (39 recipes + `_constants.json`) | 17 KB | Forge `ore_shaped` and vanilla shaped | Forge recipe loader, `AWCraftingManager` | b | `_constants.json` is a Forge feature with no equivalent |
| Research recipes | 282 (281 + `_constants.json`) | 177 KB | AW custom recipe types | `core/crafting/AWCraftingManager` | d | Custom recipe type, custom ingredient types |
| registry/research | 30 | 20 KB | AW JSON, parser `research` | `core/registry/ResearchRegistry` | c | Uses ore dict and item metadata |
| registry/npc | 5 | 1.3 MB | AW JSON | `npc/registry/*` | c | Attribute names and entity class names change |
| registry/structure | 4 | 23 KB | AW JSON | `structure/registry/*` | c | Block ids and metadata |
| registry/crop_farm, tree_farm | 6 | 4 KB | AW JSON | `automation/registry/*` | c | Block ids |
| registry/compat | 35 | 69 KB | AW JSON with `"mod"` gate | Same parsers as above | c | 16 third-party mods |
| registry/manual | 75 (26 json, 49 images) | 5.9 MB | AW manual JSON, JPG | `core/manual/ManualContentRegistry` | c + a | Content needs rewriting for new item ids; images copy |
| trades | 1 | 1 KB | AW `trade:` text blocks | None found | a (drop) | Dead 1.7-era file |
| mcmod.info | 1 | 2 KB | Forge mod metadata, 5 entries | Forge | b | Becomes one `neoforge.mods.toml` per module |
| Access transformer | 1 | 1 KB | Forge AT, SRG names | Forge FMLAT | c | 8 entries; several targets gone |
| Logo | 1 | 164 KB | PNG | mcmod.info `logoFile` | a | |
| project_resources | 342 | 207 MiB | xcf, mmf, ogg, wav, docx, xlsx, png | Not shipped | a (reference only) | 174 MB are source music stems |

## Lang

Files: `assets/ancientwarfare/lang/{en_US,de_DE,fr_FR,ja_JP,nl_NL,ru_RU,zh_CN,zh_TW}.lang`. One namespace only (`ancientwarfare`) even though five mods exist; the other four modules have no lang files of their own.

Format, from en_US.lang:

```
#items
item.wooden_hammer.name=Wooden Hammer
tile.engineering_station.0.name=Engineering Station
entity.ancientwarfarenpc.courier.name=Courier
```

Key counts (en_US, 1,792 keys, no duplicates, no malformed lines):

| Prefix | Keys | Meaning |
|---|---|---|
| `entity.ancientwarfarenpc.<type>.name` | 672 | NPC names; the type string is built at runtime from faction, type and subtype (`NpcBase.java:793`, `NpcFaction.java:258`) |
| `guistrings.*` | 384 | GUI text, grouped by feature: `guistrings.npc` 79, `spawner` 46, `automation` 43, `research` 29, `structure` 19, and so on |
| `item.*` | 360 | 227 are `item.<name>.name`; 133 are other forms (`item.vehicleSpawner.*` 40, `item.worksite_upgrade.*` 11, `item.component.*` 9, ammo variants, tooltips) |
| `tile.*` | 157 | 153 are `tile.<name>.name` (several use a metadata form such as `tile.research_station.0.name`); 4 are other forms |
| `research.*` | 31 | One key per research goal, e.g. `research.invention` |
| `ftbu_aw2.notification.*` | 31 | FTB Utilities integration text |
| `structure.validation.*` | 27 | Structure validation errors |
| `command.aw.*` | 26 | Command help and replies |
| `route.*` | 24 | Warehouse routing (deposit, withdraw, fill, take) |
| `keybind.*` | 21 | Key binding names |
| `gui.*` | 16 | Misc GUI |
| `awconfig.*`, `itemGroup.tabs.*`, `equality.*`, `jei.*`, `death.*`, 16 dye colour keys, 1 stray `guistring` | 8, 6, 5, 4, 2, 16, 1 | |

Languages:

| File | Keys | Keys not in en_US | en_US keys missing |
|---|---|---|---|
| en_US | 1,792 | 0 | 0 |
| de_DE | 1,110 | 18 | 700 |
| ja_JP | 1,127 | 13 | 678 |
| zh_CN | 1,118 | 13 | 687 |
| ru_RU | 713 | 141 | 1,221 |
| zh_TW | 658 | 251 | 1,385 |
| fr_FR | 804 | 341 | 1,330 |
| nl_NL | 606 | 154 | 1,341 |

The "not in en_US" column is stale keys from older versions; a script should drop them. Many "translated" values equal the English text (ja_JP 374, de_DE 226, nl_NL 261), so real translation coverage is lower than the key counts suggest. 49 en_US values use `%s` or `%1$s` formatting, which carries over unchanged (verification count; 65 values contain a `%` at all, the remaining 16 were not classified). No values use section-sign colour codes. zh_TW has one non-key line. fr_FR, nl_NL and ru_RU each have one duplicate key.

Loader: the 1.12 default `.lang` mechanism; no custom loader. Code references keys by string, so every translation key used in code must still exist after conversion.

1.21.1: class (b) for the format, with a hand-work part.
- Convert to `assets/<modid>/lang/<lang>.json`, lowercase names (`en_us.json`, `zh_cn.json`, `zh_tw.json`, `ja_jp.json`), flat `"key": "value"` objects. Skip `#` comment lines, drop stale keys, escape quotes and backslashes.
- The `.name` suffix is dropped by the registry key scheme: `item.wooden_hammer.name` becomes `item.<modid>.wooden_hammer`, and `tile.X.name` becomes `block.<modid>.X`. The PORTING.md table covers this.
- The one real issue: 1.12 keys carry no mod id, because all five modules share one namespace. In 1.21.1 the key embeds the mod id of the registering module, so each `item.*` and `tile.*` key must be routed to the right module's lang file. That needs the module registry from the other inventory slices (STATS has per-module block and item lists).
- Metadata-style keys (`tile.research_station.0.name`, `item.component.*`, `item.vehicleSpawner.*`, ammo variants) imply metadata or NBT variants. After the flattening in 1.13 each becomes its own item, so the key becomes the new item's own key. The mapping depends on the item redesign, not on the lang file.
- Entity names stay `entity.ancientwarfarenpc.<type>`, but the 1.21.1 form is `entity.<modid>.<registry_name>` (check that the NPC subtype strings used in code become registry names; otherwise keep the runtime-built lookup).
- The 672 NPC name keys (prefix count; verified) must be extended to 1.21.1 entity types or kept as custom-name lookups; see Open questions.

## Blockstates

71 files in `blockstates/{automation 18, core 2, npc 4, structure 42, vehicle 5}`. All use the Forge v1 format (`forge_marker`, `defaults`, `variants`):

```json
{ "forge_marker": 1,
  "defaults": { "model": "ancientwarfare:core/engineering_station" },
  "variants": { "normal": [{}],
    "facing": { "south": {}, "west": { "y": 90 }, "north": { "y": 180 }, "east": { "y": 270 } } } }
```

Statistics: 56 use `defaults.transform` (mostly `forge:default-block`), 36 use `defaults.textures` (per-variant texture override), 6 define an `inventory` variant, 3 use `submodel`. Properties per file: 0 in 11 files, 1 in 29, 2 in 15, 3 in 8, 4 to 8 in 8. The most common property names are `facing` (37 files), `variant` (16), `visible` (10), `active` (7), `part` (6), `size=N` style keys (4, structure gates or banners).

Loader: Forge ModelLoader with `forge_marker`. Block classes also register models in code (12 automation block classes reference `ModelResourceLocation` or `ModelLoader`).

1.21.1: Forge blockstate v1 is gone, and so is the `normal` key and `defaults` merge. Vanilla blockstate JSON uses `"variants": {"facing=north": {"model": "...", "y": 180}}`.
- Class (b) for about 40 files: expand `defaults`, take the Cartesian product of property keys, rename `normal` to `""`, emit one variant per combination. The property names and values must match the new `BlockState` properties, so this needs the block inventory.
- Class (c) for the 36 files with `defaults.textures`: the script must write a child model JSON per variant (`parent` plus `textures`), since vanilla blockstates cannot override textures.
- `transform` (`forge:default-block`) moves into the model's `display` block (the script can inject the standard block display transforms, or use `minecraft:block/block` as the parent).
- `inventory` variants become item models.
- `submodel` (3 files) has no vanilla equivalent: needs multipart or a hand-built model.

## Models

186 JSON files (84 block, 102 item) plus 11 OBJ and 14 m2f.

JSON facts: 102 files define `elements` inline; 99 carry a `credit` field (Blockbench export); 79 have `display`; 41 have `groups` (Blockbench outliner; harmless); 16 have `overrides` using the `blocking` predicate (shields, set up in `npc/item/ItemShield.java:29`); 4 have `texture_size`. Parents: 101 files have no parent, 38 use `item/generated` or `item/handheld`, 4 `item/handheld_rod`, the rest chain to AW models (shields, weapon variants).

Texture references:

| Pattern | Refs | Action |
|---|---|---|
| `ancientwarfare:blocks/...` | 113 | rename to `block/` |
| `ancientwarfare:items/...` | 101 | rename to `item/` |
| `blocks/...` without namespace | 34 | Resolves to the `minecraft` namespace in 1.12; check each, likely meant to be AW textures or vanilla |
| `minecraft:blocks/...` | 5 | Vanilla texture names changed in 1.13 |
| `#0`, `#texture`, `#side` | 20 | local texture variable refs, unchanged |
| bare `items/...` | 1 | rename |

Vanilla texture names used (must be mapped to 1.21.1 names): `blocks/planks_oak` to `block/oak_planks`, `blocks/log_oak` to `block/oak_log`, `blocks/log_oak_top` to `block/oak_log_top`, `blocks/cobblestone` to `block/cobblestone`, `blocks/fire_layer_0` to `block/fire_0`, `minecraft:blocks/wool_colored_red` to `minecraft:block/red_wool` (names from memory of the 1.13 flattening; check against the 1.21.1 client jar).

1.21.1 handling:
- (b) JSON models: a script renames texture paths, drops `credit` (optional, harmless to keep), and checks the `display` keys. 1.21.1 still reads `models/item/*.json` (the new item-definition files arrive in later versions; unverified for 1.21.1). `overrides` with a `blocking` predicate still work if the predicate is registered client-side with `ItemProperties.register` (unverified); the 1.21 `use`/`blocking` handling for shields should be checked.
- (d) OBJ: 11 files for flywheels, generators, torque shafts and windmill blades. They are parsed with CodeChickenLib `OBJParser` and rendered through `CCModel` bakery code, which has no counterpart in 1.21.1. NeoForge ships its own OBJ model loader (`neoforge:obj` loader in a model JSON, unverified) and BlockEntityRenderer for the animated parts. Needs renderer work per block, not a file conversion.
- m2f files (`models/automation/*.m2f` 12, `models/vehicle/*.m2f` 2): plain text with `part=` and `box=` lines, e.g. `box=cube,0.0,0.0,0.0,...`. No class in SRC loads `.m2f`. Vehicle and torque renderers load OBJ or are hand-coded. Treat as unused source art and do not ship them. (`project_resources/models` holds 66 related `.mmf`, `.blend`, `.mf2` files.)

## Textures

716 files in `textures/`: blocks 203, entity 61, gui 4, items 330, model 118. Plus `AW-logo.png` at the root. Total 2.4 MB, so this whole family is small.

| Folder | Files | Sub-folders |
|---|---|---|
| `blocks/` | 203 | automation 92, core 10, npc 4, reinforced 5, structure 92 |
| `items/` | 330 | automation 20, core 16, modeler 1, npc 82, structure 19, vehicle 192 |
| `entity/` | 61 | npc 15, structure 44, vehicle 2 |
| `model/` | 118 | automation 25, core 2, structure 13, vehicle 78 (entity/tile-entity renderer textures) |
| `gui/` | 4 | `guibackgroundlarge.png`, `guibuttons1.png`, `guibuttons2.png`, `manual.png` |

Animated textures (12 `.png.mcmeta` files, all in `textures/blocks/`):

| Texture | Size | mcmeta |
|---|---|---|
| automation `animal_farm_rear`, `crop_farm_rear`, `tree_farm_rear` | 16x32 | `interpolate: false, frametime: 3` |
| automation `fish_farm_rear`, `fruit_farm_rear`, `quarry_rear` | 16x48 | same |
| npc `town_hall` | 512x8192 (a 512-wide strip; 8192 / 512 = 16 square frames of 512x512, verified from the PNG header) | explicit `frames` list, time 3 |
| structure `advanced_spawner` | 64x256 | explicit frames index 0 to 3, time 1 |
| structure `ancient_stack`, `dragon_stack`, `owl_stack`, `stag_stack` | 16x64 and 16x32 | animation |

The mcmeta format is unchanged in 1.21.1. `town_hall.png` is a very large animated block texture; 1.21.1 treats the frame size as width x width unless `width` and `height` are set in the mcmeta.

Textures assembled or loaded at runtime:
- NPC skins are read from the skin pack (and from `config/ancientwarfare/npc/skins/`) as raw PNGs and uploaded with a custom `TextureImageBased` (`core/util/TextureImageBased.java`, called from `npc/proxy/NpcClientProxy.java:140`) under `ancientwarfare:skinpack/<name>`. They are not in `textures/`.
- `structure/render/LayeredCustomColorMaskTexture.java` composes a coloured texture from a base and a mask at runtime (dyeable items, probably banners or tents).
- `core/util/TextureFileBased.java` loads textures from disk (used for structure preview or custom images).
- Gates build texture paths in code: `structure/gates/types/Gate.java:109` (`textures/model/structure/gate/gate...`), plus `StoneCoffinRenderer` and `WoodenCoffinRenderer` (`textures/model/structure/stone_coffin_<variant>.png`). Any renamed file must keep these patterns or the code must change.

1.21.1: class (a). Rename `textures/blocks` to `textures/block` and `textures/items` to `textures/item`; `textures/model`, `entity`, `gui` keep their names (`textures/entity` stays; `textures/gui` is still read by GUI code through a plain ResourceLocation, though GUI sprites in 1.20.2+ use `textures/gui/sprites`; unverified for AW's custom GUI system). Check the file names for uppercase letters (1.13+ requires lowercase file names and `[a-z0-9/._-]` only; I did not audit this). Runtime loading classes (`TextureImageBased`, the mask texture) need a rewrite using `DynamicTexture` and `NativeImage` (unverified for 1.21.1 constructors).

## skin_pack

Folder `skin_pack/`: 3,606 PNG, all 64x64, 12.9 MB, plus `skin_pack.meta` (2,695 lines by line count; 2,694 newline characters).

`skin_pack.meta` format: `<npc type>=<png name>`, one line per mapping, e.g. `worker.farmer=po_worker_farmer_1.png`, `trader=po_trader_1.png`. Keys are `<faction>.<type>[.elite]` or `worker.<job>` and similar; 655 distinct NPC type keys across 51 prefixes (19 factions have the full set of 19 prefixed types each; for example `orc.soldier` lists 22 skins, `buffloka.soldier` 20, `empire.trader` 16).

Mapping facts:
- 1,886 distinct PNGs are referenced; 372 of them are mapped to more than one type (shared skins).
- 1,773 PNGs are not listed in the meta. `NpcSkinManager` registers every unlisted PNG under the type `custom` (`loadSkinsFromSource`). These are the skins a player can pick in the skin GUI but that no NPC type spawns with.
- 53 lines reference a PNG that does not exist in the folder (dangling entries). They are silently ignored at runtime because the loader iterates files and looks up mappings, not the other way round.

How skins are chosen (`npc/skin/NpcSkinManager.java`, `NpcSkinSettings.java:64`, `NpcSkinSettings.java:67`):
1. `loadSkins()` runs client-side; if the config `load_default_skin_pack` is true (`npc/config/AWNPCStatics.java:108`) it reads the jar folder `assets/ancientwarfare/skin_pack`, and then it reads user packs from `config/ancientwarfare/npc/skins/`.
2. `getNpcTexture(type, id)` seeds a `Random` with the NPC's id (`getIDForSkin()`), picks one skin from the type's list, so a given NPC always shows the same skin.
3. For player-owned NPCs the GUI (`npc/gui/GuiSkinSelection.java`) lets the player choose a type and a specific skin or randomise.
4. Fallback is `BASE_DEFAULT_TEXTURE` (`textures/entity/npc/npc_default.png`).

1.21.1: the PNG files are class (a) (copy). The loader is class (d): reading files from the jar and a config folder with `Files.walk` and uploading textures by hand does not fit the resource pack system. Options: ship skins as normal resource-pack textures under `textures/entity/npc/skins/` with the `.meta` mapping converted to one JSON (`assets/<modid>/skins.json`) loaded by a `ResourceManagerReloadListener`; user skin packs then become ordinary resource packs (the user-config folder approach can stay for custom skins, using `DynamicTexture`; unverified). 3,606 textures at 64x64 are fine for an atlas-free entity texture setup. A script should also drop or fix the 53 dangling lines.

## Sounds

Counts (ogg files): `ancientwarfare/sounds/npc` 362 (356 in the folder plus 6 in `npc/bard`), `ancientwarfare/sounds/events` 2, `ancientwarfarestructure/sounds` 202 (15 top-level, 1 in `auto_load`, 186 in `structures`), `ancientwarfarevehicle/sounds` 21. Total 587 files, 27.1 MB: structure 22.5 MB, npc 4.9 MB (4.9 MB total with bard 1.2 MB), vehicle 0.3 MB, events 0.07 MB.

sounds.json mapping (three files, one per namespace; the `ancientwarfare` namespace has none, so its files are mapped from the npc one):

| File | Events | Sound entries | Notes |
|---|---|---|---|
| `ancientwarfarenpc/sounds.json` | 119 | 345 | Points at `ancientwarfare:npc/<name>` and `ancientwarfare:events/teleport_in` and `teleport_out`; `bard.tune1` to `bard.tune6` point at `ancientwarfare:npc/bard/*`; category `hostile` or `neutral`, `"stream": false` |
| `ancientwarfarestructure/sounds.json` | 199 | 201 | Points at `ancientwarfarestructure:<name>` and `ancientwarfarestructure:structures/<name>`; one event (`demonic_coffin_opens`) lists its sounds as plain strings, which is valid vanilla form and not an error |
| `ancientwarfarevehicle/sounds.json` | 14 | 21 | Uses `weight` for variants (e.g. `ballista_launch` with `_1` and `_2`) |

The sound event names are registered in code: `npc/init/AWNPCSounds.java`, `structure/init/AWStructureSounds.java`, `vehicle/init/AWVehicleSounds.java` (each creates a `SoundEvent` whose name equals the sounds.json key).

Long tracks: the longest are `structures/xoltec_town_long.ogg` (1.47 MB), `sarkonid_music.ogg` (1.46 MB), `village_market_general_background_1.ogg` (1.32 MB). The 186 structure sounds are ambient loops and music played by sound-block tile entities (`structure/gui/GuiSoundBlock`, `GuiSoundSelect`) and `core/util/SongPlayData.java`, with the `auto:` prefix handling for `auto_load` sounds. Name prefixes in `structures/`: witch 24, village 22, volcano 17, wizard 13, del 13, creepy 11, evil 9, haunted 7, xoltec 6, wizards 6, vampire 6, horn 6, gongs 6, undead 5, cthulhu 4, barbarian 4, mythbusters 3, and about 30 smaller groups.

Origin unclear or risky (no credits file exists in the repo other than `template/AW structure building credits.docx`, which is about structure builders):
- 13 `del_*.ogg` files (names such as `del_cashinhand`, `del_cowboy`, `del_dontbeaplonker`) and 3 `mythbusters_*` plus 2 `myth*` files look like clips from TV programmes. They are very likely copyrighted. Do not ship them without clearing the rights.
- `bard/*` tunes come from `project_resources/ogg_sounds` (stems named "Drums of the Deep - 103", "Master of the Feast - 122", "Teller of the Tales - 130"); `suonatore_di_liuto.ogg` has no matching source. Composer and licence are not recorded anywhere in the repo.
- The `respack` source folder has NPC voice packs (`barbarian_attack`, `dwarf_hurt`, and so on), source unknown.
- `structures/test_drums` / `auto_load/test_drums.ogg` is named as a test file.
- Likeness risk (verification): `ancientwarfare/skin_pack/` has 8 PNG skins named after real people from a TV programme (`custom_myth_adamsavage`, `custom_myth_jamiehyneman`, `custom_myth_kari`, `custom_myth_grantimahara`, `custom_myth_torybelleci`, `custom_myth_scotty`, `custom_myth_busters_tori`, `custom_myth_buster`), plus `custom_only_fools_del_boy` and `custom_hp_madam_hooch` (named after a character). Decide before release whether they ship; they also fall under the `custom` pool (see skin section).
- Counts confirmed in verification: 13 `del_*.ogg`, 3 `mythbusters_*.ogg`, 2 `myth_*.ogg`. Skin PNGs with `myth` in the name total 8.
- Licence of the whole sound set is not stated; the repo licence is GPL-3.0-or-later, which covers code only as far as can be told.

Runtime sound registration: `structure/sounds/SoundLoader.java` scans `assets/ancientwarfarestructure/sounds/auto_load/` at resource reload and registers a `SoundEvent` for every `.ogg` it finds, via reflection on `SoundHandler.loadSoundResource` and an unlocked registry. Intended for user-added sounds. 1.21.1 does not allow late registry additions; use fixed `SoundEvent`s through a deferred register, and for user sounds look up the sound by `ResourceLocation` at play time, for example `SoundEvent.createVariableRangeEvent(location)` (unverified).

1.21.1 action: ogg files class (a), straight copy (verification: of the 587 ogg files, 314 are mono and 273 are stereo, read from the Vorbis identification header; stereo sounds are not distance-attenuated in vanilla, and the same is expected in 1.21.1, unverified). sounds.json class (b): same JSON layout; check that the removed or ignored `category` key does not break (unverified), and that plain-string entries and `weight` still parse (they do in vanilla). SoundLoader class (d). Sound event registration moves to `DeferredRegister<SoundEvent>`.

## Loot tables

177 files in `loot_tables/`: `chests` 158 (123 top-level, 28 under `compat/<mod>/`, 7 under `inject/`), `entities` 19.

Example (entity table `entities/archer.json`):

```json
{ "pools": [ { "name": "archer", "rolls": 1, "entries": [
  { "type": "item", "name": "ancientwarfarenpc:coin", "entryName": "coin_copper", "weight": 1,
    "functions": [ { "function": "set_count", "count": { "min": 1, "max": 8 } },
                   { "function": "set_nbt", "tag": "{metal:\"copper\"}" } ] } ] } ] }
```

Loader: `core/init/AWCoreLoot.java`.
- `loadChestLootTables()` walks `assets/ancientwarfare/loot_tables/chests` inside the jar and calls `LootTableList.register("ancientwarfare:chests/<path>")` for each file, so chest tables live in the assets tree rather than a data pack.
- `compat/<mod>/` tables register only when that mod is loaded (`Loader.isModLoaded`).
- `inject/<name>.json` tables are not registered; they are merged into the vanilla table with the same name (`abandoned_mineshaft`, `desert_pyramid`, `igloo_chest`, `jungle_temple`, `simple_dungeon`, `stronghold_corridor`, `village_blacksmith`) in `lootLoad(LootTableLoadEvent)`: pools are matched by pool `name` and entries appended.
- Entity tables are referenced from `registry/npc/*.json` (`loot_table`) and from the NPC classes.
- The 28 compat tables cover betteranimalsplus (4), biomesoplenty (1), gemsplusplus (1), harvestcraft (13), rustic (8), zawa (1).
- Chest table names are also referenced from structure templates and the spawner code (another slice).

Format changes and affected files (files are counted once per change; a file can appear in several rows):

| Change | Files | Occurrences | 1.21.1 |
|---|---|---|---|
| Item metadata via `set_data` function | 28 | 76 | no data values; the item must become its own id or a component. Items like `minecraft:dye`, `fish`, `wool`, `log` need the 1.13 flattening map |
| `set_nbt` (`tag` as SNBT string) | 55 | 414 (+ 1 prefixed `minecraft:set_nbt`) | removed in 1.20.5; use `minecraft:set_components` with component JSON. AW's coin `{metal:"copper"}` needs a coin item or component design first |
| `set_count` | 170 | 1,430 | still `minecraft:set_count`, with `count` as a number or a `{type: uniform, min, max}` provider; `{min,max}` without `type` is not valid in 1.21 (the vanilla default type applies only for numbers; unverified) |
| `rolls` as `{min,max}` | 137 | | needs `{"type":"minecraft:uniform","min":..,"max":..}` (unverified, 1.21 number provider rules) |
| `enchant_with_levels` | 5 | 80 | format changed in 1.21 (enchantments are registry holders; `options` field; unverified) |
| `enchant_randomly` | 3 | 21 | `options` field instead of `enchantments` list (unverified) |
| `looting_enchant` | 6 | 7 | replaced by `minecraft:enchanted_count_increase` (unverified) |
| `set_damage` | 3 | 105 | still exists; value format differs (damage is a fraction) |
| `set_attributes` | 2 | 2 | modifier `name` becomes `id`, operation names renamed (`addition` becomes `add_value`, unverified); attribute names lose the `generic.` prefix |
| condition `random_chance` | 8 | 206 | still `minecraft:random_chance` |
| condition `entity_properties` with custom property `ancientwarfarenpc:rides_vehicle` | 1 | 5 | custom entity property, defined in `npc/entity/faction/attributes/EntityVehicleProperty.java`; needs a custom `LootItemConditionType` (d) |
| entry `type: loot_table` (references other tables) | 21 | 43 | still supported; names must match the new paths |
| entry `type: empty` | 19 | 50 | supported |
| `entryName` | 53 | | no longer a field; remove |
| pool `name` (used by `AWCoreLoot` for merging) | all 177 | | the merge key disappears; injection moves to global loot modifiers or `LootTableLoadEvent` (NeoForge names unverified) |
| unprefixed item ids (`cooked_beef`) | 144 | | still valid with the default `minecraft` namespace, but ids renamed in 1.13+ need mapping |

Class: (b) for the mechanical field renames; (c) for `set_nbt` and `set_data`, because each requires deciding the new item or component; (d) for the entity-properties condition and for the inject mechanism; also directory rename `loot_tables` to `loot_table` and moving from `assets/` to `data/` (the 158 chest tables are in the assets tree and must move to `data/ancientwarfare/loot_table/chests/`).

## Recipes and research recipes

### recipes/ (40 files)

`recipes/{automation 9, core 4, npc 15, structure 11}` plus `_constants.json`. Types: 19 `forge:ore_shaped`, 19 `minecraft:crafting_shaped`, 1 `minecraft:crafting_shapeless`. Result namespaces: ancientwarfarenpc 15, ancientwarfare 13, ancientwarfarestructure 11. Loaded by Forge's own recipe loader, which also reads `_constants.json` (a Forge-only feature). In the 15 constants: `LOGWOOD`, `INGOTIRON`, `INGOTSTEEL`, `PLANKWOOD`, `STICKWOOD`, `INGOTGOLD`, `COBBLESTONE`, `STONE`, `GEMDIAMOND`, `DYEBLACK`, `DYEWHITE`, `DYERED`, `DYEBLUE`, `DYEYELLOW`, `FEATHER`. 34 recipe files use `"item": "#NAME"` references.

Changes: ore dictionary to tags (via constants); `minecraft:item_nbt` result type with SNBT `nbt` (11 files, e.g. fire pit `{variant:"default",lit:0b}`) becomes `components`; 9 files use `data` (metadata); result key `item` becomes `id` in 1.21 (unverified); directory `recipes` becomes `recipe`. The tool script must expand `#NAME` constants inline, since 1.21.1 has no constants file.

### research_recipes/ (282 files)

`research_recipes/{automation 49, core 15, npc 23, structure 8, vehicle 186}` plus `_constants.json` (31 constants). Loaded by `core/crafting/AWCraftingManager.java:165-166` from `config/ancientwarfare/research_recipes` and from every active mod's `assets/<modid>/research_recipes`. Custom registry `ancientwarfare:research_recipes` (line 72).

Types: `ancientwarfare:shapeless_research_recipe` 186 (all vehicle), `ancientwarfare:research_recipe` 95 (shaped). Ingredient types used: `ancientwarfare:item_count` 257, `ancientwarfare:ore_dict_count` 167, `minecraft:item_nbt` 128 (results), `forge:ore_dict` 64, `ancientwarfare:item_nbt_relaxed` 2. Each recipe names a `research` that must be learned before crafting at the research station. Result namespaces: vehicle 186, automation 49, npc 23, core 15, structure 8. 92 files use `#` constants, 172 use `data`, 115 use `nbt`, 108 use `ore`. The `ore` names in use include `string` (55), `ingotIron` (32), `leather` (23), `blockCactus` (16), `plankWood` (12), `chest` (10), `stickWood` (9), `feather` (9), `gunpowder` (7), `dustRedstone` (5).

Class (d): these are not vanilla recipes. They need a custom `RecipeType` and `RecipeSerializer` plus custom ingredients with counts, and a research gate in the crafting container. The JSON can be converted by script once the new recipe type's schema is decided; tag ids for the ore dictionary names (`c:` convention tags; for example `ingotSteel`, `gearIron`, `shaftWood`, `foodBundle` have no obvious convention tag, so AW must define its own) are class (c).

## registry/ data

Loaded by `core/registry/RegistryLoader.java`. It walks `assets/ancientwarfare/registry` in the jar and `config/ancientwarfare/registry` (overrides win, per file name). The parser is chosen by the file's `type` field, else by the file name. Optional `load_after` (list of parser names, topological ordering), `mod` (only load if that mod is present) and `disabled` fields. Modules register parsers in their init (`AncientWarfareCore.java:77`, `AncientWarfareNPC.java:130-134`, `AncientWarfareAutomation.java:104-110`, `core/proxy/ClientProxy.java:44`).

| Folder | JSON files | Parser name (class) | What it configures |
|---|---|---|---|
| `research/` | 30 | `research` (`core/registry/ResearchRegistry`) | One research goal per file: `time`, `dependencies`, `resources` (item or ore dict ingredients with counts). Example: `mathematics.json`, time 200, depends on `engineering`, needs 3 books, 5 redstone dust, a note block, 4 charcoal (`data: 1`). 12 files use item data, 28 use ore names |
| `npc/factions.json` | 1 | `factions` (`npc/registry/FactionRegistry`) | 44 factions with standing rules and hostility maps; `load_after: target_lists` |
| `npc/faction_npc_defaults.json` | 1 | `faction_npc_defaults` (`NpcDefaultsRegistry`) | Default attributes (`generic.maxHealth`, `generic.movementSpeed`, and so on), XP, flags, loot table, per-subtype overrides (20 subtypes: soldier, archer, cavalry, leader, priest, spellcaster.1-5, bard, trader, civilian...), 44 faction sections; 1.3 MB, the largest registry file |
| `npc/owned_npc_defaults.json` | 1 | `owned_npc_defaults` | Same for player-owned NPCs |
| `npc/target_lists.json` | 1 | `target_lists` (`TargetRegistry`) | Entity id lists: `hostile` 687, `friendly` 91 entries (includes ids of other mods such as `abyssalcraft:*`) |
| `npc/faction_trade_lists.json` | 1 | `faction_trade_lists` (`FactionTradeListRegistry`) | 130 trade list templates (`defaults`) and `faction_defaults` for 2 factions; each trade has `input`, `output`, `refill_frequency`, `max_trades` and item `name`, `count`, `data`, `nbt` |
| `structure/biome_groups.json` | 1 | `biome_groups` (`BiomeGroupRegistry`) | 37 named biome groups; all lists are empty in the shipped file (data comes from config or the `BiomeGroups.cfg` file in project_resources, which has 1,280 lines) |
| `structure/structure_blocks.json` | 1 | `structure_blocks` (`StructureBlockRegistry`) | `blockstate_to_item` 33 mappings and `block_passes` 55 (build order pass per block) |
| `structure/entity_spawn_nbt.json` | 1 | `entity_spawn_nbt` (`EntitySpawnNBTRegistry`) | Which NBT keys to save per entity class (6 classes) |
| `structure/territory_settings.json` | 1 | `territory_settings` (`TerritorySettingRegistry`) | 70 territory definitions (radius, weight multipliers) |
| `crop_farm/` | 3 | `crop_blocks` (5 crops), `soil_blocks` (2), `tillable_blocks` (3) | Crop farm behaviour per block |
| `tree_farm/` | 3 | `saplings` (1), `tree_scanners` (6), `tree_soil_blocks` (4) | Tree farm behaviour |
| `compat/<mod>/` | 35 | Same parsers as above, with `"mod": "<id>"` | 16 mods: biomesoplenty (5 files), forestry (2), growthcraft (4), natura (4), plants2 (4), rustic (4), harvestcraft (1), immersiveengineering (1), randomthings (1), ebwizardry (1), grimoireofgaia (1), iceandfire (1), oreberries (1), oreshrubs (1), tconstruct (2), twilightforest (2) |
| `manual/` | 26 json + 49 images | `manual_content` (client only) | See Manual |

Parser names are also fruit farm: `fruit_blocks` (`FruitFarmRegistry`) in the compat folders (8 files).

Per-parser file counts in the whole registry tree (including compat): tree_scanners 8, fruit_blocks 8, saplings 7, tree_soil_blocks 5, entity_spawn_nbt 4, crop_blocks 4, soil_blocks 3, tillable_blocks 3, and one file each for the 30 research goals, factions, faction_npc_defaults, owned_npc_defaults, target_lists, faction_trade_lists, biome_groups, structure_blocks, territory_settings.

Block and item ids inside these files use 1.12 names. Counts of ids that were renamed or split in the 1.13 flattening (JSON string occurrences across non-manual registry files): `minecraft:log` 56, `fish` 27, `planks` 22, `wool` 22, `double_plant` 18, `log2` 14, `dye` 14, `yellow_flower` 9, `reeds` 7, `sapling` 6, `leaves` 4, `netherrack` 4 (unchanged id), `fence` 3, `cooked_porkchop` 3 (unchanged), `grass` 3, `waterlily` 2, `golden_apple` 2 (unchanged), `mycelium` 2 (unchanged). The ones that need a mapping are those with `data`/`properties` plus the renamed ids (`reeds` to `sugar_cane`, `waterlily` to `lily_pad`, `fence` to `oak_fence`, `grass` to `short_grass` or `grass_block`, `yellow_flower` to `dandelion`, and so on; names from memory of the flattening, to be checked).

1.21.1 action: class (c). The loading mechanism (jar plus config override, `load_after`, `mod` gating) should become a datapack reload listener so datapacks replace config overrides; that is code work. The JSON itself needs: id and data flattening, the NPC attribute names (`generic.maxHealth` becomes `minecraft:generic.max_health` or `max_health`; unverified), entity class names in `entity_spawn_nbt.json` and `horse_entity` (`net.minecraft.entity.passive.EntityHorse` becomes an entity type id), and ore dictionary names to tags. The 16 compat folders are only useful if each mod exists for 1.21.1 on NeoForge, which is uncertain; keep the files but gate them with mod-loaded conditions (NeoForge `neoforge:conditions`, unverified).

## Manual

`registry/manual/{en_us 10, de_de 7, zh_cn 9}` JSON files (26) plus `images/` (49: 47 `.jpg`, 2 `.JPG`, 5.7 MB). Per language folder, categories: en_us has `table_of_contents`, `research`, `torque`, `machines`, `warehouse`, `npcs`, `factions`, `structures`, `decoration`, `vehicles`; de_de lacks `decoration`, `structures`, `vehicles`; zh_cn lacks `decoration`.

Format (en_us/vehicles.json):

```json
{ "type": "manual_content", "lang": "en_US", "category": "vehicles",
  "content": [ { "content_type": "heading", "text": "Vehicles", "level": 1 },
               { "content_type": "text", "text": "..." } ] }
```

Content element counts in en_us: heading 111, text 149, image 49 (`path`, `width`, `height`), item 55 (`name` is an item id such as `ancientwarfareautomation:crop_farm`), toc_list 1. Parsed by `core/manual/ManualContentRegistry.java` (loads en_us always, plus the current language; falls back to English per category); images resolved by `core/gui/manual/elements/ImageElementWrapper.java:22` as `registry/manual/<path>` through `TextureUtils`; displayed by `core/gui/manual/GuiManual`. The in-game command `awutils reloadmanual` reloads it.

1.21.1: the 49 images are class (a) but must be loaded as textures: `.jpg` is not a supported texture format in resource packs (only PNG is), so the script must convert to PNG and rename `.JPG` to lowercase. The JSON is class (c): 55 `item` elements need the new item ids; text has to be re-read for 1.12-only statements (research, mod integrations); the GUI that renders it is code (core GUI slice). A possible alternative is to replace it with Patchouli-style content, but that adds a dependency (not assessed).

## Trades

`trades/trades.cfg`: 52 lines, 5 `trade:` blocks (`npcTypes=trader,bandit.trader,...`, `outputItem=stick`, `outputMeta=0`, `inputItem1=stick`, `:endtrade`). The header says "Version 1.0". A search of SRC for `trades.cfg`, `npcTypes`, `outputMeta` finds no reader. The live trade system is `registry/npc/faction_trade_lists.json` (`FactionTradeListRegistry`). Treat the file as dead and do not port.

## mcmod.info and logo

`mcmod.info` holds 5 entries (`ancientwarfare`, `ancientwarfarevehicle`, `ancientwarfarestructure`, `ancientwarfareautomation`, `ancientwarfarenpc`) with `${version}` and `${mcversion}` placeholders filled by `build.gradle` `processResources` (lines 90 to 101). Authors: shadowmage45, P3pp3rF1y. Dependencies: the four non-core modules depend on `AncientWarfare` (the display name, not the mod id). The `@Mod` annotations in STATS give the real dependencies: core requires `codechickenlib@[3.2.3,)` and Forge 14.23.5.2816+; automation is `after:redstoneflux;after:buildcraftcore`; the other three are `required-after:ancientwarfare`. 1.21.1: class (b), one `META-INF/neoforge.mods.toml` per module (or a single one if modules merge) with the mod id, version `${...}` substitution, authors, a `[[dependencies.<modid>]]` block for the core module, and the logo path. Drop CodeChickenLib, RedstoneFlux and BuildCraft as dependencies.

## Access transformer

`ancientwarfare_at.cfg` is renamed into `META-INF/` by `build.gradle` (line 103) and named in the jar manifest as `FMLAT`. It uses SRG names with trailing comments giving the MCP name. Eight entries:

| # | 1.12 target (SRG) | MCP name | Visibility | Used at | 1.21.1 |
|---|---|---|---|---|---|
| 1 | `ItemTool.field_77862_b` | `toolMaterial` | public | `npc/entity/NpcWorker.java:109` (tool efficiency for work speed) | The field is gone in 1.20.5+ (tier is a tool component). Read `DiggerItem#getTier` or the `DataComponents.TOOL` component instead (unverified); no AT |
| 2 | `EntityAINearestAttackableTarget.field_75307_b` | `targetClass` | public | `npc/event/EventHandler.java:60` (stop NPCs targeting players) | `NearestAttackableTargetGoal.targetType` exists (unverified) and is likely still not public; keep an AT entry with the Mojang name |
| 3 | `Minecraft.field_175617_aL` | `modelManager` | public | `structure/render/SoundBlockRenderer.java:46,62` (quads of the disguise block) | `Minecraft#getModelManager()` is public (unverified); no AT. The renderer itself changes (baked model API differs) |
| 4 | `PathNavigateGround.func_179683_a(...)` | `isSafeToStandAt` | protected | overridden in `npc/ai/NpcNavigator.java:127` | The 1.21.1 ground navigation does not have this method (path evaluation moved to node evaluators); target no longer exists (unverified). Redesign NpcNavigator |
| 5 | `PathNavigateGround.func_179692_b(...)` | `isPositionClear` | protected | `NpcNavigator.java:131` | Same as 4 |
| 6 | `EntityPotion.func_190543_a(RayTraceResult, List)V` | `applySplash` | protected | overridden in `npc/entity/NoFriendlyFirePotion.java:26` | `ThrownPotion` has a similar method with a different signature, probably private (unverified); an AT entry with the Mojang name and the new descriptor, or use a mixin-free alternative (a subclass of `ThrownPotion` overriding `onHit`) |
| 7 | `EntityHanging.field_174861_a` | `hangingPosition` | public | `structure/template/plugin/defaultplugins/entityrules/TemplateRuleEntityHanging.java:44` | `HangingEntity` stores a `pos` field (unverified); AT or a setter |
| 8 | `EntityLivingBase.field_184628_bn` | `activeItemStackUseCount` | public | `npc/ai/NpcAIBlockWithShield.java:88`, `npc/entity/NpcBase.java:538-546` | `LivingEntity.useItemRemaining` (unverified); AT with the Mojang name |

File format in NeoForge: `META-INF/accesstransformer.cfg` (or the path set in `neoforge.mods.toml` and ModDevGradle's `accessTransformers` setting; unverified), lines like `public net.minecraft.world.entity.LivingEntity useItemRemaining`, with Mojang class and member names (no SRG ids, no trailing `#name` needed). Method entries use a descriptor in the form `name(Lsome/Type;)V`. Net result: at most 5 of the 8 entries survive in some form (2, 6, 7, 8, and arguably 3); 4 and 5 vanish with the navigator rewrite; 1 is replaced by an API read. The AT file stays class (c).

## Conversion work summary

Totals for the 53.1 MiB shipped slice (non-template), by bucket:

| Bucket | What | Files | Size |
|---|---|---|---|
| (a) copy or rename | textures (716), skin_pack PNGs (3,606), ogg (587), manual images (49, plus JPG to PNG), logo, mcmeta | ~4,960 | ~45 MB |
| (b) script converts | lang (8), blockstates simple (about 40), model and item JSON (186), recipes (about 25 simple), loot tables (the mechanical parts of 177), sounds.json (3), registry JSON id flattening (about 70), mcmod.info | ~520 | ~4 MB |
| (c) semi-manual | blockstates with textures or submodels (about 30), `set_nbt` and `set_data` loot (about 70 files), item NBT recipes, registry id and attribute mapping (npc, structure, compat), manual text, AT | ~150 | ~8 MB (npc registry 1.3 MB and manual JSON 0.3 MB dominate) |
| (d) real work | research recipes (282, custom type), OBJ models (11), skin loader, SoundLoader, loot condition `rides_vehicle`, loot inject, runtime texture classes, NpcNavigator AT | ~300 files plus code | ~0.5 MB of files, but most of the effort |
| Drop | m2f (14), trades.cfg (1), stale lang keys | 15 | 0.1 MB |

Rough share by size: about 85 percent is copy-only, about 8 percent script-convertible, about 15 KB in single files needing judgment, and the remaining few percent needs real work. By effort the proportions are reversed: the (c) and (d) rows hold nearly all the work.

Script order:
1. Build a flattening table for 1.12 ids, metadata and NBT to 1.21.1 ids and components (every later step depends on it). Extend PORTING.md with the AW-specific entries: `reeds`, `waterlily`, `log`/`log2`, `planks`, `wool`, `dye`, `fish`, `double_plant`, `yellow_flower`, `fence`, `grass`, `sapling`, `leaves`, `stone_slab`, and AW's own metadata or NBT variant items (coin `metal`, fire pit `variant`, totem parts).
2. Build the ore-dictionary-to-tag table (45 names are in use in constants and inline `ore` fields), define AW's own tags for names with no convention tag.
3. Inline the two `_constants.json` files into recipe and research recipe files; convert shaped and shapeless recipes; rename the directory to `recipe`.
4. Convert lang files to JSON, route keys by module, and drop stale keys.
5. Rename texture folders and fix model texture references; convert JPG manual images to PNG.
6. Expand blockstates (after the block property definitions exist), generating per-variant child models.
7. Convert loot tables: pool rolls, count, `set_nbt` to `set_components`, `set_data` to item ids, drop `entryName`, move chest tables to `data/.../loot_table/chests/`.
8. Convert registry JSON: ids, attribute names, entity class names, ore names, `mod` conditions.
9. Convert sounds.json and register events in code.
10. Convert `skin_pack.meta` to JSON and repair the 53 dangling entries.

Left for hand work: the custom research recipe type, OBJ and animated block renderers, runtime texture loading, skin loader, sound loader, loot injection and the vehicle condition, manual text review, AT entries, licence clearance of the third-party sounds.

## Candidates for capttrackerlib

- A legacy-id flattening table and converter (1.12 name plus metadata plus NBT to 1.21.1 id plus components): used by the asset script and by any runtime reader of old data (structure templates, registry files); other ported mods in the monorepo will need it too.
- A data-driven JSON registry loader with file-name or `type` dispatch, `load_after` ordering, `mod`-loaded gating and a config override folder (`RegistryLoader`): reusable for any ported mod that has JSON registries.
- A counted ingredient (item or tag with a count and optional relaxed NBT match) plus a recipe-gating helper: `ancientwarfare:item_count`, `ore_dict_count`, `item_nbt_relaxed` behaviour.
- A loot-table injector (merge pools or entries into an existing table by name, gated on a mod being loaded), replacing `AWCoreLoot.lootLoad`.
- A lang key converter (`.lang` to JSON with module routing), as a build-time script rather than runtime code.
- Ad-hoc `SoundEvent` lookup helper for sounds not known at registration time (replaces `SoundLoader`).
- A user-skin loader (directory of PNGs plus a mapping file, deterministic per-entity selection by UUID), if other mods have the same need.

## Open questions

1. The brief states about 69 MB of assets; I measured 53.1 MB for the non-template resources. Is the 69 MB a compressed jar figure, or does it include part of the template folder?
2. Origin and licence of the sound set are unknown. The `del_*` and `mythbusters_*` clips look like TV samples, and the bard music has no composer or licence on record. Need a decision to ship, replace, or drop them before release.
3. 1,773 skin PNGs and 53 dangling meta lines: confirm with the NPC slice whether unlisted skins are intended for the `custom` pool or are leftovers, and whether the skin set should be cut.
4. Which 1.21.1 mods exist for the 16 compat targets? Decides whether the compat registry and loot files are carried over.
5. 20 model texture references have no namespace or a vanilla `blocks/` path (for example `blocks/log_oak`); I did not check whether the vanilla textures exist under the same names in the 1.21.1 client jar.
6. Positional sounds: 273 of 587 ogg files are stereo (measured). Stereo files are not attenuated by distance in 1.21.1 (unverified); decide per sound whether stereo is intended.
7. The `UP` clone reports `446b7db2c` as its current commit in `git log -1`, while `1.12.x` and `78178170b` ("Release Merge") also exist; I read the working tree as it is and did not check that it matches 78178170b file by file.

## Verification

Checked by a second analyst (scratch scripts in `D:/Spectre Labs Master/analysis/scratch/assets-data-verify/`).

Checked and confirmed:
- Non-template file count 5,861 and per-extension counts (png 4,311, json 865, ogg 587, jpg 49, obj 11, m2f 14, lang 8, mcmeta 12, meta 1, cfg 2).
- Folder counts: textures 716 (704 png, 12 mcmeta), skin_pack 3,607, loot_tables 177 (chests 158 incl. 28 compat and 7 inject; entities 19), recipes 40, research_recipes 282, registry 155, models 211 (186 json, 11 obj, 14 m2f), blockstates 71 (automation 18, core 2, npc 4, structure 42, vehicle 5), trades 1.
- Lang: en_US 1,792 keys, no duplicates; fr_FR, nl_NL and ru_RU one duplicate each; per-language "missing" counts reproduce from the table.
- Blockstates: 36 with `defaults.textures`, 56 with `transform`, 6 with `inventory`, 3 with `submodel`.
- Models: 102 with inline `elements`, 99 with `credit`, 16 with `overrides`, 4 with `texture_size`.
- sounds.json: 119/199/14 events and 345/201/21 entries.
- Loot: rolls-as-object 137, set_count 170, set_nbt 55, entryName 53, set_data 28, loot_table entries 21, empty 19, random_chance 8, looting_enchant 6, enchant_with_levels 5, enchant_randomly 3, set_damage 3, set_attributes 2, rides_vehicle 1 (files containing each).
- Recipe types 19 forge:ore_shaped, 19 minecraft:crafting_shaped, 1 shapeless; research recipes 186 shapeless and 95 shaped, 31 constants.
- Skin pack: 1,886 distinct PNGs referenced, 1,773 unlisted, 53 dangling, 655 type keys, 51 prefixes.
- Textures: animated set (12 mcmeta, all under textures/blocks) and sizes (town_hall 512x8192; advanced_spawner 64x256; automation rear strips 16x32 and 16x48; ancient/dragon/owl/stag stacks 16x64 or 16x32).
- AT file: 8 entries, with the SRG names and MCP comments as listed.
- No code reads `.m2f` or `trades.cfg` or `npcTypes` (grep of src/main/java).
- OBJ users: BaseBakery, FlywheelStorageRenderer and WindmillBladeRenderer call CodeChickenLib `OBJParser`.
- `load_default_skin_pack` at AWNPCStatics.java:108; `custom` skin registration at NpcSkinManager.java:81; `Loader.isModLoaded` and `LootTableLoadEvent` in AWCoreLoot.java.
- Manual image loading at ImageElementWrapper.java:22; manual images 49 (47 jpg, 2 JPG); manual folders en_us 10, de_de 7, zh_cn 9.
- project_resources: 342 files; extensions led by png 150, ogg 81, mmf 57, xcf 32.
- PORTING.md rename table: `en_US.lang` to `en_us.json`, `tile/item .name.name` keys, `loot_tables` to `loot_table`, `recipes` to `recipe`.

Corrected in this pass:
- Entity name keys: 672, not 673 (prefix count). Fixed in the table and in the Registry/lang text.
- `%s` formatting: 49 values use `%s` or `%1$s` (was 53); 65 values contain `%`.
- Recipes folder size: 17 KB (was 12 KB).
- skin_pack.meta: 2,695 lines by line count (was 2,694).
- Sizes: the 53.1 figure is MiB; decimal is 55.7 MB. project_resources is 207 MiB (217 MB decimal), not 208 MB.
- town_hall frame count: 16 square frames, derived from the PNG height.
- demonic_coffin_opens: plain-string sound lists are valid vanilla form; the line was misleading.
- Mono/stereo: 314 mono and 273 stereo ogg files (was an open question).
- Added: skin_pack holds 8 PNG skins named after real TV people and 2 named after TV characters (likeness and rights risk), in addition to the sound clips.

Not settled (remaining doubts):
- The 69 MB brief figure and the 140 MB template size were not re-measured.
- The per-language "translated equals English" counts (ja_JP 374, de_DE 226, nl_NL 261) were not checked.
- 1.21.1 API and format names marked (unverified) remain unverified; none were checked against NeoForge or Mojang sources.
- The SoundLoader reflection claim, the `Gate.java:109` path, the research recipe ids in `AWCraftingManager`, and the loot occurrence counts per file were not re-read in this pass.
- UP HEAD 446b7db2c versus 78178170b was not checked file by file.
