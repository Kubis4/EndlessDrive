from pathlib import Path
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parent
a=np.array(Image.open(root/'before.png').convert('RGBA'))
# Remove the two edge-cut trees, including the old blended crown. Keep every
# complete interior tree. Join across an open woodland gap, not across foliage.
left,right=185,1560
whole_tree=a[:,200:450].copy()
gap=a[:,1325:1380].copy()
# This source gap has no tree crown and retains the low shrubs and ground.
span=2048-right+left
patch=np.array(Image.fromarray(gap).resize((span,512),Image.Resampling.LANCZOS))
# Replace the cut trees with a complete tree, surrounded by open ground.
for i in range(span):
    x=(right+i)%2048
    a[:,x]=patch[:,i]
a[:,1720:1970]=whole_tree
# The seam sits inside continuous sampled artwork. Identical edge pixels also
# survive HALF decoding without a visible colour/alpha jump.
a[:,0]=a[:,-1]
out=Image.fromarray(a)
out.save(root/'after.png')
out.save(root.parents[2]/'app/src/main/res/drawable-nodpi/bg_forest_alive_near.png')
bg=Image.new('RGBA',(4096,512),(133,158,152,255))
bg.alpha_composite(out,(0,0)); bg.alpha_composite(out,(2048,0))
bg.convert('RGB').save(root/'repeat.jpg')
assert np.array_equal(a[:,0],a[:,-1])
