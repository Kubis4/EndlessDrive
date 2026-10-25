from pathlib import Path
from PIL import Image, ImageEnhance
import numpy as np, shutil, hashlib, json

root=Path(__file__).resolve().parent
dest=Path('app/src/main/res/drawable-nodpi')
dest.mkdir(parents=True,exist_ok=True)
backup=root/'originals'; final=root/'final'; backup.mkdir(exist_ok=True); final.mkdir(exist_ok=True)
jobs=[('bg_forest_far.png','exec-624d4c69-0301-4b3d-91fc-e38da2b8e60d.png',210,0.94),
      ('bg_forest_alive_far.png','exec-a35b66aa-ade7-4d49-b8d1-68cbbdecdcfa.png',260,0.98)]
for name,src,y,contrast in jobs:
    target_path=dest/name
    if not (backup/name).exists(): shutil.copy2(target_path,backup/name)
    im=Image.open(root.parent.parent/'..'/'generated_images' if False else Path('C:/Users/Kubis/.codex/generated_images/01a072ad-ad57-78b0-8cf8-584b7a96d570')/src).convert('RGB')
    w,h=im.size; crop_h=min(h,w//4); y=min(max(0,y),h-crop_h)
    im=im.crop((0,y,w,y+crop_h)).resize((2048,512),Image.Resampling.LANCZOS)
    im=ImageEnhance.Contrast(im).enhance(contrast)
    a=np.asarray(im,dtype=np.uint8).copy()
    # Gently equalize the two edge columns over the whole tile. This avoids
    # a narrow vertical blend band while making the repeated tile continuous.
    left=a[:,0].astype(np.float32); right=a[:,-1].astype(np.float32)
    edge_target=(left+right)*.5
    t=np.linspace(0,1,a.shape[1])[None,:,None]
    arr=a.astype(np.float32)
    arr += (1-t)*(edge_target[:,None,:]-left[:,None,:]) + t*(edge_target[:,None,:]-right[:,None,:])
    a=np.uint8(np.clip(arr,0,255))
    if name == 'bg_forest_alive_far.png':
        # The generated far plate contained a one-pixel atmospheric scanline;
        # interpolate across it so it cannot become a horizontal band in-game.
        top, bottom = 201, 217
        for yy in range(top, bottom):
            u=(yy-top)/(bottom-top)
            a[yy]=np.uint8(a[top-1]*(1-u)+a[bottom]*u+0.5)
    out=Image.fromarray(a,'RGB'); out.save(final/name); out.save(target_path)
    assert out.size==(2048,512)
    print(name,hashlib.sha256(target_path.read_bytes()).hexdigest())
(root/'manifest.json').write_text(json.dumps({'assets':[n for n,_,_,_ in jobs],'size':[2048,512],'backups':str(backup),'source':'imagegen style transfer; crop and edge blend'},indent=2),encoding='utf-8')
