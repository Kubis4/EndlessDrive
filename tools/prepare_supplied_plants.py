"""Pack the supplied FBX textures; retain their alpha masks and repeatable UVs."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / "godot"
SOURCE = ROOT / "art/source_plants"
OUT = ROOT / "assets/models/vegetation"
OUT.mkdir(parents=True, exist_ok=True)
LAYERS = ["plants_0.png", "plants_1.png", "plants_2.png", "plants_3.png",
          "plants_4.png", "plants_5.png", "plants_6.png", "plants_9.png",
          "plants_10.png", "plants_18.png", "plants_19.png"]
atlas = Image.new("RGBA", (1024, 1024))
images = [Image.open(SOURCE / "shapespark" / name).convert("RGBA") for name in LAYERS]
images.append(Image.open(SOURCE / "calocedrus/CL04_Bark01_dif_su.png").convert("RGBA"))
leaves = Image.open(SOURCE / "calocedrus/CL04_Leaf01Front_dif_su.png").convert("RGBA")
leaves.putalpha(Image.open(SOURCE / "calocedrus/CL04_Leaf01_alp_su.png").convert("L"))
images.append(leaves)
for index, image in enumerate(images):
    image = image.resize((240, 240), Image.Resampling.LANCZOS)
    tile = Image.new("RGBA", (256, 256))
    tile.paste(image, (8, 8))
    tile.paste(image.crop((0, 0, 1, 240)).resize((8, 240)), (0, 8))
    tile.paste(image.crop((239, 0, 240, 240)).resize((8, 240)), (248, 8))
    tile.paste(tile.crop((0, 8, 256, 9)).resize((256, 8)), (0, 0))
    tile.paste(tile.crop((0, 247, 256, 248)).resize((256, 8)), (0, 248))
    atlas.paste(tile, ((index % 4) * 256, (index // 4) * 256))
atlas.save(OUT / "plants-atlas.png")
print(f"Packed {len(images)} supplied texture layers into a 1024px atlas")
