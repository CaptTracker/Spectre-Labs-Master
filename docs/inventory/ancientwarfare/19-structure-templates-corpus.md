# Structure template corpus

> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Paths are relative to src/main/resources/assets/ancientwarfare/template/ in upstream 1.12.x @ 78178170b.

## Overview numbers

The corpus is one bundled pack, `MohawkyPack1.12.2V72/`, plus one credits file. The loader reads everything under `assets/ancientwarfare/template` recursively, treats `.aws` as a structure template and `.awt` as a town template, decodes files as ISO-8859-1, and drops lines starting with `#` (`structure/template/load/TemplateLoader.java`, `loadTemplatesFromSource`; extensions from `AWStructureStatics.templateExtension` / `townTemplateExtension`). The format is line based text, not NBT: a `header:` block, a `validation:` block, `layer:` blocks of run-length-encoded rule numbers, `rule:` blocks (one per distinct block, payload is an SNBT-like `JSON:{...}` string) and `entity:` blocks (`template/load/TemplateParser.java`).

| Item | Count |
|---|---|
| Files under template/ (total) | 1495 |
| `.aws` structure templates | 1484 |
| `.awt` town templates | 10 |
| `.docx` credits file | 1 |
| Uncompressed size, all files | 143.7 MB (143737928 bytes; 137.1 MiB) |
| Uncompressed size, `.aws` | 143.7 MB |
| Templates that parsed completely | 1482 |
| Templates truncated mid-file (partial data kept by my script) | 2 |
| Folders (leaf pack folders) holding `.aws` | 74 |
| Block rules defined (`rule:` blocks) | 147067 |
| Entity rules defined (`entity:` blocks) | 7479 |
| Templates containing at least one entity rule | 602 |
| Template cells in total (x*y*z summed) | 594694396 |
| Non-air cells in total (rule number != 0) | 148161611 |

The task brief said about 1,494 files. The actual count is 1,495 files: 1,484 `.aws`, 10 `.awt` and 1 `.docx`. Rule number 0 is never defined by a `rule:` block in any file, so cell value 0 is air. Non-air counts below are cells with a non-zero rule number, taken from the layer data.

### Where the structures live

Two subtrees: `MohawkyPack1.12.2V72/normal/` (1030 templates, the world-gen and survival structures) and `MohawkyPack1.12.2V72/towns/` (454 `.aws` building blocks for town generation, plus 10 `.awt` town definitions in `towns/town_templates/`).

| Folder (under MohawkyPack1.12.2V72/) | Templates | World-gen on | Survival on | MB | Largest volume |
|---|---|---|---|---|---|
| towns/town structures | 183 | 1 | 0 | 7.1 | 137984 |
| normal/OceanIslands | 156 | 156 | 0 | 65.5 | 27137880 |
| towns/house | 126 | 0 | 0 | 1.4 | 24128 |
| towns/cosmetic | 85 | 0 | 0 | 0.5 | 16762 |
| normal/AWNationEmpire | 66 | 64 | 0 | 9.0 | 1005516 |
| towns/wall | 51 | 0 | 0 | 0.9 | 12032 |
| normal/AWNationNogg | 49 | 49 | 0 | 3.6 | 688788 |
| normal/SurvivalStructures | 42 | 0 | 42 | 1.0 | 453600 |
| normal/AWNationNorska | 37 | 37 | 0 | 2.9 | 644875 |
| normal/ModIce&FireLairs | 29 | 29 | 0 | 3.5 | 1435500 |
| normal/AWNationEmpireMod | 27 | 27 | 0 | 1.9 | 124656 |
| normal/AWNationNoggMod | 27 | 27 | 0 | 1.2 | 190624 |
| normal/ModGrimoireOfGaiaLairs | 26 | 26 | 0 | 1.6 | 311958 |
| normal/AWNationSarkonid | 25 | 25 | 0 | 3.0 | 847728 |
| normal/AWNationXoltec | 25 | 25 | 0 | 2.4 | 450432 |
| normal/ModElectroblobsTowers | 25 | 25 | 0 | 2.5 | 1262816 |
| normal/AWTribalKong | 23 | 23 | 0 | 1.5 | 519030 |
| normal/MonsterNaturalLairs | 21 | 21 | 0 | 0.6 | 115374 |
| normal/AWTribalMinossian | 20 | 20 | 0 | 1.4 | 178929 |
| normal/AWNationCoven | 18 | 18 | 0 | 1.2 | 1523340 |
| normal/AWNationDwarf | 18 | 18 | 0 | 1.2 | 151164 |
| normal/AWPowersUndead | 18 | 18 | 0 | 1.8 | 385560 |
| normal/RuinsNoMobs | 17 | 17 | 0 | 0.2 | 197823 |
| normal/SurvivalBridges | 17 | 0 | 17 | 0.5 | 343200 |
| normal/AWTribalBrigand | 16 | 16 | 0 | 0.6 | 92460 |
| normal/OceanGeneric | 16 | 16 | 0 | 0.0 | 1664 |
| normal/AWPowersMalice | 14 | 14 | 0 | 1.9 | 635664 |
| normal/AWTribalBarbarian | 14 | 14 | 0 | 1.7 | 334125 |
| normal/AWTribalZimba | 14 | 14 | 0 | 0.7 | 107484 |
| normal/AWTribalLizardman | 13 | 13 | 0 | 2.0 | 837900 |
| normal/AWTribalOrcRuined | 13 | 13 | 0 | 1.7 | 404040 |
| normal/AWTribalPirate | 13 | 13 | 0 | 0.7 | 210870 |
| normal/ModPrimitiveMobsLairs | 13 | 13 | 0 | 0.2 | 88440 |
| normal/OceanShips | 13 | 13 | 0 | 0.9 | 351995 |
| normal/RuinsSwamps | 13 | 13 | 0 | 0.9 | 1056252 |
| normal/AWTribalBufflokaPlains | 12 | 12 | 0 | 0.8 | 143616 |
| normal/SurvivalGroundFills | 12 | 0 | 12 | 0.0 | 1536 |
| normal/AWTribalOrcForest | 11 | 11 | 0 | 0.8 | 84630 |
| normal/AWTribalVyncanJungle | 11 | 11 | 0 | 0.7 | 123750 |
| normal/SurvivalBalloons | 11 | 0 | 11 | 0.6 | 327352 |
| normal/AWTribalSealsker | 10 | 10 | 1 | 0.4 | 156736 |
| normal/AWNationWitchbane | 9 | 9 | 0 | 2.0 | 275280 |
| normal/AWPowersGuardian | 9 | 9 | 0 | 0.6 | 113400 |
| towns/unique | 9 | 0 | 0 | 1.2 | 106470 |
| normal/AWTribalGiant | 8 | 8 | 0 | 0.1 | 151536 |
| normal/SurvivalAirships | 8 | 0 | 8 | 0.1 | 43956 |
| normal/AWNationEmpireCoastal | 7 | 7 | 0 | 2.0 | 498960 |
| normal/AWNationNorskaCoastal | 7 | 7 | 0 | 0.3 | 131250 |
| normal/AWTribalBufflokaForest | 7 | 7 | 0 | 0.4 | 170392 |
| normal/AWTribalSmingol | 7 | 7 | 0 | 0.3 | 29835 |
| normal/AWNationNoggCoastal | 6 | 6 | 0 | 0.7 | 409590 |
| normal/ModGrimoireOfGaiaGoblins | 6 | 6 | 0 | 0.4 | 152880 |
| normal/MonsterDungeonsAndLairs | 5 | 5 | 0 | 0.3 | 257004 |
| normal/AWPowersIshtari | 4 | 4 | 0 | 0.1 | 47500 |
| normal/AWPowersVampire | 4 | 4 | 0 | 0.4 | 207604 |
| normal/AWTribalGnome | 4 | 4 | 0 | 0.2 | 20250 |
| normal/RuinsWithMobs | 4 | 4 | 0 | 0.2 | 1654124 |
| normal/AWNationGuildMerchant | 3 | 3 | 0 | 0.2 | 36980 |
| normal/AWPowersIcelord | 3 | 3 | 0 | 0.3 | 287040 |
| normal/ModDungeonMobsLairs | 3 | 3 | 0 | 0.2 | 113740 |
| normal/ModGrimoireOfGaiaOrcs | 3 | 3 | 0 | 0.2 | 82593 |
| normal/ModMowziesMobsBossLairs | 3 | 3 | 0 | 0.1 | 162180 |
| normal/AWPowerArchivist | 2 | 2 | 0 | 0.4 | 67620 |
| normal/AWWonders | 2 | 2 | 0 | 0.1 | 114478 |
| normal/ModFishUndeadRising | 2 | 2 | 0 | 0.1 | 20800 |
| normal/AWNationElf | 1 | 1 | 0 | 0.6 | 1243056 |
| normal/AWNationReiksgard | 1 | 1 | 0 | 0.4 | 714384 |
| normal/AWPowersWizardly | 1 | 1 | 0 | 0.1 | 44979 |
| normal/AWTribalEnt | 1 | 1 | 0 | 0.0 | 11020 |
| normal/AWTribalGremlin | 1 | 1 | 0 | 0.2 | 38808 |
| normal/AWTribalHobbit | 1 | 1 | 0 | 0.0 | 36080 |
| normal/AWTribalRakshasa | 1 | 1 | 0 | 0.2 | 44880 |
| normal/ModRatsLairs | 1 | 1 | 0 | 0.2 | 195129 |
| normal/RuinsDesert | 1 | 1 | 0 | 0.0 | 7072 |

### Validation type, world-gen and survival flags

| validation `type` | Templates |
|---|---|
| ground | 1290 |
| island | 158 |
| water | 36 |

The seven values the code accepts are ground, underground, sky, water, underwater, harbor and island (`structure/template/build/validation/StructureValidationType.java`). Only ground, island and water occur. Island templates carry `minWaterDepth` / `maxWaterDepth`; water-type templates carry neither (36 files).

| worldGenEnabled | survival | Templates | Meaning |
|---|---|---|---|
| true | false | 938 | Generated by world gen only |
| false | true | 90 | Buildable from the survival structure builder only |
| true | true | 1 | Both |
| false | false | 455 | Neither flag: town building blocks (453 under towns/), plus 2 under normal/ (used only by name from other templates or the editor) |

Missing keys default to false (`StructureValidationProperties.java:21-22`). `survival` is absent from 29 files and `worldGenEnabled` is present in all 1,484. Under `towns/` one file has world gen on (`towns/town structures/Empire/EmpireTownGenCowBarn.aws`). `unique=true` in 30 templates; `preserveBlocks=true` in 195; `blockSwap=true` in 45 (two files have no `blockSwap` line and two have malformed values `magical` and `oceanfalse`, which Java `Boolean.parseBoolean` would read as false).

### Size distribution

| Metric | Min | P25 | Median | P75 | P90 | P99 | Max |
|---|---|---|---|---|---|---|---|
| x (width) | 1 | 15 | 23 | 42 | 88 | 256 | 382 |
| y (height) | 1 | 15 | 23 | 44 | 85 | 169 | 234 |
| z (length) | 1 | 15 | 23 | 42 | 82 | 253 | 375 |
| volume x*y*z | 1 | 3724 | 10558 | 75264 | 474320 | 11023480 | 27137880 |
| non-air blocks | 1 | 1496 | 3783 | 18819 | 137404 | 2467536 | 4212902 |

| Volume bucket (cells) | Templates |
|---|---|
| 0 to 1000 | 160 |
| 1000 to 10000 | 558 |
| 10000 to 100000 | 444 |
| 100000 to 1000000 | 216 |
| 1000000 to more | 106 |

Largest 15 by bounding-box volume (all in `normal/OceanIslands`):

