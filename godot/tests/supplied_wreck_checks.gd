extends SceneTree
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, description: String) -> void:
	print("%s %s" % ["PASS" if ok else "FAIL", description])
	if not ok: failures += 1
func _run() -> void:
	var world := DriveWorld.new()
	root.add_child(world)
	world.configure(24601)
	var chunk := Node3D.new()
	world.add_child(chunk)
	for variant in 2:
		var source := {"x": 300.0 + variant * 50, "offset": 20.0, "id": -3000000 - variant * 2, "wreck": true}
		world._create_supply_source(chunk, source)
		var body := chunk.get_child(variant) as StaticBody3D
		var visual := body.get_child(0) as Node3D
		check(visual.name == "SuppliedWreck%d" % variant and visual.get_child_count() > 0, "Correct supplied wreck variant is instantiated")
		check(world.nearest_building(body.global_position, 4.0).id == source.id, "Wreck can be searched by its stable loot ID")
		for mesh_instance: MeshInstance3D in visual.find_children("*", "MeshInstance3D", true, false):
			for surface in mesh_instance.mesh.get_surface_count():
				var mat := mesh_instance.mesh.surface_get_material(surface) as StandardMaterial3D
				if mat != null and mat.albedo_texture != null:
					check(mat.albedo_texture.resource_path.begins_with("res://assets/models/wrecks/"), "Wreck texture is a runtime asset")
	var run := DriveRun.new()
	var first := run.open_loot(-3000000).duplicate(true)
	check(run.open_loot(-3000000) == first, "Wreck loot remains stable after opening")
	world.batch_scenery = false
	for variant in 2:
		var site := {"id": variant * 2, "kind": "wreck", "position": Vector3(600 + variant * 50, 0, 0), "basis": Basis.IDENTITY}
		world._place_road_obstacle(chunk, site)
		var obstacle := chunk.get_child(chunk.get_child_count() - 1) as Node3D
		check(obstacle.has_node("SuppliedWreck%d" % variant) and not obstacle.has_node("RustedChassis"), "Road wreck uses supplied variant %d" % variant)
		check(obstacle.find_children("*", "CollisionShape3D", true, false).size() > 0, "Road wreck retains physical collision")
		var loot := world.nearest_building(world.to_local(obstacle.global_position), 4.0)
		check(not loot.is_empty() and int(loot.id) == -4000000 - int(site.id), "Road wreck has a stable searchable loot ID")
		check(run.open_loot(int(loot.id)) == run.open_loot(int(loot.id)), "Road wreck loot does not reroll on repeat visits")
	world.batch_scenery = true
	var batched_chunk := Node3D.new()
	world.add_child(batched_chunk)
	for variant in 2:
		var site := {"id": 20 + variant * 2, "kind": "wreck", "position": Vector3(800 + variant * 50, 0, 0), "basis": Basis.IDENTITY}
		world._place_road_obstacle(batched_chunk, site)
		var obstacle := batched_chunk.get_node("RoadObstacle_%d_wreck" % int(site.id)) as Node3D
		var loot := world.nearest_building(world.to_local(obstacle.global_position), 4.0)
		check(obstacle.find_children("*", "CollisionShape3D", true, false).size() > 0 and int(loot.get("id", 0)) == -4000000 - int(site.id), "Batched road wreck keeps collision and searchable loot")
	var batches: Dictionary = batched_chunk.get_meta("prop_batches", {})
	var visible := 0
	for batch: MultiMeshInstance3D in batches.values(): visible += batch.multimesh.visible_instance_count
	check(visible >= 2, "Batched road wrecks retain visible model geometry")
	world.queue_free()
	await process_frame
	quit(1 if failures else 0)
