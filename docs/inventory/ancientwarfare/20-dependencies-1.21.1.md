# Third-party dependencies and their 1.21.1 status

> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Looked up on 2026-11-15 (system date of this run). The sandbox UTC clock read 2026-11-16 during the Modrinth, Maven and GitHub calls, so anything published after those calls is not covered. Source paths are relative to `SRC` = `upstream/AncientWarfare2/src/main/java/net/shadowmage/ancientwarfare` unless marked `RES` (resources) or `UP` (upstream root).

Method: STATS (`analysis/jar_stats.txt`, section "EXTERNAL (third-party mod) API PREFIXES") for counts; grep of `SRC` for imports, `Loader.isModLoaded`, `@Optional`, `@Interface`, `modid` strings and dependency strings; grep of `RES` for `mod` keys and namespaces. 1.21.1 facts come from Modrinth API, Maven `maven-metadata.xml` files, GitHub API branch lists and raw files of the 1.21.1 branches. Each claim names the URL checked. Anything not confirmed is marked "not verified".

Status vocabulary used below: **Exists** = a 1.21.1 NeoForge build was found with a URL. **No 1.21.1 build** = no build was found in the sources checked (this is not proof that none exists on CurseForge, which was not queried). **Not verified** = not checked or not confirmed.

## Summary

| Mod | How Ancient Warfare uses it | Required or optional | 1.21.1 NeoForge status | Latest 1.21.1 version | URL checked | Replacement if none |
|---|---|---|---|---|---|---|
| CodeChickenLib | Model bakery, `CCRenderState`, `CCModel`, OBJ parsing, `vec` transforms, raytracer, lighting, colour, texture utils. About 87 source files touch third-party code in total; CodeChickenLib is most of it (STATS: render 27, model 24, vec 14 classes) | Required (`AncientWarfareCore.java:44` `@Mod` dependency; `build.gradle` `deobfCompile`) | Exists. Bakery and `OBJParser.parseModels` API used by AW are gone or changed in the 1.21.1 branch | 4.6.1.529 | https://maven.covers1624.net/io/codechicken/CodeChickenLib/maven-metadata.xml ; https://api.modrinth.com/v2/project/codechicken-lib/version ; https://github.com/TheCBProject/CodeChickenLib (branch `1.21.1`) | Not applicable (library stays). Bakery code must be rewritten on NeoForge model APIs (names not verified) |
| JEI (Just Enough Items) | Recipe categories, recipe wrappers and transfer handler for AW research recipes, 8 files in `core/compat/jei` | Optional (runtime: `NetworkHandler.java:158` `Loader.isModLoaded("jei")`; build: `deobfCompile` at `UP/build.gradle:70`, not compile-only) | Exists | 19.70.0.469 (2026-11-01) | https://api.modrinth.com/v2/project/jei/version ; https://maven.blamejared.com/mezz/jei/jei-1.21.1-neoforge/maven-metadata.xml | Not applicable. 1.12 plugin API must be rewritten (1.21.1 names not verified) |
| Redstone Flux (CoFH RF) | Energy interop: torque tiles and worksites implement `IEnergyReceiver`/`IEnergyProvider`; `RFProxyActual` converts torque to RF | Optional (`@Optional.Interface` in `automation/tile/torque/TileTorqueBase.java:30`, `automation/tile/worksite/TileWorksiteBase.java:37-38`; `RFProxy.java:14`) | No CoFH RF for NeoForge 1.21.1 found. The Modrinth project named `redstoneflux` is an unrelated Paper/Folia/Purpur project | none | https://api.modrinth.com/v2/project/redstoneflux/version (returns 1.21.4+ Paper only) ; Modrinth search for "redstone flux" with NeoForge and 1.21.1 facets: no hit | NeoForge built-in energy capability: `net.neoforged.neoforge.energy.IEnergyStorage` and `Capabilities.EnergyStorage.BLOCK` (verified in NeoForge branch `1.21.1`, see section) |
| FTB Library (FTBLib) and FTB Teams | Team ownership viewer `core/compat/ftb/FTBTeamViewer.java` (imports `ForgePlayer`, `ForgeTeam`, `Universe`) | Optional (`CompatLoader` checks `Loader.isModLoaded`) | Exists as FTB Library 2101.x. Mod id changed to `ftblibrary`. The 1.12 team API was replaced by FTB Teams (class names not verified) | FTB Library 2101.1.39; FTB Teams 2101.1.12 (last listed in Maven metadata) | https://maven.ftb.dev/releases/dev/ftb/mods/ftb-library-neoforge/maven-metadata.xml ; https://maven.ftb.dev/releases/dev/ftb/mods/ftb-teams-neoforge/maven-metadata.xml ; https://github.com/FTBTeam/FTB-Library (branch `1.21.1/main`) | Not applicable. Keep `TeamViewerRegistry`; implement the viewer on FTB Teams |
| Electroblob's Wizardry | NPC spellcaster faction and spell-casting AI, `npc/compat/ebwizardry` and 4 `npc/*Wizardry*` classes | Optional (`AncientWarfareNPC.java:114`, `AWNPCEntities.java:162`, `NpcClientProxy.java:108`) | No 1.21.1 NeoForge build found. Electroblob's Wizardry Redux exists only for 1.20.1 on Modrinth | none | https://api.modrinth.com/v2/search?query=electroblob (1.21.1 + NeoForge facets: no hit) ; https://api.modrinth.com/v2/project/electroblobs-wizardry-redux/version (1.20.1 only) | Drop the spellcaster NPC variant, or re-implement its spells inside AW |
| AgriCraft | Crop farm crop plugin `automation/compat/agricraft/CropAgricraftCrop.java` | Optional (`AncientWarfareAutomation.java:112` via `CompatLoader`) | No 1.21.1 build. Modrinth lists AgriCraft only up to 1.20.4 | none (latest on Modrinth 4.0.6, for 1.20.4) | https://api.modrinth.com/v2/project/agricraft/version ; https://github.com/AgriCraft/AgriCraft (branches `1.12.2`, `master`; master last pushed 2021-08-02) | Vanilla crop blocks through AW's own `CropFarmRegistry` |
| InfinityLib | Library behind AgriCraft. STATS shows 1 automation class using `com/infinityraider/infinitylib`; no import line was found in `SRC` (see Open questions) | Optional (transitive via AgriCraft; `build.gradle` `compile "curse.maven:infinitylib"`) | No 1.21.1 build. Modrinth last game version 1.18.2; GitHub master last pushed 2023-06-01 | none | https://api.modrinth.com/v2/project/infinitylib/version ; https://github.com/InfinityRaider/InfinityLib | Drop with AgriCraft |
| BuildCraft | Load-order string only: `automation/AncientWarfareAutomation.java:53` `after:buildcraftcore`. No import anywhere in `SRC` | Optional (ordering hint only) | BuildCraft Community Edition has 1.21.1 NeoForge builds | 8.0.30+1.21.1+neoforge (2026-11-15) | https://api.modrinth.com/v2/project/buildcraft-community-edition/version | Remove the string. No code depends on it |
| Twilight Forest | Reflection-only daytime compat `npc/compat/TwilightForestCompat.java`; entity id strings in `structure/config/AWStructureStatics.java` (about lines 366-381); data in `RES/registry/compat/twilightforest` and `RES/registry/npc/target_lists.json` | Optional (not in `build.gradle`; reflection) | 1.21.1 branch exists on GitHub. Released 1.21.1 build not verified (Modrinth slug not found) | not verified | https://github.com/TeamTwilight/twilightforest (branch `1.21.1`) | Not verified. Rewrite the daytime hook against TF's 1.21 code, or drop it |
| Epic Siege Mod | Reflection-only hostile-AI check `npc/compat/EpicSiegeCompat.java` (class `funwayguy.epicsiegemod.ai.ESM_EntityAINearestAttackableTarget`) | Optional (not in `build.gradle`; reflection) | No 1.21.1 build found | none | https://api.modrinth.com/v2/search?query=epic%20siege (1.21.1 + NeoForge facets: no hit) | Drop. Use AW's own target lists (`RES/registry/npc/target_lists.json`) |
| 35 registry-compat files, 28 loot-table compat files, 3 ungated NPC data files | Data only: fruit, sapling, tree-scanner, soil and crop entries; village and shop loot; NPC trade and faction defaults. Covered in "Mods referenced only by data files" | Optional (gated per file by `"mod"` key or folder name, see that section) | Mixed. Only a few of the mod ids have a 1.21.1 NeoForge hit | see section | see section | Drop the entries for mods with no 1.21.1 build |

