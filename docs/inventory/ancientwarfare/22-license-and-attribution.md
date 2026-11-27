# License and attribution

> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory.

Paths are relative to SRC (`upstream/AncientWarfare2/src/main/java/net/shadowmage/ancientwarfare`), RES (`.../src/main/resources`) or UP (`upstream/AncientWarfare2`) as named. Git facts come from read-only `git log` / `git shortlog` in UP (branch 1.12.x, history from 2014-02-11 to 2021-08-21).

## License

The mod is GPL-3.0-or-later. It is stated in four places:

| Where | What it says |
|---|---|
| UP/LICENSE.txt | Full, unmodified text of the GNU GPL, Version 3, 29 June 2007. |
| UP/README.md, section "License" | "Ancient Warfare 2.x - A mod for Minecraft, Copyright (C) 2013-2015 Shadowmage", then the standard notice: GPL "either version 3 of the License, or (at your option) any later version". |
| Source file headers | 74 of 1,377 `.java` files (5.4%) carry a header with the GPL notice. The other 1,303 have no license text. 154 of the 1,377 files are `package-info.java`; 153 of those are MCP-generated stubs, and `structure/sounds/package-info.java` is the one without the MCP comment. None carries a license. |
| RES/mcmod.info | Does not mention a license. It lists `authorList: ["shadowmage45", "P3pp3rF1y"]` for each module, an empty `credits` field, empty `url`/`updateUrl`, and descriptions that still say "Minecraft 1.7+". |

The build files UP/build.gradle and UP/gradle.properties contain no license field.

The 74 headers are two variants.

- 70 files say "Copyright 2012 John Cummens (aka Shadowmage, Shadowmage4513)" and 2 say "Copyright 2012-2013 John Cummens ...". Example, `automation/config/AWAutomationStatics.java`:

```
 Copyright 2012-2013 John Cummens (aka Shadowmage, Shadowmage4513)
 This software is distributed under the terms of the GNU General Public License.
 Please see COPYING for precise license information.

 This file is part of Ancient Warfare.

 Ancient Warfare is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
```

- 2 files carry "Copyright 2015 Olivier Sylvain (aka GotoLink)": `structure/entity/DualBoundingBox.java` and `structure/entity/RotateBoundingBox.java`.

The header refers to a file named COPYING, which does not exist in UP; the license file is LICENSE.txt.

All headers use the "version 3 or any later version" wording. The README uses the same wording. No file states "version 3 only".

No other license (Apache, MIT, LGPL, CC) is named in any Java file under SRC (grepped).

## Copyright holders and contributors

Named in the repository:

- Copyright holder in file headers: John Cummens, aka Shadowmage / shadowmage45 (2012-2013 headers, 72 files). Olivier Sylvain, aka GotoLink (2015, 2 files).
- README copyright line: "Shadowmage", 2013-2015.
- mcmod.info authors: shadowmage45, P3pp3rF1y.
- The credits document in RES also credits ThatPolishKid99 as mod ambassador and "Dan" for programming and artwork (see below). The document says only "Dan"; identifying him as CosmicDan is unverified.

`git shortlog -sne` on HEAD, 18 distinct names (some are the same person under several identities). Commit totals per person after merging obvious aliases:

| Person | Commits | Notes |
|---|---|---|
| shadowmage45 | 1,710 | Original author. History starts 2014-02-11. |
| P3pp3rF1y | 1,660 | 1,590 + 69 (`p3pp3rf1y` lowercase) + 1 (email as name). Maintainer of the 1.12 line. |
| GotoLink | 322 | Also copyright holder of two files. |
| CosmicDan / Daniel Connolly | 120 | 119 + 1. |
| WinDanesz | 160 | 88 + 71 + 1. Added the large NPC/vehicle/gate sound set. |
| ThatPolishKid99 | 37 | 29 + 5 + 3. |
| InfinityRaider | 6 | |
| Wade Wegner | 4 | |
| mcBegins2Snow (display name in Chinese) | 3 | |
| Single-commit authors | 1 each | Fro-Z, HEYMANCXN, Mazdallier, Phorcis, syamojin, tartaric_acid. |

