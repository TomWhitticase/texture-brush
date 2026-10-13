# Changelog

## Fabric 1.1.0 / NeoForge 1.0.1

### Fabric
- **Brush editor** (`B`, or Mods → Texture Brush → settings). Everything is point and click: choose the mix, change weights with −/+, add blocks from a searchable grid, rename or delete saved mixes, and see a live preview wall.
- Pattern, HUD and Noise settings (patch size, shuffle) can be changed from the editor.
- The status line switches to small block icons when the block names don't fit on screen.
- Settings are now stored as structured JSON. Settings files from 1.0.0 are converted automatically.
- Cloth Config is no longer needed.
- Added a client game test that builds with the brush in a real world and checks the editor.

### Both
- Added a mod icon.
- The NeoForge jar is now named `texturebrush-neoforge-<version>.jar`.

## 1.0.0

- First release for NeoForge 1.21.1 and Fabric 26.3.
- Patterns: Random, Noise, Cycle and Checker. Hotbar mode with configurable weights, plus named palettes.
