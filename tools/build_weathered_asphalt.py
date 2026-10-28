"""Bake quiet, seamless asphalt with sparse branching cracks, not pixel grain."""
from pathlib import Path
import math
import random
from PIL import Image, ImageDraw, ImageFilter

SIZE = 1024
rng = random.Random(5027)
coarse = Image.new("L", (16, 16))
coarse.putdata([rng.randrange(113, 124) for _ in range(256)])
# Wrap a repeated field before filtering so all edges are seamless.
repeated = Image.new("L", (48, 48))
for y in range(3):
    for x in range(3):
        repeated.paste(coarse, (x * 16, y * 16))
base = repeated.resize((SIZE * 3, SIZE * 3), Image.Resampling.BICUBIC)
base = base.filter(ImageFilter.GaussianBlur(18)).crop((SIZE, SIZE, SIZE * 2, SIZE * 2))
image = Image.merge("RGB", (base, base.point(lambda p: p + 3), base.point(lambda p: p + 2)))
cracks = Image.new("L", (SIZE, SIZE))
draw = ImageDraw.Draw(cracks)
for network in range(11):
    x, y = rng.uniform(0, SIZE), rng.uniform(0, SIZE)
    angle = rng.uniform(0, math.tau)
    paths = []
    for step in range(rng.randrange(6, 12)):
        angle += rng.uniform(-0.6, 0.6)
        length = rng.uniform(16, 44)
        nx, ny = x + math.cos(angle) * length, y + math.sin(angle) * length
        paths.append([(x, y), (nx, ny)])
        if step % 3 == 0:
            bx, by = nx + math.cos(angle + 1.1) * 42, ny + math.sin(angle + 1.1) * 42
            paths.append([(nx, ny), (bx, by)])
        x, y = nx, ny
    for path in paths:
        for ox in (-SIZE, 0, SIZE):
            for oy in (-SIZE, 0, SIZE):
                draw.line([(int(px + ox), int(py + oy)) for px, py in path], fill=195, width=1 if network % 3 else 2)
soft = cracks.filter(ImageFilter.GaussianBlur(1.3))
image.paste((89, 87, 79), mask=soft.point(lambda p: int(p * 0.6)))
image.paste((63, 65, 61), mask=cracks.filter(ImageFilter.GaussianBlur(0.35)))
# Alpha is remaining road-paint coverage, not ground transparency.
image.putalpha(soft.point(lambda p: 255 - int(p * 0.75)))
out = Path(__file__).resolve().parents[1] / "godot/assets/textures/weathered-asphalt.png"
image.save(out)
print(out)
