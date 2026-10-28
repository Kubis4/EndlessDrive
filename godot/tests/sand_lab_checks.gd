extends SceneTree
## Tests actual driving and triangle collision, without decorative tyre overlays.
const Soil = preload("res://scripts/sand_lab_soil.gd")
var failures := 0
var assertions := 0

func _initialize() -> void: call_deferred("_run")

func check(ok: bool, label: String) -> void:
	assertions += 1
	print("%s %s" % ["PASS" if ok else "FAIL", label])
	if not ok: failures += 1

func frames(count: int) -> void:
	for i in count:
		await physics_frame
		await process_frame

func _run() -> void:
	var soil := Soil.new()
	var sand := {"paved": 0.0, "deformable": true}
	for x in 40: soil.compact(Vector3(x * 0.15, 0, 0), sand)
	var first := soil.depth_at(3, 0)
	check(first > 0.04 and first < Soil.RUT_LIMIT, "First pass makes a local rut with room for further compaction")
	check(soil.depth_at(3, 0.5) < -0.01 and soil.depth_at(3, 2) == 0, "Raised sand shoulders are local; untouched ground stays unchanged")
	for x in 40: soil.compact(Vector3(x * 0.15, 0, 0), sand)
	check(soil.depth_at(3, 0) > first, "Repeated passage deepens existing geometry")
	check(soil.depth_at(3, 0.25) > 0 and soil.depth_at(3, 0) - soil.depth_at(3, 0.25) < 0.17 and soil.depth_at(3, 0.25) - soil.depth_at(3, 0.5) < 0.17, "Rut cross-section has a rounded bowl and berm rather than an abrupt vertical edge")
	var restored := Soil.new()
	restored.restore(soil.to_data())
	check(is_equal_approx(restored.depth_at(3, 0), soil.depth_at(3, 0)), "Quarter-metre soil coordinates survive round trip")
	var lab = load("res://scenes/sand_lab.tscn").instantiate()
	root.add_child(lab)
	var car: DriveVehicle = lab.vehicle
	var world = lab.world
	await frames(30)
	check(lab.dust_enabled and lab.effects.sand_lab_plume and car.sand_lab_drift > 0.0 and car.vehicle_id == "jeep" and lab.run.four_wheel_drive, "Sand lab starts with the 4x4 model, reference dust and loose-sand handling enabled")
	check(not lab.effects.trails_enabled and lab.effects._tracks.multimesh.instance_count == 0, "Sand lab allocates no decorative tracks")
	lab.toggle_dust()
	var start := car.position
	var max_sink := 0.0
	for tick in 510:
		var target_x := car.position.x + 5
		var target_z := -4 + sin((target_x + 22) * 0.13) * 2.5
		var error := wrapf(atan2(target_z - car.position.z, 5) - car.heading, -PI, PI)
		Input.action_press("right", clampf(error * 3, 0, 1))
		Input.action_press("left", clampf(-error * 3, 0, 1))
		Input.action_press("throttle", 0.7 if car.speed < 6 else 0.15)
		await frames(1)
		max_sink = maxf(max_sink, car.sink_depth)
	for action: String in ["throttle", "left", "right"]: Input.action_release(action)
	car.set_physics_process(false)
	world.flush_deformation()
	await frames(3)
	var travel := car.position.distance_to(start)
	check(travel > 20 and car.boundary_recoveries == 0, "Real car travels over 20 m without a rescue or teleport")
	check(max_sink < 0.25 and car.position.y > -0.5, "Four-wheel drive remains supported on the physical sand")
	check(lab.effects._count == 0 and world.terrain.deformation.cells.size() > 100, "Ruts are actual terrain while no decorative stamps have been made")
	var lowest := 0.0
	var highest := 0.0
	var probe := Vector2.ZERO
	for key: Vector2i in world.terrain.deformation.cells:
		var depth: float = world.terrain.deformation.cells[key]
		if depth > lowest:
			lowest = depth
			probe = Vector2(key) * Soil.CELL
		highest = minf(highest, depth)
	check(lowest > 0.06 and lowest <= Soil.RUT_LIMIT and highest < -0.015, "Driven wheels depress sand and physically raise both shoulders")
	var ray := PhysicsRayQueryParameters3D.create(Vector3(probe.x, 5, probe.y), Vector3(probe.x, -5, probe.y), 1, [car.get_rid()])
	var hit: Dictionary = world.get_world_3d().direct_space_state.intersect_ray(ray)
	check(not hit.is_empty() and absf(hit.position.y - world.height_at(probe.x, probe.y)) < 0.005, "Ray collision matches the deformed render vertex within 5 mm")
	var max_face_error := 0.0
	var max_visual_error := 0.0
	var face_probes := 0
	for key: Vector2i in world.terrain.deformation.cells:
		if float(world.terrain.deformation.cells[key]) < 0.04: continue
		var point := Vector2(key) * Soil.CELL
		var a: float = world.height_at(point.x, point.y)
		var b: float = world.height_at(point.x + Soil.CELL, point.y)
		var c: float = world.height_at(point.x, point.y + Soil.CELL)
		var px := point.x + Soil.CELL * 0.3
		var pz := point.y + Soil.CELL * 0.6
		var expected := a * 0.1 + b * 0.3 + c * 0.6
		var probe_hit: Dictionary = world.get_world_3d().direct_space_state.intersect_ray(PhysicsRayQueryParameters3D.create(Vector3(px, 5, pz), Vector3(px, -5, pz), 1, [car.get_rid()]))
		max_face_error = maxf(max_face_error, 100.0 if probe_hit.is_empty() else absf(probe_hit.position.y - expected))
		max_visual_error = maxf(max_visual_error, 100.0 if probe_hit.is_empty() else absf(probe_hit.position.y - rendered_height(world, px, pz)))
		face_probes += 1
		if face_probes >= 80: break
	check(face_probes == 80 and max_face_error < 0.005, "Heightmap collision matches physical soil triangle interiors within 5 mm")
	check(max_visual_error < 0.025, "Rounded visual midpoints stay within 2.5 cm of actual tyre collision")
	print("Collision/render maximum face error=", max_face_error)
	print("Refined render/collision gap=", max_visual_error)
	check(world.terrain.deformation.depth_at(0, 18) == 0, "Distant sand remains untouched after the driving test")
	print("SAND LAB travel=%.2f m depth=%.3f m bank=%.3f m sink=%.3f m max tile=%.2f ms" % [travel, lowest, -highest, max_sink, world.max_tile_ms])
	if "--save-preview" in OS.get_cmdline_user_args():
		var preview := Node3D.new()
		preview.name = "EditorSand"
		for key: Vector2i in world.tiles:
			var tile := MeshInstance3D.new()
			tile.name = "Sand_%d_%d" % [key.x, key.y]
			tile.mesh = world.tiles[key].mesh.mesh
			tile.material_override = world.material
			tile.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
			preview.add_child(tile)
			tile.owner = preview
		var packed := PackedScene.new()
		check(packed.pack(preview) == OK and ResourceSaver.save(packed, "res://scenes/world/generated/sand_lab_preview.scn") == OK, "Saved real driven terrain as an editor-visible preview")
		preview.free()
	if DisplayServer.get_name() != "headless":
		lab.get_node("LabControls").hide()
		lab.follow_camera = false
		var camera: Camera3D = lab.get_node("Camera3D")
		var center := (start + car.position) * 0.5
		camera.position = center + Vector3(8, 19, 22)
		camera.size = 35
		camera.look_at(center)
		await process_frame
		await RenderingServer.frame_post_draw
		check(root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/sand-lab-deformation.png")) == OK, "Native GPU image saved with dust and painted tracks OFF")
	var before: int = world.terrain.deformation.cells.size()
	lab.reset_car()
	check(world.terrain.deformation.cells.size() == before, "Resetting the car preserves all existing ruts")
	# Changing one wheel's surface cannot recolor already emitted particles.
	var fx: DriveEffects = lab.effects
	var old_dust: CPUParticles3D = fx._emitters[0]
	var old_grit: CPUParticles3D = fx._grit[0]
	var old_color := old_dust.color
	old_dust.emitting = true
	for family in [1, 3, 4, 2]: fx.select_particle_bank(0, family)
	check(old_dust.color == old_color and not old_dust.emitting and old_grit.material_override == null, "Existing sand dust/grit retains its color and material after mud, snow and water")
	check(fx._emitters[1] == fx._surface_pools[1][0].dust, "Changing one wheel does not switch the other wheels' particle banks")
	check(fx._surface_pools[0][4].grit.material_override != null and fx._surface_pools.size() == 4, "Water owns a separate material in a bounded lazy pool")
	check(fx._surface_pools[0][0].dust.mesh is SphereMesh and fx._surface_pools[0][0].dust.material_override == null and fx._surface_pools[0][0].dust.amount == 128 and fx._surface_pools[0][0].grit.amount == 192, "Sand spray uses 3D grain geometry instead of billboard clouds")
	if DisplayServer.get_name() != "headless":
		lab.toggle_dust()
		car.set_physics_process(true)
		for tick in 230:
			Input.action_press("throttle", 0.65 if car.speed < 6 else 0.15)
			Input.action_press("right", 0.65 if tick > 90 else 0)
			await frames(1)
		check(fx._emitters.any(func(emitter: CPUParticles3D) -> bool: return emitter.emitting) and not fx._tracks.visible, "Dust can be enabled independently while decorative tracks remain hidden")
		var camera: Camera3D = lab.get_node("Camera3D")
		var center := car.position - Vector3(6, 0, 0)
		camera.position = center + Vector3(8, 18, 21)
		camera.size = 18
		camera.look_at(center)
		await process_frame
		await RenderingServer.frame_post_draw
		check(root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/sand-lab-dust.png")) == OK, "Native GPU dust preview saved")
		for action: String in ["throttle", "right"]: Input.action_release(action)
		car.set_physics_process(false)
		lab.toggle_dust()
		fx._process(0)
		check(not fx._emitters.any(func(emitter: CPUParticles3D) -> bool: return emitter.emitting) and not fx._sand_clumps.any(func(emitter: CPUParticles3D) -> bool: return emitter.emitting), "Disabling spray stops all three particle layers")
		check(fx._count == 0 and fx._tracks.multimesh.instance_count == 0, "Spray driving never creates or allocates decorative trails")
	lab.reset_sand()
	check(world.terrain.deformation.cells.is_empty(), "Clear sand removes the real deformation")
	lab.queue_free()
	await frames(3)
	print("Sand lab: %d/%d passed" % [assertions - failures, assertions])
	quit(0 if failures == 0 else 1)

func rendered_height(world: Node3D, x: float, z: float) -> float:
	var key := Vector2i(floori(x / 8), floori(z / 8))
	var tile: Dictionary = world.tiles[key]
	var row: int = tile.visual_row
	var vertices: PackedVector3Array = tile.mesh.mesh.surface_get_arrays(0)[Mesh.ARRAY_VERTEX]
	var grid := (Vector2(x, z) - Vector2(key) * 8.0) / (8.0 / (row - 1))
	var ix := clampi(floori(grid.x), 0, row - 2)
	var iz := clampi(floori(grid.y), 0, row - 2)
	var f := grid - Vector2(ix, iz)
	var a := vertices[ix * row + iz].y
	var b := vertices[(ix + 1) * row + iz].y
	var c := vertices[ix * row + iz + 1].y
	var d := vertices[(ix + 1) * row + iz + 1].y
	return a * (1 - f.x - f.y) + b * f.x + c * f.y if f.x + f.y <= 1 else b * (1 - f.y) + c * (1 - f.x) + d * (f.x + f.y - 1)
