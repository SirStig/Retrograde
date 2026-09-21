# Retrograde

![Retrograde banner](docs/assets/banner.png)

[![Build](https://github.com/SirStig/Retrograde/actions/workflows/build.yml/badge.svg)](https://github.com/SirStig/Retrograde/actions/workflows/build.yml)
![Minecraft](https://img.shields.io/badge/minecraft-1.20.1%20%7C%2026.1%20%7C%2026.3-4c1)
![Loaders](https://img.shields.io/badge/loaders-Fabric%20%7C%20Forge%20%7C%20NeoForge-informational)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A full-screen chunk map for singleplayer worlds. See where you've been, what
biomes and ore are down there, then select chunks and regenerate them — with
undo, or by handing them off to another mod's retrogen.

Retrograde doesn't try to guess what's "missing" after you add a mod to an
existing world — that isn't knowable without re-running world generation.
Instead it shows you exactly what's already there and lets you act on it.

> **Beta, singleplayer only.** Regen rewrites chunks — back up any world you
> care about before pointing it at one.

## Features

- **Full-screen terrain map** — real top-down terrain rendered per block,
  covering every chunk you've explored, not just the area around you.
- **4 colour modes** — terrain, biome, ore density, and visited/unvisited.
- **Chunk inspector** — hover any chunk for coordinates, biome, visited
  status, and a full ore tally.
- **Find & filter** — search by scope, visited state, slime chunk, biome, or
  ore, with live match counts and one-click select.
- **Box selection** — shift-drag to select, ctrl-drag to deselect, up to 256
  chunks at once.
- **Regenerate with undo** — rewrite selected chunks through vanilla's own
  generator, with a snapshot to roll back to.
- **Mod retrogen support** — run another mod's retrogen over your selection
  without leaving the map (Mekanism supported today).
- **Ore & biome editing** *(optional, off by default)* — backfill ore from a
  freshly installed mod, or reassign a selection's biome in place.
- **Slime chunk overlay** — using vanilla's real `seedSlimeChunk` rule.

## Supported versions

| Minecraft | Loaders |
|---|---|
| 1.20.1 | Fabric, Forge |
| 26.1 | Fabric, NeoForge |
| 26.3 | Fabric, NeoForge |

Fabric builds require [Fabric API](https://modrinth.com/mod/fabric-api).

## Installation

1. Install a loader — [Fabric](https://fabricmc.net/use/),
   [Forge](https://files.minecraftforge.net/), or
   [NeoForge](https://neoforged.net/) — for a supported Minecraft version.
2. Grab the matching jar from the
   [Releases page](https://github.com/SirStig/Retrograde/releases).
3. Drop it into your `mods` folder and launch.

## Controls

| Input | Action |
|---|---|
| `O` | Open the map (rebindable) |
| Left-drag / right-drag / middle-drag | Pan |
| WASD / arrow keys | Pan |
| Scroll, `+` / `-` | Zoom |
| Click | Toggle a chunk's selection |
| Shift-drag | Box-select |
| Ctrl-drag | Box-deselect |
| Escape | Close the current panel, then the map, or step back a cancel |

## Using the map

Press **O** to open it. Terrain is drawn with a colour per block, the same
approach vanilla uses for the held map item, so what you see matches what's
actually generated. Loaded chunks are read live from memory; everything else
comes straight out of the region files, so the map covers everywhere you've
explored, not just nearby. Grey means the chunk has never generated.

Two panels sit on the left: one describes whatever chunk your cursor is over,
the other tracks your current selection. **Find** opens a filter panel next
to them for narrowing the map down to exactly the chunks you're after.

Select chunks, then open **Chunk Manipulation** to regenerate, undo, retrogen,
or — if you've switched them on in settings — edit ore and biome. Every job
runs behind a progress screen with a proper cancel ladder (`Cancel` →
`Force cancel` → `Leave anyway`) and a watchdog that gets you out if
something stalls.

Settings, including the switch for ore/biome editing, live behind the gear
icon and are saved to `config/retrograde.properties`.

## Known limitations

- Singleplayer only.
- Find only covers chunks the map has read — chunks that have never
  generated can't be filtered on.
- Regen is all-or-nothing; it can't preserve builds while redoing terrain
  around them (see [ROADMAP.md](ROADMAP.md)).
- Mekanism is the only mod-retrogen integration so far.
- Regen ores has no undo and can't remove ore.

## Building from source

This is a [Stonecutter](https://stonecutter.kikugie.dev/) project — only one
version's source tree is active at a time. Switch before building:

```sh
./gradlew "Set active project to <version>-<loader>"   # e.g. 1.20.1-fabric
./gradlew :<version>-<loader>:build
```

Run those as two separate commands — chaining them trips a Gradle
task-ordering check.

## Links

- [Issues](https://github.com/SirStig/Retrograde/issues)
- [Changelog](CHANGELOG.md)
- [Roadmap](ROADMAP.md)

## Credit

Built from [Mat0u5/MinecraftModTemplate](https://github.com/Mat0u5/MinecraftModTemplate)
(MIT). Retrograde is MIT licensed — see [LICENSE](LICENSE).
