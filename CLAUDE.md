# Spectre Labs mods: notes for Claude

Monorepo of Minecraft mods for **NeoForge 1.21.1** (Java 21, ModDevGradle). Many are ports of 1.12.2 Forge mods.

## Conventions
- Packages: `io.github.capttracker.<modid>`. Library: `io.github.capttracker.lib`, mod id `capttrackerlib`.
- New mods are created with `scripts/new-mod.sh <modid> "<Name>"`, never by hand. Folders in `libs/` and `mods/` are auto-included by `settings.gradle`.
- Shared build logic lives only in `build-logic/src/main/groovy/mod.conventions.gradle`. Mod `build.gradle` files hold only that mod's extra dependencies.
- Versions (MC, NeoForge, Parchment) live only in the root `gradle.properties`.
- Register content through `ModRegistrar` from the library. Don't create raw `DeferredRegister`s in mods unless the registrar lacks that registry type (then add it to the library).
- Code used by 2+ mods moves into `capttrackerlib`. Bump `mod_version` in `libs/capttrackerlib/gradle.properties` when its API changes.
- When porting, keep the original mod id. Original source goes in `legacy/<modid>/` and is never built. See `docs/PORTING.md`.
- Client-only code stays in a `client` subpackage so dedicated servers don't load it.

## Verifying changes
- `./gradlew build` must pass.
- For gameplay changes, `./gradlew :<modid>:runServer` should reach `Done (` without mod errors. (Mojang auth/`server.properties` exceptions on first launch are expected.)
- Cloud sessions need the `minecraft-mods` environment, which allows the NeoForge/Parchment/Mojang hosts.
