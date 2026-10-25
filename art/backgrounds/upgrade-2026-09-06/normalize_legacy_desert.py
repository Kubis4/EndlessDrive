from __future__ import annotations

import json
import shutil
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
ASSET_DIR = ROOT / "app/src/main/res/drawable-nodpi"
BACKUP_DIR = Path(__file__).resolve().parent / "legacy-desert-before-normalization"
REPORT_PATH = Path(__file__).resolve().parent / "legacy-desert-validation.json"
NAMES = (
    "bg_desert_far.png",
    "bg_desert_mid.png",
    "bg_desert_near.png",
    "bg_desert_dusk_far.png",
    "bg_desert_dusk_mid.png",
    "bg_desert_dusk_near.png",
)
TARGET_SIZE = (2048, 512)


def crop_to_four_to_one(image: Image.Image) -> Image.Image:
    """Crop transparent vertical padding while keeping all useful artwork."""
    rgba = np.asarray(image.convert("RGBA"))
    alpha = rgba[:, :, 3]
    rows = np.flatnonzero(np.any(alpha > 8, axis=1))
    source_crop_h = round(image.width * TARGET_SIZE[1] / TARGET_SIZE[0])
    if rows.size:
        bottom = min(image.height, int(rows[-1]) + 1)
    else:
        bottom = image.height
    top = max(0, min(bottom - source_crop_h, image.height - source_crop_h))
    bottom = min(image.height, top + source_crop_h)
    return image.crop((0, top, image.width, bottom))


def decontaminate_transparent_rgb(rgba: np.ndarray) -> np.ndarray:
    """Extend solid edge colours into antialias pixels without changing alpha."""
    out = rgba.copy()
    alpha = out[:, :, 3]
    solid = alpha >= 245
    # The generated sources carry a one-to-two pixel saturated outline even
    # where alpha is almost opaque. Start colour propagation from pixels that
    # are safely inside the silhouette, rather than from that outline.
    valid = solid.copy()
    for _ in range(2):
        interior = valid.copy()
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            shifted = np.roll(valid, (dy, dx), axis=(0, 1))
            if dy < 0:
                shifted[dy:, :] = False
            elif dy > 0:
                shifted[:dy, :] = False
            if dx < 0:
                shifted[:, dx:] = False
            elif dx > 0:
                shifted[:, :dx] = False
            interior &= shifted
        valid = interior
    core = valid.copy()
    rgb = out[:, :, :3].astype(np.uint16)
    h, w = alpha.shape

    # A 16 px wave covers the generated coloured matte around every silhouette.
    for _ in range(16):
        missing = ~valid
        if not np.any(missing & (alpha > 0)):
            break
        sums = np.zeros((h, w, 3), dtype=np.uint32)
        counts = np.zeros((h, w), dtype=np.uint16)
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1),
                       (-1, -1), (-1, 1), (1, -1), (1, 1)):
            shifted_valid = np.roll(valid, (dy, dx), axis=(0, 1))
            shifted_rgb = np.roll(rgb, (dy, dx), axis=(0, 1))
            if dy < 0:
                shifted_valid[dy:, :] = False
            elif dy > 0:
                shifted_valid[:dy, :] = False
            if dx < 0:
                shifted_valid[:, dx:] = False
            elif dx > 0:
                shifted_valid[:, :dx] = False
            take = missing & shifted_valid
            sums[take] += shifted_rgb[take]
            counts[take] += 1
        new = missing & (counts > 0)
        if not np.any(new):
            break
        rgb[new] = sums[new] // counts[new, None]
        valid[new] = True

    fringe = (alpha > 0) & valid & ~core
    out[:, :, :3][fringe] = rgb[fringe].astype(np.uint8)
    out[alpha == 0, :3] = 0
    return out


def close_horizontal_seam(rgba: np.ndarray, span: int = 72) -> np.ndarray:
    """Blend only the outer margin and make the terminal columns identical."""
    out = rgba.astype(np.float32)
    h, w, _ = out.shape
    for i in range(span):
        left = i
        right = w - 1 - i
        edge_weight = 0.5 * (1.0 - i / max(1, span - 1)) ** 2
        a = out[:, left].copy()
        b = out[:, right].copy()
        avg = (a + b) * 0.5
        out[:, left] = a * (1.0 - edge_weight) + avg * edge_weight
        out[:, right] = b * (1.0 - edge_weight) + avg * edge_weight
    edge = (out[:, 0] + out[:, -1]) * 0.5
    out[:, 0] = edge
    out[:, -1] = edge
    return np.clip(np.rint(out), 0, 255).astype(np.uint8)


def main() -> None:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    report: dict[str, object] = {"targetSize": list(TARGET_SIZE), "assets": {}}

    for name in NAMES:
        source_path = ASSET_DIR / name
        backup_path = BACKUP_DIR / name
        if not backup_path.exists():
            shutil.copy2(source_path, backup_path)

        # Always derive from the untouched backup, so the script is repeatable.
        source = Image.open(backup_path).convert("RGBA")
        cropped = crop_to_four_to_one(source)
        resized = cropped.resize(TARGET_SIZE, Image.Resampling.LANCZOS)
        rgba = close_horizontal_seam(np.asarray(resized))
        rgba = decontaminate_transparent_rgb(rgba)
        terminal = np.rint(
            (rgba[:, 0].astype(np.uint16) + rgba[:, -1].astype(np.uint16)) * 0.5
        ).astype(np.uint8)
        rgba[:, 0] = terminal
        rgba[:, -1] = terminal
        Image.fromarray(rgba, "RGBA").save(source_path, optimize=True)

        alpha = rgba[:, :, 3]
        edge_error = int(np.abs(rgba[:, 0].astype(np.int16) - rgba[:, -1].astype(np.int16)).max())
        visible_rows = np.flatnonzero(np.any(alpha > 8, axis=1))
        report["assets"][name] = {
            "sourceSize": list(source.size),
            "outputSize": list(TARGET_SIZE),
            "alphaPreserved": bool(np.any(alpha < 255)),
            "visibleRows": [int(visible_rows[0]), int(visible_rows[-1])] if visible_rows.size else [],
            "maximumEdgeChannelDifference": edge_error,
        }

    REPORT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
