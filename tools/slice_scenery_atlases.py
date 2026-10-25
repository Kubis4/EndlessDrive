"""Split generated 4x2 scenery atlases and remove baked checker backgrounds."""

from __future__ import annotations

from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
SOURCE_OUT = ROOT / "art" / "scenery-generated"

ATLASES = {
    "temperate": (
        Path(r"C:\Users\Kubis\.codex\generated_images\01a07cc8-2390-78a1-a2af-3e5528b60b30\exec-280c4c36-530d-4f98-b916-ad6579d09364.png"),
        ["tree_broad_a", "tree_broad_b", "tree_pine", "tree_dead", "tree_willow", "bush_green", "reeds", "logs"],
    ),
    "roadside": (
        Path(r"C:\Users\Kubis\.codex\generated_images\01a07cc8-2390-78a1-a2af-3e5528b60b30\exec-37a7cd12-b9ed-467d-a0f5-0bc8d9112e92.png"),
        ["fence", "stone", "grass", "power_pole", "guide_post", "milestone", "container", "logs_alt"],
    ),
    "arid": (
        Path(r"C:\Users\Kubis\.codex\generated_images\01a07cc8-2390-78a1-a2af-3e5528b60b30\exec-366671f3-196b-4ca4-bcfe-11a0af8eb1c8.png"),
        ["cactus_a", "cactus_b", "bush_dry", "tree_dead_desert", "stone_sand", "stone_desert", "fence_desert", "container_desert"],
    ),
    "seasonal": (
        Path(r"C:\Users\Kubis\.codex\generated_images\01a07cc8-2390-78a1-a2af-3e5528b60b30\exec-01a3fcdb-4479-40b3-bcc7-aa6f497d4071.png"),
        ["tree_pine_snow_a", "tree_pine_snow_b", "tree_dead_snow", "bush_snow", "stone_snow", "logs_snow", "tree_autumn", "bush_autumn"],
    ),
}

SOLID_SPRITES = {"stone", "guide_post", "milestone", "stone_sand", "stone_desert", "stone_snow"}
CACTUS_SPRITES = {"cactus_a", "cactus_b"}


def checker_alpha(rgb: np.ndarray) -> np.ndarray:
    values = rgb.astype(np.int16)
    chroma = values.max(axis=2) - values.min(axis=2)
    light = values.mean(axis=2)
    # Generated checkerboards are neutral gray/white. Authored objects retain
    # color or dark line work, including the blue-gray shading on snow.
    color_alpha = np.clip((chroma - 9) * 32, 0, 255)
    dark_alpha = np.clip((110 - light) * 8, 0, 255)
    return np.maximum(color_alpha, dark_alpha).astype(np.uint8)


def keep_main_component(alpha: np.ndarray) -> np.ndarray:
    mask = alpha > 24
    h, w = mask.shape
    visited = np.zeros_like(mask, dtype=bool)
    largest: list[tuple[int, int]] = []
    for start_y, start_x in zip(*np.nonzero(mask)):
        if visited[start_y, start_x]:
            continue
        component: list[tuple[int, int]] = []
        queue: deque[tuple[int, int]] = deque([(int(start_y), int(start_x))])
        visited[start_y, start_x] = True
        while queue:
            y, x = queue.popleft()
            component.append((y, x))
            for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not visited[ny, nx]:
                    visited[ny, nx] = True
                    queue.append((ny, nx))
        if len(component) > len(largest):
            largest = component
    main = np.zeros_like(mask)
    for y, x in largest:
        main[y, x] = True
    # Recover antialiased edge pixels adjacent to the main silhouette without
    # retaining the generated checker's isolated compression speckles.
    grown = Image.fromarray((main * 255).astype(np.uint8), "L").filter(ImageFilter.MaxFilter(5))
    return np.array(grown) > 0


def fill_mask_holes(mask: np.ndarray) -> np.ndarray:
    h, w = mask.shape
    outside = np.zeros_like(mask, dtype=bool)
    queue: deque[tuple[int, int]] = deque()
    for x in range(w):
        if not mask[0, x]: queue.append((0, x))
        if not mask[h - 1, x]: queue.append((h - 1, x))
    for y in range(h):
        if not mask[y, 0]: queue.append((y, 0))
        if not mask[y, w - 1]: queue.append((y, w - 1))
    while queue:
        y, x = queue.popleft()
        if outside[y, x] or mask[y, x]:
            continue
        outside[y, x] = True
        if y: queue.append((y - 1, x))
        if y + 1 < h: queue.append((y + 1, x))
        if x: queue.append((y, x - 1))
        if x + 1 < w: queue.append((y, x + 1))
    return mask | ~outside