| Template | Size x,y,z | Volume | Non-air blocks | File MB |
|---|---|---|---|---|
| OceanIslands/IslandsJaggedRocksPeakedIsland.aws | 356,231,330 | 27137880 | 4206049 | 0.95 |
| OceanIslands/IslandsGiantLandTaiga.aws | 336,210,371 | 26177760 | 4189555 | 1.76 |
| OceanIslands/IslandsRockySpiresHuge.aws | 382,165,342 | 21556260 | 3318664 | 0.78 |
| OceanIslands/IslandsObsidianCragsHuge.aws | 341,171,323 | 18834453 | 2874828 | 0.77 |
| OceanIslands/IslandsGOGSpectacular.aws | 358,137,375 | 18392250 | 4212902 | 3.00 |
| OceanIslands/IslandsLizardmanTemples.aws | 359,132,375 | 17770500 | 3922106 | 2.16 |
| OceanIslands/IslandsSavannaSpiresZimbaTombs.aws | 318,161,264 | 13516272 | 2476455 | 2.09 |
| OceanIslands/IslandsSavannaSpiresKingSolomonsMines.aws | 258,169,296 | 12906192 | 3277301 | 2.75 |
| OceanIslands/IslandsJungleMountainNoMobs.aws | 245,200,255 | 12495000 | 3185840 | 0.71 |
| OceanIslands/IslandsErodedCliffsForest.aws | 318,118,332 | 12457968 | 2310432 | 0.78 |
| OceanIslands/IslandsSavannaZimbaTemples.aws | 294,130,323 | 12345060 | 2955042 | 1.36 |
| OceanIslands/IslandsFireGiantVolcanoRocky.aws | 249,182,262 | 11873316 | 3284486 | 1.25 |
| OceanIslands/IslandsIceAndFireEverything.aws | 331,134,253 | 11221562 | 4072451 | 2.46 |
| OceanIslands/IslandsIceDragonDoubleDragon.aws | 215,234,221 | 11118510 | 1895745 | 0.58 |
| OceanIslands/IslandsMountainTwo.aws | 215,232,221 | 11023480 | 1926033 | 0.39 |

Largest 10 by non-air block count:

| Template | Size x,y,z | Non-air blocks | Volume |
|---|---|---|---|
| OceanIslands/IslandsGOGSpectacular.aws | 358,137,375 | 4212902 | 18392250 |
| OceanIslands/IslandsJaggedRocksPeakedIsland.aws | 356,231,330 | 4206049 | 27137880 |
| OceanIslands/IslandsGiantLandTaiga.aws | 336,210,371 | 4189555 | 26177760 |
| OceanIslands/IslandsIceAndFireEverything.aws | 331,134,253 | 4072451 | 11221562 |
| OceanIslands/IslandsLizardmanTemples.aws | 359,132,375 | 3922106 | 17770500 |
| OceanIslands/IslandsRockySpiresHuge.aws | 382,165,342 | 3318664 | 21556260 |
| OceanIslands/IslandsFireGiantVolcanoRocky.aws | 249,182,262 | 3284486 | 11873316 |
| OceanIslands/IslandsSavannaSpiresKingSolomonsMines.aws | 258,169,296 | 3277301 | 12906192 |
| OceanIslands/IslandsJungleMountainNoMobs.aws | 245,200,255 | 3185840 | 12495000 |
| OceanIslands/IslandsSavannaZimbaTemples.aws | 294,130,323 | 2955042 | 12345060 |

Largest 5 files on disk:

| Template | MB |
|---|---|
| OceanIslands/IslandsGOGSpectacular.aws | 3.00 |
| OceanIslands/IslandsSavannaSpiresKingSolomonsMines.aws | 2.75 |
| OceanIslands/IslandsIceAndFireEverything.aws | 2.46 |
| OceanIslands/IslandsLizardmanTemples.aws | 2.16 |
| OceanIslands/IslandsSavannaSpiresZimbaTombs.aws | 2.09 |

455 templates exceed 48 blocks on at least one axis (48 is the vanilla structure-block limit; whether 1.21.1 `StructureTemplate` itself enforces it is unverified) and 178 exceed 100 on at least one axis.

### Town templates (`.awt`)

| File | territoryName | min-max size | townBlockSize | selectionWeight | wall types | structure refs |
|---|---|---|---|---|---|---|
| EmpireUnwalledTown.awt | empire | 10-12 | 48 | 50 | 0 | 69 |
| EmpireWalledCity.awt | empire | 14-20 | 50 | 90 | 11 | 72 |
| NoggUnwalledTown.awt | nogg | 10-14 | 48 | 150 | 0 | 60 |
| NoggWalledTown.awt | nogg | 10-16 | 48 | 250 | 11 | 61 |
| NorskaUnwalledTown.awt | norska | 8-12 | 48 | 70 | 0 | 51 |
| NorskaWalledTown.awt | norska | 12-16 | 48 | 200 | 6 | 56 |
| SarkonidUnwalledTown.awt | sarkonid | 8-10 | 48 | 70 | 0 | 60 |
| SarkonidWalledTown.awt | sarkonid | 10-16 | 48 | 70 | 7 | 59 |
| UndeadUnwalledGraveyard.awt | undead | 6-8 | 20 | 70 | 0 | 102 |
| UndeadWalledGraveyard.awt | undead | 6-8 | 32 | 70 | 6 | 123 |

Town headers carry: name, minSize, maxSize, buildingExpansion, preventNaturalHostileSpawns, selectionWeight, clusterValue, townBlockSize, townPlotSize, wallStyle, wallSize, exteriorSize, exteriorPlotSize, interiorEmptyPlotChance, randomVillagersPerChunk, roadBlock/roadBlocks (8 files use `roadBlocks`, 2 use `roadBlock=minecraft:gravel` plus `roadMeta`), biomeWhiteList, biomeList, dimensionWhiteList, dimensionList, lamp, territoryName, biomeReplacement. Sections: walls, wallPatterns, uniqueStructures, mainStructures, houseStructures, cosmeticStructures, exteriorStructures. Entries are `TemplateName` or `TemplateName:weight` (the weight is the number after the colon). The ten towns reference 713 entries (355 distinct names) by header `name`; 7 distinct names do not match any template: EmpireCityGenWatchCommandersTower, EmpireTownStockBarn, NoggTownGenRuinedHouse, NorskaTownGenCraftsmanGlasWorker, NorskaTownGenTavernTheRagnorok, SarkonidTownGenBank, SmingolTownGenLargeInvasionForce. Parser: `structure/town/TownTemplateParser.java` (not read in detail for this slice).

Name collisions: the loader keys templates by header `name` and keeps one on conflict (`TemplateLoader.java` logs "present in multiple locations"). Two names occur twice: `NoggTownGenForge` (`towns/house/Nogg/` and `towns/town structures/Nogg/`) and `MountainGiantBopMountains` (files `AWTribalGiant/MountainGiant.aws` and `AWTribalGiant/MountainGiantBopMountains.aws`). Five files have a header name that differs from the file name.

## Format versions

| Header `version=` | Templates | Note |
|---|---|---|
| 2.11 | 691 | Current (WoodenCoffinFixer) |
| 2.10 | 690 | LootSettingsPotionRegistryNameFixer |
| 2.8 | 84 | EntityEquipmentFixer |
| 2.7 | 2 | EntityRuleNameFixer |
| 2.6 | 17 | EntityPositionToNBTFixer |

Every file has `version`, `name`, `size` and `offset` in the header; the parser refuses a file missing any of them. Versions found: 2.6 to 2.11 (5 distinct values). No template is older than 2.5, the version of `BlockMetaToBlockStateFixer`, so every block rule is already stored as a 1.12 block state (`blockName` plus `properties`) and there are no numeric `id`/`meta` block rules in the corpus. Fixers registered in `AncientWarfareStructure.java:151-160`, in version order: FactionExpansionFixer 2.2, JsonSimplificationFixer 2.3, RuleNameConsolidationFixer 2.4, BlockMetaToBlockStateFixer 2.5, EntityPositionToNBTFixer 2.6, EntityRuleNameFixer 2.7, EntityEquipmentFixer 2.8, TileLootFixer 2.9, LootSettingsPotionRegistryNameFixer 2.10, WoodenCoffinFixer 2.11 (`template/datafixes/fixers/*` for the first seven; `TileLootFixer`, `LootSettingsPotionRegistryNameFixer` and `WoodenCoffinFixer` live in `structure/datafixes/`). The 17 version-2.6 and 2 version-2.7 files and the 84 version-2.8 files are upgraded at load time; for a port, either keep these fixers or run them once offline. `Version.isGreaterThan` is `major > o.major || minor > o.minor` (`StructureTemplate.java:238`), which is wrong when majors differ; harmless here because every version has major 2.

The `mods=` header line (comma-separated mod ids; the template is skipped, with an INFO log line, if any listed mod is not loaded, `TemplateParser.java:78-82` via `CompatUtils.areModsLoaded`) is present in 1,467 of 1,484 files (17 files, all version 2.6, have no `mods=` line). 1,286 files have an empty list, 181 name at least one mod.

| Mod id in `mods=` header | Templates |
|---|---|
| grimoireofgaia | 40 |
| iceandfire | 39 |
| rustic | 22 |
| ebwizardry | 20 |
| betteranimalsplus | 18 |
| harvestcraft | 12 |
| zawa | 9 |
| primitivemobs | 8 |
| mowziesmobs | 5 |
| biomesoplenty | 3 |
| dungeonmobs | 2 |
| mod_lavacow | 2 |
| quark | 1 |
| rats | 1 |
| waystones | 1 |

Header coverage is incomplete: many more templates reference blocks, entities or items from mods than declare them (see the per-mod table below), so in the real mod those templates load even if the mod is absent and the unknown ids become air or fail at placement.

File preamble comments seen: `# minified`, `# Ancient Warfare Structure Template File`, `# Auto-generated structure template file. created on: ...` (`TemplateExporter.java:74`). Encoding is ISO-8859-1 (`TemplateExporter.java:35`); non-ASCII bytes occur in sign text (the section sign U+00A7 is byte 0xA7).

## Blocks referenced

A block reference is the `blockState.blockName` in a rule payload (every block-type rule has one). 450 distinct block names: 226 vanilla, 55 Ancient Warfare (own), 169 from other mods (26 mods). 1790 distinct (block, properties) combinations in total, 1156 of them vanilla; that is the size of the state remap table a converter needs. "Templates" counts templates that define at least one rule for the block; "Placed" is the number of cells filled with it (from layer data).

### Vanilla (`minecraft:`)

Top 40 by number of templates (the 1.12 ids below are pre-flattening names; the full list is reproducible with stats.py):

