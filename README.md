# Color Correct

Editor-style color grading for **Minecraft 1.21.11 (Fabric)** — a real post-processing
pipeline, inspired by the color panels of software like Lightroom, with the exact
`Color` panel layout (White Balance / Light / Creative / …).

The world is graded on the GPU every frame **before the HUD is drawn**, so your
interface, hotbar and chat stay untouched — exactly like a grading pass in a
video editor.

![icon](src/main/resources/assets/colorcorrect/icon.png)

## Features

**White Balance**
- Temperature (−100…100)
- Tint (−100…100)

**Light**
- Exposure (−4…+4 EV)
- Contrast, Highlights, Shadows, Whites, Blacks (−100…100)

**Color Wheels**
- Shadows / Midtones / Highlights wheels (drag the pin, right-click to reset)
- Split toning: shadow & highlight hue/saturation

**Creative**
- **Look** presets (shader-side looks): None, Warm Sunset, Cool Arctic, Vintage Film,
  Noir, Vivid, Faded Dream, Cyberpunk
- **Color Space**: sRGB, Rec. 709, DCI-P3, Adobe RGB (gamut + gamma emulation)
- Intensity (0–100%), Faded Film, Sharpen, Vibrance, Saturation (0–200%)

**Effects**
- Clarity (local contrast), Film Grain (animated), Vignette, Chromatic Aberration

**Presets page** — one-click looks that set the whole panel:
Neutral, Warm, Cool, Vintage Film, Noir, Vivid, Cyberpunk, Cinematic

## Controls

| Key | Action |
| --- | --- |
| `K` | Open the Color Correct panel |
| `F6` | Toggle the effect on/off |
| `C` (hold) | Compare before/after (bypasses the grade while held) |
| Mod Menu | Config button in Mods list (optional) |

Sliders: drag to set · scroll wheel for fine steps · **right-click any slider/wheel to reset**.
Changes are saved automatically (debounced) to `config/colorcorrect.json`.

## How it works

- The grade is implemented as a fragment shader following the conventions of
  Minecraft's own post-effect system (`shaders/post/*.vsh`/`.fsh`), with all
  settings packed into one `std140` uniform block (`ColorCorrectConfig`).
- It is applied through vanilla's public post-effect API
  (`PostEffectPipeline` / `PostEffectProcessor.parseEffect`): the graded image is
  written to an internal **swap** target and blitted back to `minecraft:main`,
  exactly like the vanilla blur/invert chains.
- The pass is injected with Fabric API's `HudElementRegistry` (`addFirst`), which
  runs right after the world and any vanilla effects (underwater blur, portal,
  nausea, …) are rendered and before any HUD element — no mixins required.
- The pipeline is rebuilt on config changes (the GPU program itself is cached,
  so rebuilding is cheap); when film grain is enabled the processor refreshes
  periodically to animate the grain.

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **≥ 0.19.5**
- Fabric API **≥ 0.141.6** (any recent build for 1.21.11)
- Java **21+**

## Building from source

```sh
# JDK 21 required
./gradlew build
# output: build/libs/color-correct-1.0.0.jar
```

## Compatibility notes

- Works with vanilla rendering only (no Iris/ShaderPack support yet).
- The pass runs every frame while enabled; disable it when not grading to save
  the final blit.
- Files: `src/main/resources/assets/colorcorrect/shaders/post/color_grade.fsh`
  is the heart of the grade — tweak and reload resources (F3+T) to iterate.

## License

MIT
