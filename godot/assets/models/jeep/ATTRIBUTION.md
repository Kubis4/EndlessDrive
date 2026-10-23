# Jeep Cherokee — third-party model

Title: **Jeep Cherokee**

Author: **Renafox**, https://sketchfab.com/kryik1023

Source: https://sketchfab.com/3d-models/jeep-cherokee-3fdd9619be2c4e7b809888d08cbdab88

License recorded in the supplied GLB: **CC BY-NC-SA 4.0**
https://creativecommons.org/licenses/by-nc-sa/4.0/

The original supplied `jeep_cherokee.glb` is preserved alongside its extracted
albedo, ORM and normal textures. Adapted assets in this directory are provided
under the same CC BY-NC-SA 4.0 license. The asset's license does not grant
permission for commercial use. Obtain suitable permission or replace the model
before a commercial release; do not assume the repository's code license overrides
these third-party asset terms. No endorsement by the original author is implied.

Changes for Endless Drive:

- Converted source axes/units to metre-scale, +X-forward game coordinates.
- Separated the existing four disconnected wheel islands without cutting the body.
- Centred steer/spin pivots at the measured axle centres; 2.96 m wheelbase.
- Increased tyres uniformly by 12.99% to the existing game's 0.44 m rolling radius.
- Retained original UVs, normals, tangents and all three material textures.
- Added a selective paint shader, retaining glass, trim, rubber and weathering.
- Created a convex body collision from the actual mesh and added a skid plate,
  cylindrical tyre contacts, headlight markers and brake-lamp overlays.

The native editable rig is `jeep_cherokee.tscn`. The explicit offline conversion
script is `res://tests/build_jeep_rig.gd`; it is not executed on game startup.
Rebuilding replaces the generated mesh/collision resources, not the editable scene.
