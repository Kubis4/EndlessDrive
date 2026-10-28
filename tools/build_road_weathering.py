"""Bake smooth, tileable road wear. RGBA are data, never surface opacity.

R: broad deposits, G: earth/aggregate clusters, B: sparse aggregate,
A: branching fractures. No per-pixel random grain or repeating tyre stripes.
"""
from pathlib import Path
import math
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SIZE = 1024
rng = random.Random(271026)


def field(cells, blur):
    coarse = Image.new("L", (cells, cells))
    coarse.putdata([rng.randrange(16, 240) for _ in range(cells * cells)])
    wrapped = Image.new("L", (cells * 3, cells * 3))
    for y in range(3):
        for x in range(3):
            wrapped.paste(coarse, (x * cells, y * cells))
    return wrapped.resize((SIZE * 3, SIZE * 3), Image.Resampling.BICUBIC).filter(
        ImageFilter.GaussianBlur(blur)
    ).crop((SIZE, SIZE, SIZE * 2, SIZE * 2))


broad = field(9, 10)
clusters = field(43, 3)
aggregate = field(156, 0.8)
# Combine scales before thresholding: stones form irregular groups rather
# than an evenly spaced dot lattice. Mipmaps soften the distant pebbles.
pebbles = np.asarray(aggregate, dtype=np.float32) * 0.65 + np.asarray(clusters) * 0.35
aggregate = Image.fromarray(np.clip((pebbles - 105) * 2 + 105, 0, 255).astype(np.uint8))
fractures = Image.new("L", (SIZE, SIZE))
draw = ImageDraw.Draw(fractures)
for network in range(15):
    x, y = rng.uniform(0, SIZE), rng.uniform(0, SIZE)
    angle = rng.uniform(0, math.tau)
    segments = []
    for step in range(rng.randrange(4, 12)):
        angle += rng.uniform(-0.8, 0.8)
        length = rng.uniform(10, 48)
        nx, ny = x + math.cos(angle) * length, y + math.sin(angle) * length
        segments.append(((x, y), (nx, ny)))
        if rng.random() < 0.35:
            segments.append(((nx, ny), (nx + math.cos(angle + 1.2) * length, ny + math.sin(angle + 1.2) * length)))
        x, y = nx, ny
    for segment in segments:
        for ox in (-SIZE, 0, SIZE):
            for oy in (-SIZE, 0, SIZE):
                draw.line([(round(px + ox), round(py + oy)) for px, py in segment], fill=235, width=2)
fractures = fractures.filter(ImageFilter.GaussianBlur(0.5))
image = Image.merge("RGBA", (broad, clusters, aggregate, fractures))
out = Path(__file__).resolve().parents[1] / "godot/assets/textures/road-weathering.png"
image.save(out)
print(out)
