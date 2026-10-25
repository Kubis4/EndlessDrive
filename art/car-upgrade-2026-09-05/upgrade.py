"""RGB-only material finish. No geometric transformations of production sprites."""
from pathlib import Path
import hashlib
import json
import numpy as np
from PIL import Image, ImageFilter, ImageDraw

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT/'originals'
OUTPUT = ROOT/'final'
ANCHORS = {
    'car_base_body': (0,0), 'car_seat_rear': (430,120),
    'car_seat_front': (750,85), 'car_trunk': (-15,120),
    'car_hood': (1120,153), 'car_taillight_std': (5,201),
    'car_headlight_std': (1295,258), 'car_bumper_rear_std': (-10,300),
    'car_bumper_front_fog': (1290,315), 'car_door_rear': (340,45),
    'car_door_front': (715,45), 'car_roof_rack': (442,-15),
}

def paint_mask(p):
    r,g,b,a = [p[...,i].astype(np.int16) for i in range(4)]
    return (a>8)&(r>=g)&(g>b+24)&(r>b+68)&(r>105)

def blur_luma(luma, radius):
    return np.asarray(Image.fromarray(np.uint8(np.clip(luma,0,255))).filter(ImageFilter.GaussianBlur(radius)),dtype=float)

def upgrade(image, name):
    original = np.array(image.convert('RGBA'))
    rgb = original[...,:3].astype(float)
    h,w = original.shape[:2]
    y,x = np.mgrid[:h,:w]
    dx,dy = ANCHORS.get(name,(0,0))
    gx,gy = x+dx,y+dy
    luminance = rgb @ np.array([.35,.5,.15])
    detail = np.clip(luminance-blur_luma(luminance,1.5),-16,16)
    form = np.clip(luminance-blur_luma(luminance,7),-22,22)
    # Keep the silhouette and its antialiasing colours untouched, too.
    interior = np.asarray(Image.fromarray(original[...,3]).filter(ImageFilter.MinFilter(5))) > 245
    paint = paint_mask(original)
    glass = (rgb[...,2]>rgb[...,0]*1.3)&(rgb[...,1]>rgb[...,0]*1.2)&(rgb[...,2]>35)&~paint
    delta = detail*.65 + form*.20
    if name.startswith('wheel_'):
        # Existing grooves and spokes retain their precise radii and position.
        delta = detail*.85 + form*.38
        metal = np.clip((luminance-60)/100,0,1)
        delta += metal*3 - (1-metal)*1.5
    elif 'seat' in name:
        weave = np.sin(x*np.pi/2)*np.sin(y*np.pi/2)
        delta += weave*1.0 + form*.18
    elif 'light' in name or name == 'car_bumper_front_fog':
        lens = (luminance>120)&~paint
        delta += lens*(3 + 1.6*np.cos(x*np.pi/3))
    # One lighting field in assembled car coordinates keeps separate panels
    # visually continuous. Strength is intentionally modest for the game scale.
    sheen = 8*np.exp(-((gy-(177+.016*gx))/18)**2)
    lower_shade = -10*np.exp(-((gy-348)/90)**2)
    metallic = .55*np.sin(gx*1.7+gy*.73)*np.sin(gy*2.1)
    delta += paint*(sheen+lower_shade+metallic+form*.16)
    reflection = 10*np.exp(-((gx+gy*1.8-1080)/50)**2)
    delta += glass*(reflection+3+form*.12)
    # Hue-preserving luminance change rather than moving/redrawing features.
    result = np.uint8(np.clip(np.rint(rgb+delta[...,None]),0,255))
    proposed = original.copy()
    proposed[...,:3] = result
    proposed[~interior] = original[~interior]
    # Keep the renderer's exact paint/fixed classification at every pixel.
    # Near a threshold, fall back to the original pixel instead of shifting it.
    changed_class = paint_mask(proposed) != paint
    proposed[changed_class] = original[changed_class]
    return Image.fromarray(proposed)

