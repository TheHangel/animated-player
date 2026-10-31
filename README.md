# Animated Player

Smooth, articulated player animations with an animated face, for Minecraft 1.21.1 on NeoForge and Fabric.
A port of thehippomaster21's 1.7.10 mod.

## Layout

| Folder | Contents |
|---|---|
| `common/` | All the mod's code and resources (model, animations, layers, mixins, settings screen, textures, translations). Compiled against vanilla Minecraft only, so no loader API can slip in. |
| `neoforge/` | NeoForge entry point, NeoForge hooks (armor textures, NeoForge-only mixin), `neoforge.mods.toml`, banner. |
| `fabric/` | Fabric entry point, Mod Menu integration, `fabric.mod.json`, icon. |
| `*/src/debug/` | Dev-only test driver (`runDebugClient`): automatic scenarios with screenshots. Never in the jars. |
| `tools/` | Scripts for the skins and the translations. |
| `presentation/` | Mod page (CurseForge / Modrinth): `README.md`, animation GIFs, settings screenshots. |

The neoforge and fabric projects compile the common sources into their own jar; `common` ships nothing.

## Commands

| | |
|---|---|
| `./gradlew build` | Both jars: `neoforge/build/libs/` and `fabric/build/libs/` |
| `./gradlew :neoforge:runClient` / `:fabric:runClient` | Play in a dev client |
| `./gradlew :neoforge:runDebugClient -Pscenario=<name>` | Automatic test with screenshots (`neoforge/run/screenshots/`); scenarios: `full` (default), `skins`, `config`, `limbs`, `toggle`, `boat`, `items`, `compat`, `compare`, `icons`, `banners`, `gifs` |
| `tools/make_gifs.sh` | After `-Pscenario=gifs`: the transparent GIFs of `presentation/gifs/` (needs ffmpeg and gifsicle) |
| `python3 tools/translations.py` | Rewrite the translations from `tools/translations.py` |

Versions are set in `gradle.properties`.
