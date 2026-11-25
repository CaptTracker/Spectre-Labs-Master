# Handoff: porting legacy 1.12.2 mods (local session on Spectre-UAV-1)

You are taking over from a cloud session. It set up the mod repo, but it couldn't read
the legacy mod jars because they're too large to upload and only exist on this PC.
Your job: inventory and decompile the old jars, then start porting them.

## Context

- **Owner:** CaptTracker. Brand: Spectre Labs.
- **Repo:** https://github.com/CaptTracker/spectre-labs-master (branch `main`).
  It's already set up and building. Read its `CLAUDE.md`, `README.md` and `docs/PORTING.md` before doing anything else.
- **Target:** Minecraft **1.21.1**, **NeoForge 21.1.x**, **Java 21**, ModDevGradle.
  Most of the originals are **1.12.2 Forge** mods.
- **Repo layout:**
  - `libs/spectrelib/`: the shared library mod (`ModRegistrar`, `Ids`, `ItemStacks`). Every mod depends on it.
  - `mods/<modid>/`: one folder per mod. Create new ones with `scripts/new-mod.sh` (on Windows, run it from Git Bash).
  - `legacy/<modid>/`: the original source or jar, for reference only. Jars and `decompiled/` folders are git-ignored.
- **The legacy jars are in the local `Spectre Labs Master` folder** (this session's working folder).

## Step 0: Check the setup

1. Check these tools are installed: `git`, `java -version` (needs **21**), and `gh auth status`.
   If one is missing, tell the user what to install. Don't install system software yourself without asking.
2. Find out whether this folder is a clone of the repo: run `git remote -v`.
   - **It's a clone:** run `git pull`.
   - **It's not** (just a folder of jars): clone the repo **next to** this folder, or into a `repo/` subfolder.
     Then copy each jar into `legacy/<modid>/` in the clone. Don't move or delete the user's original jars.
3. Run `gradlew.bat build` (or `./gradlew build` in Git Bash) in the clone. It must pass before you change anything.

## Step 1: Take inventory (no porting yet)

For every jar, work out:

| Field | Where to find it |
|---|---|
| Mod id, name, version, MC version | `mcmod.info` in the jar, or the `@Mod(modid=...)` annotation in the decompiled main class |
| Size, and **why it's big** | List the jar's contents (`jar tf`, or open it as a zip). Look for bundled libraries (`shadow`/`shaded` packages, `META-INF/libraries`, `com/google`, `kotlin/`, `scala/`), big sounds or textures, or nested jars |
| Dependencies | `mcmod.info` `dependencies`, the `@Mod(dependencies=...)` string, and imports of other mods' APIs |
| Rough size of the code | Number of classes and lines (after decompiling) |
| What it adds | Blocks, items, tile entities, GUIs, entities, worldgen, networking, config, coremod/ASM transformers |

Record the results in **`docs/LEGACY-INVENTORY.md`** in the repo: one table row per mod, plus a short note on each.
Flag the hard cases:
- **Coremods** (`IFMLLoadingPlugin`, ASM class transformers). These have to be rewritten as **mixins** or NeoForge events.
- Mods that depend on **other mods that were never updated** past 1.12.2.
- **Shared code duplicated across several mods.** These are candidates for `spectrelib`.

## Step 2: Decompile

- Use **Vineflower** (the best Java decompiler for Minecraft mods). Download the latest `vineflower-<version>.jar` from Maven Central (`org.vineflower:vineflower`).
- For each mod, run `java -jar vineflower.jar legacy/<modid>/<mod>.jar legacy/<modid>/decompiled/`
- Code in 1.12.2 jars uses obfuscated names (`func_12345_a`, `field_6789_b`), because these were built against MCP mappings.
  Don't try to fix them up by hand. Instead, look up the modern Mojang name for each call while porting (see `docs/PORTING.md`).
  If the user finds original **source code** for a mod, always use that instead of the decompiled version.
- Assets (`assets/`) come out of the jar unchanged. Copy those, not decompiled guesses.
- **Never commit jars or decompiled code.** `.gitignore` already excludes them, so double-check `git status` before committing.

## Step 3: Agree on the order with the user

Show the user the inventory and suggest a porting order: **smallest, most self-contained mod first** (it tests the whole workflow).
Then mods that share code with it, then the large or coremod-heavy ones last. **Wait for the user to confirm before porting.**

## Step 4: Port one mod at a time

Follow `CLAUDE.md` and `docs/PORTING.md`. For each mod:

1. Create a branch, e.g. `git checkout -b port/<modid>`.
2. Run `scripts/new-mod.sh <modid> "<Mod Name>"` with the **same mod id** as the 1.12.2 original. Add `ported_from=1.12.2` to its `gradle.properties`.
3. Copy assets first, applying the folder renames in `docs/PORTING.md`
   (`textures/blocks` → `textures/block`, `.lang` → `.json`, and so on).
4. Register everything through `ModRegistrar`. Move code that two or more mods need into `spectrelib`.
5. Clean up while porting, since these mods are old:
   - Replace metadata variants with separate blocks or block states.
   - Use data components instead of item NBT.
   - Use JSON or datagen for recipes and loot.
   - Use tags instead of the ore dictionary.
   - Keep client-only code in a `client` subpackage.
6. Before calling a mod done, check:
   - `gradlew build` passes.
   - `gradlew :<modid>:runClient` launches, and the mod's content works in game. Ask the user to test anything visual.
   - `gradlew :<modid>:runServer` reaches `Done (` with no errors from the mod. This catches client-only code that would crash a dedicated server.
7. Commit (no jars and no decompiled code), push the branch, and open a pull request for the user to review.
   Write commit messages that say what was ported and anything left out or changed.

## Ground rules

- Ask the user before deleting anything outside the repo, or before installing software.
- Don't change the shared versions in the root `gradle.properties` without asking.
- If a feature can't be ported as-is (an old coremod hack, or a dependency that's gone), tell the user and suggest an alternative. Don't silently drop it.
- Keep `CLAUDE.md` up to date if you add new conventions.
