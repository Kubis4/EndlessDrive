from pathlib import Path
from PIL import Image
import numpy as np, shutil, hashlib, json
root=Path('app/src/main/res/drawable-nodpi'); archive=Path('art/backgrounds/upgrade-2026-09-06/selected-originals'); archive.mkdir(parents=True,exist_ok=True)
names=[f'bg_{t}_{l}.png' for t in ('forest','forest_alive','industry','sandstorm') for l in ('far','mid','near')]; report=[]
for n in names:
 p=root/n
 if not (archive/n).exists(): shutil.copy2(p,archive/n)
 a=np.array(Image.open(p).convert('RGBA'),dtype=np.float32); assert a.shape[:2]==(512,2048)
 l=a[:,0].copy(); r=a[:,-1].copy(); m=(l+r)*.5; t=np.linspace(0,1,2048)[None,:,None]
 a += (1-t)*(m[:,None,:]-l[:,None,:])+t*(m[:,None,:]-r[:,None,:]); a=np.clip(a,0,255)
 if not n.endswith('_far.png'): a[:64]=0
 Image.fromarray(np.uint8(a+.5),'RGBA').save(p)
 report.append({'file':n,'size':[2048,512],'edge_error':int(np.abs(a[:,0]-a[:,-1]).max()),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
Path('art/backgrounds/upgrade-2026-09-06/selected-validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8'); print('normalized',len(report))