Contributors are identified only by git names and e-mail addresses. There is no CLA, no AUTHORS or CONTRIBUTORS file, and no copyright assignment in UP. Contributors keep copyright in their own contributions; the 2012 headers were never updated to list later holders.

## Asset provenance

Counts are from RES. Git history was read for each folder; it shows who committed files, not who created them.

| Asset group | Files | Origin as documented | Gaps |
|---|---|---|---|
| Block, item, GUI, entity, model textures (`assets/ancientwarfare/textures/{blocks,items,gui,entity,model}`) | 704 png (191 blocks, 330 items, 61 entity, 118 model, 4 gui); the blocks folder also has 12 `.mcmeta` files. The per-author counts below are not re-derived for the whole folder (blocks-only check: P3pp3rF1y 30, shadowmage45 20, WinDanesz 6, ThatPolishKid99 6) | No credit document. Commit authors for the folder: shadowmage45 (73), P3pp3rF1y (79+7), WinDanesz (28), ThatPolishKid99 (6), GotoLink (5), CosmicDan (3). Source art for some is in UP/project_resources (`xcf`, `machine`, `models`, `uvmaps`), committed by shadowmage45, P3pp3rF1y, GotoLink. | Who drew each texture is not recorded. Some may derive from vanilla Minecraft textures (not checked pixel by pixel). No license stated. |
| NPC skin pack (`assets/ancientwarfare/skin_pack`) | 3,606 png plus 1 `.meta` | `AW structure building credits.docx` has a "Skin Credits" section listing 22 creator names (some entries group several people) who "donated skins or gave permission": LukyLukaz, Mullraugh, Remuz, Gleeshers, KingVigik, Tom_Clark, Ivoryyy, Asprin60, LotC Jandy, Eyhoffi, Wildflowers, Wassers, Cziboczer, Hibiki Ekko, DeusVidet, Maximr, ArthurB, Sunborn, Demicha, WildRoze, Beordan, Jujubunny. Several are Planet Minecraft members. Git: skin pack loader added by shadowmage45 on 2014-05-23; a 2019 commit by P3pp3rF1y says "Updated factions with new vampire faction + skin updates (all by Mohawky)". | Credits are by person, not by file; no file-to-author map. Permission was given to the mod, in a document; no license terms. Permission for a different mod or a redistributed port is not recorded. Hundreds of skins (monsters, "Custom_monster_puppet", "OzCowardlyLion", names suggesting existing characters) may use third-party characters. Not checked. |
| Structure templates (`assets/ancientwarfare/template/MohawkyPack1.12.2V72`) | 1,484 `.aws` files (69 subfolders of `normal/` and 6 of `towns/`, 45 folder names starting `AW`, e.g. AWNationEmpire 66 files directly in its folder (verified); AWNationNogg 49 and AWNationNorska 37 not re-checked) | The docx credits Mohawky (build team leader) for 50% of the structures, "freely granted permission to use, in perpetuity, for the Ancient Warfare 2 Mod by Shadowmage and Pepperfly". It names team builders (TheOddlySeagull, Gungnir343, Weserts555, Dan, Faenum, Silentine, Overjay, Landar, SirSquiddly, Arcuriel) and about 70 further builders by schematic title and source site (MCSchematics, Planet Minecraft, Minecraft Schematics). GotoLink gave permission for wall designs. Git: committers are P3pp3rF1y (49), shadowmage45 (16), WinDanesz (2), CosmicDan (1), p3pp3rf1y (1). | The document states permission was obtained, but the permissions themselves (messages, terms) are not in the repository. It admits some credits may be missing and that for inactive authors permission was assumed. Permission is granted to "this mod" and "AW2", not to arbitrary redistribution. Some builds derive from other mods' content (Formivore's walled city generator, Grimoire of Gaia models by Silentine, Electroblob's Wizardry link). The credits also name bundle packs that builds were adapted from (Novv, deerassassin0424, LemonFox, BlackCorsairs, BlockMasterUk, InMines, thebigbaron), the Ravendale tower series by Alegion, and structures listed as "used from Hogwarts" and "Towers used from Castle with Dragons" (jstoekm2). No per-structure author map. |
| Structure-template credits document | 1 docx (`template/AW structure building credits.docx`, 188 paragraphs, 160 non-empty) | Authoritative statement of structure and skin credits, written in Mohawky's first person. It contains a "Hey that's my structure" opt-out notice: builders can ask for credit or removal. | Not a license. It is not mentioned in README or mcmod.info. mcmod.info `credits` is empty. |
| Sound effects, NPC and entity (`assets/ancientwarfare/sounds/npc`, `events`) | 358 ogg (npc 362 files, of which 6 are bard tracks listed below, so 356 others; events 2) | No source document. Git: WinDanesz commits "added a big bunch of sfx files, assigned sounds for all entities" and "Added the optimized and registered sound files from auto_load"; shadowmage45 (3); CosmicDan (1). | Whether the sfx are original recordings, purchased, or from free libraries is not recorded. No license or author named anywhere. |
| Structure and vehicle sounds (`assets/ancientwarfarestructure/sounds`, `ancientwarfarevehicle/sounds`) | 202 + 21 ogg | Same as above (WinDanesz added gate, drawbridge, ballista, ram and vehicle sounds). | Same: no source or license recorded. |
| Bard music (`assets/ancientwarfare/sounds/npc/bard`) | 6 ogg (`drums-103`, `lord-112`, `master-122`, `suonatore_di_liuto`, `teller-130-1`, `teller-130-2`) | Source tracks are in UP/project_resources/ogg_sounds (77 entries including a `use` subfolder; the five families hold 74 files; added by shadowmage45, 2014-11-14 to 11-18) with names "Drums of the Deep" (20), "Lord of the Land" (13), "Master of the Feast" (12), "Suonatore di Liuto" (20), "Teller of the Tales" (9), plus `shades of four.wav` and `som.ogg`. | Composer, source library and license are not recorded anywhere. These read as titles of purchased or third-party stock music. Highest-risk group for redistribution. |
| Data files (`assets/ancientwarfare/{blockstates,models,lang,loot_tables,recipes,registry,research_recipes,trades}`, and `sounds.json` in ancientwarfarenpc, ancientwarfarestructure and ancientwarfarevehicle) | 862 json in ancientwarfare plus 3 sounds.json; 8 `.lang` files | Written by the mod's developers (git history of the folders: shadowmage45, P3pp3rF1y, WinDanesz, ThatPolishKid99). Source code and data fall under the repo license. Translations in `lang` are contributed (see contributors, tartaric_acid and others by e-mail only). | Lang files by translators have no per-file credit. Blockstate and model JSON may copy vanilla patterns (not checked). |
| Development resources (UP/project_resources): `xcf` (110), `models` (66, Blender and MF2 files), `machine`, `uvmaps`, `research`, `respack` (example resource pack zip) | 342 files in total (xcf 110, models 66; the rest not counted) | Working files by the devs (git author counts not re-derived; xcf is 38 shadowmage45, 2 P3pp3rF1y in its history). | Not shipped in the jar. No license note. |
| Branding and docs (UP/project_resources): logos, `patreon_logo.png`, `instruction_manual` (34 files: png screenshots, two PDF manuals, `mod info.docx`), `AW-logo.png` in RES | about 40 files | Project logo and manual by the project. | Logo author not recorded. The Patreon logo is a third-party trademark. Manuals are for 1.7.10 / 2.3-beta. |

