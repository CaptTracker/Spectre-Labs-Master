# Upstream state and prior art

> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Looked up on 2026-11-15 (system date of this run). GitHub and Modrinth API calls and CurseForge page reads were made on that date; anything published after it is not covered.

## Upstream branches and history

Local clone: `D:/Spectre Labs Master/upstream/AncientWarfare2` (read-only git commands only).

| Item | Value | Evidence |
|---|---|---|
| Release commit the jar was built from | `78178170b`, 2021-04-29, "Release Merge" | `git log -1 78178170b` |
| Latest commit on `origin/1.12.x-dev` | `13d570d65`, 2021-10-10, P3pp3rF1y | `git log -1 origin/1.12.x-dev` |
| Commits on `origin/1.12.x-dev` after the release | 24 (1 is a merge) | `git rev-list --count 78178170b..origin/1.12.x-dev` |
| Diff size of that range | 133 files, +1182 / -626 lines. Of these, 132 are under `src/` (31 Java, 2 JSON, 99 `.aws` structure templates) and 1 is `.github/workflows/gradle.yml`. Counts from `git diff --name-only` | `git diff --stat 78178170b origin/1.12.x-dev` |
| Default branch `origin/1.12.x` | head `446b7db2c`, 2021-08-21, "fixing comments action dep version". Its top commits are CI and comment-filter changes from the same day | `git log -1 origin/1.12.x` |
| Other remote branches | `1.12.x-vehicles`, `1.12.x-worldgen`, `2.3`, `2.4`, `2.5`, `master`. Names only; their heads were not inspected | `git branch -a` |
| Upstream repo (GitHub API) | `P3pp3rF1y/AncientWarfare2`, default branch `1.12.x`, last push 2021-11-12, GPL-3.0, 153 stars, 106 forks, 104 open issues (includes PRs) | https://api.github.com/repos/P3pp3rF1y/AncientWarfare2 |
| Tags on the repo | 55 tags (`git tag --list`). None has `1.12` in its name, so no 1.12.2 release tag is visible. The earlier claim that the last 20 tags are 1.7.10 builds was not re-checked | `git tag --list` |

Summary: upstream development on 1.12.x stopped in late 2021. The `-dev` branch is the last set of fixes and features, all from May to October 2021. The 1.12.2 jar was not rebuilt after that.

## Unreleased work on 1.12.x-dev

Commits are `git log 78178170b..origin/1.12.x-dev`, newest first. Dates are commit dates. Authors are shown as they appear in git.