## CodeChickenLib

### Usage in Ancient Warfare

Required. CodeChickenLib is a hard dependency of the Core module:

- `SRC/core/AncientWarfareCore.java:44`: `dependencies = "required:forge@[0.000.000.001,);" + CodeChickenLib.MOD_VERSION_DEP`. The constant value is in the CCL jar, not in `SRC`, so the version range is not verified here.
- `SRC/core/AncientWarfareCore.java:3`: `import codechicken.lib.CodeChickenLib;`
- `UP/build.gradle`: `deobfCompile "codechicken:CodeChickenLib:${ccl_version}:deobf"`. `UP/gradle.properties`: `ccl_version=1.12.2-3.2.3.+`.

Usage by package, from STATS (number of classes per module: automation / core / structure / vehicle):

| CCL package | Classes (auto / core / struct / veh) | What AW uses | Example files (SRC-relative) |
|---|---|---|---|
| `codechicken.lib.render` | 15 / 2 / 10 / 0 (27) | `CCModel`, `CCRenderState.instance` (5 uses), `OBJParser.parseModels` (3 calls), `CCModelState`, `IItemRenderer` | `automation/render/FlywheelStorageRenderer.java:6-8`, `automation/render/WindmillBladeRenderer.java:7`, `structure/render/StoneCoffinRenderer.java:3-5` |
| `codechicken.lib.model` | 13 / 2 / 9 / 0 (24) | `ModelRegistryHelper`, `bakery.CCBakeryModel`, `ModelBakery`, `IBakeryProvider`, `IBakery`, `ISimpleBlockBakery`, `ILayeredBlockBakery`, `key.IBlockStateKeyGenerator`, `DummyBakedModel`, `bakedmodels.ModelProperties` | `automation/block/BlockTorqueDistributor.java:3-7`, `core/render/BaseBakery.java:3-4`, `core/render/BlockStateKeyGenerator.java:3`, `structure/block/BlockStoneCoffin.java:3` |
| `codechicken.lib.vec` | 12 / 2 / 0 / 0 (14) | `Transformation`, `Rotation`, `Translation`, `Vector3`, `RedundantTransformation`, `uv.IconTransformation` | `automation/render/TorqueShaftRenderer.java:3-7`, `core/render/BaseBakery.java:11-12`, `automation/render/TorqueTieredRenderer.java:3` |
| `codechicken.lib.util` | 1 / 1 / 5 / 0 (7) | `TransformUtils`, `ResourceUtils` | `structure/render/WoodenCoffinRenderer.java:5`, `structure/proxy/ClientProxyStructure.java:3` |
| `codechicken.lib.texture` | 2 / 0 / 2 / 0 (4) | `TextureUtils` | `automation/render/AnimatedBlockRenderer.java:6`, `structure/render/ParticleOnlyModel.java:5` |
| `codechicken.lib.lighting` | 2 / 0 / 0 / 0 (2) | `LightModel` | `automation/render/AnimatedBlockRenderer.java:3`, `automation/render/FlywheelStorageRenderer.java:4` |
| `codechicken.lib.colour` | 1 / 0 / 0 / 0 (1) | `ColourRGBA` | `automation/render/FlywheelStorageRenderer.java:3` |
| `codechicken.lib.raytracer` | 0 / 1 / 0 / 1 (2) | `RayTracer` | `core/util/RayTraceUtils.java:3`, `vehicle/input/VehicleInputHandler.java:3` |
| `codechicken.lib.block` | 0 / 0 / 1 / 0 (1) | `property.unlisted.UnlistedStringProperty` | `structure/block/BlockSoundBlock.java:3` |
| `codechicken.lib.render.item` / `.particle` | counted under render | `IItemRenderer`, `IModelParticleProvider` | `structure/render/ProtectionFlagRenderer.java:4`, `structure/render/ParticleOnlyModel.java:4` |

Superclass use (STATS, "TOP EXTERNAL SUPERCLASSES"): 20 automation classes extend `codechicken/lib/model/bakery/CCBakeryModel`. That is the core of the block-model system in the Automation module.

