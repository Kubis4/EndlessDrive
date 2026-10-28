extends SceneTree
## Standalone: --headless --path godot --script res://tests/stop_placement_checks.gd
## Does not load main/HUD, change save data, or modify other agents' test runners.

var checks := 0
var failures := 0


func _initialize() -> void:
	call_deferred("_run")


func check(condition: bool, description: String) -> void:
	checks += 1
	if not condition:
		failures += 1
		push_error("FAIL: " + description)


func _run() -> void:
	_test_catalog()
	var world := DriveWorld.new()
	root.add_child(world)
	world.configure(24601)
	world.update_stream(0)
	await physics_frame
	await physics_frame
	check(world._models.size() == 8 and world._models.has("road_delineator"), "Environment cache includes the original props and road delineator")
	check(world.chunks.size() == 7, "Streaming count remains seven")
	var tutorial: Dictionary = world.buildings[0]
	check(tutorial.kind == "workshop" and tutorial.position == world._roadside_point(42, -18), "Exact tutorial workshop position")
	var seen: Dictionary = {}
	for region in range(0, 11):
		var stop := DriveStops.next_stop(region * DriveStops.REGION_LENGTH, world.world_seed)
		world.update_stream(float(stop.x))
		await physics_frame
		await physics_frame
		check(world.chunks.size() == 7, "Seven chunks at stop %d" % int(stop.id))
		var live: Dictionary = world.buildings[int(stop.id)]
		check(live.kind == stop.kind and live.title == stop.title, "Live metadata matches pure catalog")
		var pos: Vector3 = live.position
		check(world.nearest_building(pos).id == stop.id, "Nearest building exposes matching metadata")
		check(world.next_stop(float(stop.x)).position == pos, "World navigation enriches catalog with position")
		seen[str(stop.kind) + str(signf(float(stop.offset)))] = true
		_test_scatter(world, stop)
		_test_driveway(world, stop)
		if str(stop.kind) in ["workshop", "repair", "paint"]:
			_test_bay(world, stop)
	world.update_stream(100000)
	await process_frame
	world.update_stream(0)
	check(world.buildings[0] == tutorial, "Stream out/back preserves tutorial metadata and position")
	check(seen.size() == 10, "All five model types appear on both sides")
	world.queue_free()
	await process_frame
	print("STOP RESULT: %d checks, %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)


func _test_catalog() -> void:
	for seed_value: int in [24601, 0, 1, -72, 701, 1234567, 9, 42]:
		var profile := DriveTerrain.new()
		profile.seed_value = seed_value
		var previous := -1.0
		var gas_region := 0
		var cycle: Dictionary = {}
		for region in range(0, 106):
			var stop := DriveStops.next_stop(region * DriveStops.REGION_LENGTH, seed_value)
			var x := float(stop.x)
			var id := int(stop.id)
			check(stop.has_all(["kind", "title", "local_x", "offset", "x", "id"]), "Catalog schema")
			check(id == int(floor(x / 96.0)) and is_equal_approx(float(stop.local_x), x - id * 96.0), "ID is owning chunk")
			check(DriveStops.for_chunk(id, seed_value) == DriveStops.by_id(id, seed_value), "Direct ID/chunk lookup agrees")
			var repeated := DriveStops.next_stop(region * DriveStops.REGION_LENGTH, seed_value)
			check(repeated == stop, "Repeated lookups are deterministic")
			check(DriveStops.next_stop(x, seed_value).id == id, "Next stop includes current coordinate")
			check(DriveStops.next_stop(x + 0.1, seed_value).id > id, "Next stop advances after passing coordinate")
			if previous >= 0.0:
				check(x - previous >= 3700.0 and x - previous <= 4000.0, "Sparse stop spacing stays within target")
			previous = x
			check(absf(float(stop.offset)) >= 18.0 and absf(float(stop.offset)) <= 26.0, "Roadside offset range")
			check(DriveStops._clear_footprint(profile, x, float(stop.offset)), "Footprint clears roads and rock limits")
			for dx: float in [-20, 0, 20]:
				check(profile.fork_at(x + dx).is_empty(), "Yard and entrance are away from both fork lanes")
			if stop.kind == "gas":
				check(region - gas_region <= 5, "Gas occurs within five stops, including the initial wait")
				gas_region = region
			if region > 0:
				cycle[stop.kind] = true
				if region % 5 == 0:
					check(cycle.size() == 5, "Every shuffled cycle contains all five types")
					cycle.clear()
		check(DriveStops.by_id(3, seed_value).kind == "workshop" and DriveStops.by_id(6, seed_value).kind == "workshop", "Legacy workshop IDs retain their loot theme")
		check(DriveStops.by_id(3, seed_value).x == 330.0, "Legacy fallback retains original coordinate")
		check(DriveStops.for_chunk(3, seed_value).is_empty() and DriveStops.for_chunk(-1, seed_value).is_empty(), "Fallbacks do not spawn additional stops")
	check(DriveStops.next_stop(INF, 0).is_empty(), "Nonfinite navigation coordinate is rejected")
	check(DriveStops.next_stop(-1000, 0).id == 0, "Before journey start navigates to tutorial")
	check(DriveStops.next_stop(1000, 0) != DriveStops.next_stop(1000, 1), "Seed changes catalog placement")
	# Find a stop whose reserved yard overlaps an adjacent chunk, not just its owner.
	var seam_stop: Dictionary = {}
	for seed_value in 200:
		var stop := DriveStops.next_stop(DriveStops.REGION_LENGTH / 2, seed_value)
		if float(stop.local_x) < 8.0 or float(stop.local_x) > 88.0:
			seam_stop = stop
			break
	check(not seam_stop.is_empty(), "Catalog exercises cross-chunk yard placement")


func _test_scatter(world: DriveWorld, stop: Dictionary) -> void:
	var stops: Array[Dictionary] = [stop]
	for chunk: Node3D in world.chunks.values():
		for node: Node in chunk.get_children():
			if node.name in ["Saguaro", "DesertScrub", "WeatheredMesaRock", "MountainPine", "WetlandTree", "DesertUtilityPole", "RanchWireFence"]:
				var point: Vector3 = node.position
				if node.name == "RanchWireFence": point.x += 2.5
				check(not world._reserved_for_stop(point, stops), "Scenery avoids every stop's yard/driveway across chunk seams")
	var side := signf(float(stop.offset))
	for delta: float in [-3.0, 0.0, 3.0]:
		var x := float(stop.x) + delta
		check(world._reserved_for_stop(world._point(x, side * 10.5), stops), "Driveway is reserved on either roadside")
	for delta: float in [-11.5, -8.0, 0.0, 8.0, 11.5]:
		for lateral: float in [absf(float(stop.offset)) - 8.0, absf(float(stop.offset)), absf(float(stop.offset)) + 3.7]:
			var point := world._point(float(stop.x) + delta, lateral * side)
			check(world._reserved_for_stop(point, stops), "Entire curved turning yard is reserved")


func _test_driveway(world: DriveWorld, stop: Dictionary) -> void:
	var x := float(stop.x)
	var side := signf(float(stop.offset))
	var space := world.get_world_3d().direct_space_state
	for step in 8:
		var lateral := lerpf(0.0, absf(float(stop.offset)) - 8.0, step / 7.0) * side
		for dx: float in [-1.2, 0.0, 1.2]:
			var ground := world._point(x + dx, lateral)
			var query := PhysicsRayQueryParameters3D.create(ground + Vector3.UP * 1.9, ground + Vector3.UP * 0.3)
			check(space.intersect_ray(query).is_empty(), "Road-to-service approach clears actual scenery and building collision")
	var stop_pos: Vector3 = world.buildings[int(stop.id)].position
	var approach := stop_pos - Vector3(0, 0, side * 8.0)
	check(world.nearest_building(approach).id == stop.id, "Service interaction is reachable in the forecourt")
	print("STOP: %s id=%d x=%.2f offset=%.2f" % [str(stop.kind), int(stop.id), x, float(stop.offset)])


func _test_bay(world: DriveWorld, stop: Dictionary) -> void:
	var pos: Vector3 = world.buildings[int(stop.id)].position
	var front := -signf(float(stop.offset))
	var space := world.get_world_3d().direct_space_state
	var approach := pos + Vector3(0, 0, front * 7.0)
	check(world.nearest_building(approach).id == stop.id, "Service remains reachable outside the supplied building")
	check(not world.is_sheltered(approach), "A closed building does not falsely shelter the forecourt")
	var wall_query := PhysicsRayQueryParameters3D.create(pos + Vector3(-8, 1.5, 0), pos + Vector3(0, 1.5, 0))
	check(not space.intersect_ray(wall_query).is_empty(), "Garage wall remains a real collision barrier")
