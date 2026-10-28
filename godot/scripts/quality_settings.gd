extends RefCounted
## Performance choices affect decoration and lighting, never road collisions,
## the available loot, storm timing or tyre traction.
static func apply(root: Node, level: int) -> void:
	level = clampi(level, 0, 2)
	# Scale only the 3D buffer; HUD text and touch controls keep full resolution.
	var scale: float = [0.70, 0.82, 1.0][level]
	root.get_viewport().msaa_3d = Viewport.MSAA_2X if level == 2 else Viewport.MSAA_DISABLED
	root.get_viewport().set_meta("terrain_detail_tier", level)
	if not is_equal_approx(root.get_viewport().scaling_3d_scale, scale): root.get_viewport().scaling_3d_scale = scale
	var world := root.get("world") as DriveWorld
	if world != null: world.set_graphics_quality(level)
	var sun := root.get("sun") as DirectionalLight3D
	if sun != null:
		sun.shadow_enabled = level > 0
		sun.directional_shadow_max_distance = [45.0, 65.0, 120.0][level]
		sun.directional_shadow_mode = DirectionalLight3D.SHADOW_ORTHOGONAL if level < 2 else DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
	for node: Node in root.find_children("*", "CPUParticles3D", true, false):
		var particles := node as CPUParticles3D
		if not particles.has_meta("full_amount"): particles.set_meta("full_amount", particles.amount)
		var desired := maxi(1, roundi(int(particles.get_meta("full_amount")) * [0.35, 0.65, 1.0][level]))
		if particles.amount != desired: particles.amount = desired