Asset groups with no documented origin or license: block, item, GUI and model textures; all sound effects (580+ ogg across three namespaces); bard music and its source tracks; logo; translations. Documented by person but without license terms: skins and structures.

## What the license requires of a port

This is what the GPL text says; it is not legal advice.

- A 1.21.1 port is a modified version, a "work based on the Program" (GPL section 0 definitions: to "modify" a work means to copy from or adapt all or part of it). Its code stays under GPL-3.0-or-later (section 5 c: the whole work must be licensed under this License to anyone who comes into possession of a copy; section 10: no further restrictions may be imposed).
- Keep the license text and notices: each copy must carry an appropriate copyright notice, keep intact all notices stating the License applies, keep the absence-of-warranty notice, and give recipients a copy of the license (section 4 for verbatim copies, section 5 a and 5 c for modified ones, section 1 for source). In practice: keep the existing file headers, include the GPL text with the port, and keep the copyright lines of Cummens/Shadowmage and GotoLink.
- Mark changes: the modified version must "carry prominent notices stating that you modified it, and giving a relevant date" (section 5 a). Git history can serve for this, but a notice in the distributed files is the safer reading.
- Offer the source: if the port is distributed as a compiled mod jar, the "Corresponding Source" must be provided with it or by a written offer, or a network location, for the period required (section 6 a to d). This includes the build scripts used to produce the jar (section 1 definition of Corresponding Source).
- Network or "no extra restrictions": no extra terms, e.g. "All Rights Reserved", may be added to the covered code (section 10 last paragraph, section 7 on additional terms).
- Section 13 (Affero) does not apply; the license is plain GPL.
- The "or any later version" wording lets the port choose GPL-3.0-only or a later GPL for its own changes, with the original code still available as v3 or later. Choosing is a licensing decision for the owner.
- Assets. The GPL covers the "source code" and any work the license applies to; it does not give the project permission to relicense third-party assets. Whether skins, structures, sounds and music are covered by GPL, or only by their authors' permissions, is not recorded in the repository (see the table). Whether it is allowed to redistribute them in a port is a question for the owner or a lawyer.
- Section 7 permits additional permissions from copyright holders, section 13 covers combination with AGPL-3.0 works only, so it is not a general linking exception. Whether those apply is a judgment for the owner.