| Commit | Date | Author | What it does | Worth carrying into the port | Why |
|---|---|---|---|---|---|
| 13d570d65 | 2021-10-10 | P3pp3rF1y | Fixes oversized stacks not saving in the drafting station's build resources | yes | Data bug in the structure resource list. Keep as a behaviour test |
| 072c051ac | 2021-07-17 | P3pp3rF1y | Sound blocks in placed structures now play music. Sound names in several structures fixed to match registered names. Adds `SoundBlockFixer` datafix | yes | Real bug. Also needs a data fixer for old structures |
| fc321a5c8 | 2021-07-12 | WinDanesz | Updates MoCreatures `entity_spawn_nbt.json` | no | Data for an optional MoCreatures integration. Only useful if that integration is ported |
| 47ce0a40f | 2021-07-12 | WinDanesz | Merge of `1.12.x-dev` | no | Merge commit, no content |
| a70818034 | 2021-07-12 | WinDanesz | Adds MoCreatures entity NBT tag support | no | Depends on MoCreatures, which has no known 1.21.1 build (unverified) |
| b344288d7 | 2021-07-01 | P3pp3rF1y | Build passes now place blocks that need support (torches and similar) after their support | yes | Structure placement bug |
| dd2a474e0 | 2021-06-21 | WinDanesz | Removes an old registry entry for the halberd | yes | Registry cleanup. Do not register the old id in the port |
| a12fea81b | 2021-06-21 | WinDanesz | Halberds can temporarily disable shields | maybe | Combat feature in `npc` (`ItemHalberd`). Small, but it changes gameplay |
| c78629b18 | 2021-06-12 | WinDanesz | Snow no longer covers I&F gold and silver and AW coin stacks (issue #1281) | maybe | Fix depends on Ice and Fire compat, which is itself optional |
| c8d3a3924 | 2021-06-07 | WinDanesz | Some dirt and grass blocks excluded from survival structure requirements | maybe | Balance data. Carry only if survival structures are kept |
| e9b9eeca6 | 2021-05-28 | WinDanesz | Tamed mounts and pets are no longer targeted by friendly Tribe or Nation NPCs (issue #1276) | yes | Faction targeting bug |
| cabc36ac9 | 2021-05-28 | WinDanesz | Simplifies a condition in `ConquerHelper#checkBBConquered` | no | Refactor. The port rewrites this code anyway |
| 90d40069c | 2021-05-28 | WinDanesz | Protection flag claiming now handles friendly and neutral non-passive NPCs in a structure | yes | Conquest rule bug |
| ba0c80a07 | 2021-05-28 | WinDanesz | Moves to JDK 1.11, re-adds Sonar | no | Build tooling. Target is Java 21 with ModDevGradle |
| 55bbb30e5 | 2021-05-28 | WinDanesz | Updates `gradle.yaml` | no | CI |
| f139e7665 | 2021-05-28 | WinDanesz | Updates `gradle.yml` | no | CI |
| e9fccba63 | 2021-05-28 | WinDanesz | Advanced spawner excluded from survival structure requirements | maybe | Balance data, same as c8d3a3924 |
| b4ab16255 | 2021-05-07 | P3pp3rF1y | Door placement changed to place both halves when the bottom half is placed | yes | Structure placement bug |
| 27b7f29d1 | 2021-05-07 | P3pp3rF1y | NPCs no longer walk through open iron doors (vanilla pathing issue) | yes | NPC pathing. Re-test on NeoForge pathing, because the vanilla door logic differs |
| 69b7e4323 | 2021-05-07 | P3pp3rF1y | Barrier blocks removed from templates on load, and barriers not scanned by default. Advanced spawners map to zero cost in survival | yes | Data and template-load fix. Keep the barrier-strip rule |
| a4875ef8f | 2021-05-06 | P3pp3rF1y | Gate proxy blocks removed from survival requirements. Real gate items added instead | maybe | Survival design choice. Carry if survival structures are kept |
| b5707cb86 | 2021-05-06 | P3pp3rF1y | Structure builder blocks hidden from creative tabs and JEI. Drafting station finishes at once for creative players | maybe | Small UX rule. Carry if the drafting station is ported |
| 289677678 | 2021-05-05 | P3pp3rF1y | Structure preview and compact view of survival resource needs | maybe | Feature with a sizeable GUI and render cost (`PreviewRenderer`, `StructurePreviewElement`). Port only after the GUI plan exists |
| c3b7f0605 | 2021-05-02 | P3pp3rF1y | Protection flag coordinates displayed and highlighted | maybe | Small UI fix. Carry with the protection flag work |

Count: 24 commits. yes = 9 (13d570d65, 072c051ac, b344288d7, dd2a474e0, e9b9eeca6, 90d40069c, b4ab16255, 27b7f29d1, 69b7e4323). maybe = 8 (a12fea81b, c78629b18, c8d3a3924, e9fccba63, a4875ef8f, b5707cb86, 289677678, c3b7f0605). no = 7 (fc321a5c8, a70818034, 47ce0a40f merge, cabc36ac9, ba0c80a07, 55bbb30e5, f139e7665). 9 + 8 + 7 = 24.

Diff stat note: the full range changes 133 files. Most of the line count is `.aws` structure data, so the Java changes are small.

## Existing ports or continuations

Lookup date 2026-11-15. The searches were: GitHub repository search for "ancient warfare minecraft" (sorted by updated); the GitHub forks list of `P3pp3rF1y/AncientWarfare2` (sorted newest, pages 1 and 2, 106 forks total); Modrinth search for "ancient warfare", "ancient warfare 2", "ancientwarfare", "ancient warfare 2 npc" and "ancient warfare remastered"; and two web searches for 1.20.1/1.21.1 and for 1.16.5/1.18.2/1.19.2 continuations. Repo metadata came from the GitHub API, and the CurseForge page was read with WebFetch (a summary, not a raw page).

| Project | Minecraft and loader | Completeness | License | Last activity | Who | URL |
|---|---|---|---|---|---|---|
| Ancient Warfare 2: Remastered (CurseForge) | 1.20.1; loaders Forge, Fabric, NeoForge (confirmed on CurseForge page) | Described as a community remaster restoring NPCs, automation, research, structures, vehicles, siege and compat. Not verified in game. No source repository link on the page | GPL-3.0 | Updated 2026-11-04 (CurseForge page, confirmed) | TAWILATUMER | https://www.curseforge.com/minecraft/mc-mods/ancient-warfare-2-remastered |
| hailam0314-cell/ancientwarfare-0.1.0-porting-source | 1.20.1 Forge 47.x, Java 17, needs GeckoLib 4.x | README: playable, still in active development, some behaviour differs from 1.12.2. Single "Initial commit" on the repo | GPL-3.0 | Created and pushed 2026-08-11 | hailam0314-cell (unverified identity) | https://github.com/hailam0314-cell/ancientwarfare-0.1.0-porting-source |
| Capitan-Alatriste/AncientWarfare2-reborn (claimed fork) | Claimed NeoForge 1.21.1 core migration. NOT CONFIRMED: the public repo page, read 2026-11-15 by web fetch, shows description "Ancient Warfare Mod for Minecraft 1.12.x", branch 1.12.x with 4,046 commits, MinecraftForge and no NeoForge mention. A `CORE_MIGRATION_INDEX.md` appears in the file listing, but its phase contents were not readable. Commit dates and the 2026 phase list are unverified | GPL-3.0 (page licence text) | Unverified (no date on page; GitHub API rate-limited during this check) | Capitan-Alatriste (unverified identity) | https://github.com/Capitan-Alatriste/AncientWarfare2-reborn |
| Steaf23/AncientWarfare | Fabric, Stonecutter multi-version. Repo description: "Rewrite of Ancient Warfare 2 made for minecraft 26.1 and up". Commit "added 26.3 stonecutter build" | Early. Has `aw2_data`, `blockbench` and `AncientWarfareTODO` folders. Not built or checked in game | GPL-3.0 | Created 2026-05-15, last push 2026-10-25 | Steaf23 (unverified identity) | https://github.com/Steaf23/AncientWarfare |
| Castorice1337/ancient-warfare-2-fabric | Fabric 1.21.11 scaffold (README title "Columbina") | Early. Commits add warehouse UI, courier ownership and work timing parity, legacy resource validation. 0 issues | GPL-3.0-or-later | Created 2026-04-10, last push 2026-04-12 | Castorice1337 (unverified identity) | https://github.com/Castorice1337/ancient-warfare-2-fabric |
| Hundred Years Warfare (search result only) | NeoForge 1.21.1 and 1.20.1 | Not an AW2 port. Takes worker and workstation ideas from AW2 (per search snippet) | not checked | not checked | not checked | https://www.mado-hosting.com/minecraft-mods/hundred-years-warfare |
| Ancient Warfare 2.5 (search result only) | Described as an unofficial port to modern Minecraft; versions not in the snippet | not checked | not checked | not checked | not checked | https://www.curseforge.com/minecraft/mc-mods/ancient-warfare-2-5 |
| MarvinEckhardt Modrinth projects: Ancient Warfare 3 NPCs, Ancient Warfare 3 Worksites, Age of Steam AW2 Generators | NeoForge 1.21 and 1.21.1 | Project pages describe AW3 NPCs that farm and fight. Not shown to be derived from the AW2 source (no source URL on the Modrinth record). Relationship unverified | LicenseRef-All-Rights-Reserved (code cannot be reused) | Modrinth date_modified: AW3 Worksites 2025-03-14, AW3 NPCs 2026-02-22, Age of Steam generators 2026-03-29 | MarvinEckhardt | https://modrinth.com/mod/ancient-warfare-3-npcs |
| Ancient Warfare 2: Tweaked (Modrinth) | 1.12.2 Forge | QoL and misc updates for AW2. Not a port | GPL-3.0-or-later | Updated 2025-06-09 | lumberjacksparrow | https://modrinth.com/mod/ancient-warfare-2-tweaked |
| crunkazcanbe/AW2-NPC-VisFix | 1.12.2 fix | One commit, invisible faction NPCs in large packs. Not a port | MIT | 2026-10-04 | crunkazcanbe | https://github.com/crunkazcanbe/AW2-NPC-VisFix |
| Hypocisy/AncientWarfare2, SBrown510/AncientWarfare2-v1.20.1 (forks) | Descriptions say "1.20.x" | The code is 1.12-era (last commits 2021-08-21). The 1.20 claim is not supported by the repo content. Treat as stale | GPL-3.0 | Pushed 2024-08-01 and 2024-05-23 | not checked | https://github.com/Hypocisy/AncientWarfare2 |

Further hits from the GitHub repository search (ordered by update; the API was rate-limited for later checks, so only the search listing was seen). Neither is a 1.21 port:

| Project | Notes | License | Last push | URL |
|---|---|---|---|---|
| shadowmage45/AncientWarfare | Search hit, not opened. Original author's repo; relation to 1.12.x branch not checked | NOASSERTION (per API) | 2023-03-01 (API) | https://github.com/shadowmage45/AncientWarfare |
| yuanze31/Ancient_Warfare | Bedrock Edition (BE) addon, not a Java port. Archived 2024-01-31 per the web page. No fork link on page | GPL-3.0 | Not shown on page (7 commits) | https://github.com/yuanze31/Ancient_Warfare |

Not ports, but noted to avoid confusion: the Modrinth project "Ancient Warfare 3" (`ancient-warfare`, 1.7.10, DylanLogan2581) is a different mod. AshtonatorGaming/AncientEmpires is an original "Ancient Warfare-style" mod, not a port.

Searched with no 1.16, 1.18 or 1.19 continuation found: GitHub repo search, the fork list, Modrinth and two web searches. No project was found for 1.16.5, 1.18.2 or 1.19.2. The CurseForge page (checked 2026-11-15) lists 1.20.1 with Forge, Fabric and NeoForge builds.

Upstream 1.12.x forks still getting commits in 2026 (fixes or tooling, not ports): blahthebiste/AncientWarfare2 (last commit 2026-09-29, "Fix lizardmen having no dialogue"), owen-yang08/AncientWarfare2 (2026-10-14, "Add editor task", "Add repo health workflow"). Content not reviewed.

## Contributors

From `git shortlog -sn --no-merges origin/1.12.x` (commit counts, merged identities listed separately):

| Name in git | Commits |
|---|---|
| shadowmage45 | 1648 |
| P3pp3rF1y | 1449 |
| GotoLink | 309 |
| WinDanesz | 154 |
| CosmicDan | 119 |
| p3pp3rf1y (lowercase alias of P3pp3rF1y) | 64 |
| ThatPolishKid99 | 35 |
| InfinityRaider | 6 |
| Wade Wegner | 3 |
| mc开始下雪 | 3 |
| Fro-Z, HEYMANCXN, Mazdallier, Phorcis, syamojin, tartaric_acid | 1 each |

Notes: the lowercase alias and one email-address identity (`p3pp3rf1y@gmail.com`) belong to P3pp3rF1y. The README credits Shadowmage as the original author under GPL-3.0, and the maintainer since 2019 is P3pp3rF1y. Commit counts do not measure how much code each author wrote. Credit the Modrinth and CurseForge authors separately when they are used.

## What this means for the port

1. There is no maintained 1.12 line to build on. Upstream stopped in 2021. The 1.12.2 source at `SRC` is the baseline, and the 2021 commits above are the last known fixes. Carry the nine "yes" fixes into the port as acceptance tests.
2. The NeoForge 1.21.1 fork by Capitan-Alatriste is claimed in this report but not confirmed: the public repo page read on 2026-11-15 shows 1.12.x and no NeoForge. It is on the same target, and its phase index is a useful map of the core module's dependency order. Read it for plan ideas. Do not copy code from it without a license decision (it is GPL-3.0, and so is the upstream). The monorepo has no root LICENSE file (checked on `REPO`), so the license of any code taken from GPL sources is an open question.
3. Other ports (1.20.1 Forge, Fabric 1.21.11, Fabric 26.x) target other loaders. Their architecture notes may help the plan but not their code, which is at the same license risk.
4. Asset provenance: the 1.20.1 port says its models were recreated with AI-assisted tools (MODEL_NOTICE.md in that repo). If the port needs replacement models, check this kind of notice before reusing it.
5. Do not treat the Modrinth AW3 projects as AW2 code. Their license is all rights reserved.
6. Keep the mod ids unchanged, as `docs/PORTING.md` requires. None of the community ports changed the five mod ids in a way that matters here, but this was not checked file by file.

## Open questions

1. The Remastered page was read through a summary (WebFetch, not raw HTML). Loaders (Forge, Fabric, NeoForge for 1.20.1) and date were confirmed. No source repository link was found on the page.
2. Identity of the Capitan-Alatriste, Steaf23, Castorice1337 and hailam0314-cell accounts is not established. The GitHub profiles were not checked. The Capitan-Alatriste NeoForge claim is itself unconfirmed (see the table row).
3. The 1.12.x branches `1.12.x-vehicles`, `1.12.x-worldgen`, `2.3`, `2.4`, `2.5` and `master` were not inspected. Whether any holds unreleased work is unknown.
4. The 1.12.2 release tag and its commit were not confirmed. Only 1.7.10-era tags appeared in the tail of the tag list.
5. The monorepo (`REPO`) has no root LICENSE file. Its license decides whether GPL-3.0 code from upstream or from forks can be used directly.
6. Whether the three 2021-08-21 commits on the default branch `origin/1.12.x` (CI and comment-filter) contain any code change is not checked.
7. Whether AncientWarfare 2.5 on CurseForge or Hundred Years Warfare on NeoForge is related to AW2 code is not checked.
8. The GitHub issue search found no upstream issue that asks for or discusses a port, apart from the closed questions noted here. Issue #1359 (2021-11-22, "is there will be update for new versions?") was answered "No, not at this time" by a maintainer. Only the titles and comments of listed issues were read, not the full issue history.

## Verification

Checked on 2026-11-15 by the verification pass (slice upstream-prior-art).

What was checked:
- Git (read-only, UP): release commit 78178170b (2021-04-29), origin/1.12.x-dev head 13d570d65 (2021-10-10), origin/1.12.x head 446b7db2c (2021-08-21), 24 commits in range (23 non-merge), diff 133 files +1182/-626, file-type breakdown, commit file lists for 27b7f29d1, 072c051ac, b4ab16255, the no-merge shortlog for origin/1.12.x (top entries match).
- Local claims confirmed against git: SoundBlockFixer added by 072c051ac; door logic in TemplateRuleBlockDoors touched by b4ab16255; iron-door pathing in NpcWalkNodeProcessor by 27b7f29d1; ConquerHelper and ItemHalberd in the changed file set.
- GitHub API https://api.github.com/repos/P3pp3rF1y/AncientWarfare2: 153 stars, 106 forks, 104 open issues, last push 2021-11-12, GPL-3.0, default branch 1.12.x. Confirmed.
- Modrinth API https://api.modrinth.com/v2/search?query=ancient%20warfare: AW3 Worksites and NPCs and Age of Steam generators are NeoForge 1.21/1.21.1 with LicenseRef-All-Rights-Reserved; AW2 Tweaked is 1.12.2 GPL-3.0-or-later, 2025-06-09. Confirmed. No Modrinth AW2 project for 1.16 to 1.21 was in the results.
- GitHub repo search (ancient warfare minecraft): confirmed Steaf23/AncientWarfare (GPL-3.0, 2026-10-25), crunkazcanbe/AW2-NPC-VisFix (MIT, 2026-10-04), hailam0314-cell (GPL-3.0, 2026-08-11), Castorice1337 (GPL-3.0, 2026-04-12). Missed by the analyst: shadowmage45/AncientWarfare and yuanze31/Ancient_Warfare (added above).
- CurseForge Remastered page (web fetch): 1.20.1, Forge/Fabric/NeoForge, GPL-3.0, updated 2026-11-04, author TAWILATUMER, no source link.
- Capitan-Alatriste repo page (web fetch): does not show NeoForge 1.21.1.

Corrections made:
- Diff breakdown: 99 .aws, 31 Java, 2 JSON, 1 yml (was 100 and 30).
- Tag list: 55 tags, none with 1.12 in name (the 1.7.10 claim was unchecked).
- Capitan row and summary: the NeoForge fork claim is marked unconfirmed.
- Remastered loaders confirmed; source-link gap noted.
- Modrinth dates for the AW3 projects corrected.
- Added two missed repos (shadowmage45/AncientWarfare, yuanze31/Ancient_Warfare).

Could not settle:
- GitHub API rate-limited for this client after the first few calls. Fork list (sort by newest), Capitan commit dates and phase list, and the repository search total (55) were not re-checked.
- The 2026 Steaf23, hailam0314-cell and Castorice1337 details were not re-read this pass.
- Upstream issue list (#1359 comment) and the 1.12.x-vehicles, -worldgen, 2.3 to 2.5 and master branches were not inspected.
- The 1.16, 1.18 and 1.19 search results were not repeated.
