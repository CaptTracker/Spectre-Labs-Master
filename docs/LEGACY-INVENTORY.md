# Legacy mod inventory

Inventory of the 1.12.2 jars to be ported, taken on 2026-11-15. No porting has started.

Only one legacy jar was found: **Ancient Warfare 2** (`ancientwarfare-1.12.2-2.7.0.1038.jar`). It holds five mods that share one source tree, so there is one table row per mod id. It is a third-party mod (authors shadowmage45, P3pp3rF1y and contributors), licensed GPL-3.0-or-later.

Detailed reports, one per slice of the code and data, are in [`inventory/ancientwarfare/`](inventory/ancientwarfare/). Each was written by one analyst and checked against the source by a second; the checks ended in "minor fixes" for 21 reports and "major fixes" for the prior-art report (21). API names for 1.21.1 that nobody could vouch for are marked "(unverified)" in those reports.

## The mods

| Mod id | Name | Version | MC | Depends on | Code | Registers |
|---|---|---|---|---|---|---|
| `ancientwarfare` | Ancient Warfare Core | 1.12.2-2.7.0.1038 | 1.12.2 | Forge 14.23.5.2816+, **CodeChickenLib 3.2.3+ (required)**; optional JEI, FTBLib | 276 files, 21,808 lines | 2 blocks, 17 items, 2 tile entities, 6 menus, 5 key bindings; owns the GUI toolkit, the 59-id GUI table and the 32-packet channel used by all modules |
| `ancientwarfareautomation` | Ancient Warfare Automation | same | 1.12.2 | Core; optional Redstone Flux, AgriCraft (BuildCraft is named but unused) | 229 files, 21,070 lines | 26 blocks (38 after flattening), 1 item with 11 variants, 36 tile entity ids, 21 menus, 24 config options |
| `ancientwarfarenpc` | Ancient Warfare NPCs | same | 1.12.2 | Core; optional Electroblob's Wizardry | 264 files, 24,731 lines | 22 entity types, 59 items, 1 block, 1 tile entity, 119 sounds, 44 AI tasks, 13 menus |
| `ancientwarfarestructure` | Ancient Warfare Structures | same | 1.12.2 | Core | 400 files, 42,078 lines | 49 blocks (about 100 after flattening), 10 items, 20 tile entities, 2 entities, 199 sounds, 15 menus, 23 config options; world generation and 1,484 bundled structure templates |
| `ancientwarfarevehicle` | Ancient Warfare Vehicles | same | 1.12.2 | Core | 208 files, 26,498 lines | 84 items, 2 entities covering 21 vehicle types and 51 ammo types, 14 sounds, 10 packets, 13 key bindings |

Total: 1,377 source files, 136,185 lines (111,306 of code), 2,182 classes.

### Why the jar is 75 MB

Assets are 92% of it. Code is 3.2 MB. There are no bundled libraries, shaded packages or nested jars.

| What | Compressed | Files | Used by |
|---|---|---|---|
| Structure sounds and music | 22.7 MB | 202 | Structures |
| Structure templates (`MohawkyPack1.12.2V72`, 144 MB unpacked) | 20.2 MB | 1,494 | Structures |
| NPC skin pack | 12.6 MB | 3,607 | NPCs |
| Manual images | 5.7 MB | 75 | Core |
| NPC sounds (bard music, voices) | 4.5 MB | 364 | NPCs |
| Textures | 2.5 MB | 716 | all |
| Classes | 3.2 MB | 2,182 | all |

## Notes on each mod

**Core** is the base everything else needs. Its real weight is three shared systems: a home-grown GUI widget toolkit (77 screens across all modules build on it), an NBT-message menu layer, and one packet channel. It also holds the research tree (30 goals gating 281 custom recipes), a JSON registry loader, the in-game manual and about 30 utility classes. Effort: large. See reports 01 and 02.

**Automation** has six area worksites (crop, tree, fruit, animal and fish farms, quarry) plus an auto-crafter, a warehouse multiblock, a mailbox system, chunk loaders, and "torque", a custom energy grid with four generators and flywheel storage. Worksite logic leans on 1.12 materials, metadata and plant interfaces, and the ten torque machines render purely in code through CodeChickenLib. Effort: very large. See reports 03 to 05 and 16.

**NPCs** has 7 player-owned and 15 faction NPC types on one 1,459-line base class, 44 data-driven factions, orders kept in item NBT, two trading systems, custom pathfinding and a 3,606-skin pack loaded at runtime. Effort: very large. See reports 06 to 08 and 17.

**Structures** is the biggest and riskiest module: a custom template format, scanner and builders, rule plugins, world generation with territories and towns, gates built from an invisible entity plus proxy blocks, and the bundled template pack. Effort: very large, with two open design problems (world generation and the template data). See reports 09 to 13 and 19.

**Vehicles** is one entity class covering 21 siege engine and boat types, one missile entity covering 51 ammo types, custom movement physics with its own client sync, and 24 hand-coded box models. Effort: very large for the entity, large for the rest. See reports 14, 15 and 17.