## Points that interact with this repository

1. `gradle.properties` (REPO root) sets `mod_license=All Rights Reserved` as the default for every mod (line 20), and it is passed to the generated mod metadata by `build-logic/src/main/groovy/mod.conventions.gradle` (line 94). REPO/README.md also says "All Rights Reserved by default". A port built from AW2 code cannot be published under those terms (GPL section 5 c, section 10). The port's own `gradle.properties` would have to override `mod_license` (a per-mod override is unverified: only `prop('mod_license')` was checked in `mod.conventions.gradle`) with `GPL-3.0-or-later`.
2. The repository itself has no LICENSE file at the root (checked: none), and `mods/` is empty at the time of this analysis. If the monorepo is published, the AW2-derived mod directory carries GPL while other mods can remain under other terms only if they are separate works.
3. The port would depend on `capttrackerlib` (`libs/capttrackerlib`). If the AW port links to the library, and the two are distributed together, the combination is a GPL concern: the library code that is part of the combined work must be available under GPL-compatible terms (GPL section 5 c, "aggregate" exception in the final paragraph of section 5 applies only to separate and independent works). The library's own license is not stated; its `gradle.properties` and `build.gradle` contain no license string, so it currently inherits the repo default "All Rights Reserved". That is not GPL-compatible. The owner has to decide whether to license the library GPL-compatibly, or keep AW-specific code out of it. Whether Java-level linking creates a combined work is a legal question; this report does not answer it.
4. The existing metadata should be carried over: authors (shadowmage45, P3pp3rF1y, and the contributors above) in `mod_authors` or a credits field. REPO `mod_authors` default is "CaptTracker" only.
5. Third-party content that already exists in `legacy/ancientwarfare` (the jar and decompiled sources) is a copy of the GPL source and carries the same obligations if redistributed.

## Open questions for the owner

