# Roadside scenery texture sources

Generated with the built-in ImageGen tool as four 4x2 sprite atlases. Every cell
was requested as one isolated, centered object with a transparent background,
no labels, no border, no frame, no road and no scenery behind it. The shared
style prompt was: painterly semi-realistic 2D side-view game art, warm natural
light, detailed but readable at mobile size, matching Endless Drive's existing
painted biome backgrounds.

- **Temperate atlas:** two broadleaf trees, pine, dead tree, willow, green bush,
  reeds and stacked logs.
- **Roadside atlas:** wooden fence, stone cluster, grass tuft, wooden utility
  pole, guidepost, milestone, metal container and a second stacked-log variant.
- **Arid atlas:** two cacti, dry bush, desert dead tree, sandstone rock, desert
  rock cluster, weathered desert fence and rusty desert container.
- **Seasonal atlas:** two snow-covered pines, snowy dead tree, snow bush, snow
  stones, snow-covered logs, autumn broadleaf tree and autumn bush.

`tools/slice_scenery_atlases.py` preserves these atlases, splits their cells,
cleans generated matte/checker artifacts and exports Android-ready transparent
PNGs into `app/src/main/res/drawable-nodpi`.