def assembly(folder, tint=None):
    canvas = Image.new('RGBA',(1552,610),(0,0,0,0))
    def load(name):
        im = Image.open(folder/(name+'.png')).convert('RGBA')
        if tint is not None and not ('seat' in name or 'light' in name):
            p=np.array(im); mask=paint_mask(p)
            v=p[...,:3].astype(np.int32)
            lum=np.clip(((v[...,0]*35+v[...,1]*50+v[...,2]*15)//100*1.28).astype(int),45,255)
            p[...,:3][mask]=np.uint8(lum[mask,None]*np.array(tint)/255)
            im=Image.fromarray(p)
        return im
    order=['car_seat_rear','car_seat_front','car_base_body','car_trunk','car_hood',
           'car_taillight_std','car_headlight_std','car_bumper_rear_std','car_bumper_front_fog',
           'car_door_rear','car_door_front','car_roof_rack']
    for name in order:
        dx,dy=ANCHORS[name]
        canvas.alpha_composite(load(name),(dx+40,dy+40))
    radius=round(1472*.083)
    wheel=load('wheel_std')
    # Preview only: match the game's alpha-content wheel normalization.
    wheel=wheel.crop(wheel.getbbox()).resize((radius*2,radius*2),Image.Resampling.LANCZOS)
    for fx in [.191,.828]:
        canvas.alpha_composite(wheel,(round(1472*fx)+40-radius,round(459*.913)+40-radius))
    return canvas

def main():
    OUTPUT.mkdir(exist_ok=True)
    records=[]
    for path in sorted(SOURCE.glob('*.png')):
        src=Image.open(path).convert('RGBA')
        dst=upgrade(src,path.stem)
        a,b=np.array(src),np.array(dst)
        assert src.size==dst.size
        assert np.array_equal(a[...,3],b[...,3]), path.name
        assert np.array_equal(paint_mask(a),paint_mask(b)), path.name
        assert src.getbbox()==dst.getbbox(), path.name
        dst.save(OUTPUT/path.name)
        visible=a[...,3]>0
        records.append({'file':path.name,'size':list(src.size),
            'original_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
            'alpha_sha256':hashlib.sha256(a[...,3].tobytes()).hexdigest(),
            'alpha_changed_pixels':int(np.count_nonzero(a[...,3]!=b[...,3])),
            'paint_class_changed_pixels':int(np.count_nonzero(paint_mask(a)!=paint_mask(b))),
            'visible_rgb_changed_pixels':int(np.count_nonzero(np.any(a[...,:3]!=b[...,:3],axis=-1)&visible))})
    (ROOT/'validation.json').write_text(json.dumps(records,indent=2)+'\n')
    # The production PNGs are never resized; only these review composites are.
    sheet=Image.new('RGB',(1240,1010),'#28333d'); draw=ImageDraw.Draw(sheet)
    for i,(folder,label,tint) in enumerate([(SOURCE,'ORIGINAL',None),(OUTPUT,'UPGRADE',None),(OUTPUT,'UPGRADE / BLUE PAINT',(66,126,177))]):
        draw.text((25,i*334+10),label,fill='white')
        im=assembly(folder,tint).resize((1164,458),Image.Resampling.LANCZOS)
        # Scale the whole assembled car uniformly for the review layout.
        im=im.resize((814,321),Image.Resampling.LANCZOS)
        sheet.paste(im,(200,i*334+15),im)
    sheet.save(ROOT/'assembly-review.jpg',quality=95)
    # Full-size crops make small material changes reviewable.
    details=Image.new('RGB',(1200,700),'#28333d'); d=ImageDraw.Draw(details)
    for row,name in enumerate(['car_door_front','wheel_sport']):
        for col,folder in enumerate([SOURCE,OUTPUT]):
            im=Image.open(folder/(name+'.png')).convert('RGBA')
            im.thumbnail((540,300),Image.Resampling.LANCZOS)
            x=col*600+25; y=row*350+35
            d.text((x,row*350+10),name+' / '+('ORIGINAL' if col==0 else 'UPGRADE'),fill='white')
            details.paste(im,(x,y),im)
    details.save(ROOT/'detail-review.jpg',quality=95)
    print(f'Validated {len(records)} sprites; alpha and paint classification unchanged in all.')

if __name__=='__main__':
    main()
