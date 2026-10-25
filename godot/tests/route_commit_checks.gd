extends SceneTree
## A choice made while a retained fork chunk is still installing its collider.
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var world := DriveWorld.new()
	root.add_child(world)
	world.configure(24601)
	world.async_streaming = true
	var baked := DriveTerrainBake.bake(world.terrain, 5)
	world._create_chunk(5, baked)
	var retained: Node3D = world.chunks[5]
	var stale := Node3D.new()
	world.add_child(stale)
	world.chunks[8] = stale
	var called := {"stale": false}
	world._schedule(func() -> void: called.stale = true, stale, "obsolete_branch")
	var x := 550.0
	var choice := world.choose_route(Vector3(x, 0, world.terrain.road_centers(x)[1]))
	for i in 120: await process_frame
	var bands := 0
	for collision in retained.find_children("GroundBand*", "CollisionShape3D", true, false):
		if (collision.shape as ConcavePolygonShape3D).get_faces().size() > 0: bands += 1
	var ok: bool = not choice.is_empty() and bands == DriveTerrainBake.BAND_COUNT and not called.stale
	print("RETAINED FORK COLLISION %s bands=%d/%d" % ["PASS" if ok else "FAIL", bands, DriveTerrainBake.BAND_COUNT])
	print("FUTURE BRANCH JOBS discarded=", not called.stale)
	var revision := world.choose_route(Vector3(x, 0, world.terrain.road_centers(x)[0]))
	var revised: bool = not revision.is_empty() and revision.revised and int(world.terrain.route_choices["0"]) == -1 and retained.is_inside_tree()
	ok = ok and revised
	print("VISIBLE EXIT REVISION ", "PASS" if revised else "FAIL")
	world.async_streaming = false
	world.configure(24601)
	world._create_chunk(6)
	var visible_exit: Node3D = world.chunks[6]
	world._create_chunk(8)
	var occupied: Node3D = world.chunks[8]
	world.async_streaming = true
	world._schedule(world._finish_chunk_collision.bind(occupied, 8), occupied, "publish_collision")
	x = 590
	choice = world.choose_route(Vector3(x, 0, world.terrain.road_centers(x)[1]))
	var held: bool = not choice.is_empty() and world.chunks[6] == visible_exit and visible_exit.is_inside_tree() and occupied.is_inside_tree() and world._retiring_chunks.get(8) == occupied
	for i in 2: await process_frame
	held = held and world._retiring_chunks.get(8) == occupied and occupied.is_inside_tree()
	world._create_chunk(8, DriveTerrainBake.bake(world.terrain, 8))
	var replacement: Node3D = world.chunks[8]
	held = held and not replacement.visible and occupied.is_inside_tree()
	for i in 120: await process_frame
	var replaced := world._retiring_chunks.is_empty() and replacement.visible
	for collision in replacement.find_children("GroundBand*", "CollisionShape3D", true, false):
		replaced = replaced and (collision.shape as ConcavePolygonShape3D).get_faces().size() > 0
	ok = ok and held and replaced
	print("LATE BRANCH FLOOR %s old_floor_held=%s replacement_complete=%s" % ["PASS" if held and replaced else "FAIL", held, replaced])
	world.free()
	quit(0 if ok else 1)
