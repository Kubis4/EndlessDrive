"""Finish ImageGen plates as transparent, horizontally repeating game tiles.

Run from the repository root: python art/backgrounds/prepare_backgrounds.py
Original generated plates are preserved in sources/.
"""
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
SIZE = (2048, 512)


def extract_layer(image):
    a = np.array(image.convert('RGBA'), dtype=np.float32)
    rgb = a[:, :, :3]
    # Chroma-key only the artificial magenta; preserve the generated alpha.
    excess = np.minimum(rgb[:, :, 0], rgb[:, :, 2]) - rgb[:, :, 1]
    key = np.clip((excess - 12) / 65, 0, 1)
    a[:, :, 3] *= 1 - key
    # Neutralize low-level magenta spill retained in antialiased foliage.
    spill = np.maximum(excess, 0)
    rgb[:, :, 0] -= spill
    rgb[:, :, 2] -= spill
    # Remove saturated extraction debris, absent from the muted art palette.
    saturation = rgb.max(axis=2) - rgb.min(axis=2)
    artifact = (saturation > 155) & (a[:, :, 3] < 254)
    a[artifact, 3] = 0
    a[a[:, :, 3] < 8] = 0
    # Crop only the empty band below the terrain, keeping the sky-space above.
    coverage = (a[:, :, 3] > 128).mean(axis=1)
    occupied = np.flatnonzero(coverage > .30)
    if occupied.size:
        a = a[:occupied[-1] + 1]
    return Image.fromarray(np.uint8(np.clip(a, 0, 255)))


def repeat_tile(image):
    a = np.array(image.convert('RGBA'), dtype=np.float32) / 255
    a[:, :, :3] *= a[:, :, 3:4]
    height, width = a.shape[:2]
    overlap = max(32, width // 28)
    # Find matching quiet strips near the ends; avoid blending through trunks.
    best = None
    step = max(8, width // 100)
    for start in range(0, width // 7, step):
        for end in range(width - width // 7, width + 1, step):
            left = a[::4, start:start + overlap]
            right = a[::4, end-overlap:end]
            delta = np.abs(left-right)
            score = delta[:, :, :3].mean() + 3 * delta[:, :, 3].mean()
            if best is None or score < best[0]:
                best = (score, start, end)
    _, start, end = best
    t = np.linspace(0, 1, overlap)[None, :, None]
    t = t*t*(3-2*t)
    join = a[:, end-overlap:end]*(1-t) + a[:, start:start+overlap]*t
    out = np.concatenate([a[:, start+overlap:end-overlap], join], axis=1)
    alpha = out[:, :, 3:4]
    out[:, :, :3] = np.divide(out[:, :, :3], alpha, out=np.zeros_like(out[:, :, :3]), where=alpha > 0)
    tile = Image.fromarray(np.uint8(np.clip(out*255, 0, 255))).resize(SIZE, Image.Resampling.LANCZOS)
    pixels = np.array(tile)
    # Equal endpoints after resampling, including alpha. The join itself uses
    # neighbouring source columns, so this is only a subpixel correction.
    edge = ((pixels[:, 0].astype(np.uint16) + pixels[:, -1]) // 2).astype(np.uint8)
    pixels[:, 0] = edge
    pixels[:, -1] = edge
    pixels[pixels[:, :, 3] == 0] = 0
    return Image.fromarray(pixels)


def main():
    manifest = json.loads((ROOT/'manifest.json').read_text(encoding='utf-8'))
    report = []
    for entry in manifest:
        name = entry['name']
        source = Image.open(ROOT/'sources'/f'{name}.png')
        layer = name.rsplit('_', 1)[1]
        if layer != 'far':
            source = extract_layer(source)
        result = repeat_tile(source)
        dest = ROOT/'tiles'/f'bg_{name}.png'
        dest.parent.mkdir(parents=True, exist_ok=True)
        result.save(dest, optimize=True)
        p = np.array(result)
        assert result.size == SIZE
        assert np.array_equal(p[:, 0], p[:, -1]), name
        if layer == 'far':
            assert p[:, :, 3].min() == 255, name
        else:
            assert p[0, :, 3].max() == 0, name
            assert (p[:, :, 3] == 0).mean() > .20, name
        report.append({'name': name, 'size': list(SIZE), 'rgba': True,
                       'identical_horizontal_edges': True,
                       'transparent_fraction': round(float((p[:, :, 3] == 0).mean()), 3)})
    (ROOT/'validation.json').write_text(json.dumps(report, indent=2)+'\n')
    scenes = list(dict.fromkeys(e['name'].rsplit('_', 1)[0] for e in manifest))
    previews = ROOT/'previews'
    previews.mkdir(exist_ok=True)
    contact = Image.new('RGB', (1200, len(scenes)*224), '#e7ebee')
    draw = ImageDraw.Draw(contact)
    for index, scene in enumerate(scenes):
        layers = [Image.open(ROOT/'tiles'/f'bg_{scene}_{layer}.png').convert('RGBA') for layer in ('far','mid','near')]
        composite = layers[0].copy()
        for layer, factor in zip(layers[1:], (.68, .78)):
            # Independent bottom-anchored parallax bands; leave distant terrain visible.
            band = layer.resize((2048, int(512*factor)), Image.Resampling.LANCZOS)
            composite.alpha_composite(band, (0, 512-band.height))
        composite.convert('RGB').save(previews/f'{scene}.jpg', quality=94)
        doubled = Image.new('RGB', (4096, 512))
        doubled.paste(composite, (0, 0)); doubled.paste(composite, (2048, 0))
        doubled.save(previews/f'{scene}_repeat.jpg', quality=92)
        draw.text((16,index*224+7), scene.replace('_',' ').upper(), fill='#253745')
        contact.paste(composite.resize((800,200)), (0,index*224+24))
        contact.paste(doubled.resize((400,100)), (800,index*224+74))
    contact.save(previews/'all_backgrounds.jpg', quality=94)
    print(f'Validated {len(report)} RGBA tiles: 2048x512, equal horizontal edges, correct alpha.')


if __name__ == '__main__':
    main()