| Block | Templates | Placed |
|---|---|---|
| minecraft:dirt | 1175 | 16282695 |
| minecraft:grass | 1102 | 1385691 |
| minecraft:torch | 975 | 63406 |
| minecraft:stone | 901 | 104609667 |
| minecraft:stone_slab | 876 | 162870 |
| minecraft:log | 836 | 540172 |
| minecraft:planks | 826 | 350092 |
| minecraft:cobblestone | 800 | 1603706 |
| minecraft:wooden_slab | 768 | 124642 |
| minecraft:stonebrick | 736 | 2005777 |
| minecraft:tallgrass | 732 | 280931 |
| minecraft:chest | 706 | 7118 |
| minecraft:stone_brick_stairs | 603 | 122005 |
| minecraft:bed | 599 | 9028 |
| minecraft:furnace | 598 | 2411 |
| minecraft:gravel | 571 | 2276803 |
| minecraft:crafting_table | 566 | 1648 |
| minecraft:fence | 563 | 68623 |
| minecraft:stone_stairs | 555 | 38468 |
| minecraft:red_flower | 493 | 17199 |
| minecraft:leaves | 480 | 1891196 |
| minecraft:cobblestone_wall | 479 | 31315 |
| minecraft:yellow_flower | 461 | 9556 |
| minecraft:water | 439 | 499407 |
| minecraft:oak_stairs | 416 | 97755 |
| minecraft:double_stone_slab | 410 | 113972 |
| minecraft:ladder | 350 | 8021 |
| minecraft:wooden_door | 342 | 3818 |
| minecraft:sand | 332 | 8818676 |
| minecraft:glass_pane | 313 | 14570 |
| minecraft:iron_bars | 303 | 13036 |
| minecraft:double_wooden_slab | 301 | 8452 |
| minecraft:wall_sign | 298 | 1666 |
| minecraft:mossy_cobblestone | 275 | 296666 |
| minecraft:trapdoor | 274 | 8473 |
| minecraft:cauldron | 273 | 773 |
| minecraft:spruce_stairs | 271 | 50228 |
| minecraft:bookshelf | 243 | 12992 |
| minecraft:stained_hardened_clay | 241 | 96625 |
| minecraft:birch_fence | 232 | 18988 |

Vanilla names that cannot be copied straight to 1.21.1 ids (my reading of the 1.13 "flattening"; not checked against a 1.21.1 registry dump in this task, hence unverified): 38 split-by-variant names (a `variant`, `color` or `type` property chooses between many new ids), 44 renamed or restructured names, so 82 of 226 distinct vanilla names (36.3%) need an explicit remap. Counts:

| Group | Distinct names | Templates using any | Rules | Placed cells |
|---|---|---|---|---|
| Split by variant/colour/type | 38 | 1449 | 21737 | 138304214 |
| Renamed / restructured | 44 | 1392 | 20840 | 1729629 |

Split names: anvil, brown_mushroom_block, carpet, cobblestone_wall, concrete, concrete_powder, dirt, double_plant, double_stone_slab, double_stone_slab2, double_wooden_slab, hardened_clay, leaves, leaves2, log, log2, monster_egg, planks, prismarine, quartz_block, red_flower, red_mushroom_block, red_sandstone, sand, sandstone, sapling, sponge, stained_glass, stained_glass_pane, stained_hardened_clay, stone, stone_slab, stone_slab2, stonebrick, tallgrass, wooden_slab, wool, yellow_flower.

Renamed or restructured names (1.12 name -> new name): bed -> *_bed, brick_block -> bricks, daylight_detector_inverted -> daylight_detector[inverted], deadbush -> dead_bush, end_bricks -> end_stone_bricks, fence -> oak_fence, fence_gate -> oak_fence_gate, flower_pot -> flower_pot + potted_*, golden_rail -> powered_rail, grass -> grass_block, grass_path -> dirt_path, lit_furnace -> furnace[lit], lit_pumpkin -> jack_o_lantern, lit_redstone_lamp -> redstone_lamp[lit], lit_redstone_ore -> redstone_ore[lit], magma -> magma_block, melon_block -> melon, mob_spawner -> spawner, nether_brick -> nether_bricks, noteblock -> note_block, portal -> nether_portal, powered_repeater -> repeater, red_nether_brick -> red_nether_bricks, redstone_torch -> redstone_torch / redstone_wall_torch, reeds -> sugar_cane, silver_glazed_terracotta -> light_gray_glazed_terracotta, skull -> *_head/skull, slime -> slime_block, snow -> snow_block, snow_layer -> snow, standing_banner -> *_banner, standing_sign -> oak_sign, torch -> torch / wall_torch, trapdoor -> oak_trapdoor, unlit_redstone_torch -> redstone_torch[lit=false], unpowered_comparator -> comparator, unpowered_repeater -> repeater, wall_banner -> *_wall_banner, wall_sign -> oak_wall_sign, waterlily -> lily_pad, web -> cobweb, wooden_button -> oak_button, wooden_door -> oak_door, wooden_pressure_plate -> oak_pressure_plate.

Everything else (e.g. cobblestone, chest, furnace, ladder, iron_bars, glass_pane, ores, stairs with `facing`/`half`/`shape`) keeps its name; only its property set may change. All numeric block metadata is gone from the data, so the remap keys on property values such as `variant:"stone_brick"` or `color:"purple"` rather than on a meta integer.

### Ancient Warfare (own) blocks

All AW namespaces count as in-repo content that the port itself must register. 48 names are from the Structures module, 2 from Core, 5 from Automation; none from NPCs or Vehicles (those appear as entities and items, below).

| Block | Templates | Rules | Placed |
|---|---|---|---|
| ancientwarfarestructure:advanced_spawner | 1167 | 35137 | 35137 |
| ancientwarfarestructure:fire_pit | 591 | 659 | 3964 |
| ancientwarfarestructure:advanced_loot_chest | 503 | 3422 | 3422 |
| ancientwarfarestructure:table | 408 | 489 | 5970 |
| ancientwarfarestructure:chair | 270 | 5870 | 5870 |
| ancientwarfarestructure:bench | 246 | 398 | 3799 |
| ancientwarfarestructure:stool | 245 | 269 | 1652 |
| ancientwarfarestructure:urn | 211 | 2404 | 2404 |
| ancientwarfarestructure:protection_flag | 188 | 276 | 276 |
| ancientwarfarestructure:decorative_flag | 175 | 920 | 920 |
| ancientwarfarestructure:altar_candle | 142 | 1631 | 1631 |
| ancientwarfarestructure:brazier_flame | 136 | 139 | 1461 |
| ancientwarfarestructure:gate_proxy | 130 | 4989 | 4989 |
| ancientwarfarestructure:loot_basket | 112 | 793 | 793 |
| ancientwarfarestructure:sound_block | 102 | 146 | 146 |
| ancientwarfarestructure:altar_long_cloth | 94 | 407 | 407 |
| ancientwarfarestructure:altar_lectern | 87 | 145 | 271 |
| ancientwarfarestructure:altar_short_cloth | 77 | 335 | 335 |
| ancientwarfarestructure:coin_stack_gold | 75 | 100 | 407 |
| ancientwarfarestructure:coin_stack_silver | 67 | 88 | 213 |
| ancientwarfarestructure:gravestone | 57 | 1113 | 1113 |
| ancientwarfarestructure:totem_part | 49 | 1005 | 1005 |
| ancientwarfarestructure:wooden_coffin | 39 | 435 | 435 |
| ancientwarfarestructure:coin_stack_copper | 36 | 61 | 101 |
| ancientwarfarestructure:wooden_throne | 32 | 78 | 104 |
| ancientwarfarestructure:brazier_ember | 26 | 26 | 118 |
| ancientwarfarestructure:orc_totem_2_lit | 26 | 35 | 134 |
| ancientwarfarestructure:drafting_station | 22 | 26 | 26 |
| ancientwarfarestructure:orc_totem_1 | 22 | 32 | 126 |
| ancientwarfarestructure:wooden_post | 22 | 112 | 236 |
| ancientwarfarestructure:golden_throne | 21 | 44 | 48 |
| ancientwarfarestructure:tribal_stool | 21 | 59 | 161 |
| ancientwarfarestructure:orc_totem_2 | 19 | 28 | 65 |
| ancientwarfare:engineering_station | 18 | 21 | 21 |
| ancientwarfarestructure:scissor_seat | 17 | 39 | 74 |
| ancientwarfare:research_station | 16 | 27 | 27 |
| ancientwarfarestructure:iron_cage | 12 | 60 | 140 |
| ancientwarfarestructure:stretching_rack | 12 | 52 | 56 |
| ancientwarfarestructure:tribal_chair | 12 | 40 | 82 |
| ancientwarfarestructure:goblin_totem_1 | 11 | 12 | 39 |
| ancientwarfarestructure:golden_idol | 11 | 26 | 40 |
| ancientwarfarestructure:goblin_totem_2 | 10 | 11 | 45 |
| ancientwarfarestructure:stone_coffin | 10 | 272 | 272 |
| ancientwarfarestructure:goblin_totem_2_lit | 9 | 16 | 38 |
| ancientwarfarestructure:structure_scanner_block | 9 | 11 | 11 |
| ancientwarfarestructure:statue | 8 | 26 | 26 |
| ancientwarfareautomation:auto_crafting | 7 | 10 | 10 |
| ancientwarfarestructure:stake | 7 | 23 | 27 |
| ancientwarfarestructure:altar_sun | 6 | 7 | 13 |
| ancientwarfarestructure:gibbet | 5 | 20 | 28 |
| ancientwarfarestructure:coin_stack_ancient | 4 | 5 | 15 |
| ancientwarfareautomation:hand_cranked_generator | 2 | 6 | 6 |
| ancientwarfareautomation:stirling_generator | 1 | 1 | 1 |
| ancientwarfareautomation:windmill_blade | 1 | 225 | 225 |
| ancientwarfareautomation:windmill_generator | 1 | 2 | 2 |

`ancientwarfarestructure:advanced_spawner` is in 1167 of 1484 templates (35137 rules, 35137 cells): it is how mobs, NPCs and vehicles are placed in structures. It carries `spawnerSettings` NBT with `spawnGroups` / `settingsList` / `entityId` (see Conversion risks).

### Other mods

Number of templates that reference each non-vanilla, non-AW namespace, by role. "any (content)" is the union over blocks, tile entities, mobs, spawner mobs, chest-spawned mobs, items and loot tables; biome references and capability keys are listed separately because they do not stop a structure from building. The `forge:` namespace (3 templates, items only) is Forge itself and is left out. Some tile-entity ids registered by mods use the `minecraft:` namespace (listed below).