## Hard cases

### Coremods
None. There is no `IFMLLoadingPlugin` and no ASM transformer. The jar ships only an access transformer with 8 entries; about 3 of the targets no longer exist in 1.21.1 (report 18).

### Dependencies that were never updated past 1.12.2
Looked up on 2026-11-15 (report 20).

| Mod | Use in Ancient Warfare | 1.21.1 NeoForge | What to do |
|---|---|---|---|
| CodeChickenLib | Required. Model bakery, OBJ parsing and render state in about 30 classes | 4.6.1.529 exists, but the bakery API Ancient Warfare uses is gone | Drop it; rewrite on JSON models and block entity renderers |
| JEI | Optional recipe plugin | 19.70.0.469 | Keep, redo the plugin |
| FTBLib | Optional team ownership | FTB Library 2101.1.39, FTB Teams 2101.1.12 (mod id changed) | Keep, small |
| Redstone Flux | Optional energy bridge | None | Use NeoForge's built-in energy capability |
| Electroblob's Wizardry | Optional spell-casting NPC | None (Modrinth checked, CurseForge not) | Drop, or give the NPC built-in spells |
| AgriCraft, InfinityLib | Optional crop farm compat | None (same caveat) | Drop |
| BuildCraft | Load-order string only | Not needed | Drop |

The template pack also references blocks, mobs and biomes from about 26 other 1.12 mods; roughly 683 of 1,484 templates use some (that count is unverified, see report 19).

### Shared code, and candidates for `capttrackerlib`
There is only one legacy mod, so nothing is duplicated across mods. Inside Ancient Warfare, Core already plays the shared-library role. Pieces generic enough for `capttrackerlib` if a second mod wants them: the item-count map, relative-side rotation math, the menu NBT data channel, the per-level fake player, and later the GUI widget toolkit. The rest of Core should stay in Ancient Warfare.

### Things that cannot be ported as they are
- **World generation.** `IWorldGenerator` is gone, and the old design writes whole structures into loaded chunks after generation. It needs a custom structure type with chunk-clipped pieces (report 11).
- **The template pack.** 1,484 files in a custom text format full of 1.12 block states and NBT. It needs an offline converter, remap tables, and a decision on templates that use other mods' content (reports 09 and 19).
- **Gates.** An invisible entity that swaps its own bounding box by reflection. It needs a redesign around proxy-block shapes (report 13).
- **CodeChickenLib rendering** of the torque machines, and all immediate-mode GL drawing (reports 16 and 17).
- **Vehicle movement and sync**, which suppress vanilla position packets (report 14).
- **Metadata variants and item NBT** throughout: 78 registered blocks become about 140, and orders, backpacks, spawners and scanners need data components.
- **Old-world migration.** All 17 data fixers can be deleted; 1.12 worlds will not carry over.

### Module boundaries
The five modules reference each other in both directions (Core's data fixers name the other four; NPCs, Vehicles and Structures reference each other's entities and blocks). In 1.12 that was safe because they shipped in one jar. As separate mods the cross-references have to be cut or put behind interfaces.

## Facts the owner should weigh

- **License.** A public port must stay GPL-3.0-or-later, keep the notices and offer source. The repo defaults every mod to "All Rights Reserved" and `capttrackerlib` states no license, and the port would depend on it. This needs the owner's decision, possibly a lawyer's (report 22).
- **Asset provenance.** Skins and structures are credited by name with permission only. Sound effects and bard music have no documented origin, and several look like clips from TV or film. Eight skins depict real people (reports 18 and 22).
- **Existing ports.** Other projects exist for modern versions, including "Ancient Warfare Remastered" on CurseForge for 1.20.1 (Forge, Fabric and NeoForge builds, GPL-3.0, updated 2026-11-04) and a NeoForge 1.21.1 fork whose state is unconfirmed. None was found for 1.21.1 as a finished release (report 21).
- **Unreleased upstream fixes.** Upstream's `1.12.x-dev` branch has 24 commits newer than this jar; 9 are bug fixes worth carrying over (report 21).
- **Bugs found in the original** that a port should fix rather than copy: worksite efficiency upgrades are close to a no-op, the battering-ram block-break chance is always 0%, the animal farm drop handler is never registered, and the "find worksite" permission check looks inverted.

## How this was produced

- The jar's contents were read directly (`mcmod.info`, `@Mod` annotations, class files). Statistics and the scripts that made them are kept outside the repo in `analysis/`.
- The jar was matched to upstream source: `P3pp3rF1y/AncientWarfare2`, branch `1.12.x`, commit `78178170b`. All 1,377 source files map one-to-one to the jar's classes, all 59,941 bytecode line numbers land on code lines at that commit (and not at neighbouring commits), and all 7,356 resources match apart from line endings and the version stamp in `mcmod.info`. Use that source, not the decompiled code.
- The jar was also decompiled with Vineflower 1.12.0 into `legacy/ancientwarfare/decompiled/` (git-ignored): 1,378 files, no failures.
- The baseline `gradlew build` of this repo passed before anything was added.
