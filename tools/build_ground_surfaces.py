"""Validate/integrate the new imagegen ground atlas; never regenerate old noise tiles.

Use --source PATH to copy a newly generated 2x2 photographic atlas unchanged.
Without --source, validate the committed image and refresh its build metadata.
There is no image-generation API call or procedural bitmap fallback here.
"""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "godot/assets/textures/ground-surfaces.png"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--source", type=Path)
args = parser.parse_args()
if args.source and args.source.resolve() != OUT.resolve():
    shutil.copyfile(args.source, OUT)
image = Image.open(OUT).convert("RGB")
width, height = image.size
if width != height or width % 2 or not 1024 <= width <= 2048:
    raise SystemExit("Expected a square 2x2 atlas, 1024..2048 pixels, with even dimensions")
half = width // 2
layers = []
for index, name in enumerate(["sand", "stone", "earth", "grass"]):
    tile = np.asarray(image.crop(((index % 2) * half, (index // 2) * half,
                                 (index % 2 + 1) * half, (index // 2 + 1) * half)), dtype=float) / 255
    luma = tile @ np.array([0.299, 0.587, 0.114])
    border_jump = max(abs(luma[:16].mean() - luma[-16:].mean()),
                      abs(luma[:, :16].mean() - luma[:, -16:].mean()))
    if border_jump > 0.08:
        raise SystemExit(f"{name}: lighting mismatch at repeat boundary ({border_jump:.3f})")
    layers.append({"name": name, "mean_luma": round(float(luma.mean()), 6),
                   "deviation": round(float(luma.std()), 6),
                   "border_mean_jump": round(float(border_jump), 6)})
metadata = {"source": "imagegen built-in; new photographic ground atlas",
            "width": width, "height": height, "layer_size": half,
            "sha256": hashlib.sha256(OUT.read_bytes()).hexdigest(), "layers": layers}
OUT.with_suffix(".json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
print(json.dumps(metadata))