| Namespace | any (content) | blocks | tile ents | mobs | spawner mobs | chest mobs | items | loot tables | biomes | distinct ids (content) |
|---|---|---|---|---|---|---|---|---|---|---|
| primitivemobs | 286 | 0 | 0 | 236 | 84 | 2 | 9 | 0 | 0 | 32 |
| ebwizardry | 213 | 138 | 41 | 6 | 53 | 7 | 110 | 73 | 0 | 124 |
| grimoireofgaia | 173 | 8 | 0 | 11 | 125 | 62 | 79 | 0 | 0 | 80 |
| rustic | 166 | 113 | 26 | 0 | 0 | 0 | 75 | 0 | 0 | 76 |
| mocreatures | 122 | 0 | 0 | 105 | 21 | 1 | 6 | 0 | 0 | 47 |
| iceandfire | 117 | 28 | 0 | 15 | 88 | 26 | 49 | 16 | 0 | 116 |
| harvestcraft | 90 | 27 | 0 | 0 | 0 | 0 | 71 | 0 | 0 | 120 |
| dungeonmobs | 81 | 0 | 0 | 5 | 58 | 30 | 0 | 0 | 0 | 28 |
| dungeontactics | 67 | 2 | 0 | 0 | 0 | 0 | 66 | 0 | 0 | 44 |
| thesummoner | 66 | 0 | 0 | 56 | 16 | 1 | 1 | 0 | 0 | 4 |
| vampirism | 56 | 0 | 0 | 50 | 0 | 1 | 7 | 0 | 6 | 13 |
| eplus | 56 | 55 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 2 |
| mod_lavacow | 52 | 0 | 0 | 31 | 25 | 3 | 15 | 0 | 0 | 29 |
| zawa | 50 | 6 | 4 | 0 | 29 | 0 | 23 | 0 | 0 | 46 |
| betteranimalsplus | 46 | 7 | 7 | 0 | 37 | 0 | 8 | 0 | 0 | 45 |
| xreliquary | 42 | 5 | 5 | 0 | 0 | 0 | 41 | 0 | 0 | 21 |
| mowziesmobs | 42 | 1 | 0 | 3 | 39 | 1 | 2 | 0 | 0 | 16 |
| exoticbirds | 33 | 0 | 0 | 0 | 7 | 0 | 26 | 0 | 0 | 6 |
| explorercraft | 25 | 4 | 0 | 0 | 1 | 0 | 23 | 0 | 42 | 24 |
| charm | 23 | 1 | 1 | 0 | 0 | 0 | 22 | 0 | 0 | 7 |
| spartanweaponry | 22 | 0 | 0 | 0 | 0 | 0 | 22 | 0 | 0 | 28 |
| gemsplusplus | 19 | 1 | 0 | 0 | 0 | 0 | 18 | 0 | 0 | 47 |
| comforts | 19 | 6 | 6 | 0 | 0 | 0 | 13 | 0 | 0 | 5 |
| familiarfauna | 18 | 0 | 0 | 7 | 11 | 0 | 0 | 0 | 0 | 4 |
| minecraftfuture | 17 | 1 | 0 | 0 | 0 | 0 | 16 | 0 | 0 | 6 |
| pirates | 17 | 2 | 0 | 0 | 1 | 0 | 14 | 0 | 0 | 5 |
| astikorcarts | 16 | 0 | 0 | 15 | 0 | 0 | 5 | 0 | 0 | 6 |
| openglider | 13 | 0 | 0 | 0 | 0 | 0 | 13 | 0 | 0 | 2 |
| razak_demonmod | 12 | 0 | 0 | 8 | 4 | 0 | 0 | 0 | 0 | 4 |
| waystones | 11 | 10 | 10 | 0 | 0 | 0 | 1 | 0 | 0 | 3 |
| silentgems | 11 | 0 | 0 | 0 | 0 | 0 | 11 | 0 | 0 | 6 |
| reptilemod | 10 | 0 | 0 | 0 | 10 | 0 | 0 | 0 | 0 | 3 |
| faunanatural | 9 | 0 | 0 | 0 | 9 | 0 | 0 | 0 | 0 | 1 |
| eerieentities | 9 | 0 | 0 | 0 | 8 | 2 | 0 | 0 | 0 | 3 |
| gravestone-extended | 7 | 0 | 0 | 6 | 0 | 0 | 2 | 0 | 0 | 8 |
| biomesoplenty | 6 | 2 | 0 | 0 | 0 | 0 | 5 | 0 | 477 | 4 |
| simplytea | 6 | 2 | 0 | 0 | 0 | 0 | 6 | 0 | 0 | 6 |
| spartanshields | 6 | 0 | 0 | 0 | 0 | 0 | 6 | 0 | 0 | 4 |
| structurize | 5 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 5 |
| tombstone | 4 | 0 | 0 | 0 | 0 | 0 | 4 | 0 | 0 | 2 |
| advanced-fishing | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 | 0 | 2 |
| quark | 4 | 1 | 0 | 2 | 1 | 0 | 2 | 0 | 0 | 25 |
| byg | 3 | 2 | 0 | 0 | 0 | 0 | 1 | 0 | 99 | 2 |
| solcarrot | 3 | 0 | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 1 |
| traverse | 2 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 355 | 1 |
| spookybiomes | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 63 | 2 |
| owls | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 1 |
| ma | 2 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 1 |
| minecolonies | 2 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 2 |
| zoocraftdiscoveries | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 1 |
| dawnoftimebuilder | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| rats | 1 | 1 | 0 | 0 | 1 | 0 | 1 | 0 | 0 | 8 |
| bonecraft | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 1 |
| harvestersnight | 1 | 0 | 0 | 0 | 1 | 0 | 1 | 0 | 0 | 2 |
| experienceore | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| usefulbackpacks | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 1 |
| chancecubes | 1 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |

Namespaces that appear only as biome ids (no content): nt (390 templates), environs (249 templates), auxbiomes (87 templates), redwoods (33 templates), thaumcraft (27 templates), pvj (3 templates), biomeoplenty (1 templates). Namespaces that appear only as entity capability keys inside mob NBT: llibrary, multimob, dsurround, customnpcs, bettercombatmod, twilightforest.

683 of 1484 templates reference at least one other-mod id in content (blocks, tile entities, mobs, spawner mobs, items, loot tables); 801 templates reference only vanilla and Ancient Warfare ids. 58 distinct other-mod namespaces appear in content, 70 if biomes are included.

Other-mod blocks (169 distinct names, top 30 by templates; the rest are one or two templates each):

| Block | Templates | Placed |
|---|---|---|
| ebwizardry:crystal_flower | 97 | 575 |
| eplus:advanced_table | 55 | 59 |
| ebwizardry:arcane_workbench | 51 | 62 |
| rustic:gargoyle | 23 | 113 |
| rustic:table_spruce | 22 | 144 |
| rustic:deathstalk_mushroom | 18 | 132 |
| iceandfire:goldpile | 16 | 1050 |
| harvestcraft:pamdragonfruit | 13 | 191 |
| rustic:barrel | 13 | 60 |
| rustic:liquid_barrel | 13 | 23 |
| iceandfire:chared_stone | 10 | 47520 |
| rustic:leaves_apple | 10 | 5305 |
| waystones:waystone | 10 | 20 |
| rustic:vase | 9 | 17 |
| ebwizardry:crystal_ore | 8 | 137 |
| iceandfire:chared_cobblestone | 8 | 42563 |
| iceandfire:silver_ore | 8 | 156 |
| rustic:brewing_barrel | 8 | 90 |
| iceandfire:ash | 7 | 6033 |
| rustic:chair_birch | 6 | 37 |
| rustic:grape_leaves | 6 | 393 |
| rustic:grape_stem | 6 | 131 |
| comforts:hammock_white | 5 | 509 |
| rustic:leaves | 5 | 3876 |
| rustic:rope | 5 | 144 |
| grimoireofgaia:deco_garden_gnome | 4 | 11 |
| iceandfire:chared_grass | 4 | 96 |
| rustic:chili_crop | 4 | 1008 |
| rustic:log | 4 | 407 |
| rustic:table_birch | 4 | 29 |

Other-mod block names per namespace: iceandfire 37, rustic 31, harvestcraft 18, quark 17, betteranimalsplus 13, ebwizardry 7, dungeontactics 6, zawa 5, structurize 5, grimoireofgaia 3, comforts 3, gemsplusplus 3, rats 3, simplytea 2, xreliquary 2, biomesoplenty 2, explorercraft 2, spookybiomes 2, eplus 1, charm 1, waystones 1, experienceore 1, minecraftfuture 1, pirates 1, byg 1, mowziesmobs 1.

## Entities and tile entities referenced

### Entity rules (`entity:` blocks and `mobID`)

Each `entity:` block names its type in `mobID` (plugins `entity`, `vanillaHangingEntity`, `AWNpc`, `AWVehicle`) or, for gates, in `gateType` (plugin `awGate`). `Templates` = templates with at least one such rule; `Rules` = rules defined.

Totals: 124 distinct `mobID` values (29 vanilla, 13 AW, 82 other mods) plus 6 gate types.

| Entity | Templates | Rules |
|---|---|---|
| minecraft:item | 141 | 502 |
| minecraft:creeper | 137 | 463 |
| minecraft:zombie | 136 | 512 |
| minecraft:skeleton | 118 | 443 |
| minecraft:armor_stand | 86 | 416 |
| minecraft:spider | 67 | 149 |
| minecraft:chicken | 64 | 211 |
| minecraft:cow | 47 | 185 |
| minecraft:pig | 42 | 171 |
| minecraft:sheep | 31 | 137 |
| minecraft:enderman | 30 | 57 |
| minecraft:zombie_villager | 19 | 24 |
| minecraft:painting | 17 | 128 |
| minecraft:item_frame | 17 | 325 |
| minecraft:witch | 15 | 27 |
| minecraft:horse | 11 | 19 |
| minecraft:villager | 7 | 26 |
| minecraft:squid | 6 | 219 |
| minecraft:boat | 4 | 6 |
| minecraft:chest_minecart | 3 | 3 |
| minecraft:husk | 3 | 3 |
| minecraft:rabbit | 2 | 19 |
| minecraft:bat | 2 | 2 |
| minecraft:minecart | 1 | 1 |
| minecraft:xp_orb | 1 | 10 |
| minecraft:zombie_horse | 1 | 1 |
| minecraft:donkey | 1 | 1 |
| minecraft:mooshroom | 1 | 17 |
| minecraft:falling_block | 1 | 3 |

| AW entity / gate | Templates | Rules |
|---|---|---|
| ancientwarfarevehicle:vehicle | 88 | 438 |
| ancientwarfarenpc:faction.trader | 60 | 168 |
| ancientwarfarenpc:faction.soldier.elite | 5 | 6 |
| ancientwarfarenpc:faction.siege_engineer | 4 | 11 |
| ancientwarfarenpc:faction.leader | 3 | 3 |
| ancientwarfarenpc:faction.civilian.male | 2 | 10 |
| ancientwarfarenpc:faction.soldier | 2 | 5 |
| ancientwarfarenpc:faction.cavalry | 2 | 6 |
| ancientwarfarenpc:faction.priest | 1 | 1 |
| ancientwarfarevehicle:missile | 1 | 3 |
| ancientwarfarenpc:faction.mounted_archer | 1 | 2 |
| ancientwarfarenpc:faction.civilian.female | 1 | 2 |
| ancientwarfarenpc:faction.archer | 1 | 5 |
| gate.doubleWood | 61 | 77 |
| gate.verticalIron | 58 | 107 |
| gate.verticalWooden | 27 | 57 |
| gate.drawbridge | 14 | 14 |
| gate.singleWood | 7 | 7 |
| gate.doubleIron | 3 | 4 |

Other-mod entities (top 30 of 82 by templates):

| Entity | Templates | Rules |
|---|---|---|
| primitivemobs:haunted_tool | 159 | 563 |
| primitivemobs:skeleton_warrior | 148 | 661 |
| primitivemobs:support_creeper | 117 | 274 |
| primitivemobs:treasure_slime | 89 | 170 |
| thesummoner:summoner | 56 | 82 |
| vampirism:vampire | 43 | 78 |
| mocreatures:werewolf | 29 | 43 |
| mod_lavacow:raven | 21 | 34 |
| mocreatures:snail | 21 | 39 |
| mocreatures:rat | 19 | 29 |
| primitivemobs:trollager | 17 | 25 |
| mocreatures:silverskeleton | 16 | 17 |
| mocreatures:ant | 14 | 25 |
| astikorcarts:mobcart | 12 | 17 |
| mocreatures:horsemob | 12 | 15 |
| primitivemobs:mother_spider | 11 | 14 |
| mod_lavacow:scarecrow | 11 | 13 |
| iceandfire:if_mob_skull | 9 | 39 |
| mocreatures:manticore | 8 | 9 |
| vampirism:advanced_vampire | 8 | 9 |
| primitivemobs:baby_spider | 8 | 35 |
| grimoireofgaia:centaur | 8 | 10 |
| familiarfauna:familiarfauna.butterfly | 7 | 34 |
| primitivemobs:dodo | 6 | 27 |
| mocreatures:wraith | 6 | 7 |
| razak_demonmod:centaur | 6 | 6 |
| primitivemobs:traveling_merchant | 6 | 7 |
| mocreatures:roach | 6 | 8 |
| mod_lavacow:banshee | 5 | 11 |
| ebwizardry:evil_wizard | 5 | 6 |

Entity rule counts per mod namespace (templates / distinct ids): primitivemobs 236/13, mocreatures 105/31, thesummoner 56/1, vampirism 50/5, mod_lavacow 31/4, astikorcarts 15/3, iceandfire 15/7, grimoireofgaia 11/4, razak_demonmod 8/3, familiarfauna 7/1, ebwizardry 6/2, gravestone-extended 6/3, dungeonmobs 5/2, mowziesmobs 3/2, quark 2/1.

