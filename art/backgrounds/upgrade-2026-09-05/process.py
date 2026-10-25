"""Normalize generated parallax layers and close their horizontal wrap in premultiplied RGBA.

Run with the bundled Python runtime. Sources and generation prompts are in manifest.json.
Original project assets are preserved once under originals/ before installation.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parents[2]
DEST = PROJECT / 'app/src/main/res/drawable-nodpi'
SIZE = (2048, 512)

def normalize(path, name):
    im = Image.open(path).convert('RGBA')
    if not name.endswith('_far'):
        raw = np.array(im)
        if raw[:64,:,3].mean() > 250:
            # Some generated outputs bake a nearly white checkerboard into RGB.
            # Its neutral bright pixels are distinct from every biome's palette.
            rgb = raw[...,:3].astype(float)
            minimum = rgb.min(axis=-1)
            chroma = np.ptp(rgb, axis=-1)
            alpha = np.clip((240-minimum)/75, 0, 1)
            alpha *= np.where(minimum > 180, np.clip((chroma-6)/28,0,1), 1)
            # Remove the light matte on the very narrow antialiased contour.
            corrected = (rgb - 247*(1-alpha[...,None])) / np.maximum(alpha[...,None], .001)
            raw[...,:3] = np.uint8(np.clip(corrected,0,255))
            raw[...,3] = np.uint8(alpha*255)
            raw[raw[...,3]==0] = 0
            im = Image.fromarray(raw)
        else:
            raw[raw[...,3]<24] = 0
            im = Image.fromarray(raw)
        # Generated sprites sometimes include a transparent margin below the
        # ground strip; the production tile must end in that solid ground.
        occupied = (np.array(im)[...,3]>128).sum(axis=1)
        bottom = int(np.flatnonzero(occupied > im.width*.5)[-1])+1
        im = im.crop((0,0,im.width,bottom))
        # Restore the reference silhouette's vertical occupancy by cropping only
        # redundant transparent space above the generated artwork.
        reference = np.array(Image.open(ROOT/'originals'/f'bg_{name}.png').convert('RGBA'))
        def skyline(a):
            return int(np.flatnonzero((a[...,3]>128).sum(axis=1)>max(4,a.shape[1]*.005))[0])
        target = skyline(reference)/reference.shape[0]
        top = skyline(np.array(im))
        crop_height = round((im.height-top)/max(.1,1-target))
        im = im.crop((0, im.height-crop_height, im.width, im.height))
    if name in ('desert_far', 'sandstorm_far'):
        # Keep the circular sun circular while adapting the generator's 2:1
        # canvas to 4:1. Compress empty upper sky, retain the horizon position.
        src = np.array(im)
        ys = np.interp(np.linspace(0,1,im.height), [0,.1,.5,1], [0,.3,.5,1])*(im.height-1)
        lo = ys.astype(int); hi = np.minimum(lo+1,im.height-1)
        t = (ys-lo)[:,None,None]
        im = Image.fromarray(np.uint8(src[lo]*(1-t)+src[hi]*t))
    im = im.resize(SIZE, Image.Resampling.LANCZOS)
    a = np.asarray(im, dtype=np.float32) / 255
    if name.endswith('_far'):
        assert a[..., 3].min() == 1, f'{name}: far layer is not opaque'
    else:
        assert a[:64, :, 3].max() == 0, f'{name}: upper sky must be transparent'
    a[..., :3] *= a[..., 3:4]
    if not name.endswith('_far'):
        # Restore an opaque ground baseline under any generated alpha fringe.
        floor = a[-20:]
        solid = floor[...,3] > .95
        color = np.median(floor[...,:3][solid],axis=0)
        coverage = np.linspace(0,1,12)[:,None,None]
        missing = (1-a[-12:,:,3:4])*coverage
        a[-12:,:,:3] += color*missing
        a[-12:,:,3:4] += missing
    # End-to-start overlap: the final join uses neighbouring source columns.
    overlap = 96
    w = SIZE[0] - overlap
    t = np.linspace(0, 1, overlap)[None, :, None]
    t = t * t * (3 - 2 * t)
    out = a[:, :w].copy()
    out[:, :overlap] = a[:, w:] * (1 - t) + a[:, :overlap] * t
    # Resize premultiplied channels separately to avoid dark transparent fringes.
    out = np.stack([np.asarray(Image.fromarray(out[..., c]).resize(SIZE, Image.Resampling.BICUBIC)) for c in range(4)], axis=-1)
    out = np.clip(out, 0, 1)
    edge = (out[:, 0] + out[:, -1]) / 2
    out[:, 0] = out[:, -1] = edge
    out[..., :3] /= np.maximum(out[..., 3:4], 1 / 65535)
    out[out[..., 3] < 0.5 / 255] = 0
    return Image.fromarray(np.uint8(np.clip(out * 255 + .5, 0, 255)))

def main():
    manifest = json.loads((ROOT / 'manifest.json').read_text(encoding='utf-8'))
    originals = ROOT / 'originals'
    originals.mkdir(exist_ok=True)
    final = ROOT / 'final'
    final.mkdir(exist_ok=True)
    report = []
    for item in manifest:
        name = item['name']
        filename = f'bg_{name}.png'
        backup = originals / filename
        if not backup.exists():
            backup.write_bytes((DEST / filename).read_bytes())
        im = normalize(item['source'], name)
        im.save(final / filename)
        p = np.asarray(im)
        assert np.array_equal(p[:, 0], p[:, -1]), name
        assert im.size == SIZE
        assert p[-1,:,3].min() == 255, f'{name}: ground must reach bottom'
        report.append({'name': name, 'size': list(im.size), 'edge_rgba_max_error': int(np.abs(p[:, 0].astype(int)-p[:, -1]).max()), 'top_alpha_max': int(p[0,:,3].max()), 'bytes': (final / filename).stat().st_size})
    (ROOT / 'validation.json').write_text(json.dumps(report, indent=2)+'\n')
    # Visual proof: composed biomes and two neighbouring copies around the wrap.
    themes = list(dict.fromkeys(i['name'].rsplit('_', 1)[0] for i in manifest))
    sheet = Image.new('RGB', (1536, len(themes)*220), '#182025')
    d = ImageDraw.Draw(sheet)
    for row, theme in enumerate(themes):
        paths = [final / f'bg_{theme}_{layer}.png' for layer in ('far','mid','near')]
        if not all(p.exists() for p in paths):
            continue
        composite = Image.open(paths[0]).convert('RGBA')
        for path in paths[1:]:
            composite = Image.alpha_composite(composite, Image.open(path).convert('RGBA'))
        tile = composite.resize((768,192), Image.Resampling.LANCZOS).convert('RGB')
        d.text((12,row*220+5),theme,fill='white')
        sheet.paste(tile,(0,row*220+25)); sheet.paste(tile,(768,row*220+25))
    sheet.save(ROOT / 'tile-review.jpg', quality=93)
    print(json.dumps(report, indent=2))

if __name__ == '__main__':
    main()
