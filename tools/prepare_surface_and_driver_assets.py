"""Prepare generated road material tiles and the in-car driver sprite."""

from pathlib import Path
import shutil

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
SOURCE_OUT = ROOT / "art" / "surface-generated"
ATLAS = Path(r"C:\Users\Kubis\.codex\generated_images\01a07cc8-2390-78a1-a2af-3e5528b60b30\exec-ce373d23-4793-4323-9795-5bbd4eab9e10.png")
DRIVER = Path(r"C:\Users\Kubis\.codex\generated_images\01a07cc8-2390-78a1-a2af-3e5528b60b30\exec-4cb7cbd3-3689-41da-a0d1-00c21a6d63b3.png")
MATERIALS = [
    "asphalt", "cracks", "concrete", "dirt",
    "gravel", "sand", "snow", "ice",
    "water", "grass", "earth", "stone",
]


def seamless_tile(cell: Image.Image) -> Image.Image:
    # Mirrored quadrants guarantee matching left/right and top/bottom edges.
    # The large source crop keeps enough natural variation that the symmetry
    # disappears once the tile is projected into short terrain quads.
    core = ImageEnhance.Contrast(cell.convert("RGB")).enhance(0.88)
    core = ImageEnhance.Color(core).enhance(0.82).resize((128, 128), Image.Resampling.LANCZOS)
    tile = Image.new("RGB", (256, 256))
    tile.paste(core, (0, 0))
    tile.paste(core.transpose(Image.Transpose.FLIP_LEFT_RIGHT), (128, 0))
    tile.paste(core.transpose(Image.Transpose.FLIP_TOP_BOTTOM), (0, 128))
    tile.paste(core.transpose(Image.Transpose.FLIP_LEFT_RIGHT).transpose(Image.Transpose.FLIP_TOP_BOTTOM), (128, 128))
    rgba = tile.convert("RGBA")
    rgba.putalpha(Image.new("L", rgba.size, 104))
    return rgba


def prepare_materials() -> None:
    image = Image.open(ATLAS).convert("RGB")
    SOURCE_OUT.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ATLAS, SOURCE_OUT / "surface_material_atlas.png")
    xs = [round(i * image.width / 4) for i in range(5)]
    ys = [round(i * image.height / 3) for i in range(4)]
    for index, name in enumerate(MATERIALS):
        col, row = index % 4, index // 4
        cell = image.crop((xs[col], ys[row], xs[col + 1], ys[row + 1]))
        seamless_tile(cell).save(OUT / f"material_{name}.png", optimize=True)


def prepare_driver() -> None:
    image = Image.open(DRIVER).convert("RGBA")
    SOURCE_OUT.mkdir(parents=True, exist_ok=True)
    shutil.copy2(DRIVER, SOURCE_OUT / "driver_source.png")
    data = np.array(image)
    alpha = data[:, :, 3]
    # Remove the one-pixel generated color fringe before shrinking in game.
    alpha = np.array(Image.fromarray(alpha, "L").filter(ImageFilter.MinFilter(5)))
    data[:, :, 3] = alpha
    data[alpha == 0, :3] = 0
    image = Image.fromarray(data, "RGBA")
    bbox = image.getchannel("A").getbbox()
    if bbox is None:
        raise RuntimeError("Generated driver sprite is empty")
    pad = 12
    box = (
        max(0, bbox[0] - pad), max(0, bbox[1] - pad),
        min(image.width, bbox[2] + pad), min(image.height, bbox[3] + pad),
    )
    driver = image.crop(box)
    driver.thumbnail((640, 640), Image.Resampling.LANCZOS)
    driver.save(OUT / "car_driver.png", optimize=True)


if __name__ == "__main__":
    prepare_materials()
    prepare_driver()