### Mobs placed through spawner blocks and chests

Mobs are mostly placed by spawners rather than entity rules. The vanilla-style `mob_spawner` tile (`SpawnData.id`, `SpawnPotentials[].Entity.id`) and AW `advanced_spawner_tile` (`spawnerSettings.spawnGroups[].settingsList[].entityId`) together reference 218 distinct entity ids; AW loot chests/baskets/coffins can also spawn a mob when opened (`lootSettings.entity`), 77 distinct ids there.

| Namespace | Templates with spawner mobs | Distinct ids |
|---|---|---|
| ancientwarfarenpc | 1054 | 16 |
| minecraft | 342 | 37 |
| grimoireofgaia | 125 | 38 |
| iceandfire | 88 | 20 |
| primitivemobs | 84 | 15 |
| dungeonmobs | 58 | 15 |
| ebwizardry | 53 | 5 |
| mowziesmobs | 39 | 6 |
| betteranimalsplus | 37 | 11 |
| zawa | 29 | 7 |
| mod_lavacow | 25 | 13 |
| mocreatures | 21 | 11 |
| thesummoner | 16 | 1 |
| familiarfauna | 11 | 3 |
| reptilemod | 10 | 3 |
| faunanatural | 9 | 1 |
| eerieentities | 8 | 1 |
| exoticbirds | 7 | 5 |
| razak_demonmod | 4 | 1 |
| owls | 2 | 1 |
| dawnoftimebuilder | 1 | 1 |
| harvestersnight | 1 | 1 |
| quark | 1 | 1 |
| rats | 1 | 3 |
| pirates | 1 | 1 |
| explorercraft | 1 | 1 |

Top spawner-spawned ids: ancientwarfarenpc:faction.soldier (726), ancientwarfarenpc:faction.soldier.elite (461), ancientwarfarenpc:faction.civilian.female (423), ancientwarfarenpc:faction.archer (402), ancientwarfarenpc:faction.archer.elite (389), ancientwarfarenpc:faction.leader (353), ancientwarfarenpc:faction.priest (330), ancientwarfarenpc:faction.civilian.male (278), ancientwarfarenpc:faction.leader.elite (221), ancientwarfarenpc:faction.cavalry (207), ancientwarfarenpc:faction.trader (180), ancientwarfarenpc:faction.mounted_archer (102), ancientwarfarenpc:faction.siege_engineer (81), minecraft:witch (62), minecraft:bat (59), minecraft:zombie (59), minecraft:horse (54), minecraft:pig (48), minecraft:cow (44), minecraft:chicken (43).

### Tile-entity ids in rule NBT (`teData.id`)

70 distinct tile-entity ids across 1254 templates. Ids in the `minecraft:` namespace include several that are actually mod tile entities registered without a namespace in 1.12 (`minecraft:eplus.advanced.table`, `minecraft:ebwizardryarcaneworkbenchtileentity`, `minecraft:tyrantskull`, `minecraft:dread_spawner`, `minecraft:pamapiary`, `minecraft:pixie_house`, `minecraft:rats.rat_hole`, `minecraft:rats.rat_trap`, `minecraft:dragonforgebrick`, `minecraft:dragonforgeinput`); those are marked "legacy name" below.

| Tile entity id | Templates | Rules | Kind |
|---|---|---|---|
| ancientwarfarestructure:advanced_spawner_tile | 1167 | 35137 | AW |
| minecraft:chest | 706 | 7133 | vanilla |
| minecraft:furnace | 604 | 2454 | vanilla |
| ancientwarfarestructure:advanced_loot_chest_tile | 503 | 3422 | AW |
| ancientwarfarestructure:chair_tile | 270 | 5870 | AW |
| minecraft:skull | 224 | 2983 | vanilla |
| ancientwarfarestructure:urn_tile | 211 | 2404 | AW |
| ancientwarfarestructure:protection_flag_tile | 188 | 276 | AW |
| ancientwarfarestructure:decorative_flag_tile | 175 | 920 | AW |
| ancientwarfarestructure:altar_candle_tile | 142 | 1663 | AW |
| ancientwarfarestructure:gate_proxy_tile | 130 | 4989 | AW |
| ancientwarfarestructure:loot_basket | 112 | 793 | AW |
| minecraft:brewing_stand | 105 | 351 | vanilla |
| ancientwarfarestructure:sound_block_tile | 102 | 146 | AW |
| ancientwarfarestructure:colored_tile | 94 | 711 | AW |
| minecraft:enchanting_table | 91 | 106 | vanilla |
| minecraft:banner | 62 | 1779 | vanilla |
| ancientwarfarestructure:gravestone_tile | 57 | 1113 | AW |
| minecraft:eplus.advanced.table | 55 | 59 | legacy name of a mod TE |
| ebwizardry:arcane_workbench | 40 | 51 | other mod |
| ancientwarfarestructure:wooden_coffin | 39 | 435 | AW |
| minecraft:dispenser | 34 | 117 | vanilla |
| minecraft:hopper | 33 | 289 | vanilla |
| minecraft:noteblock | 22 | 453 | vanilla |
| ancientwarfarestructure:drafting_station_tile | 22 | 26 | AW |
| minecraft:mob_spawner | 20 | 92 | vanilla |
| ancientwarfare:engineering_station_tile | 18 | 21 | AW |
| ancientwarfare:research_station_tile | 16 | 27 | AW |
| minecraft:jukebox | 15 | 27 | vanilla |
| rustic:tileentitybarrel | 13 | 60 | other mod |
| rustic:tileentityliquidbarrel | 12 | 22 | other mod |
| minecraft:ender_chest | 12 | 14 | vanilla |
| minecraft:ebwizardryarcaneworkbenchtileentity | 11 | 11 | legacy name of a mod TE |
| ancientwarfarestructure:stone_coffin | 10 | 272 | AW |
| waystones:waystone | 10 | 20 | other mod |
| rustic:tileentityvase | 9 | 17 | other mod |
| ancientwarfarestructure:structure_scanner_block_tile | 9 | 11 | AW |
| rustic:tileentitybrewingbarrel | 8 | 90 | other mod |
| minecraft:command_block | 8 | 19 | vanilla |
| ancientwarfarestructure:statue_tile | 8 | 26 | AW |
| ancientwarfarestructure:stake_tile | 7 | 9 | AW |
| ancientwarfareautomation:auto_crafting_tile | 7 | 10 | AW |
| betteranimalsplus:head | 7 | 19 | other mod |
| comforts:tile_entity_hammock | 6 | 514 | other mod |
| minecraft:tyrantskull | 6 | 45 | legacy name of a mod TE |
| minecraft:dropper | 5 | 5 | vanilla |
| zawa:te_zawa_feeder | 4 | 21 | other mod |
| minecraft:daylight_detector | 3 | 31 | vanilla |
| minecraft:end_portal | 3 | 70 | vanilla |
| xreliquary:tile_pedestal | 3 | 5 | other mod |
| minecraft:dread_spawner | 3 | 27 | legacy name of a mod TE |
| minecraft:shulker_box | 3 | 3 | vanilla |
| minecraft:lectern | 3 | 3 | vanilla |
| ebwizardry:magic_light | 2 | 14 | other mod |
| minecraft:comparator | 2 | 2 | vanilla |
| minecraft:dragonforgebrick | 2 | 14 | legacy name of a mod TE |
| minecraft:dragonforgeinput | 2 | 2 | legacy name of a mod TE |
| ancientwarfareautomation:hand_cranked_generator_tile | 2 | 6 | AW |
| xreliquary:tile_pedestal_passive | 2 | 3 | other mod |
| ancientwarfareautomation:windmill_blade_tile | 1 | 225 | AW |
| minecraft:pamapiary | 1 | 12 | legacy name of a mod TE |
| rustic:tileentityapiary | 1 | 8 | other mod |
| charm:barrel | 1 | 1 | other mod |
| rustic:tileentitycabinet | 1 | 2 | other mod |
| chancecubes:tilechancecube | 1 | 1 | other mod |
| minecraft:rats.rat_trap | 1 | 3 | legacy name of a mod TE |
| minecraft:rats.rat_hole | 1 | 1 | legacy name of a mod TE |
| minecraft:pixie_house | 1 | 5 | legacy name of a mod TE |
| ancientwarfareautomation:windmill_generator_tile | 1 | 2 | AW |
| ancientwarfareautomation:stirling_generator_tile | 1 | 1 | AW |

### Entity capability data and other entity NBT

Entity rules store the full `entityData` NBT of the scanned mob, including 1.12 `ForgeCaps` and attribute lists. These keys appear (templates / distinct): ForgeCaps keys:

| ForgeCaps key | Templates |
|---|---|
| llibrary:extendedentitydatacapability | 509 |
| ancientwarfarestructure:respawn_data | 437 |
| multimob:summonable | 375 |
| vampirism:iextendedcreature | 343 |
| dsurround:data | 238 |
| multimob:tameable | 118 |
| dsurround:emojidata | 40 |
| customnpcs:itemscripteddata | 26 |
| bettercombatmod:ioffhandattack | 25 |
| bettercombatmod:isecondhurttimer | 25 |
| ebwizardry:casting_data | 22 |
| twilightforest:cap_shield | 13 |
| Parent | 2 |

One key is the AW capability `ancientwarfarestructure:respawn_data`. The remaining keys belong to other mods (llibrary, multimob, vampirism, dsurround, customnpcs, bettercombatmod, ebwizardry, twilightforest). The key `Parent` (2 templates) is a stray non-capability compound. Attribute ids present: `forge.swimSpeed`, `generic.armor`, `generic.armorToughness`, `generic.attackDamage`, `generic.attackSpeed`, `generic.flyingSpeed`, `generic.followRange`, `generic.knockbackResistance`, `generic.maxHealth`, `generic.movementSpeed`, `horse.jumpStrength`, `potioncore.damageResistance`, `potioncore.jumpHeight`, `potioncore.magicDamage`, `potioncore.magicShielding`, `potioncore.projectileDamage`, `vampirism.sundamage`, `zombie.spawnReinforcements`. Paintings use the 1.12 `Motive` strings (20 distinct, e.g. Pigscene, Wanderer, Graham, Kebab).

## World-gen settings in use

Settings come from the `validation:` block. Keys seen and how many of the 1,484 files have them: type, worldGenEnabled, unique, preserveBlocks, selectionWeight, clusterValue, minDuplicateDistance, dimensionWhiteList, dimensionList, biomeList, leveling, fill, border (all 1,484); biomeWhiteList 1,483; blockSwap 1,482; survival 1,455; biomeGroupList 1,379; biomeReplacement 1,355; territoryName 1,346; preventNaturalHostileSpawns 1,095; structureAuthor 717; minGenerationHeight and maxGenerationHeight 673; minWaterDepth and maxWaterDepth 158. Property list: `StructureValidationProperties.java`.

### Dimensions

| dimensionWhiteList | dimensionList | Templates |
|---|---|---|
| true | 0 | 855 |
| true | '' | 380 |
| false | '' | 248 |
| false | 0 | 1 |

Only dimension 0 (overworld) is ever named. 855 templates whitelist dimension 0 explicitly; 380 whitelist an empty list; 248 blacklist an empty list. An empty list is not "any dimension": `StructureValidator.java:164-168` replaces an empty accepted-dimension list with whitelist `[0]`, so all 1,484 templates are effectively overworld-only, except one file that blacklists dimension 0 and so generates in no vanilla dimension. No Nether or End dimension ids occur. 1.21.1 uses dimension ResourceKeys (`minecraft:overworld`), so numeric 0 maps to the overworld.

### Biomes

