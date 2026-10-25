from pathlib import Path
from shutil import copy2
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
RES = ROOT / 'app' / 'src' / 'main' / 'res' / 'drawable-nodpi'
BACKUP = Path(__file__).resolve().parent / 'forest-edge-fix-before'
BACKUP.mkdir(parents=True, exist_ok=True)

for path in sorted(RES.glob('bg_forest_alive_*.png')):
    backup = BACKUP / path.name
    if not backup.exists():
        copy2(path, backup)
    image = Image.open(path).convert('RGBA')
    data = np.array(image)
    rgb = data[:, :, :3].copy()
    alpha = data[:, :, 3]
    # Generated foliage has a light matte in partially transparent pixels.
    # Borrow RGB from the nearest opaque pixel so the existing alpha silhouette
    # stays unchanged while compositing no longer produces white/bright halos.
    for radius in (1, 2, 3, 5):
        best = alpha.copy()
        best_rgb = rgb.copy()
        for dy in range(-radius, radius + 1):
            for dx in range(-radius, radius + 1):
                if dx == 0 and dy == 0:
                    continue
                shifted_a = np.roll(np.roll(alpha, dy, axis=0), dx, axis=1)
                take = shifted_a > best
                best[take] = shifted_a[take]
                shifted_rgb = np.roll(np.roll(rgb, dy, axis=0), dx, axis=1)
                best_rgb[take] = shifted_rgb[take]
        fringe = (alpha > 0) & (alpha < 240)
        rgb[fringe] = best_rgb[fringe]
    data[:, :, :3] = rgb
    # Remove the remaining low-alpha neutral scanlines from the generated
    # cut-outs; these are matte remnants, not part of the foliage silhouette.
    neutral_matte = (
        (alpha > 0) & (alpha < 220) &
        ((rgb.max(axis=2) - rgb.min(axis=2)) < 28) &
        (rgb.min(axis=2) > 95)
    )
    alpha[neutral_matte] = 0
    data[:, :, 3] = alpha
    data[:, -1] = data[:, 0]
    Image.fromarray(data, 'RGBA').save(path)
    print(path.name, 'ok')
