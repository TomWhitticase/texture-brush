# Texture Brush

A client-side Minecraft mod that mixes blocks for you as you build. Want a brick wall broken up with granite? Put both in your hotbar, press the toggle key, and build. Each placement picks the block for you, so you don't have to keep switching slots.

It only uses normal client actions (changing the selected slot, moving items in your inventory), so it works on any server, including vanilla ones, without being installed there.

## Install

Download the jar for your loader from [Releases](../../releases) (or build it, see below) and put it in your instance's `mods/` folder.

| Jar | Minecraft | Needs |
|---|---|---|
| `texturebrush-<version>.jar` | 1.21.1 (NeoForge 21.1.x) | nothing else |
| `texturebrush-fabric-<version>.jar` | 26.3 (Fabric) | Fabric API. Mod Menu is optional (adds a settings button). |

## Use

| Action | NeoForge key | Fabric key |
|---|---|---|
| Turn the brush on/off | `G` | `V` |
| Add/remove the held hotbar slot from the palette | `Alt+G` | `U` |
| Next pattern | `Alt+P` | `Y` |
| Next named palette (or back to hotbar mode) | `Alt+L` | `N` |
| Open the brush editor | (config screen) | `B` |

The Fabric keys are different because Minecraft 26.3 uses `G` for Quick Actions and its key bindings don't support Alt combos.

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

Cycle through them with the palette key (`Alt+L` / `N`). Hold any block from the palette and the brush swaps in the others:
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

### Fabric: brush editor
Press **B** in game, or use **Mods → Texture Brush → settings icon**. Everything is point-and-click, so it works well with a trackpad:
- **Top row:** turn the brush on or off, switch pattern, and show or hide the HUD. With Noise selected, there's also a patch-size slider and a **Shuffle patches** button.
- **Mixes (left):** pick *Hotbar blocks* or a saved mix. Whatever you pick is what the brush uses. **+ New** saves the blocks in your hotbar as a new mix; **Delete** asks you to confirm.
- **Blocks (middle):** each block shows its share with a bar. Use **−/+** to change its weight (shift-click for ±5) and **✕** to remove it. **+ Add block** opens a searchable grid of every block, with the ones in your inventory first. In hotbar mode, setting a weight to 0 leaves that block out.
- **Preview (right):** an 8×8 wall built with the current mix and pattern. Use **New preview** to roll a different example.

Settings are saved to `config/texturebrush.json`.

### NeoForge
Open **Mods → Texture Brush → Config**, or edit `config/texturebrush-client.toml`. Changes to the file apply live.

## Build
There are two independent Gradle builds. The pattern logic and its tests in `common/` are shared by both.

```sh
cd neoforge && ./gradlew build   # JDK 21, jar in neoforge/build/libs/
cd fabric && ./gradlew build     # Gradle itself must run on JDK 25, jar in fabric/build/libs/
./gradlew runClient              # dev client with the mod loaded (in either folder)
cd fabric && ./gradlew runClientGameTest   # opens the game, builds with the brush, screenshots the editor
```

The Fabric game test opens a real client and runs these checks:
- places a floor with the brush and checks that the blocks were mixed;
- clicks **+** in the editor and checks the weight changed;
- loads a 1.0.0-format config and checks it converts;
- saves screenshots to `fabric/build/run/clientGameTest/screenshots/`.

## Notes
- Very strict anti-cheat plugins on public servers might flag rapid slot switching. It's fine in singleplayer and on normal servers.
- MIT licensed.