| biomeWhiteList | Has biomeList | Has biomeGroupList | Templates |
|---|---|---|---|
| true | True | False | 922 |
| true | False | False | 287 |
| false | False | False | 256 |
| true | True | True | 17 |
| None | True | False | 1 |
|  | False | False | 1 |

287 distinct biome ids across the validation blocks, in 13 valid namespaces. The table below also lists `biomeoplenty` (one template, a typo for `biomesoplenty`), so 14 rows. Three ids are malformed: `bglowshroombayou` (no prefix, folded into `minecraft` in the counts) and `minecraft_birch_forest` and `minecraft_roofed_forest` (underscore instead of colon). 1.12 biome ids are registry names such as `minecraft:swampland`; they need mapping to 1.21.1 biomes or tags. Mod biomes (BoP, Traverse, Environs, Natural Terrain "nt", BYG, Aux Biomes, Spooky Biomes, Explorercraft, Redwoods, Thaumcraft, Vampirism, PVJ) have no guaranteed 1.21.1 counterpart.

| Namespace | Distinct biome ids | Templates referencing |
|---|---|---|
| minecraft | 56 | 870 |
| biomesoplenty | 51 | 477 |
| nt | 71 | 390 |
| traverse | 20 | 355 |
| environs | 24 | 249 |
| byg | 40 | 99 |
| auxbiomes | 5 | 87 |
| spookybiomes | 6 | 63 |
| explorercraft | 3 | 42 |
| redwoods | 5 | 33 |
| thaumcraft | 1 | 27 |
| vampirism | 1 | 6 |
| pvj | 3 | 3 |
| biomeoplenty | 1 | 1 |

Biome ids without a namespace prefix (e.g. `bglowshroombayou`) fall under `minecraft:` in this count; they are malformed entries and are probably typos for a BoP biome. A blacklist with an empty list (256 templates) means "any biome". All vanilla biome ids used in `biomeList`, with templates referencing (1.12 name; the 1.13+ name differs for most, e.g. swampland, extreme_hills, ice_flats, mesa, hell, savanna_rock, mutated_*, taiga_cold; I give no per-biome mapping here because I did not verify target names):

| 1.12 biome | Templates |
|---|---|
| minecraft:deep_ocean | 181 |
| minecraft:ocean | 151 |
| minecraft:extreme_hills | 116 |
| minecraft:forest_hills | 110 |
| minecraft:forest | 108 |
| minecraft:swampland | 83 |
| minecraft:birch_forest_hills | 73 |
| minecraft:ice_flats | 64 |
| minecraft:birch_forest | 61 |
| minecraft:beaches | 61 |
| minecraft:taiga_cold | 59 |
| minecraft:plains | 56 |
| minecraft:taiga_cold_hills | 52 |
| minecraft:mutated_birch_forest_hills | 50 |
| minecraft:mutated_birch_forest | 48 |
| minecraft:ice_mountains | 45 |
| minecraft:taiga | 40 |
| minecraft:desert_hills | 36 |
| minecraft:mutated_swampland | 35 |
| minecraft:roofed_forest | 34 |
| minecraft:jungle | 33 |
| minecraft:smaller_extreme_hills | 31 |
| minecraft:jungle_edge | 31 |
| minecraft:mutated_desert | 27 |
| minecraft:mutated_mesa_clear_rock | 27 |
| minecraft:mutated_mesa | 27 |
| minecraft:mutated_mesa_rock | 27 |
| minecraft:mesa | 26 |
| minecraft:mesa_clear_rock | 26 |
| minecraft:mesa_rock | 26 |
| minecraft:mutated_roofed_forest | 24 |
| minecraft:taiga_hills | 24 |
| minecraft:cold_beach | 23 |
| minecraft:mutated_extreme_hills | 19 |
| minecraft:savanna | 19 |
| minecraft:mushroom_island | 18 |
| minecraft:mushroom_island_shore | 18 |
| minecraft:savanna_rock | 17 |
| minecraft:redwood_taiga | 15 |
| minecraft:redwood_taiga_hills | 14 |
| minecraft:jungle_hills | 14 |
| minecraft:extreme_hills_with_trees | 13 |
| minecraft:stone_beach | 13 |
| minecraft:frozen_ocean | 13 |
| bglowshroombayou | 12 |
| minecraft:desert | 9 |
| minecraft:mutated_ice_flats | 8 |
| minecraft:mutated_plains | 7 |
| minecraft:mutated_redwood_taiga | 3 |
| minecraft:mutated_taiga_cold | 3 |
| minecraft:mutated_taiga | 2 |
| minecraft:mutated_forest | 2 |
| minecraft_birch_forest | 1 |
| minecraft_roofed_forest | 1 |
| minecraft:mutated_redwood_taiga_hills | 1 |
| minecraft:frozen_river | 1 |

Top 25 mod biomes: traverse:meadow (111), biomesoplenty:kelp_forest (103), biomesoplenty:coral_reef (103), traverse:forested_hills (98), environs:tall_oak_forest_hills (97), environs:tall_oak_forest (94), nt:oak_forest (94), nt:oak_forest_hills (94), nt:tall_oak_forest (93), nt:tall_oak_forest_hills (93), nt:cold_forest_hill (93), biomesoplenty:grove (86), environs:dead_oak_forest (85), biomesoplenty:dead_swamp (84), biomesoplenty:dead_forest (81), traverse:woodlands (73), traverse:badlands (70), biomesoplenty:shrubland (66), environs:moor (62), traverse:cliffs (59), traverse:rocky_plains (56), spookybiomes:ghostly_forest (54), biomesoplenty:bayou (54), traverse:birch_forested_hills (53), environs:wasteland_eroded (51), nt:cold_birch_forest_hills (49), nt:cold_birch_forest (49), nt:birch_hills (49), biomesoplenty:ominous_woods (47), environs:dead_oak_forest_hills (46), nt:heath (45), traverse:rocky_plateau (41), biomesoplenty:alps (40), nt:fungal_jungle (39), nt:fungal_jungle_hills (39), traverse:snowy_coniferous_forest (39), environs:wasteland_spikes (39), environs:silk_wetland (38), biomesoplenty:cold_desert (38), biomesoplenty:mystic_grove (37), environs:silkglades (35), biomesoplenty:mangrove (35), biomesoplenty:marsh (34), auxbiomes:marsh (33), auxbiomes:wasteland (31), redwoods:alpine (30), nt:highland (30), nt:shrubland (30), nt:scrubland (30), nt:moorland (29), biomesoplenty:lavender_fields (29), nt:iron_beach (29), nt:aegean_archipelago (29), biomesoplenty:orchard (28), thaumcraft:magical_forest (27), biomesoplenty:woodland (27), traverse:thicket (26), biomesoplenty:origin_beach (26), explorercraft:snowdonia (25), nt:lush_desert (25), nt:black_beach (25), nt:orange_beach (25), nt:purple_beach (25), biomesoplenty:moor (24), environs:sandstone_ranges (23), nt:sahel (23), traverse:desert_shrubland (23), nt:tropical_desert (23), byg:bsonorandesert (23), biomesoplenty:tropical_island (23), byg:bweepingwitchforest (22), biomesoplenty:white_beach (22), nt:fen (21), byg:bwhisperingwoods (21), nt:stone_mountains (21), nt:mountains (20), biomesoplenty:scrubland (20), spookybiomes:seeping_forest (20), biomesoplenty:wasteland (19), environs:stone_hills (18), byg:bbayou (17), nt:rockland (16), nt:cold_roofed_forest (16), nt:cold_roofed_forest_hills (16), biomesoplenty:wetland (14), traverse:lush_hills (14), byg:bsavannacanopy (14), byg:bbaobabsavanna (14), biomesoplenty:savanna_plateau_extension (14), biomesoplenty:savanna_extension (14), environs:tall_oak_wetland (13). (Full list is reproducible from the script.)

`biomeGroupList` names AW-defined biome groups (`registry/BiomeGroupRegistry.java`) rather than biomes: smingol (7), empire (3), undead (2), nogg (1), sarkonid (1), icelord (1), rakshasa (1), vyncan (1).

`biomeReplacement` (biome the chunk is rewritten to after placement, to stop surface-biome blending): `minecraft:` (1131), `(absent)` (129), `(empty)` (36), `minecraft:birch_forest` (35), `minecraft:jungle` (31), `minecraft:beaches` (15), `spookybiomes:ghostly_forest` (14), `minecraft:extreme_hills` (13), `minecraft:plains` (12), `minecraft:forest` (11), `minecraft:savanna` (8), `minecraft:savanna_rock` (8), `minecraft:minecraft:` (6), `minecraft:frozen_ocean` (6), `biomesoplenty:steppe` (5), `minecraft:desert` (4), `minecraft:forest_hills` (4), `minecraft:swampland` (4), `minecraft:hell` (2), `minecraft:birch_forest_hills` (2), `minecraft:extreme_hills_with_trees` (2), `minecraft:mesa` (1), `minecraft:minecraft:mutated_mesa` (1), `minecraft:ice_flats` (1), `minecraft:ice_mountains` (1), `minecraft:jungle_hills` (1), `minecraft:taiga_cold` (1). The value `minecraft:` (empty path) is the "no replacement" default in 1,131 files; `minecraft:minecraft:...` is a malformed double prefix in 7 files. Replacement targets that matter in 1.21.1 are `minecraft:birch_forest`, `jungle`, `beaches`, `extreme_hills`, `plains`, `forest`, `savanna`, `savanna_rock`, `frozen_ocean`, `desert`, `swampland`, `hell` and mod biomes `spookybiomes:ghostly_forest`, `biomesoplenty:steppe` (1.12 names).

### Territories

`territoryName` ties a structure to an AW "territory" (a named region type used for faction/nation spawning, `worldgen/TerritoryManager.java`). 73 distinct names; 920 templates set one; 19 world-gen-enabled templates have none. Top 30:

| territoryName | Templates |
|---|---|
| ruins | 103 |
| empire | 99 |
| nogg | 79 |
| norska | 40 |
| minossian | 28 |
| sarkonid | 25 |
| xoltec | 25 |
| oceanjungle | 24 |
| kong | 23 |
| oceancastaway | 22 |
| oceanrocky | 21 |
| dwarf | 18 |
| oceanplains | 18 |
| dungeon | 17 |
| generic | 16 |
| oceanopen | 16 |
| swampruins | 15 |
| zimba | 15 |
| oceansavanna | 15 |
| coven | 14 |
| barbarian | 14 |
| bufflokaplains | 13 |
| oceanforest | 12 |
| empirecoastal | 11 |
| orcforest | 11 |
| vyncanjungle | 11 |
| oceansearocks | 11 |
| beachruins | 10 |
| jungleruins | 10 |
| piratecoastal | 10 |

Town templates set territories empire, nogg, norska, sarkonid, undead.

### Placement numbers

| Setting | Distribution (value: templates) |
|---|---|
| selectionWeight | 30: 405, 1: 315, 50: 259, 0: 229, 60: 98, 40: 82 |
| clusterValue | 1: 316, 50: 271, 100: 259, 0: 229, 30: 111, 60: 47 |
| minDuplicateDistance | 1000: 715, 1: 321, 0: 230, 2000: 144, 500: 24, 900: 17 |
| leveling | 0: 795, 12: 147, 10: 112, 25: 90, 35: 89, 6: 55 |
| fill | 0: 815, 12: 149, 10: 122, 35: 88, 25: 87, 6: 54 |
| border | 0: 780, 6: 201, 10: 143, 5: 138, 12: 107, 8: 57 |
| minGenerationHeight | (absent): 811, 0: 641, 150: 22, 40: 2, 160: 1, 55: 1 |
| maxGenerationHeight | (absent): 811, 255: 658, 0: 15 |
| minWaterDepth | (absent): 1326, 10: 150, 8: 3, 0: 2, 100: 1, 12: 1 |
| maxWaterDepth | (absent): 1326, 50: 129, 20: 8, 26: 7, 16: 4, 40: 2 |
| preventNaturalHostileSpawns | true: 646, false: 448, (absent): 389, : 1 |
| structureAuthor | (absent): 767, mohawky: 535, (empty): 137, OddlySeagull: 29, Gungnir: 12, gungnir: 1, seagull: 1, Dan: 1, `dan + update by mohawky`: 1 |