def repair_colors(data: np.ndarray, repair: np.ndarray, valid: np.ndarray) -> None:
    """Propagate nearby authored colors into small removed fringe holes."""
    known = valid.copy()
    pending = repair.copy()
    rgb = data[:, :, :3].astype(np.uint16)
    for _ in range(24):
        if not pending.any():
            break
        total = np.zeros_like(rgb, dtype=np.uint32)
        count = np.zeros(known.shape, dtype=np.uint16)
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            shifted_known = np.roll(known, (dy, dx), axis=(0, 1))
            shifted_rgb = np.roll(rgb, (dy, dx), axis=(0, 1))
            if dy == -1: shifted_known[-1, :] = False
            if dy == 1: shifted_known[0, :] = False
            if dx == -1: shifted_known[:, -1] = False
            if dx == 1: shifted_known[:, 0] = False
            total += shifted_rgb * shifted_known[:, :, None]
            count += shifted_known
        reached = pending & (count > 0)
        rgb[reached] = total[reached] // count[reached, None]
        known |= reached
        pending &= ~reached
    data[:, :, :3] = rgb.astype(np.uint8)


def split_atlas(key: str, source: Path, names: list[str]) -> None:
    image = Image.open(source).convert("RGBA")
    SOURCE_OUT.mkdir(parents=True, exist_ok=True)
    image.save(SOURCE_OUT / f"scenery_{key}_atlas.png")
    xs = [round(i * image.width / 4) for i in range(5)]
    ys = [round(i * image.height / 2) for i in range(3)]
    original_has_alpha = Image.open(source).mode == "RGBA" and image.getextrema()[3][0] < 255

    for index, name in enumerate(names):
        col, row = index % 4, index // 4
        sprite = image.crop((xs[col], ys[row], xs[col + 1], ys[row + 1]))
        data = np.array(sprite)
        alpha = data[:, :, 3] if original_has_alpha else checker_alpha(data[:, :, :3])
        if not original_has_alpha:
            alpha = np.where(keep_main_component(alpha), alpha, 0).astype(np.uint8)
        else:
            # Contract the generated edge by three pixels. The transparent
            # source atlas contains a saturated red/green halo outside the
            # actual silhouettes; a one-pixel contraction still left it
            # visible after Android scaled the sprites down.
            alpha = np.array(Image.fromarray(alpha, "L").filter(ImageFilter.MinFilter(7)))
            if name in CACTUS_SPRITES:
                rgb = data[:, :, :3].astype(np.int16)
                fringe = (
                    ((rgb[:, :, 0] > 160) & (rgb[:, :, 1] < 95) & (rgb[:, :, 2] < 95))
                    | ((rgb[:, :, 1] > 130) & (rgb[:, :, 0] < 95) & (rgb[:, :, 2] < 95))
                )
                core = (alpha > 20) & ~fringe
                core &= keep_main_component((core * 255).astype(np.uint8))
                silhouette = fill_mask_holes(core)
                repair = silhouette & fringe
                repair_colors(data, repair, core)
                alpha = np.where(silhouette, np.maximum(alpha, 230), 0).astype(np.uint8)
        if name in SOLID_SPRITES:
            filled = fill_mask_holes(alpha > 28)
            alpha = np.where(filled, np.maximum(alpha, 245), alpha).astype(np.uint8)
        data[:, :, 3] = alpha
        data[alpha == 0, :3] = 0
        sprite = Image.fromarray(data, "RGBA")
        bbox = sprite.getchannel("A").getbbox()
        if bbox is None:
            raise RuntimeError(f"Empty sprite: {name}")
        pad = 8
        left = max(0, bbox[0] - pad)
        top = max(0, bbox[1] - pad)
        right = min(sprite.width, bbox[2] + pad)
        bottom = min(sprite.height, bbox[3] + pad)
        sprite.crop((left, top, right, bottom)).save(OUT / f"scenery_{name}.png", optimize=True)


def main() -> None:
    for key, (source, names) in ATLASES.items():
        split_atlas(key, source, names)


if __name__ == "__main__":
    main()
