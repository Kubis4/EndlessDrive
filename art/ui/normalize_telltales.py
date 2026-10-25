from pathlib import Path
from shutil import copy2

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
BACKUP = ROOT / "art" / "ui" / "telltales-before-normalization"

names = {
    "ic_telltale_oil.png",
    "ic_telltale_temp.png",
    "ic_telltale_battery.png",
    "ic_telltale_engine.png",
    "ic_telltale_coolant.png",
    "ic_telltale_tyre.png",
    "ic_telltale_wheelspin.png",
    "ic_telltale_brake.png",
    "ic_telltale_low_beam.png",
    "ic_telltale_high_beam.png",
    "ic_telltale_roof_light.png",
}


def normalized_alpha(alpha: np.ndarray) -> np.ndarray:
    # Equalize average coverage while retaining the original antialiasing.
    # A shared cap prevents broad icons from looking overexposed at 18 dp.
    source = alpha.astype(np.float32) / 255.0
    mask = source > 0
    if not mask.any():
        return alpha
    lo, hi = 0.08, 4.0
    for _ in range(32):
        gamma = (lo + hi) / 2.0
        adjusted = np.minimum(190.0, 255.0 * np.power(source[mask], gamma))
        if float(adjusted.mean()) > 125.0:
            lo = gamma
        else:
            hi = gamma
    result = np.minimum(190.0, 255.0 * np.power(source, (lo + hi) / 2.0))
    result = np.rint(result).astype(np.uint8)
    result[(alpha > 0) & (result == 0)] = 1
    return result


for folder in sorted(RES.glob("drawable*")):
    for path in sorted(folder.glob("ic_telltale_*.png")):
        if path.name not in names:
            continue
        relative = path.relative_to(RES)
        backup = BACKUP / relative
        backup.parent.mkdir(parents=True, exist_ok=True)
        if not backup.exists():
            copy2(path, backup)
        image = Image.open(backup).convert("RGBA")
        pixels = np.array(image)
        pixels[:, :, :3] = 255
        pixels[:, :, 3] = normalized_alpha(pixels[:, :, 3])
        Image.fromarray(pixels, "RGBA").save(path)
        print(relative.as_posix())