## Rule types in use

Every `rule:` / `entity:` block has `plugin=<name>`; the name selects a handler registered in `StructurePluginManager` (`template/plugin/defaultplugins/`: `StructurePluginVanillaHandler`, `StructurePluginAutomation`, `StructurePluginNpcs`, `StructurePluginVehicles`). Counts are rules defined / templates using it / cells placed (block rules).

| plugin= | Rules | Templates | Placed cells | Handler class (blockrules/ or entityrules/) |
|---|---|---|---|---|
| vanillaBlocks | 57223 | 1468 | 146890630 | TemplateRuleVanillaBlocks (plain blockstate) |
| blockTile | 57199 | 1207 | 57199 | TemplateRuleBlockTile (any block with a tile entity, stores teData NBT) |
| inventory | 11360 | 913 | 11360 | TemplateRuleBlockInventory (container with items / loot level) |
| entity | 6096 | 462 |  | TemplateRuleEntity (generic entity NBT) |
| doors | 3300 | 682 | 9757 | TemplateRuleBlockDoors |
| vanillaSkull | 2983 | 224 | 2983 | TemplateRuleVanillaSkull |
| bed | 2921 | 599 | 9028 | TemplateRuleBed (bedColor 0-15) |
| vine | 2920 | 204 | 431490 | TemplateRuleVine |
| banner | 1779 | 62 | 1779 | TemplateRuleBanner |
| vanillaSign | 1696 | 306 | 1696 | TemplateRuleBlockSign (signContents0-3 plain text) |
| fluid | 1588 | 482 | 741590 | TemplateRuleFluid |
| flag | 1182 | 265 | 1182 | TemplateRuleFlag |
| totemPart | 1005 | 49 | 1005 | TemplateRuleTotemPart |
| vanillaFlowerPot | 941 | 108 | 941 | TemplateRuleFlowerPot |
| coffin | 707 | 46 | 707 | TemplateRuleCoffin |
| vanillaHangingEntity | 460 | 33 |  | TemplateRuleEntityHanging |
| AWVehicle | 438 | 88 |  | TemplateRuleVehicle |
| awGate | 266 | 133 |  | TemplateRuleGates |
| awTorqueMulti | 225 | 1 | 225 | TemplateRuleTorqueMultiblock |
| AWNpc | 219 | 75 |  | TemplateRuleEntityNpc |
| vanillaSpawner | 26 | 5 | 26 | TemplateRuleVanillaSpawner |
| rotatable | 9 | 2 | 9 | TemplateRuleRotatable |
| shulkerBox | 3 | 3 | 3 | TemplateRuleShulkerBox |

The code defines more block-rule classes than the corpus uses: `TemplateRuleMulti`, `TemplateRuleStructureBuilder`, `RuleWorld` and others exist in `blockrules/` but have no plugin line here. Of the 23 plugin names in the corpus, 5 are entity rules (entity, vanillaHangingEntity, AWNpc, AWVehicle, awGate) and 18 are block rules. 154,546 `rule:` and `entity:` payloads (147,067 block + 7,479 entity) were all parsed by my SNBT parser with zero errors.

## Conversion risks

"Templates" = templates containing the item; "Rules/stacks" = rules or item stacks. All counts come from the script. The format itself is the first risk: it is a bespoke text format with 1.12 SNBT payloads, so every template must be converted once (offline) or the loader must keep reading it and translate on load.

| Risk | Templates | Rules/stacks | Detail |
|---|---|---|---|
| 1.12 vanilla block ids needing remap (split or renamed) | 1462 | 42577 | Remap table keyed on (name, variant/color/type property) |
| Mod blocks (other-mod namespaces) | 288 | 1653 | 26 mods; need a 1.21.1 mod or substitution |
| Other-mod mobs/entities (entity rules) | 301 | 2473 | 82 ids from 15 mods |
| Other-mod mobs in spawners | 329 | - | 165 ids |
| Command blocks (`minecraft:command_block` tile; also repeating/chain) | 8 | 19 | 18 of 19 have an empty command; 1 has `/setblock ~ ~-5 ~ redstone_block 0 replace` (1.12 numeric-data syntax) |
| Vanilla spawners (`vanillaSpawner`, `minecraft:mob_spawner`) | 5 | 26 | NBT shape differs in 1.21 (`SpawnData.entity.id`, `spawn_potentials` with `data`); 1.12 shorts for delays |
| AW advanced spawners (`advanced_spawner_tile`) | 1167 | 35137 | AW-owned NBT; embeds `customTag` with ArmorItems/HandItems; mob ids need remap |
| Written books (`minecraft:written_book` stacks) | 8 | 79 | `pages` tag in 8 templates; 1.12 plain/JSON text; 1.21 uses data components (unverified detail) |
| Writable books | 4 | 23 | same |
| Skulls / heads (`vanillaSkull`) | 224 | 2983 | SkullType numeric 0-5 (skeleton, wither, zombie, player, creeper, dragon); 40 distinct player-profile textures in 38 templates; Mojang skin URLs |
| Banners (`banner` plugin + banner items) | 62 | 1779 | `Base` colour int, `Patterns` with 1.12 short codes (31 distinct); 1.21 uses pattern holder ids/components |
| Signs in 1.12 text format (`vanillaSign`) | 306 | 1696 | 1,333 sign rules hold text (257 templates, 3,481 non-empty lines); all plain strings with `§r` formatting codes, no JSON; 1.21 needs `front_text/back_text` components |
| Beds (`bed`) | 599 | 2921 | `bedColor` 0-15 -> 16 coloured bed blocks |
| Flower pots (`vanillaFlowerPot`) | 108 | 941 | `itemName`+`itemMeta` TE format; 1.13+ uses one block per plant (`potted_*`) |
| Item stacks inside containers/entities with NBT `tag` | 589 | - | 148 distinct item ids with a tag |
| Items with non-zero `Damage` (meta/durability) | 306 | 6976 | 375 distinct item@damage pairs; items with meta (potions, dyes, spawn eggs, wool) were split into separate ids in 1.13 |
| Numeric enchantment ids (`ench` lists) | 185 | 1891 | 68 distinct numeric ids; 1.21 uses registry keys and `enchantments` component |
| Numeric potion effect ids in item NBT (`CustomPotionEffects`) | 105 | 2219 | 26 distinct numeric effect ids |
| Enchanted books / potions / tipped arrows / maps / fireworks / shields | 128 | 1122 | Each has item-specific NBT that changed in 1.20.5+ (unverified details) |
| Spawn eggs (`minecraft:spawn_egg`, NBT `EntityTag`) | 27 | 52 | 1.13+ has one item per mob |
| Entity NBT with `ForgeCaps` (mod capability data) | 552 | 6550 | No equivalent; strip. 1.21 data attachments are a different system (unverified mapping) |
| Entity attribute ids in 1.12 form (`generic.maxHealth`, `forge.swimSpeed`, `potioncore.*`, `zombie.spawnReinforcements`) | 467 | - | 18 attribute names; renamed in 1.16+ to snake_case (e.g. `generic.max_health`), mod ones disappear |
| Paintings and item frames (`vanillaHangingEntity`) | 33 | 460 | Painting `Motive` strings are 1.12 names; positions stored as packed longs |
| AW gates (`awGate`) | 133 | 266 | Gate entities with `gateType` strings (6 kinds); need the Structures gate system ported |
| AW vehicles (`AWVehicle`) | 88 | 438 | Entity NBT with ammo/upgrade inventories; needs Vehicles module |
| AW NPCs (`AWNpc` and spawner `ancientwarfarenpc:*`) | 1054 | 219 | 1,054 templates spawn AW NPCs through advanced spawners; needs NPC module and faction names |
| Torque multiblock (windmill, `awTorqueMulti`) | 1 | 225 | One template; Automation blocks with absolute `x,y,z` and `controlPos` stored in teData |
| Loot table names | 626 (verifier count: 661) | - | 144 distinct (verifier count: 145 distinct `lootTableName` values). Six `ancientwarfare:inject/chests/*` references match no registered table name. Their JSON files exist under `loot_tables/chests/inject/` and are injection overlays keyed to vanilla names `chests/X` (`core/init/AWCoreLoot.java:89-91, 107`), so the references likely never resolve. `ancientwarfare:chests/bap_village_farm_goose` has no file. Other missing names belong to ebwizardry, iceandfire and advanced-fishing and depend on those mods. Vanilla names changed in 1.13 (unverified) |
| Templates larger than 100 blocks on an axis | 178 | - | 156 ocean islands up to 27M cells (largest file 3.0 MB of text); large conversion output and memory |
| Truncated template files | 2 | - | `normal/AWNationWitchbane/WitchbaneLargeDesertCastle.aws`, `normal/AWPowersGuardian/GuardiansMansionBase.aws` end inside an entity block (see How the numbers were produced) |

Numeric metadata: no block rule uses numeric metadata. `legacy_data` appears only as `"0"` in the flower-pot block state (937 rules). Numbers that still stand in for a 1.12 meta value: `bedColor` (0-15; 16 distinct values used), banner `Base` and pattern `Color` ints, flower-pot `itemMeta` (non-zero in 251 of 941 pots), skull `SkullType`, and item `Damage` (non-zero in 6976 stacks, above).

### AW items inside containers

Items in chests, furnaces, loot baskets and mob equipment name 969 distinct ids: 276 vanilla, 113 AW, 580 other mods. AW items are mostly NPC weapons and shields (`ancientwarfarenpc:coin` 476 templates, spears, halberds, shields), `ancientwarfare:` quills, manual and research books, and `ancientwarfarevehicle:spawner`. Mod items other than AW: ebwizardry (110 templates), grimoireofgaia (79 templates), rustic (75 templates), harvestcraft (71 templates), dungeontactics (66 templates), iceandfire (49 templates), xreliquary (41 templates), exoticbirds (26 templates), explorercraft (23 templates), zawa (23 templates), charm (22 templates), spartanweaponry (22 templates).

Most common item NBT keys in stacks: `metal` (476), `ench` (172), `Potion` (80), `display` (73), `Fluid` (44), `StoredEnchantments` (30), `EntityTag` (27), `AttributeModifiers` (25), `RepairCost` (18), `CustomPotionColor` (18), `CustomPotionEffects` (18), `variant` (16), `spawnData` (14), `SkullOwner` (12), `include` (11), `structureName` (11), `structData` (11), `SG_UUIDLeast` (9), `SGProperties` (9), `SGStatistics` (9).

### Loot tables referenced

`lootTableName` fields in loot chests/baskets/coffins and inventory rules: `minecraft:chests/simple_dungeon` (172), `minecraft:chests/village_blacksmith` (119), `ancientwarfare:chests/castle_treasury_low_value` (55), `ebwizardry:chests/wizard_tower` (53), `ancientwarfare:chests/castle_treasury_high_value` (51), `minecraft:chests/woodland_mansion` (45), `minecraft:chests/stronghold_library` (41), `ancientwarfare:chests/castle_treasury_medium_value` (38), `minecraft:chests/jungle_temple` (36), `ancientwarfare:chests/beast_treasure_hoard` (34), `ancientwarfare:chests/peasant_low_value` (32), `ancientwarfare:chests/gravestone_medium_value` (30), `minecraft:chests/desert_pyramid` (27), `ancientwarfare:chests/peasant_medium_value` (26), `ancientwarfare:chests/norska` (26). Namespaces: advanced-fishing 2 ids, ancientwarfare 108 ids, ebwizardry 12 ids, iceandfire 4 ids, minecraft 18 ids.

