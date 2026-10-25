extends SceneTree
## Numeric terrain, real refuge collision, anchor lifetime and roadside obstacle regressions.
var failures := 0
var checks := 0

func _initialize() -> void: call_deferred("_run")

func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])

func _run() -> void:
	var profile := DriveTerrain.new()
	profile.seed_value = 1000003
	var pairs := {}
	var ford_sides := {}
	for junction in 21:
		var style := profile.junction_style(junction)
		pairs[str(style.upper_material) + ":" + str(style.lower_material)] = true
		ford_sides[int(style.ford_side)] = true
	check(pairs.size() >= 6 and ford_sides.size() == 2, "Junction surfaces and ford sides vary deterministically across the route")
	var kinds := {}
	var repeatable := true
	var early_strength := 0.0
	var late_strength := 0.0
	var geometry_events := 0
	var other := DriveTerrain.new()
	other.seed_value = profile.seed_value
	for section in 500:
		var event := profile.road_event(section)
		repeatable = repeatable and event == other.road_event(section)
		if event.is_empty(): continue
		kinds[str(event.kind)] = true
		if float(event.x) < 10000: early_strength = maxf(early_strength, float(event.strength))
		if float(event.x) > 100000: late_strength = maxf(late_strength, float(event.strength))
		if event.kind != "mud":
			var actual := profile.height_at(float(event.x), float(event.z))
			profile._events[section] = {}
			var without := profile.height_at(float(event.x), float(event.z))
			profile._events[section] = event
			if absf(actual - without) > 0.025: geometry_events += 1
	check(kinds.size() == 6 and repeatable, "Six seeded event types reproduce exactly over 120 km")
	check(late_strength > early_strength + 0.6, "Mud and terrain obstacles grow gradually over the expedition")
	check(geometry_events > 35, "Jumps, ditches, washouts, potholes and rocks change real collision heights")
	var pothole_profile := DriveTerrain.new()
	var pothole_depth := 0.0
	for section in 200:
		var event := pothole_profile.road_event(section)
		if event.is_empty() or event.kind != "potholes": continue
		var x := float(event.x)
		var z := float(event.z) + 1.0
		var actual := pothole_profile.height_at(x, z)
		pothole_profile._events[section] = {}
		pothole_depth = pothole_profile.height_at(x, z) - actual
		break
	print("SEEDED POTHOLE central depth=%.3fm" % pothole_depth)
	check(pothole_depth > 0.20, "A central seeded pothole cuts more than 20cm into the actual terrain height field")
	var material_jump := 0.0
	var color_jump := 0.0
	for boundary in range(1, 6):
		var x := boundary * DriveTerrain.REGION
		var before := profile.sample(x - 0.01, profile.center_at(x - 0.01) + 18, false)
		var after := profile.sample(x + 0.01, profile.center_at(x + 0.01) + 18, false)
		for layer in 14: material_jump = maxf(material_jump, absf(float(before.material_weights[layer]) - float(after.material_weights[layer])))
		color_jump = maxf(color_jump, Vector3(before.color.r - after.color.r, before.color.g - after.color.g, before.color.b - after.color.b).length())
	check(material_jump < 0.01 and color_jump < 0.01, "Biome texture weights and colour remain continuous at region boundaries")
	var world := DriveWorld.new()
	root.add_child(world)
	world.hazards_enabled = true
	world.configure(1000003)
	world.update_stream(270)
	for tick in 3: await physics_frame; await process_frame
	var shelter := world.find_shelter(Vector3(200, 0, world.center_at(200)), 360, true)
	check(not shelter.is_empty() and float(shelter.position.x) >= 200, "An actual loaded roadside shelter is reachable ahead within 360 metres")
	if not shelter.is_empty():
		var pos: Vector3 = shelter.position
		check(world.is_sheltered(pos + Vector3.UP * 0.6) and not world.is_sheltered(pos + Vector3(0, 0.6, 8)), "Shelter protection applies only beneath the roof footprint")
		var query := PhysicsRayQueryParameters3D.create(world.to_global(pos + Vector3.UP), world.to_global(pos + Vector3.UP * 6))
		check(not world.get_world_3d().direct_space_state.intersect_ray(query).is_empty(), "The refuge has a physical roof above the vehicle")
		var road := Vector3(pos.x, pos.y + 1.2, world.center_at(pos.x))
		query = PhysicsRayQueryParameters3D.create(world.to_global(road), world.to_global(pos + Vector3.UP * 1.2))
		check(world.get_world_3d().direct_space_state.intersect_ray(query).is_empty(), "Open refuge entrance can be driven into directly from the road")
	var crate_clear := false
	for chunk: Node3D in world.chunks.values():
		var crate := chunk.get_node_or_null("RoadDebris") as RigidBody3D
		if crate != null: crate_clear = absf(world.terrain.road_distance(crate.position.x, crate.position.z)) > 10
	check(crate_clear, "Scavengable loose crate starts clear of the road")
	var anchor_kinds := {}
	for anchor: Node in get_nodes_in_group("winch_anchor"):
		if world.is_ancestor_of(anchor): anchor_kinds[str(anchor.get_meta("anchor_kind"))] = true
	check(anchor_kinds.has("tree") and anchor_kinds.has("pole") and anchor_kinds.has("rock"), "Real trees, poles and rocks expose usable winch anchor markers")
	world.position = Vector3(-2048, 0, 128)
	check((world.find_shelter(Vector3(200, 0, world.center_at(200)), 360, true).position as Vector3).distance_to(shelter.position) < 0.001, "Shelter navigation remains in logical coordinates after origin rebasing")
	world.queue_free()
	await process_frame
	await process_frame
	check(get_nodes_in_group("winch_anchor").is_empty() and get_nodes_in_group("storm_shelter").is_empty(), "Unloaded world releases all shelter and anchor registrations")
	print("EXPEDITION WORLD %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
