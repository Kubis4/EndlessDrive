from pathlib import Path
from shutil import copy2

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
BACKUP = ROOT / "art" / "ui" / "coolant-before-size-alpha-tuning"
NAME = "ic_telltale_coolant.png"
SCALE = 0.90
TARGET_MEAN_ALPHA = 125.0
ALPHA_CAP = 190.0


def normalize_alpha(alpha: np.ndarray) -> np.ndarray:
    """Match the dashboard set while retaining the reference antialiasing."""
    source = alpha.astype(np.float32) / 255.0
    mask = source > 0
    if not mask.any():
        return alpha

    lo, hi = 0.08, 4.0
    for _ in range(40):
        gamma = (lo + hi) / 2.0
        adjusted = np.minimum(ALPHA_CAP, 255.0 * np.power(source[mask], gamma))
        if float(adjusted.mean()) > TARGET_MEAN_ALPHA:
            lo = gamma
        else:
            hi = gamma

    result = np.minimum(ALPHA_CAP, 255.0 * np.power(source, (lo + hi) / 2.0))
    result = np.rint(result).astype(np.uint8)
    result[(alpha > 0) & (result == 0)] = 1
    return result


for target in sorted(RES.glob(f"drawable*/{NAME}")):
    relative = target.relative_to(RES)
    backup = BACKUP / relative
    backup.parent.mkdir(parents=True, exist_ok=True)
    if not backup.exists():
        copy2(target, backup)

    # Always derive from the untouched reference mask, making this repeatable.
    source = Image.open(backup).convert("RGBA")
    source_alpha = source.getchannel("A")
    bbox = source_alpha.getbbox()
    if bbox is None:
        continue

    glyph = source_alpha.crop(bbox)
    scaled_size = (
        max(1, round(glyph.width * SCALE)),
        max(1, round(glyph.height * SCALE)),
    )
    glyph = glyph.resize(scaled_size, Image.Resampling.LANCZOS)
    glyph_alpha = normalize_alpha(np.array(glyph))

    canvas_alpha = Image.new("L", source.size, 0)
    position = (
        (source.width - glyph.width) // 2,
        (source.height - glyph.height) // 2,
    )
    canvas_alpha.paste(Image.fromarray(glyph_alpha, "L"), position)

    result = Image.new("RGBA", source.size, (255, 255, 255, 0))
    result.putalpha(canvas_alpha)
    result.save(target)
    print(relative.as_posix(), "bbox", glyph.size, "mean alpha", f"{glyph_alpha[glyph_alpha > 0].mean():.1f}")
