extends RefCounted
## Speed-dependent rolling/ploughing/fluid losses, in m/s². There is no
## standing water brake or abrupt speed cap, so low-range crawling still works.
static func deceleration(surface: Dictionary, speed: float) -> float:
	var v := absf(speed)
	var road := clampf(float(surface.get("road", 1.0)), 0.0, 1.0)
	var depth := maxf(0.0, float(surface.get("water", 0.0)))
	var weights: PackedFloat32Array = surface.get("material_weights", PackedFloat32Array())
	var mud := 1.0 if int(surface.get("material", -1)) in [DriveTerrain.SurfaceKind.MUD, DriveTerrain.SurfaceKind.PEAT] else 0.0
	var ice := 1.0 if int(surface.get("material", -1)) == DriveTerrain.SurfaceKind.ICE else 0.0
	if weights.size() >= 14:
		mud = maxf(mud, weights[3] + weights[13])
		ice = maxf(ice, weights[12])
	var snow := maxf(ice, float(surface.get("snow", 0.0)))
	var moving := 0.35 + v * 0.13
	# A wet wheel should not need extra engine torque merely to start moving.
	if depth > 0.03: moving = 0.10 + v * 0.13
	return 0.48 + v * v * 0.0028 + float(surface.get("drag", 0.0)) * moving \
		+ (1.0 - road) * (v * 0.22 + v * v * 0.026) \
		+ mud * (v * 0.35 + v * v * 0.070) \
		+ snow * (v * 0.18 + v * v * 0.025) \
		+ smoothstep(0.025, 0.60, depth) * (v * 0.95 + v * v * 0.17)