## Credits document

`AW structure building credits.docx` (25,485 bytes, a zip archive; text read from `word/document.xml`, 189 paragraph elements, 160 of them with text). Summary:

- Title "Ancient Warfare faction structures", by Mohawky. Describes six years of work (starting in 1.6.4) building structures and seeking permission to adapt other builders' work for the 1.12.2 version. About 50% of all structures are Mohawky's own work, granted in perpetuity to Ancient Warfare 2 (the document names Shadowmage and Pepperfly as the mod authors).
- AW2 build team: P3pp3rF1y (programmer); Mohawky (build team lead, reviser of donated structures, creator of the Tribe/Nation/Powers system); TheOddlySeagull (many structures: Undead, Sarkonid and Norska walled and unwalled towns, Undead graveyards, Minossian temples and island structures); Gungnir343 (challenge structures for tribe and nation NPCs); Weserts555 (many structures across tribes and nations); Dan (programming, artwork, wizard towers, undead crypts, furnishings, the Electroblobs Wizardry bridge with spellcaster NPCs and the mod Ancient Spellcraft); Faenum (Guardians and other structures); Silentine (models and furnishings, and Grimoire of Gaia mobs for AW structures); Overjay, Landar, SirSquiddly, Arcuriel (other building and art); ThatPolishKid99 (community ambassador).
- Structure sources: the closed MCSchematics site plus Planet Minecraft and Minecraft Schematics, used by donation or permission. Named bundles: Novv, deerassassin0424, LemonFox, BlackCorsair, BlockMasterUk, InMines medieval packs; thebigbaron warships; Amarok medieval villages (adapted); Alegion Ravendale towers; Formivores walled-city walls (permission of GOTOLINK); Helkellen and xthatsmycakex mine schematics; FilaStyle84 and AuraCloud teepee inspiration for Buffloka villages.
- About 65 individual credits of the form "structure, author, site": for example Darkfort (Amarok), Troll City (EMXA), Pirate Ship (Darkcrafter31), Wizards Towers (MaxCrack), Demon Castle (r0adkill), Castlevania (ilikecutepeople), Dover Castle (if62668), Helms Deep and Castle Thornhall (Bokops), Skycrown (mranto1), structures from Hogwarts and Castle with Dragons (jstoekm2).
- A "Hey that's my structure!" apology section: missing credits are unintended; builders can ask to be credited or to have a structure removed.
- Skin credits for NPC skins (not templates): LukyLukaz, Mullraugh, Remuz, Gleeshers, KingVigik, Tom_Clark, Ivoryyy, Asprin60, LotC Jandy, Eyhoffi, Wildflowers, DeusVidet, Maximr, ArthurB, Sunborn, Demicha, WildRoze, Beordan, Jujubunny and others.

Port relevance: the credits are attribution obligations owed for third-party builds; the document grants permission to the AW2 mod, not explicitly to a port, and the bundle is described as used "with permission", not under the mod's GPL. Whether the structures can be redistributed in a NeoForge port under GPL-3.0-or-later is not established by this file; the document should ship with any ported pack, and the licence status of the structures should be confirmed with the pack author (see Open questions). The structure author field in templates (`structureAuthor`): mohawky 535, OddlySeagull 29, Gungnir 12, blank 137, absent 767, others 4 (`seagull`, `gungnir`, `Dan`, `dan + update by mohawky`).

## How the numbers were produced

Scripts, all in `D:/Spectre Labs Master/analysis/agents/templates-corpus/` (run with `python -I`):

- `parse.py`: walks the template folder, reads each file as ISO-8859-1, mirrors `TemplateParser` (header, validation, rule, entity and layer blocks; layer row syntax `NxA|r,B|r`), counts cells per rule number from the layer data, writes `data.json`.
- `snbt.py`: a small parser for the 1.12 SNBT-like `JSON:{...}` payload (compounds, lists, typed arrays, quoted strings, numeric suffixes).
- `stats.py`: parses every rule payload (154,546 block and entity payloads, 0 parse errors) and collects block names, tile-entity ids, mob ids, item stacks (any compound with string `id` and `Count`), loot tables, capability keys, attributes, enchantments and so on into `stats.pkl`.
- `probe2.py`: file sizes, name collisions, sign/skull/bed/flag/flower-pot detail, town reference check, credits text extraction (`credits.txt`).
- `build_report.py`: generates this report from the above, so every table is computed.

Parse results: 1,484 `.aws` and 10 `.awt` read; the credits `.docx` read as a zip. Two `.aws` files did not parse to the end:

| File | Problem |
|---|---|
| normal/AWNationWitchbane/WitchbaneLargeDesertCastle.aws | truncated: file ends inside an entity/rule block (partial data kept) |
| normal/AWPowersGuardian/GuardiansMansionBase.aws | truncated: file ends inside an entity/rule block (partial data kept) |

Both end in the middle of an `entity:` block after `data:` with no `JSON:` line (WitchbaneLargeDesertCastle: 4 `entity:` openers vs 3 `:endentity`; GuardiansMansionBase: 1 vs 0); the file was cut off, probably when the pack was zipped or exported. All rules and layers before the cut parsed; they are counted in the totals above. The real loader would raise on these (an entity block without `JSON:` data), so they are likely broken in the shipped mod as well.

Method limits: "Templates" columns count a template once per key regardless of how many rules use it. "Placed" counts cells from layer data (rule number occurrences times layer span). Item stacks are found structurally (compound with string `id` and a `Count` key); a few non-item compounds could match but none were seen in the samples checked. Vanilla "renamed/split" classification is from my knowledge of the 1.13 flattening and is not verified against a registry dump. I did not run any mod code.

## Open questions

- Are the third-party structures (MCSchematics, Planet Minecraft, Minecraft Schematics donations) redistributable in a port, and under which licence? The credits file says "permission" but not terms.
- Which of the 58 other-mod namespaces (Ice and Fire, Grimoire of Gaia, Electroblob's Wizardry, Rustic, Primitive Mobs, MoCreatures, BoP, BYG, Traverse, Environs, Natural Terrain and others) have 1.21.1 NeoForge versions? None is verified here. A policy is needed: drop those templates, substitute vanilla, or load them only when the mod is present (the header `mods=` mechanism, which is incomplete: many templates use mods without declaring them).
- One template blacklists dimension 0 (`dimensionWhiteList=false`, `dimensionList=0`); with every other template overworld-only it can never generate. Is that intentional?
- What is the intended behaviour of the two truncated files and the two duplicate template names (`NoggTownGenForge`, `MountainGiantBopMountains`)? Which copy wins in the loader?
- Should the port convert templates offline to vanilla structure NBT / jigsaw pools, or keep the AW text format with its own loader? Rules such as advanced_spawner, gate_proxy, loot baskets and protection flags have no vanilla structure-NBT equivalent.
- Does 1.21.1 vanilla `StructureTemplate` accept the largest islands (382 x 234 x 375, 27M cells)? Not verified; these may need chunked placement or a separate feature.
- Seven structure names referenced by town templates have no template file (EmpireCityGenWatchCommandersTower, EmpireTownStockBarn, NoggTownGenRuinedHouse, NorskaTownGenCraftsmanGlasWorker, NorskaTownGenTavernTheRagnorok, SarkonidTownGenBank, SmingolTownGenLargeInvasionForce); are they intentionally absent?
- The six `ancientwarfare:inject/chests/*` references match no registered table name. Their JSON files sit in `loot_tables/chests/inject/` and are applied to vanilla tables by `core/init/AWCoreLoot.java`, so the templates may point at tables that never receive the injection. Confirm in game. `ancientwarfare:chests/bap_village_farm_goose` has no JSON file.

## Verification

Checked by a second pass (scripts in `D:/Spectre Labs Master/analysis/agents/templates-corpus-verify/`, run with `python -I`).

Reproduced exactly: 1,495 files (1,484 `.aws`, 10 `.awt`, 1 `.docx`); 143,737,928 bytes; versions (691/690/84/2/17); validation types (ground 1290, island 158, water 36); worldGen/survival matrix (938/90/1/455); 29 files without a `survival` key; 17 files without `mods=` (all version 2.6); 1,286 empty and 181 non-empty `mods=` lists; the `mods=` table (grimoireofgaia 40 ... waystones 1); 594,694,396 total cells; 148,161,611 non-air cells (layer ranges such as `layer: 0-3` count once per layer in the range); 147,067 block rules; 7,479 entity rules (the two truncated files add a partial block, 7,481 if counted); 602 templates with entity rules; 450 block names (226 vanilla, 55 AW, 169 other from 26 namespaces); 1,790 block-state combinations (1,156 vanilla); 70 tile-entity ids in 1,254 templates, with per-id template counts matching the table; 124 mobIDs and 6 gate types; 1,054 templates with `ancientwarfarenpc` spawner entities; 287 biome ids; 73 territory names; 30 `unique`, 195 `preserveBlocks`, 45 `blockSwap=true`, malformed `magical` and `oceanfalse`; 455 templates over 48 blocks and 178 over 100; the folder table (74 two-level folders, counts match); the plugin rule counts (23 plugin names: 18 block, 5 entity); the largest volumes (27,137,880 down to 11,023,480); the 2 truncated files; the 2 name collisions; 5 header names that differ from the file name; no rule with `number=0` (the analyst's claim holds).

Changed in the report:
- Corrected the mods-gate wording: a template with a missing mod is skipped with an INFO log line, not silently (`TemplateParser.java:78-82`).
- Corrected the default-value line numbers in `StructureValidationProperties.java` (21-22, not 20-21).
- Corrected the fixer locations: `TileLootFixer`, `LootSettingsPotionRegistryNameFixer` and `WoodenCoffinFixer` are under `structure/datafixes/`.
- Corrected the biome namespace count: 13 valid namespaces, plus a `biomeoplenty` typo and three malformed ids.
- Completed the `structureAuthor` distribution (gungnir 1, Dan 1, `dan + update by mohawky` 1 were missing).
- Credits docx: 189 paragraph elements, 160 with text (not 188).
- Loot tables: the six `ancientwarfare:inject/chests/*` JSON files do exist, under `loot_tables/chests/inject/`, and are injection overlays keyed to vanilla names. The report said they had no JSON. The only missing in-pack file is `chests/bap_village_farm_goose`. Own count: 145 distinct `lootTableName` values in 661 templates, against the report's 144 and 626.

Not settled:
- The "683 templates use other-mod content" and "58 other-mod namespaces" figures. A rough scan of quoted ids in rule and entity payloads gives 761 templates and 59 namespaces, including false positives such as `ma`, `mm` and `potioncore` that need a stricter test. Not resolved.
- Biome per-namespace template counts (for example `minecraft` 870, `nt` 390) and the mods-by-namespace tables beyond the `mods=` header table were not recomputed.
- The 218 vs 217 distinct spawner entity ids: my spawner-only scan gives 217; the report's 218 presumably also counts vanilla `mob_spawner` ids. Not reproduced.
- The fixer version numbers (2.2 to 2.11) were checked only for registration order, not against each fixer's declared version.
- The 1.21.1 names and the 1.13 flattening split/rename lists are unverified, as the report already states. No 1.21.1 registry was consulted.
- The 7 town-referenced names with no template: all 7 are absent from the template name set (confirmed). Three were confirmed as referenced in `.awt` files; the other four were not individually checked.
- The analyst's summary JSON says "83 vanilla names need flattening remap" but the report body says 82 (38 split + 44 renamed). 82 is the consistent figure and is what the report body uses.
