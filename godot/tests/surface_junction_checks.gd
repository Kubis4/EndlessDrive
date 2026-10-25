extends SceneTree
var failures := 0
var checks := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
	if not ok: failures += 1
func frames(count := 2) -> void:
	for i in count:
		await physics_frame
		await process_frame
func _run() -> void:
	for kind in [0, 1, 2, 4, 6, 9, 10, 11]:
		var field := DriveDeformation.new()
		var road := DriveDeformation.new()
		var surface := {"paved": 0.0, "material": kind, "deformable": true, "road": 0.0}
		for i in 50:
			field.compact(Vector3.ZERO, surface)
			road.compact(Vector3.ZERO, merge(surface, {"road": 1.0}))
		check(field.depth_at(0, 0) > 0.10 and absf(road.depth_at(0, 0) / field.depth_at(0, 0) - 0.5) < 0.01, "Material %d: maintained road has half the off-road rut depth" % kind)
		check(field.depth_at(0, 0.55) < -0.01 and absf(field.depth_at(0, 0.5) - field.depth_at(0, 0.75)) < 0.13, "Material %d: rounded displaced shoulders" % kind)
		var restored := DriveDeformation.new()
		restored.restore(field.to_data())
		check(absf(restored.depth_at(0.125, 0.375) - field.depth_at(0.125, 0.375)) < 0.0001, "Material %d: quarter-metre soil survives save/load" % kind)
	var mud := DriveDeformation.new()
	var pocket := {"paved": 0.0, "material": 3, "deformable": true, "road": 1.0, "soil_softness": 0.98, "soil_depth": 0.85, "soil_bearing": 0.48}
	for i in 600: mud.load_soil(Vector3.ZERO, pocket, 1.1, 0.95, 1.0, 1.0 / 60.0)
	check(mud.depth_at(0, 0) > 0.55, "Saturated road obstacle can fail deeply under sustained wheelspin")
	var hard := DriveDeformation.new()
	hard.compact(Vector3.ZERO, {"paved": 1.0, "material": 7}, 2.0)
	hard.compact(Vector3.ZERO, {"paved": 0.0, "deformable": false, "material": 5}, 2.0)
	check(hard.cells.is_empty(), "Asphalt and solid rock remain firm")
	var bridge_profile := DriveTerrain.new()
	var bridge := bridge_profile.sample(5544, bridge_profile.center_at(5544))
	check(bridge_profile.feature_at(5544).kind == "bridge" and not bridge.deformable and float(bridge.soil_depth) == 0.0, "Gravel over a structural bridge deck cannot excavate its solid foundation")
	var legacy := DriveDeformation.new()
	legacy.restore([[0, 0, 0.12], [1, 0, 0.08]])
	check(absf(legacy.depth_at(0.5, 0) - 0.10) < 0.0001, "Old metre-grid saves migrate without spikes or lost interpolation")
	for seed_value in range(24600, 24604):
		var terrain := DriveTerrain.new()
		terrain.seed_value = seed_value
		var layout: String = terrain.junction_style(0).layout
		var paths := terrain.junction_paths(0)
		var follows := true
		var backward := false
		for path: PackedVector2Array in paths:
			for i in path.size() - 1:
				backward = backward or path[i + 1].x < path[i].x - 0.1
				for step in 8:
					var p := path[i].lerp(path[i + 1], float(step) / 8.0)
					if p.x <= 360.0 or p.x >= 600.0: continue
					follows = follows and absf(terrain.road_distance(p.x, p.y)) < 0.03 and float(terrain.sample(p.x, p.y, false).road) > 0.99
		check(follows, "%s: render and tyre samples follow every road arm" % layout)
		if layout == "switchback": check(backward, "%s includes travel towards decreasing X" % layout)
		terrain.route_choices = {"0": 1}
		terrain.clear_caches()
		var open := true
		for x in range(600, 712, 4):
			var lanes := terrain.road_centers(x)
			open = open and lanes.size() == 2
			for z in lanes: open = open and float(terrain.sample(x, z, false).road) > 0.99
		check(open and absf(terrain.road_centers(711.99)[1] - terrain.road_centers(711.99)[0]) < 0.01, "%s: both exits stay paved/drivable and join the continuing route" % layout)
	var world := DriveWorld.new()
	root.add_child(world)
	world.configure(24601)
	world._create_chunk(0)
	var chunk: Node3D = world.chunks[0]
	var p := Vector3(20, 0, world.center_at(20) + 16.0)
	var surface := {"paved": 0.0, "material": 0, "road": 0.0, "deformable": true}
	for i in 40: world.terrain.deformation.compact(p, surface)
	var band := floori(p.x / DriveTerrainBake.BAND_ROWS)
	world._update_rut_band(chunk, band)
	await frames()
	var max_gap := 0.0
	var hit_count := 0
	var render: MeshInstance3D = chunk.get_node("SandAndTracks" if band == 0 else "SandAndTracks/TerrainStrip%d" % band)
	for lateral in range(-6, 7):
		var z := p.z + lateral * 0.125
		var height := world.height_at(p.x, z)
		var query := PhysicsRayQueryParameters3D.create(Vector3(p.x, height + 3.0, z), Vector3(p.x, height - 3.0, z))
		var hit := world.get_world_3d().direct_space_state.intersect_ray(query)
		if not hit.is_empty():
			hit_count += 1
			max_gap = maxf(max_gap, absf(float(hit.position.y) - mesh_height(render.mesh, p.x, z)))
	check(hit_count == 13 and max_gap < 0.025, "Actual main-world fine collision follows the rounded wheel channel")
	check(world.last_refined_quads > 0 and world.last_refined_quads < 100, "Only local touched quads receive the finer mesh")
	print("DETAIL worker=%.2f ms commit=%.2f ms quads=%d maximum collision gap=%.4f m" % [world.last_detail_worker_ms, world.max_deformation_ms, world.last_refined_quads, max_gap])
	var loaded := DriveTerrain.new()
	loaded.seed_value = world.world_seed
	loaded.deformation.restore(world.terrain.deformation.to_data())
	var rebaked := DriveTerrainBake.bake(loaded, 0)
	var original: Array = chunk.get_meta("terrain_arrays")
	var appearance := true
	for i in (original[Mesh.ARRAY_NORMAL] as PackedVector3Array).size():
		appearance = appearance and (original[Mesh.ARRAY_NORMAL][i] as Vector3).distance_to(rebaked.arrays[Mesh.ARRAY_NORMAL][i]) < 0.001
		var a: Color = original[Mesh.ARRAY_COLOR][i]
		var b: Color = rebaked.arrays[Mesh.ARRAY_COLOR][i]
		appearance = appearance and absf(a.r - b.r) + absf(a.g - b.g) + absf(a.b - b.b) < 0.001
	check(appearance, "Restored soil preserves its base palette and does not add the deformation normal twice")
	var vehicle := load("res://scenes/vehicle.tscn").instantiate() as DriveVehicle
	vehicle.set_physics_process(false)
	vehicle.run = DriveRun.new()
	vehicle.world = world
	world.add_child(vehicle)
	var effects := DriveEffects.new()
	effects.vehicle = vehicle
	effects.world = world
	world.add_child(effects)
	for family in 4:
		var bank: Dictionary = effects._surface_pools[0][family]
		check(bank.dust.material_override is ShaderMaterial and bank.grit.mesh is SphereMesh and bank.clumps != null, "Spray family %d has procedural powder, 3D grains and clumps" % family)
		var color: Color = bank.dust.material_override.get_shader_parameter("powder_tint")
		for next in range(5, -1, -1): effects.select_particle_bank(0, next)
		check(bank.dust.material_override.get_shader_parameter("powder_tint") == color, "Airborne family %d retains its immutable material" % family)
	effects.reset_particles()
	check(effects._clump_emitters.all(func(e: CPUParticles3D) -> bool: return not e.emitting), "Reset stops every surface's clump bank")
	world.queue_free()
	await frames()
	print("SURFACES AND JUNCTIONS %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
func merge(a: Dictionary, b: Dictionary) -> Dictionary:
	var result := a.duplicate()
	result.merge(b, true)
	return result

func mesh_height(mesh: ArrayMesh, x: float, z: float) -> float:
	var arrays := mesh.surface_get_arrays(0)
	var points: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
	for i in range(0, indices.size(), 3):
		var a := points[indices[i]]
		var b := points[indices[i + 1]]
		var c := points[indices[i + 2]]
		var ab := Vector2(b.x - a.x, b.z - a.z)
		var ac := Vector2(c.x - a.x, c.z - a.z)
		var ap := Vector2(x - a.x, z - a.z)
		var denominator := ab.cross(ac)
		if absf(denominator) < 0.000001: continue
		var u := ap.cross(ac) / denominator
		var v := ab.cross(ap) / denominator
		if u < -0.0001 or v < -0.0001 or u + v > 1.0001: continue
		return a.y + u * (b.y - a.y) + v * (c.y - a.y)
	return INF
