extends SceneTree
var checks := 0
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func _run() -> void:
	var world := DriveWorld.new()
	root.add_child(world)
	world.set_process(false)
	for coverage: float in [0.0, 0.5, 1.0]:
		for variant in 5:
			BiomeModels.tree(world, Vector3.ZERO, 1, coverage > 0.5, false, coverage, variant)
			var tree := world.get_child(world.get_child_count() - 1) as Node3D
			check(tree.scene_file_path.begins_with("res://assets/models/vegetation/") and tree.find_children("*", "MeshInstance3D", true, false).size() == 1, "Tree %d / snow %.1f uses one supplied baked mesh" % [variant, coverage])
			tree.free()
	var chunk := Node3D.new()
	world.add_child(chunk)
	for i in 2:
		var mesh := MeshInstance3D.new()
		mesh.mesh = BoxMesh.new()
		mesh.position = Vector3(10000 + i * 4, 1, 10)
		chunk.add_child(mesh)
		world._batch_visuals(mesh, chunk)
	var tight := true
	for batch: MultiMeshInstance3D in chunk.get_children():
		var bounds := batch.multimesh.custom_aabb
		tight = tight and bounds.position.x > 9999 and not bounds.has_point(Vector3.ZERO) and bounds.has_volume()
	check(tight, "Unused batch capacity cannot extend visibility bounds back to world origin")
	chunk.free()
	world.configure(1000003)
	for index in 3: world._create_chunk(index)
	var grass_count := 0
	var grounded := true
	var green := true
	for terrain: Node3D in world.chunks.values():
		var sites: PackedVector3Array = terrain.get_meta("grass_sites", PackedVector3Array())
		grass_count += sites.size()
		check(sites.size() <= 54, "Grass keeps a per-chunk upper budget")
		for p: Vector3 in sites:
			grounded = grounded and absf(p.y + 0.025 - world.rendered_height_at(p.x, p.z)) < 0.001
			green = green and float(world.terrain.sample(p.x, p.z, false, true).material_weights[DriveTerrain.SurfaceKind.GRASS]) >= 0.45
	check(grass_count > 0 and green and grounded, "Small grass tufts are grounded and restricted to actual green terrain: %d" % grass_count)
	world.configure(24601)
	var tunnel := RouteHazard.new()
	tunnel.world = world
	tunnel.kind = "tunnel"
	tunnel.feature_x = 1704
	world.add_child(tunnel)
	tunnel.build()
	for frame in 3:
		await physics_frame
		await process_frame
	var space := world.get_world_3d().direct_space_state
	for dx: float in [-24, 0, 24]:
		var x := 1704.0 + dx
		var center := Vector3(x, world.height_at(x, world.center_at(x)), world.center_at(x))
		var ray := PhysicsRayQueryParameters3D.create(center + Vector3.UP * 60, center + Vector3.UP * 9)
		var hit := space.intersect_ray(ray)
		check(not hit.is_empty() and str(hit.collider.name).begins_with("TunnelMountainCollisionRoof"), "Actual mountain rock lies above the hollow tunnel at %.0f" % x)
	var open := true
	for row in range(-34, 35, 4):
		var x := 1704.0 + row
		var center := Vector3(x, world.height_at(x, world.center_at(x)), world.center_at(x))
		var ray := PhysicsRayQueryParameters3D.create(center + Vector3.UP * 0.5, center + Vector3.UP * 2.7)
		open = open and space.intersect_ray(ray).is_empty()
	check(open, "Mountain construction leaves car-height clearance through the entire tunnel")
	world.queue_free()
	await process_frame
	print("SCENERY REFRESH: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
