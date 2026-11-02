# Endless Drive

Off-road driving game for Android, made in **Godot 4.7.2** (Forward Mobile on phones). One
procedural road leads from the fields or the desert (drawn at random for each new run) ever
higher, up the serpentines of the last range to a transmitter on the summit at about 1700 m.
That ends the journey, and the land goes on being made for whoever drives further, downhill at
first. The way climbs evenly for the whole 90 km of road; on it: fallen trees and poles, rockfalls
and wrecks to get past, fuel, loot, weather, tyres and a garage paid for in scrap.

- Package: `sk.kubis.endlessdrive`
- Main scene: `offroad/endless_menu.tscn`, the game itself `offroad/endless.tscn`

A run goes from the start to the end and is saved as it goes; there are no checkpoints in the
game. For testing only, the SKOK button (N on a computer, or in the pause menu) lists places
before each landscape and each part of the last stage, and jumps there with a full tank. A far
jump first has to plan the road that far (about a minute on a computer). A run that has jumped
earns no scrap for what it skipped.

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
