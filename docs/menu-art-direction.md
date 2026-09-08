# Alpine evening menu (concept A)

Typography refinement: bundled [Anton](https://github.com/google/fonts/tree/main/ofl/anton)
for the tall, condensed title and [Bebas Neue](https://github.com/google/fonts/tree/main/ofl/bebasneue)
for menu labels, numbers and buttons. Both font licenses are included in the APK under
`assets/font_licenses` and in `docs/font-licenses`. The title uses a 0.76 horizontal
glyph scale to approach the reference's narrow proportions, with size adapting to width.
Menu-only buttons use amber surfaces and 23sp lettering; gameplay controls are unchanged.
The left landscape identity block has no scroll container. It measures the title and
profile statistics with the actual font metrics, then uniformly fits both to the available
width and height. Branding stays at the top and statistics at the bottom, including on
short landscape screens and with increased system font size.
Road markings are golden yellow. Low-opacity sun rays, warm bloom and a feathered
valley haze follow the existing 12-second clock, underneath all interactive UI.

Selected by the user on 2026-09-08. The interface is live Compose UI, over a clean
photographic background generated with the built-in image_gen tool. Sample numbers
from the concept are replaced by the player's actual saved profile and run.

Asset: `app/src/main/res/drawable-nodpi/menu_alpine_evening.png`.
Reference: the approved golden-hour mountain menu concept A.

Final generation prompt:

> Use case: precise-object-edit. Production background asset for this exact ENDLESS DRIVE menu concept A. Remove ALL UI, all letters, all text, all panels, buttons, stats and logos, reconstruct the landscape behind them. Keep the same photorealistic alpine mountains, pine forest, warm golden hour sunlight from upper right, beautiful detailed weathered asphalt and roadside grass. Wide landscape 16:9. Critical geometry for animation: road is completely straight, centered, vanishing point exactly 50% image width and 50% image height; straight asphalt edges extend to 10% and 90% image width at bottom. NO road markings at all, NO center stripes, NO edge stripes, NO reflector posts, NO cars. Plain realistic asphalt road, no large potholes, no prominent cracks. Natural detailed foreground trees both sides; atmospheric mountains at horizon. Do not add anything graphical. Save a clean photographic game background.

The rendered image's measured vanishing point is approximately (0.505, 0.505).
The animated markings and photographic mesh share image coordinates and the same
centre-crop transform on all aspect ratios. The 12-second loop advances the markings
by two spacings. Reused mesh buffers add subtle forward motion to the asphalt and
verges, with two overlapping passes hiding their resets. The mountains remain still.
Animation state is read during drawing rather than recomposing the menu every frame.

Validation (2026-09-08):
- Standard debug APK and isolated `sk.kubis.endlessdrive.menupreview` APK build successfully.
- Unit suite: 225 tests, 222 passed. All three MenuRoadFx tests pass. Failures are
  `FuelAndSuspensionTest.headlightsCycleThroughLowHighAndOffAndHighBeamsUseMoreBattery`,
  `HighSpeedHandlingTest.fullBuildAtOneThirtyAcrossTestTrackAndGeneratedHills`, and
  `VehicleMassAndTimingTest.newRunsUseSeveralSafeStartingLandscapes` in unchanged gameplay code.
- Menu instrumentation tests could not execute their assertions: the installed
  Espresso version calls the unavailable `InputManager.getInstance` on the device's Android.
- The isolated preview installs and launches without an AndroidRuntime crash. The
  original application cannot be updated by the local debug signing key and was preserved.
- Device screenshots show the lock screen, so final on-device visual/touch verification
  remains pending an unlocked device. No screenshot of the lock screen is a menu preview.
