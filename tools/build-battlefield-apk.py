"""Stage an isolated Android demo; leave the main game's export/settings intact."""
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
source = root / 'godot'
stage = root / 'build' / 'battlefield-android'
stage.mkdir(parents=True, exist_ok=True)
for folder in ['assets', 'desert_battlefield', 'shaders']:
    shutil.copytree(source / folder, stage / folder, dirs_exist_ok=True)
(stage / 'scripts').mkdir(exist_ok=True)
shutil.copy2(source / 'scripts/vehicle_catalog.gd', stage / 'scripts/vehicle_catalog.gd')
shutil.copy2(source / 'icon.svg', stage / 'icon.svg')
shutil.copytree(source / '.godot/imported', stage / '.godot/imported', dirs_exist_ok=True)
config = (source / 'project.godot').read_text(encoding='utf-8')
config = config.replace('config/name="Endless Drive"', 'config/name="Battlefield Demo"')
config = config.replace('run/main_scene="res://scenes/boot.tscn"', 'run/main_scene="res://desert_battlefield/desert_battlefield.tscn"')
config = config.replace('"gl_compatibility"', '"mobile"').replace('"GL Compatibility"', '"Mobile"')
config += '\n[input_devices]\n\npointing/emulate_mouse_from_touch=true\n\n'
(stage / 'project.godot').write_text(config, encoding='utf-8')
(stage / 'export_presets.cfg').write_text('''[preset.0]
name="Battlefield Android"
platform="Android"
runnable=true
export_filter="resources"
export_files=PackedStringArray("res://desert_battlefield/desert_battlefield.tscn", "res://assets/models/jeep/jeep_chassis.res", "res://assets/models/izh/izh_chassis.res", "res://scripts/vehicle_catalog.gd", "res://desert_battlefield/battlefield.gd", "res://desert_battlefield/vehicle.gd", "res://desert_battlefield/soil.gd", "res://desert_battlefield/terrain.gd", "res://desert_battlefield/terrain_chunks.gd", "res://desert_battlefield/track_trail.gd", "res://desert_battlefield/mobile_controls.gd", "res://desert_battlefield/dust.gdshader", "res://desert_battlefield/grain.gdshader", "res://desert_battlefield/sand.gdshader", "res://desert_battlefield/tracks.gdshader")
include_filter="assets/models/jeep/ATTRIBUTION.md,assets/ASSET_LICENSES.md,desert_battlefield/*.gdshaderinc"
exclude_filter="desert_battlefield/*checks.gd,desert_battlefield/verify_scene.gd,desert_battlefield/build_scene.gd,desert_battlefield/capture_jump.gd,desert_battlefield/rut_gallery.gd"
export_path="../battlefield-demo.apk"

[preset.0.options]
gradle_build/use_gradle_build=false
architectures/armeabi-v7a=false
architectures/arm64-v8a=true
architectures/x86=false
architectures/x86_64=true
version/code=1
version/name="0.1-test"
package/unique_name="sk.kubis.endlessdrive.battlefield"
package/name="Battlefield Demo"
package/signed=true
screen/immersive_mode=true
user_data_backup/allow=false
permissions/internet=false
''', encoding='utf-8')
print(f'Staged Android demo: {stage}')