### 1.21.1 details

- Exists. Latest 1.21.1 NeoForge build: **4.6.1.529** (Modrinth: published 2026-07-23, https://api.modrinth.com/v2/project/codechicken-lib/version; Maven metadata lists `1.21.1-4.6.1.529`, https://maven.covers1624.net/io/codechicken/CodeChickenLib/maven-metadata.xml).
- Maven: group `io.codechicken`, artifact `CodeChickenLib`, repository `https://maven.covers1624.net/` (coordinates from the 1.21.1 branch `build.gradle`, publishing block).
- Build tool: ModDevGradle (`net.neoforged.moddev` 2.0.77), Java 21, the same toolchain as the target (1.21.1 branch `build.gradle`).
- mods.toml: `modId="codechickenlib"`, dependency on `neoforge` and `minecraft [1.21.1]` (1.21.1 branch `src/main/resources/META-INF/neoforge.mods.toml`).
- License: LGPL-2.1 (GitHub API) / LGPL-2.1-or-later (Modrinth). Source: https://github.com/TheCBProject/CodeChickenLib, branch `1.21.1`.

API survey of the 1.21.1 branch. Method: GitHub git tree of branch `1.21.1` (372 `.java` files, not truncated), file-name match, then raw file reads for signatures. This checks the branch source, not the released jar.

| AW reference | 1.21.1 status | Note |
|---|---|---|
| `render.CCModel` | Present (`src/main/java/codechicken/lib/render/CCModel.java`, class at line 24) | Same package |
| `render.CCRenderState` (`.instance`) | Class present (`render/CCRenderState.java`, line 44). Method `instance` not checked | Unverified method level |
| `render.OBJParser.parseModels` | Class moved to `render/model/OBJParser.java`. `parseModels` is absent; the public entry point is `parse(ResourceProvider, ResourceLocation, VertexFormat.Mode, Transformation, boolean)` (line 125) | **Signature changed: rewrite** |
| `raytracer.RayTracer` (`getStartVec`, `getEndVec`, `getCorrectedHeadVec`) | Present (`raytracer/RayTracer.java`). Those names occur in the file (lines 24-57) | Signatures not checked |
| `model.bakery.*` (`CCBakeryModel`, `ModelBakery`, `IBakery`, `IBakeryProvider`, `ISimpleBlockBakery`, `ILayeredBlockBakery`, `key.IBlockStateKeyGenerator`) | **Absent.** No `bakery` directory in `model/` | **Gone: rewrite** |
| `model.ModelRegistryHelper` | **Absent** | **Gone** |
| `render.buffer.BakingVertexBuffer` | **Absent.** `render/buffer/` has `BakedQuadVertexBuilder`, `TransformingVertexConsumer`, `DelegatingVertexConsumer` and others | **Gone** (closest: `BakedQuadVertexBuilder`, unverified equivalence) |
| `render.CCModelState` | **Absent.** `model/PerspectiveModelState.java` exists | Probably replaced (unverified) |
| `model.DummyBakedModel` | Present (`model/DummyBakedModel.java`) | |
| `model.bakedmodels.ModelProperties` (`PerspectiveProperties`) | `ModelProperties.java` present. Nested `PerspectiveProperties` not checked | Partly verified |
| `vec.Transformation`, `Rotation`, `Translation`, `Vector3`, `RedundantTransformation` | Present (`vec/`). `Transformation` is now `ITransformation<Vector3, Transformation>` (line 9) | Generic signature changed; call sites may need edits |
| `vec.uv.IconTransformation` | Present (`vec/uv/IconTransformation.java`, raw URL returned 200) | |
| `lighting.LightModel` | Moved: `render/lighting/LightModel.java` | Package changed |
| `util.TransformUtils`, `util.ResourceUtils` | Present (`util/`) | |
| `texture.TextureUtils` | Present (`texture/TextureUtils.java`) | |
| `colour.ColourRGBA` | Present (`colour/ColourRGBA.java`) | |
| `render.item.IItemRenderer` | Present (`render/item/IItemRenderer.java`) | |
| `render.particle.IModelParticleProvider` | Present (`render/particle/IModelParticleProvider.java`) | |
| `block.property.unlisted.UnlistedStringProperty` | **Absent** (no `block/property` package) | Use a NeoForge model property (not verified) |

What this means: the rendering and baking layer (about 20 Automation block classes on `CCBakeryModel`, plus Structure blocks on `ModelRegistryHelper`/`CCBakeryModel`) has no direct 1.21.1 CCL equivalent. The replacement is NeoForge's model pipeline (unbaked geometry, `BakedModel`, model events), whose class names were **not verified** in this slice. CCL 1.21.1 still provides vector and transform math, OBJ parsing (new signature), a raytracer, and `DummyBakedModel`/`PerspectiveModel` helpers.

## JEI (Just Enough Items)

### Usage in Ancient Warfare

Optional, compat only.

- Package `SRC/core/compat/jei/`: 8 files import `mezz.jei.api` (STATS: `mezz/jei/api` 8 classes in core). Files: `AWJEIPlugin.java` (imports `mezz.jei.api.JEIPlugin`, `IModPlugin`, `IModRegistry`, `ISubtypeRegistry`, `IRecipeCategoryRegistration`, `IRecipeTransferRegistry`; lines 3-12), `MultiRecipeTransferHandler.java:3-8`, `ResearchRecipeCategory.java:3-9` (also `mezz.jei.config.Constants`, line 9), `ResearchRecipeWrapper.java:3-5`, `ShapedRecipeWrapper.java:3-5` (also the internal `mezz.jei.plugins.vanilla.crafting.ShapelessRecipeWrapper`, line 5), `ShapedResearchRecipeCategory.java:3-4`, `ShapedResearchRecipeWrapper.java:3-4`, `ShapelessResearchRecipeCategory.java:3-4`.
- Runtime gate: `SRC/core/network/NetworkHandler.java:158`: `if (Loader.isModLoaded("jei"))` registers `PacketTransferRecipe` (`SRC/core/compat/jei/PacketTransferRecipe.java`, the only network packet in this slice).
- Build: `UP/build.gradle` `deobfCompile "mezz.jei:jei_${minecraft_version}:${jei_version}"`, `jei_version=4.11.0.212` (`UP/gradle.properties`).
- Purpose: research (tech-tree) recipes shown in JEI as shaped and shapeless categories, with a transfer handler that moves items into the crafting grid.

### 1.21.1 details

- Exists. Latest 1.21.1 NeoForge build: **19.70.0.469** (published 2026-11-01). Modrinth `jei`, project id `u6dRKJwZ`, MIT: https://api.modrinth.com/v2/project/jei/version (filtered to game version 1.21.1, loader neoforge).
- Maven: `mezz.jei:jei-1.21.1-neoforge:19.70.0.469`, repository `https://maven.blamejared.com/`. Metadata checked: https://maven.blamejared.com/mezz/jei/jei-1.21.1-neoforge/maven-metadata.xml.
- 1.21.1 mod id: presumed `jei` (matches the `Loader.isModLoaded("jei")` string in AW). **Not verified.**
- API names (plugin annotation, `IModPlugin` methods, category and transfer registration): **not verified.** A probe of `mezz/JustEnoughItems` branch `1.21.1` at `src/main/java/mezz/jei/api/JeiPlugin.java` returned 404, so the path is unknown. Do not assume the 1.12 names carry over. The two internal JEI classes AW uses (`mezz.jei.config.Constants`, `mezz.jei.plugins.vanilla.crafting.ShapelessRecipeWrapper`) are internal and should be assumed gone.
- License: MIT (Modrinth).

## Redstone Flux (CoFH RF)

### Usage in Ancient Warfare

Optional energy interop, only in the Automation module. STATS: `cofh/redstoneflux/api` in 3 automation classes.

- `SRC/automation/tile/torque/TileTorqueBase.java:3`, `:30` `@Optional.Interface(iface = "cofh.redstoneflux.api.IEnergyReceiver", modid = "redstoneflux", striprefs = true)`, `:64-82` `@Optional.Method(modid = "redstoneflux")` on the RF methods, `:270` `Loader.isModLoaded("redstoneflux")`.
- `SRC/automation/tile/worksite/TileWorksiteBase.java:3-4`, `:37-38` (two `@Interface` annotations for `IEnergyProvider` and `IEnergyReceiver`), `:65-89` `@Method(modid = "redstoneflux")`.
- `SRC/automation/tile/torque/TileTorqueSidedCell.java:179`: `Loader.isModLoaded("redstoneflux")`, with a comment about RF compat passing null (line 201).
- `SRC/automation/proxy/RFProxy.java:14` loads `RFProxyActual` by reflection only if `redstoneflux` is loaded. `RFProxyActual.java:4, 23-25` calls `IEnergyReceiver.receiveEnergy` with torque converted by `AWAutomationStatics.torqueToRf`.
- `SRC/automation/AncientWarfareAutomation.java:53`: `dependencies = "required-after:ancientwarfare;after:redstoneflux;after:buildcraftcore"`. The `after:` part is only an ordering hint.
- Build: `UP/build.gradle` `deobfCompile "cofh:RedstoneFlux:${rf_version}:universal"`, `rf_version=1.12-2.0.2.3`.

### 1.21.1 details

- No CoFH RedstoneFlux build for NeoForge 1.21.1 was found. The Modrinth project named `redstoneflux` (id `BFaqHOz2`, MIT, https://github.com/Jadiefication/RedstoneFlux) lists only Folia, Paper and Purpur loaders for 1.21.4 and later. It is not the CoFH library. Modrinth search for "redstone flux" with NeoForge and 1.21.1 facets returned no match.
- Replacement: NeoForge's built-in energy capability. **Verified** in NeoForge branch `1.21.1` (raw source, https://raw.githubusercontent.com/NeoForged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/energy/IEnergyStorage.java and `.../capabilities/Capabilities.java`):
  - `net.neoforged.neoforge.energy.IEnergyStorage` with `int receiveEnergy(int, boolean)`, `int extractEnergy(int, boolean)`, `int getEnergyStored()`.
  - `Capabilities.EnergyStorage.BLOCK` (a `BlockCapability<IEnergyStorage, @Nullable Direction>`) and `Capabilities.EnergyStorage.ITEM`.
  - `PORTING.md` line 24 already says `IEnergyStorage` is queried through `getCapability`.
- Consequence: on 1.21.1 `Loader.isModLoaded("redstoneflux")` has no matching mod and the `@Optional` RF code path is dead. The energy bridge should be rewritten as a NeoForge capability, not as an RF compat layer. The torque-to-FE conversion rate is a design choice to carry over from `AWAutomationStatics`.

## FTB Library (FTBLib) and FTB Teams

### Usage in Ancient Warfare

Optional team-ownership compat.

- `SRC/core/compat/ftb/FTBCompat.java:9`: `getModId()` returns `"ftblib"`. `init()` registers `FTBTeamViewer` with `TeamViewerRegistry.registerTeamViewer`.
- `SRC/core/compat/ftb/FTBTeamViewer.java:3-5` imports `com.feed_the_beast.ftblib.lib.data.ForgePlayer`, `ForgeTeam`, `Universe`. Line 17: `FTBLIB_MOD_ID = "ftblib"`.
- `SRC/core/AncientWarfareCore.java:23`, `:79`: `CompatLoader.registerCompat(new FTBCompat())`. `SRC/core/compat/CompatLoader.java:17` gates `init()` with `Loader.isModLoaded`.
- The AW-side abstraction is `SRC/core/owner/TeamViewerRegistry` (AW's own code, which stays).
- Build: `UP/build.gradle` `deobfCompile "com.feed_the_beast.mods:FTBLib:${ftblib_version}"`, `ftblib_version=5.4.4.5`.

### 1.21.1 details

- FTB Library exists for 1.21.1 NeoForge. Latest in Maven metadata: **2101.1.39**. Coordinates: `dev.ftb.mods:ftb-library-neoforge`, repository `https://maven.ftb.dev/releases/` (https://maven.ftb.dev/releases/dev/ftb/mods/ftb-library-neoforge/maven-metadata.xml). A `dev.ftb.mods:ftb-library` artifact with the same versions also exists in that repo.
- **Mod id changed.** The FTB Library 1.21.1 branch `gradle.properties` sets `mod_id=ftblibrary` (https://raw.githubusercontent.com/FTBTeam/FTB-Library/1.21.1/main/gradle.properties). AW uses `"ftblib"` in two places (`FTBCompat.java:9`, `FTBTeamViewer.java:17`). If the string is carried over unchanged, the compat never loads.
- Team API: FTB Teams is the 1.21 team layer. Maven `dev.ftb.mods:ftb-teams-neoforge`, last listed 2101.1.12 (https://maven.ftb.dev/releases/dev/ftb/mods/ftb-teams-neoforge/maven-metadata.xml). The replacement classes for `ForgePlayer`, `ForgeTeam` and `Universe` are **not verified**.
- FTB Library source: https://github.com/FTBTeam/FTB-Library, branch `1.21.1/main` exists (GitHub API branch list). Its license was not verified (the LICENSE raw URL returned 404).
- Modrinth: no FTB Library project was found under the slugs tried (`ftb-library`, `ftblibrary`). CurseForge project 404465 is cited in the 1.21.1 `gradle.properties` as the Forge page id; it was not queried.

## Electroblob's Wizardry

### Usage in Ancient Warfare

Optional. Used only in the NPC module, for spell-casting faction NPCs (STATS, npc: `electroblob/wizardry/spell` 4 classes, `util` 3, `entity` 2, `registry` 2, `event` 1, `packet` 1).

- `SRC/npc/compat/ebwizardry/EBWizardryCompat.java:12`: `getModId()` returns `"ebwizardry"`. Registered at `SRC/npc/AncientWarfareNPC.java:128`.
- `SRC/npc/compat/ebwizardry/FactionAllyDesignation.java:3` (`IElementalDamage`); `SRC/npc/compat/ebwizardry/ai/EntityAIAttackSpellImproved.java:3-10` (`ISpellCaster`, `SpellCastEvent`, `PacketNPCCastSpell`, `WizardryPacketHandler`, `Spells`, `Spell`, `SpellModifiers`).
- `SRC/npc/entity/faction/NpcFactionSpellcasterWizardry.java:3-9`; `SRC/npc/container/ContainerNpcFactionSpellcasterWizardry.java:3`; `SRC/npc/gui/GuiNpcFactionSpellcasterWizardry.java:3`.
- Gates: `SRC/npc/AncientWarfareNPC.java:114` (registers the container only if loaded; the comment at lines 110-112 says class references must stay inside lambdas), `SRC/npc/init/AWNPCEntities.java:162`, `SRC/npc/proxy/NpcClientProxy.java:108`.
- Data: `RES/registry/compat/ebwizardry/entity_spawn_nbt.json` (gated by `"mod": "ebwizardry"`); `RES/registry/npc/faction_npc_defaults.json` (27 references, not gated per file); `RES/registry/npc/target_lists.json` (6 references).
- Build: `UP/build.gradle` `compile "curse.maven:ElectroblobsWizardry:${eb_cf_fileid}"`, `eb_cf_fileid=2929294` (CurseMaven, a CurseForge file id).

### 1.21.1 details

- No 1.21.1 NeoForge build was found. Modrinth search on "electroblob" with `categories:neoforge` and `versions:1.21.1` returned nothing. The only Electroblob's project found was `electroblobs-wizardry-redux` ("Electroblob's Wizardry Redux"), whose game versions list only 1.20.1 (https://api.modrinth.com/v2/project/electroblobs-wizardry-redux/version).
- CurseForge was not queried, so a 1.21.1 build there is **not verified**.
- Replacement: none found. Options are to drop the spellcaster faction, or to re-implement a small spell interface inside AW. Do not depend on an unverified third-party magic mod.

## AgriCraft and InfinityLib

### Usage in Ancient Warfare

Optional crop-farm compat.

- `SRC/automation/compat/agricraft/AgricraftCompat.java:9`: `getModId()` returns `"agricraft"`. `init()` calls `CropFarmRegistry.registerCrop(new CropAgricraftCrop())`. Registered at `SRC/automation/AncientWarfareAutomation.java:112`.
- `SRC/automation/compat/agricraft/CropAgricraftCrop.java:3-5` imports `com.infinityraider.agricraft.api.v1.crop.IAgriCrop`, `init.AgriBlocks`, `tiles.TileEntityCrop`; the file uses `Optional<TileEntityCrop>` through `WorldTools.getTile` (lines 28, 40).
- Comment at `SRC/automation/registry/CropFarmRegistry.java:54`: compat crops are added at the front of the list so they are processed first. This is AW's own registry and stays.
- InfinityLib: STATS reports 1 automation class under `com/infinityraider/infinitylib`. A grep of `SRC` for `infinityraider` finds only the three `agricraft` imports above. The InfinityLib reference is therefore probably transitive (for example, a class the AgriCraft API extends). Not resolved.
- Build: `UP/build.gradle` `compile "curse.maven:agricraft:${agricraft_cf_fileid}"` (`2609648`) and `compile "curse.maven:infinitylib:${infinitylib_cf_fileid}"` (`2537265`). Both are CurseMaven ids.

### 1.21.1 details

- AgriCraft: no 1.21.1 build. Modrinth `agricraft` (MIT, https://github.com/AgriCraft/AgriCraft) game versions stop at 1.20.4 (https://api.modrinth.com/v2/project/agricraft/version). GitHub branches: `1.12.2`, `master`, `dev`, and others; `master` last pushed 2021-08-02.
- InfinityLib: no 1.21.1 build. Modrinth `infinitylib` (MIT) game versions: 1.18.2 only (https://api.modrinth.com/v2/project/infinitylib/version). GitHub `InfinityRaider/InfinityLib` master last pushed 2023-06-01.
- Replacement: AW's `CropFarmRegistry` with vanilla crop blocks (the crop logic is in AW code). Drop the AgriCraft crop class and its registration.

## BuildCraft

### Usage in Ancient Warfare

Name only. `SRC/automation/AncientWarfareAutomation.java:53` lists `after:buildcraftcore` in the dependency string. No import, class, or `modid` reference to BuildCraft exists anywhere in `SRC` or `RES` (grep for `buildcraft`, case-insensitive). STATS has no BuildCraft prefix. Not in `build.gradle`.

### 1.21.1 details

- BuildCraft Community Edition (Modrinth `buildcraft-community-edition`, MPL-2.0) has 1.21.1 builds with loader `neoforge`. Latest 1.21.1 version seen: **8.0.30+1.21.1+neoforge** (publish date not verified; version confirmed at https://api.modrinth.com/v2/project/buildcraft-community-edition/version?game_versions=["1.21.1"] on the verification run).
- Whether the 1.21.1 build's mod id is `buildcraftcore` is **not verified**. It does not matter, because AW does not need BuildCraft.
- Replacement: remove the string. No code depends on it.

## Twilight Forest

### Usage in Ancient Warfare

Optional, reflection only. Not in `build.gradle`, and STATS does not see it because it is loaded by name.

- `SRC/npc/compat/TwilightForestCompat.java:14-29`: `getModId()` returns `"twilightforest"`. `init()` reflects `twilightforest.TFConfig.dimension` and `TFConfig$Dimension.dimensionID` (lines 21-22), then calls `WorldTools.registerDimensionDaytimeLogic(dimensionId, world -> world.getWorldTime() % 24000 < 12000)`. Registered at `SRC/npc/AncientWarfareNPC.java:127`.
- `SRC/structure/config/AWStructureStatics.java:366-381` (and following lines): entity and loot id strings `twilightforest:entities/...` used as data.
- Data: `RES/registry/compat/twilightforest/saplings.json` and `tree_scanners.json` (17 references, gated by `"mod": "twilightforest"`); `RES/registry/npc/target_lists.json` (44 references, not gated).

### 1.21.1 details

- GitHub `TeamTwilight/twilightforest` has a `1.21.1` branch (branch list checked; default branch `latest`, pushed 2026-11-15). Raw `README.md` on `1.21.1` was fetched.
- A Modrinth project for the core mod was not found under the slugs tried (`twilightforest`, `the-twilight-forest`, `twilight-forest`). Add-on projects exist (for example `twilight-forest-the-lost-blocks`, which lists 1.21.1), but they are not the core mod.
- Released 1.21.1 jar: **not verified**. The reflected field names (`TFConfig.dimension`, `dimensionID`) are 1.12 names and must be checked against the 1.21 source before any rewrite.

## Epic Siege Mod

### Usage in Ancient Warfare

Optional, reflection only. Not in `build.gradle`.

- `SRC/npc/compat/EpicSiegeCompat.java`: `getModId()` returns `"epicsiegemod"`. `init()` loads `funwayguy.epicsiegemod.ai.ESM_EntityAINearestAttackableTarget` by name and registers `EventHandler.INSTANCE.registerAdditionalHostileAICheck`. Registered at `SRC/npc/AncientWarfareNPC.java:126`.

### 1.21.1 details

- No 1.21.1 NeoForge build found. Modrinth search "epic siege" with NeoForge and 1.21.1 facets returned nothing. CurseForge was not queried.
- Replacement: drop. AW's own hostile target list (`RES/registry/npc/target_lists.json`) covers the intent.

## Mods referenced only by data files

Data in `RES` names other mods in three ways. Each file's gate is what decides whether a missing mod breaks anything.

### Gating rules (code)

- `SRC/core/registry/RegistryLoader.java:190`, `:219-220`: a registry JSON with a `"mod"` key is skipped unless that mod is loaded. Applies to `RES/registry/compat/*`.
- `SRC/core/init/AWCoreLoot.java:78-80`: a loot-table file under `chests/compat/<folder>/` is registered only if `Loader.isModLoaded(<folder>)`. The gate is the **folder name**, not the file content.
- `SRC/npc/registry/FactionDefinition.java:57-58` and `OwnedNpcDefault.java:27-28`: NPC target lists match by registry-name string (`EntityRegistry.getEntry(...).getRegistryName().toString()`), so an entity from a missing mod never matches and is not a load error. The `TargetRegistry` parse path was not traced.

### 1. Registry compat, `RES/registry/compat/` (35 files, 16 mod ids)

Each file has a `"mod"` key. Counts are files per folder.

| Mod id (folder) | Files | Content | 1.21.1 NeoForge status (checked 2026-11-15) |
|---|---|---|---|
| biomesoplenty | 5 | fruit blocks, saplings, tillable blocks, tree scanners, tree soil | No hit on mod-id search. Not verified |
| ebwizardry | 1 | entity spawn NBT | No 1.21.1 build found (see Electroblob's section) |
| forestry | 2 | saplings, tree scanners | Top hit "Forestry: CE" (`forestry-community-edition`). Identity not confirmed |
| grimoireofgaia | 1 | entity spawn NBT | No hit. Not verified |
| growthcraft | 4 | crop, fruit, sapling, soil blocks. Uses sub-namespaces `growthcraft_rice`, `growthcraft_grapes`, `growthcraft_hops`, `growthcraft_apples`, `growthcraft_bamboo` | No hit. Not verified |
| harvestcraft | 1 | fruit blocks (36 entries) | Top hit "Pam's HarvestCraft 2: Food Core" (`pams-harvestcraft-2-food-core`). Likely the successor; not confirmed |
| iceandfire | 1 | entity spawn NBT | Top hit is an add-on, not the core mod. Not verified |
| immersiveengineering | 1 | crop blocks | Modrinth slug `immersiveengineering` returned as a 1.21.1 NeoForge hit |
| natura | 4 | crop, fruit, tree scanners, tree soil | Top hit "Naturalist" (not the same mod). Not verified |
| oreberries | 1 | fruit blocks | Top hit "Oreberries Replanted". Identity not confirmed |
| oreshrubs | 1 | fruit blocks | No hit. Not verified |
| plants2 | 4 | fruit, sapling, tree scanner, tree soil | No hit. Not verified |
| randomthings | 1 | tillable blocks | No hit. Not verified |
| rustic | 4 | fruit, sapling, soil, tree scanners | Top hit "Rustic Delight". Identity not confirmed |
| tconstruct | 2 | tree scanners, tree soil | No hit. Not verified |
| twilightforest | 2 | saplings, tree scanners | See Twilight Forest section |

Note: the mod id count for natura and rustic in the grep (5 and 3) differs from the folder count (4 and 4) because some file text contains more than one `"mod"` key. Folder counts are used here.

### 2. Loot-table compat, `RES/loot_tables/chests/compat/` (28 files, 6 folders)

Gate: folder name (`AWCoreLoot.java:78-80`).

| Folder | Files | Note |
|---|---|---|
| betteranimalsplus | 4 | village farm loot. No 1.21.1 hit on mod-id search. Not verified |
| biomesoplenty | 1 | village farm barley. Not verified |
| gemsplusplus | 1 | craftsman jewel loot, 16 references. No 1.21.1 hit. Not verified |
| harvestcraft | 13 | shop and village farm loot. `harvestcraft_shop_oliveoil.json` is under this folder but references `rustic` items (2 references). The folder gate does not check those items |
| rustic | 8 | shop, lumberjack, craftsman and farm loot. Gated by folder |
| zawa | 1 | tannery loot. Not verified |

### 3. Ungated NPC data files, `RES/registry/npc/` (3 files, no `"mod"` key)

- `faction_npc_defaults.json`: 27 references to `ebwizardry`. Not gated per file. Whether an entry for a missing mod is skipped was not traced.
- `faction_trade_lists.json`: 237 references to `rustic` and 2 to `harvestcraft`. Not gated per file. Trade items from missing mods were not traced.
- `target_lists.json`: references to about 60 other namespaces. The largest are `lycanitesmobs` (119), `grimoireofgaia` (80), `thebetweenlands` (36), `mocreatures` (34), `iceandfire` (29), `tolkienmobs` (29), `twilightforest` (44), `abyssalcraft` (40). These are string matches (see gating rules), so missing mods are inert.

For the target-list mods, 1.21.1 status was checked only for a sample by Modrinth mod-id search (top hit only): lycanitesmobs, mocreatures, thebetweenlands, tolkienmobs, abyssalcraft, thaumcraft (top hit "Modonomicon", not the same mod), vampirism (exists: `vampirism`), primitivemobs, dungeonmobs, gravestone-extended, atum, bewitchment. All others were not checked.

Namespace scan of `RES/registry/` and `RES/loot_tables/` for JSON only. `.aws` structure templates and `.nbt` files were **not** scanned for third-party block or entity ids (see Open questions).

## What this means for the port

Dependencies to add to the 1.21.1 build (ModDevGradle, per mod `build.gradle` for the modules that need them, shared via `build-logic` conventions if more than one module uses them, as `CLAUDE.md` requires):

| Dependency | Coordinates | Used by | Required or optional |
|---|---|---|---|
| CodeChickenLib 4.6.1.529 | `io.codechicken:CodeChickenLib:1.21.1-4.6.1.529` from `https://maven.covers1624.net/` | core, automation, structure, vehicle | Required. Bakery and OBJ code must be rewritten, not just re-linked |
| JEI 19.70.0.469 | `mezz.jei:jei-1.21.1-neoforge:19.70.0.469` from `https://maven.blamejared.com/` | core (`core/compat/jei`) | Optional. compileOnly plus runtime-optional in mods.toml |
| FTB Library 2101.1.39 and FTB Teams 2101.1.12 | `dev.ftb.mods:ftb-library-neoforge:2101.1.39`, `dev.ftb.mods:ftb-teams-neoforge:2101.1.12` from `https://maven.ftb.dev/releases/` | core (`core/compat/ftb`) | Optional. Only if team ownership compat is kept |
| NeoForge energy | none (built into NeoForge) | automation | Replaces RF. No third-party dependency |

Compat to drop or redo:

| Compat | Action | Reason |
|---|---|---|
| Redstone Flux | Replace with `Capabilities.EnergyStorage.BLOCK` / `IEnergyStorage`. Remove `@Optional` and the `redstoneflux` string checks | No 1.21.1 RF. The NeoForge capability is verified |
| Electroblob's Wizardry spellcaster NPC (`npc/compat/ebwizardry`, `NpcFactionSpellcasterWizardry`, container, GUI, `EntityAIAttackSpellImproved`) | Drop, or re-implement spells inside AW | No 1.21.1 build |
| AgriCraft and InfinityLib (`automation/compat/agricraft`) | Drop. Keep `CropFarmRegistry` | No 1.21.1 build |
| BuildCraft `after:buildcraftcore` | Remove the string | Not used by code |
| Epic Siege Mod (`npc/compat/EpicSiegeCompat`) | Drop | No 1.21.1 build |
| Twilight Forest (`npc/compat/TwilightForestCompat`) | Rewrite against TF 1.21 source, or drop | 1.21.1 branch exists; released build and API not verified |
| Mod id `ftblib` (`FTBCompat.java:9`, `FTBTeamViewer.java:17`) | Change to `ftblibrary` | 1.21.1 `mod_id` (see FTB section) |
| Data entries for mods with no 1.21.1 build (registry compat, loot folders, target lists) | Keep the data, let the gate skip it. Strip the entries for mods that are dropped | Most are gated already. Non-gated files (`faction_trade_lists`, `faction_npc_defaults`) need a check before the port |

Things to keep in AW's own code: `TeamViewerRegistry`, `CropFarmRegistry`, `CompatLoader` (or its 1.21.1 replacement), and `RegistryLoader`'s `"mod"` gate. These are AW's own mechanisms and do not depend on any external mod.

Do not move any of this into `capttrackerlib`. Third-party libraries are not shared AW code. The only shared-between-mods item is CodeChickenLib itself, which belongs in the common build conventions.

## Open questions

1. JEI 1.21.1 API: plugin annotation, `IModPlugin` methods and category/transfer registration are not verified. The probed path `mezz/JustEnoughItems` branch `1.21.1` `src/main/java/mezz/jei/api/JeiPlugin.java` returned 404. Find the correct path before porting `core/compat/jei`.
2. CodeChickenLib 1.21.1 method-level checks not done: `CCRenderState.instance`, `RayTracer.getStartVec` and related signatures, `PerspectiveProperties` inside `ModelProperties`. The NeoForge replacement for the bakery pipeline (unbaked geometry, `BakedModel`, model events) and its class names are not verified.
3. InfinityLib reference: STATS counts one automation class under `com/infinityraider/infinitylib`, but no import of it exists in `SRC`. Find which class (probably transitive through the AgriCraft API) before dropping it.
4. CurseForge was not queried for Electroblob's Wizardry, AgriCraft, InfinityLib, Epic Siege Mod or FTB Library 1.21.1 builds. A CurseForge API key would be needed. Modrinth and Maven results only.
5. Twilight Forest core mod: released 1.21.1 build, its Modrinth/CurseForge slug, and its 1.21 API for the daytime hook are not verified. Only the GitHub `1.21.1` branch was seen.
6. Structure templates (`RES/assets/ancientwarfare/template/*.aws`, gzipped NBT) were not scanned for third-party block and entity ids. A binary scan is needed to finish the data-only mod list.
7. Untraced load behaviour: `TargetRegistry`, `FactionTradeListRegistry` and `NpcDefaultsRegistry` parsing with an absent mod (for `faction_trade_lists.json` and `faction_npc_defaults.json`, which have no `"mod"` gate). Confirm that a missing mod entry is skipped and not fatal.
8. The value of `CodeChickenLib.MOD_VERSION_DEP` and the 1.12 CCL 3.2.3 jar contents are not in the source tree. The AW version range for CCL is therefore not verified.

## Verification

Verification run 2026-11-15 by a second analyst. Scratch area: `analysis/agents/deps-modern-verify/` (no scripts were needed; all checks were one-shot curl calls).

Checked and confirmed:
- Maven `https://maven.covers1624.net/io/codechicken/CodeChickenLib/maven-metadata.xml`: 1.21.1 builds end at `1.21.1-4.6.1.529`. Confirmed. Note the same metadata also lists `1.21.11-*` builds for a different Minecraft version, which are not relevant here.
- Modrinth `https://api.modrinth.com/v2/project/codechicken-lib/version` filtered to 1.21.1 and neoforge: newest `4.6.1.529` (2026-07-23). Confirmed.
- Maven `https://maven.blamejared.com/mezz/jei/jei-1.21.1-neoforge/maven-metadata.xml`: release `19.70.0.469`. Confirmed.
- Maven `https://maven.ftb.dev/releases/dev/ftb/mods/ftb-library-neoforge/maven-metadata.xml`: 2101.1.x series ends at `2101.1.39`. Confirmed. Note the metadata's overall latest is `26.1.2.12` for other Minecraft versions, so "2101.1.39" must be stated as the 1.21.1 line.
- Maven `https://maven.ftb.dev/releases/dev/ftb/mods/ftb-teams-neoforge/maven-metadata.xml`: 2101.x ends at `2101.1.12`. Confirmed.
- Modrinth search `electroblob` with the 1.21.1 facet: no hit. Electroblob's Wizardry Redux (`electroblobs-wizardry-redux`) last game version `1.20.1`. Confirmed.
- Modrinth search `epic siege` with the 1.21.1 facet: no hit. Confirmed.
- Modrinth `agricraft` game versions top out at `1.20.4`. Confirmed.
- Modrinth `buildcraft-community-edition` 1.21.1 build `8.0.30+1.21.1+neoforge` exists. Confirmed (publish date not checked).
- Modrinth search `redstone flux` with 1.21.1 and neoforge facets: no hit. Confirmed.
- Modrinth search `ftb library` with the 1.21.1 facet returns only add-ons, not the core library on Modrinth. Confirmed as stated.
- `https://raw.githubusercontent.com/NeoForged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/energy/IEnergyStorage.java` returns HTTP 200. The file exists on branch 1.21.1. Method signatures and `Capabilities.EnergyStorage.BLOCK` were not re-read.

Local claims checked against SRC and UP (all confirmed):
- `core/AncientWarfareCore.java:44` carries the CodeChickenLib dependency string.
- `automation/AncientWarfareAutomation.java:53` carries `after:redstoneflux;after:buildcraftcore`, and BuildCraft appears in no other file under SRC.
- `core/compat/ftb/FTBCompat.java:9` and `FTBTeamViewer.java:17` both use `"ftblib"`.
- `core/network/NetworkHandler.java:158` gates on `Loader.isModLoaded("jei")`.
- `automation/tile/torque/TileTorqueBase.java:30` has the `@Optional.Interface` for `cofh.redstoneflux.api.IEnergyReceiver`. `TileWorksiteBase.java` imports `IEnergyProvider` and `IEnergyReceiver` (lines 3-4) and carries `@Interface` at line 37.
- `npc/compat/ebwizardry/EBWizardryCompat.java:12` returns `"ebwizardry"`.
- `npc/AncientWarfareNPC.java:126`, `:127`, `:128` register EpicSiege, TwilightForest and EBWizardry compat. `npc/AncientWarfareNPC.java:114` gates on `Loader.isModLoaded("ebwizardry")`.
- `automation/AncientWarfareAutomation.java:112` registers `AgricraftCompat`. `automation/compat/agricraft/AgricraftCompat.java:9` returns `"agricraft"`.
- `core/init/AWCoreLoot.java:78-80` gates loot folders on `Loader.isModLoaded(modName)`.
- `UP/gradle.properties` holds `ftblib_version=5.4.4.5`, `rf_version=1.12-2.0.2.3`, `ccl_version=1.12.2-3.2.3.+`, `jei_version=4.11.0.212`. `UP/build.gradle:69-73` uses `deobfCompile` for RF, JEI, CCL and FTBLib and `compile` for Electroblob's Wizardry.
- Infinity references: `infinityraider` appears only in `automation/compat/agricraft/CropAgricraftCrop.java`, which matches the report's claim that no direct import exists outside the AgriCraft crop class.

Changed in this run:
- JEI build row: "compile-only" replaced with the real 1.12 build line (`deobfCompile`, `UP/build.gradle:70`).
- BuildCraft row: the 2026-11-15 publish date is marked not verified. The version stands.

Not settled (marked "not verified" or left as is):
- GitHub branch lists for `TeamTwilight/twilightforest` and `FTBTeam/FTB-Library` returned no `1.21.x` names in this run, so the `1.21.1` branch claims for those two repositories are not re-confirmed here. Twilight Forest's released 1.21.1 build remains not verified.
- The FTB `mod_id=ftblibrary` value from the 1.21.1 `gradle.properties` was not re-fetched.
- The JEI publish date (2026-11-01), AgriCraft 4.0.6, and the Redstone Flux Modrinth project details (`BFaqHOz2`, loader list) were not re-checked.
- The NeoForge `IEnergyStorage` method list and `Capabilities.EnergyStorage.BLOCK` were not re-read from source.
- CurseForge was not queried, as in the original report.
- The CodeChickenLib 1.21.1 branch API survey (372 files, method-level names) was not re-run.
