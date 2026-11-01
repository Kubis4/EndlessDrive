# Endless Drive

Off-road driving game for Android, made in **Godot 4.7.2** (Forward Mobile on phones). One
procedural road of 50 km and more leads from the fields or the desert to the mountains,
with fuel, loot, weather, tyres and a garage paid for in scrap found on the way.

- Package: `sk.kubis.endlessdrive`
- Main scene: `offroad/endless_menu.tscn`, the game itself `offroad/endless.tscn`

## Build the APK

Open the project in Godot 4.7.2 with the Android export templates installed, and set the
Java and Android SDK paths in the editor settings. A test build, signed with the debug key:

```powershell
$env:GODOT_ANDROID_KEYSTORE_RELEASE_PATH="$HOME\.android\debug.keystore"
$env:GODOT_ANDROID_KEYSTORE_RELEASE_USER="androiddebugkey"
$env:GODOT_ANDROID_KEYSTORE_RELEASE_PASSWORD="android"
godot --headless --path . --export-release "Android" "build/EndlessDrive-test.apk"
```

## Where things are

- `offroad/`: the world, the expedition, menu, garage and phone display
- `desert_battlefield/`: the car (`vehicle.gd`), soil and ruts, touch controls, the game scene's script
- `assets/`: models, textures, sounds and fonts the game uses

The tools that baked the assets, the test scenes and the regression checks were removed
from the working tree and remain in the git history.

## Licences

`assets/ASSET_LICENSES.md` lists where the assets come from. Some must not be redistributed
or have no recorded licence, so keep this repository private and clear them before any
public release.
