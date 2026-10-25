from pathlib import Path
from shutil import copy2

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
RES = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
BACKUP = Path(__file__).resolve().parent / "forest-before-repaint"
GENERATED = Path(r"C:\Users\Kubis\.codex\generated_images\01a072ad-ad57-78b0-8cf8-584b7a96d570")

SOURCES = {
    "bg_forest_alive_far.png": ("exec-007d27fc-ac88-4076-af35-e2d9b6a8589f.png", (0, 130, 1774, 700), False),
    "bg_forest_alive_mid.png": ("exec-77ea06d2-ba31-496c-8a11-625b58a03e73.png", (0, 100, 1983, 596), True),
    "bg_forest_alive_near.png": ("exec-64c2be42-985a-421a-9667-f9e2fd51b3ea.png", (0, 200, 1774, 735), True),
}


def remove_checker(rgb: np.ndarray) -> np.ndarray:
    values = rgb.astype(np.float32)
    darkest = values.min(axis=2)
    chroma = values.max(axis=2) - darkest
    # The generator rendered its transparency preview into RGB using several
    # slightly different near-white checker tones. Neutral pixels brighter
    # than 225 are therefore background; colour or darkness identifies art.
    alpha = np.maximum(chroma * 4.5, (225.0 - darkest) * 3.4)
    alpha = np.clip(alpha, 0.0, 255.0)
    alpha[(chroma < 10.0) & (darkest > 220.0)] = 0.0
    alpha[alpha < 22.0] = 0.0
    return alpha.astype(np.uint8)


def equalize_edges(image: Image.Image, width: int = 72) -> Image.Image:
    data = np.array(image).astype(np.float32)
    original = data.copy()
    for distance in range(width):
        left_x = distance
        right_x = data.shape[1] - 1 - distance
        shared = (original[:, left_x] + original[:, right_x]) * 0.5
        # Smoothstep keeps the interior untouched and removes the derivative
        # jump at the wrap boundary as well as the single-column colour jump.
        t = distance / max(1, width - 1)
        amount = 1.0 - (t * t * (3.0 - 2.0 * t))
        data[:, left_x] = original[:, left_x] * (1.0 - amount) + shared * amount
        data[:, right_x] = original[:, right_x] * (1.0 - amount) + shared * amount
    data[:, -1] = data[:, 0]
    return Image.fromarray(np.clip(data, 0, 255).astype(np.uint8), image.mode)


BACKUP.mkdir(parents=True, exist_ok=True)
for name, (generated_name, crop_box, transparent) in SOURCES.items():
    target = RES / name
    backup = BACKUP / name
    if not backup.exists():
        copy2(target, backup)
    source = Image.open(GENERATED / generated_name).convert("RGB").crop(crop_box)
    if transparent:
        pixels = np.array(source)
        alpha = remove_checker(pixels)
        source = Image.fromarray(np.dstack((pixels, alpha)), "RGBA")
    else:
        source = source.convert("RGBA")
    source = source.resize((2048, 512), Image.Resampling.LANCZOS)
    source = equalize_edges(source)
    source.save(target)
    print(name, source.size, source.mode)
