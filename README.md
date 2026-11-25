# Spectre Labs — Minecraft Mods

All of CaptTracker's Minecraft mods in one repo, including ports of old 1.12.2 mods.

| | |
|---|---|
| Minecraft | **1.21.1** |
| Loader | **NeoForge 21.1.x** |
| Java | **21** |
| Build | Gradle + [ModDevGradle](https://github.com/neoforged/ModDevGradle) |

## Layout

```
libs/spectrelib/   Shared library mod. Every other mod depends on it.
mods/<modid>/          One folder per mod (new mods and ports).
legacy/<modid>/        Original 1.12.2 source, for reference while porting. Not built.
templates/mod/         Starting point used by scripts/new-mod.sh.
build-logic/           Shared Gradle setup (mod.conventions) used by every mod.
docs/                  Porting notes and conventions.
```

Each folder in `libs/` or `mods/` that has a `build.gradle` is picked up automatically.

## Common commands

Make a new mod:

```bash
scripts/new-mod.sh rubytools "Ruby Tools"
```

Build everything (jars end up in each mod's `build/libs/`):

```bash
./gradlew build
```

Build one mod:

```bash
./gradlew :rubytools:build
```

Play-test a mod in a dev client (the library loads automatically):

```bash
./gradlew :rubytools:runClient
```

Generate data (models, recipes, loot tables) from datagen code:

```bash
./gradlew :rubytools:runData
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Where settings live

- **Root `gradle.properties`**: Minecraft/NeoForge/Parchment versions, author, license. Change once, applies to every mod.
- **`mods/<modid>/gradle.properties`**: that mod's id, name, version and description.
- **`mods/<modid>/build.gradle`**: extra dependencies for that mod only.

## The shared library (`spectrelib`)

Code goes in the library once **two or more mods** need it. It currently has:

- `ModRegistrar`: one object per mod that holds the block, item, block-entity, menu, sound, data-component and creative-tab registries, and fills the creative tab automatically.
- `Ids`: short `ResourceLocation` helpers.
- `ItemStacks`: common item helpers (`giveOrDrop`, component-aware equality).

## IDE setup

Open the repo folder in IntelliJ IDEA and import it as a Gradle project. Each mod gets its own run configurations (`Client`, `Server`, `Data`).

## License

All Rights Reserved by default (`mod_license` in `gradle.properties`). Change it there if you want mods to be open source.
