# 3D material atlas v1

Generated on 2026-10-22 with the built-in `image_gen` tool (imagegen skill), opaque output. No CLI/API fallback was used.

Production asset: `app/src/main/assets/expedition/materials-v1.png`.

The original generated atlas is preserved without pixel edits. At runtime Android extracts the four quadrants into independent 512 × 512 textures, each with its own repeat sampling and mip chain. This avoids atlas edge bleed.

## Final prompt

Create one production game texture atlas for a mobile 3D abandoned-road driving game. Square image, exact 2 by 2 grid of four equal square texture tiles, absolutely no gaps, frames, text, labels or perspective. TOP LEFT: seamless dry fine pale sand and tiny sparse pebbles, subtle ripples, restrained warm beige. TOP RIGHT: seamless forest soil with sparse tiny dry grass strands, fine earth grains and small stones, restrained neutral olive brown. BOTTOM LEFT: seamless worn asphalt, fine gray aggregate, a few hairline cracks, no road markings. BOTTOM RIGHT: seamless old painted metal neutral light gray, restrained fine scratches, tiny occasional rust freckles, no panels or seams. Each quadrant is its own edge-to-edge repeating orthographic albedo material texture. Flat uniform diffuse illumination, NO shadows, no highlights, no ambient occlusion, no dramatic lighting, no objects, no large distinctive stains. Small-scale realistic detail with restrained contrast suitable for tinting and viewing from an elevated camera. Keep left/right and top/bottom edges within each tile matching for repeat sampling. Entire atlas opaque.