1. Is the port to be published at all? If yes, will the AW2-derived mod be released under GPL-3.0-or-later, and is the repository default `All Rights Reserved` overridden for it?
2. Should `capttrackerlib` be GPL-compatibly licensed, or is code used by the AW port to be kept out of it?
3. Is the 2,000-plus-file asset set (skins, structures) to be bundled? The credits document grants permission to AW2 only, with some permissions assumed; does the owner want to contact the maintainers (P3pp3rF1y, Mohawky) or exclude those packs?
4. Who made the sound effects and the five bard music tracks, and under what license? Nothing in UP says. Should these be replaced?
5. Who made the block, item, GUI and model textures, and are any derived from vanilla Minecraft assets or other mods?
6. Are the contributors (listed above) to be contacted or credited? No CLA exists, so any relicensing beyond "GPL v3 or later" would need every contributor's agreement.
7. How should the notices be kept: preserve every existing header, add a modification notice (GPL 5 a) and where, and where will the source offer (GPL 6) live?
8. Limits of this analysis: file headers and texts were counted by search, not manually reviewed; skins and textures were not pixel-compared to vanilla or third-party originals; `.aws` templates were not opened to find embedded author metadata; the web was not consulted to confirm any third-party terms.

## Verification

Checked by a second analyst against SRC, RES, UP and REPO (read-only; scripts in `D:/Spectre Labs Master/analysis/scratch/license-verify/`).

Checked and confirmed: 1,377 `.java` files; 74 with the GPL header (70 "2012", 2 "2012-2013", 2 GotoLink); no Apache, MIT, LGPL or CC named in Java; no COPYING file in UP; all 74 headers say "any later version"; the quoted AWAutomationStatics header matches exactly; the two Olivier Sylvain files; 704 png in the five texture folders; 3,606 skin png; 1,484 `.aws`; 862 json in `assets/ancientwarfare`; 8 `.lang`; 202 and 21 structure and vehicle ogg; 6 bard files and their names; mcmod.info authors and empty credits; README license text and the "All Rights Reserved by default" line; `mod_license` at gradle.properties line 20; `mod_authors=CaptTracker`; no LICENSE file at the repo root; `mods/` empty; no license string in capttrackerlib gradle files; UP git history 2014-02-11 to 2021-08-21; shortlog counts for shadowmage45 (1,710), GotoLink (322), CosmicDan (119+1), WinDanesz (88+71+1), ThatPolishKid99 (29+5+3); the quoted "added a big bunch of sfx files, assigned sounds for all entities"; the quoted "Added the optimized and registered sound files from auto_load"; the quoted credits sentence "freely granted permission to use, in perpetuity, for the Ancient Warfare 2 Mod by Shadowmage and Pepperfly"; the 22 skin credit names; bard source track counts (20, 13, 12, 20, 9) and commit dates 2014-11-14 to 11-18.

Changed: P3pp3rF1y total 1,668 to 1,660 (1,590 + 69 + 1); MCP package-info count 153 to 154 total with 153 MCP-generated; skin creator count "about 28" to 22 names; skin-update quote replaced with the exact 2019 commit message; structure folder description (about 50 faction folders was not supported: 69 subfolders under normal/, 6 under towns/, 45 AW* folder names); structure committer list gained p3pp3rf1y (1); added omitted third-party sources (bundle packs, Ravendale, Hogwarts and Castle with Dragons references); credits docx size corrected to 188 paragraphs (160 non-empty); npc ogg count clarified (362 including 6 bard); removed sounds.json from the `assets/ancientwarfare` group (it is in the three other namespaces); bard source tracks corrected from 82 to 77 entries (74 in the five families); project_resources dev-resource count corrected (342 files total, not about 220); GPL section 13 claim corrected (it covers AGPL combination only); "Dan" identification as CosmicDan marked unverified; per-mod `mod_license` override marked unverified.

Not settled: the per-author counts for the whole textures folder (only blocks was checked); the shadowmage45 145-commit figure for project_resources; the "data_files: 11" count in the analyst's JSON, which the report does not define; the "Several are Planet Minecraft members" claim (URLs appear in the credits, membership not checked); the manuals being for 1.7.10 / 2.3-beta; the 1.21.1 `mod_license` path and any NeoForge metadata behaviour; the 136,185-line total from the brief (my recount is 134,828 lines across 1,377 files, so the brief's figure is not reproduced; the report does not state a line count). The README quote has a double space after "2013-2015" in the original; the report uses one space. No web lookups were made.
