# 1986 Chevrolet M1009 — third-party model

Title: **1986 Chevrolet M1009**

Author: **snafuj**, https://sketchfab.com/snafuj

Source: https://sketchfab.com/3d-models/1986-chevrolet-m1009-d2a462c09f054bb093e360a263478732

License recorded in the supplied GLB: **CC BY 4.0**
https://creativecommons.org/licenses/by/4.0/

Changes for Sirocco (`desert_battlefield/build_m1009.gd`, from the GLB kept in
`assets/source/m1009/`, not in git):

- Scaled by 1.13 to the real truck (2.70 m wheelbase, 4.66 m long) and turned
  to the game's +X-forward rig with the origin at axle height.
- Split the single wheel mesh into four wheels on their own spin/steer pivots.
- Textures extracted to compressed PNGs; materials otherwise as supplied.
- Added a convex collision from the body shell, headlight markers and brake-lamp
  lenses. Line primitives in the source were dropped.
