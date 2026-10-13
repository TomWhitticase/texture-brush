# Texture Brush

A client-side Minecraft mod (NeoForge 1.21.1) that mixes blocks for you as you build. Want a brick wall broken up with granite? Put both in your hotbar, press **G**, and build. Each placement picks the block for you, so you don't have to keep switching slots.

It only uses normal client actions (changing the selected slot, moving items in your inventory), so it works on any server, including vanilla ones, without being installed there.

## Install

1. Download `texturebrush-<version>.jar` from [Releases](../../releases) (or build it, see below).
2. Put it in your instance's `mods/` folder. It needs NeoForge 21.1.x for Minecraft 1.21.1 and has no other dependencies.

## Use

| Key (default) | Action |
|---|---|
| `G` | Turn the brush on/off |
| `Alt+G` | Add/remove the held hotbar slot from the palette |
| `Alt+P` | Next pattern |
| `Alt+L` | Next named palette (or back to hotbar mode) |

You can rebind these under **Options → Controls → Key Binds → Texture Brush**.

While the brush is on, the status line above the hotbar shows the palette, the pattern and each block's share, and green bars mark the slots in use. The brush only kicks in when you're holding one of the palette blocks, so you can still place other blocks by selecting them.

### Hotbar mode (default)
Every block in your hotbar is in the palette. To use only some of them, select a slot and press `Alt+G` to add or remove it.

Ratios are set in the config, not by stack size. `weights` holds `item=weight` entries. The default `minecraft:bricks=3` with granite at the default weight of 1 gives 75% bricks and 25% granite. Blocks with no entry use `defaultWeight`, and a weight of 0 leaves a block out.

### Named palettes
These are saved mixes such as:

```
Weathered Stone: minecraft:stone_bricks=60, minecraft:cracked_stone_bricks=25, minecraft:mossy_stone_bricks=15
```

Cycle through them with `Alt+L`. Hold any block from the palette and the brush swaps in the others:
- **Creative:** the blocks are created in your held slot, so nothing needs to be in your hotbar.
- **Survival:** the blocks are pulled from your hotbar or inventory. Blocks you don't have are skipped.

### Patterns
| Pattern | Result |
|---|---|
| **Random** | Weighted random choice on every placement |
| **Noise** | Weighted patches (size set by `noiseScale`, layout by `noiseSeed`). The same spot always gives the same block, which is handy for patching a wall later. |
| **Cycle** | Evenly interleaved order that follows the weights (3:1 → A A B A A A B A …) |
| **Checker** | Alternates by position, ignoring weights |

## Configure
Open **Mods → Texture Brush → Config** in game, or edit `config/texturebrush-client.toml`. Changes to the file apply live.

## Build
JDK 21 is required.

```sh
./gradlew build        # jar in build/libs/, runs the unit tests
./gradlew runClient    # dev client with the mod loaded
```

## Notes
- Very strict anti-cheat plugins on public servers might flag rapid slot switching. It's fine in singleplayer and on normal servers.
- MIT licensed.
