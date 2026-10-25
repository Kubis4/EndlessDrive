from pathlib import Path
from shutil import copy2

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(r"C:\Users\Kubis\AppData\Local\Temp\codex-clipboard-732fd747-aa85-42c6-ba2c-07ac466b445d.png")
RES = ROOT / "app" / "src" / "main" / "res"
BACKUP = ROOT / "art" / "ui" / "coolant-before-reference"
SIZES = {
    "drawable": 48,
    "drawable-hdpi": 36,
    "drawable-xhdpi": 48,
    "drawable-xxhdpi": 72,
}

source = np.array(Image.open(SOURCE).convert("RGB"))
red = source[:, :, 0].astype(np.int16)
green = source[:, :, 1].astype(np.int16)
blue = source[:, :, 2].astype(np.int16)
alpha = np.clip((red - np.maximum(green, blue) - 35) * 2.3, 0, 255).astype(np.uint8)
ys, xs = np.where(alpha > 8)
crop = Image.fromarray(alpha, "L").crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))

for folder, size in SIZES.items():
    target = RES / folder / "ic_telltale_coolant.png"
    backup = BACKUP / folder / target.name
    backup.parent.mkdir(parents=True, exist_ok=True)
    if not backup.exists():
        copy2(target, backup)
    margin = max(3, round(size * 0.10))
    max_w = size - margin * 2
    max_h = size - margin * 2
    scale = min(max_w / crop.width, max_h / crop.height)
    glyph = crop.resize(
        (max(1, round(crop.width * scale)), max(1, round(crop.height * scale))),
        Image.Resampling.LANCZOS,
    )
    result = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    x = (size - glyph.width) // 2
    y = (size - glyph.height) // 2
    result.putalpha(Image.new("L", (size, size), 0))
    alpha_layer = Image.new("L", (size, size), 0)
    alpha_layer.paste(glyph, (x, y))
    result.putalpha(alpha_layer)
    result.save(target)
    print(folder, target.name, result.size)
