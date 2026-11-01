# Exports the plants the game draws in thousands from the foliage pack and the
# sunflower OBJs to GLB (geometry only; build_foliage.gd puts the textures on).
# Run: blender -b FoliagePack.blend --python offroad/export_foliage.py -- OUT_DIR OBJ_DIR
import os
import sys

import bpy

args = sys.argv[sys.argv.index("--") + 1:]
out = args[0]
obj_dir = args[1]
os.makedirs(out, exist_ok=True)

# Object name -> ratio kept by decimation (1: as it is).
PACK = {
    "Yarrow": 0.5, "BlanketFlower": 0.7, "Beard Tongue": 0.4,
    "WheateGrass_Sprig_01": 1.0, "WheateGrass_Sprig_02": 1.0, "WheateGrass_Sprig_03": 1.0,
    "BuffaloGrass_Sprig_01": 1.0, "BuffaloGrass_Sprig_02": 1.0,
    "LongGrass_01": 1.0, "LongGrass_02": 1.0, "LongGrass_03": 1.0,
    "Phlox_Sprig_01": 1.0, "Phlox_Sprig_02": 1.0,
    "Kinnikinnick_Sprig": 0.5, "EarleafBellflower_Sprig_01": 0.6,
}


def export(obj, name, ratio):
    bpy.ops.object.select_all(action="DESELECT")
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    if ratio < 1.0:
        modifier = obj.modifiers.new("reduce", "DECIMATE")
        modifier.ratio = ratio
    bpy.ops.export_scene.gltf(filepath=os.path.join(out, name + ".glb"), use_selection=True, export_apply=True,
                              export_materials="NONE", export_animations=False, export_yup=True)
    depsgraph = bpy.context.evaluated_depsgraph_get()
    mesh = obj.evaluated_get(depsgraph).data
    print("FOLIAGE %-30s tris=%d size=%s" % (name, sum(len(p.vertices) - 2 for p in mesh.polygons), tuple(round(d, 2) for d in obj.dimensions)))


# Every object into view, or the exporter's selection misses those hidden.
for collection in bpy.data.collections:
    collection.hide_viewport = False
    collection.hide_render = False
for layer_collection in bpy.context.view_layer.layer_collection.children:
    layer_collection.exclude = False
    layer_collection.hide_viewport = False
scene_objects = bpy.context.scene.collection.all_objects
for name, ratio in PACK.items():
    obj = bpy.data.objects[name]
    if obj.name not in scene_objects:
        bpy.context.scene.collection.objects.link(obj)
    obj.hide_set(False)
    obj.hide_viewport = False
    export(obj, name.replace(" ", "_").lower(), ratio)

for i in range(1, 6):
    bpy.ops.wm.obj_import(filepath=os.path.join(obj_dir, "Sunflower_0%d.obj" % i), forward_axis="NEGATIVE_Z", up_axis="Y")
    obj = bpy.context.selected_objects[0]
    export(obj, "sunflower_0%d" % i, 0.3)
print("FOLIAGE done")
