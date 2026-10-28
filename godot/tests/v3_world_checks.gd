extends SceneTree
const Resistance = preload("res://scripts/surface_resistance.gd")
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, label: String) -> void:
	print("%s %s" % ["PASS" if ok else "FAIL", label])
	if not ok: failures += 1
func terminal(surface: Dictionary) -> float:
	var speed := 0.0
	for i in 3600:
		speed = maxf(0.0, speed + (6.0 - Resistance.deceleration(surface, speed)) / 60.0)
	return speed
func _run() -> void:
	var road := {"road": 1.0, "drag": 0.4, "material": DriveTerrain.SurfaceKind.GRAVEL}
	var shoulder := {"road": 0.0, "drag": 1.25, "material": DriveTerrain.SurfaceKind.DIRT}
	var water := {"road": 1.0, "drag": 2.8, "water": 0.6, "material": DriveTerrain.SurfaceKind.DIRT}
	var mud := {"road": 1.0, "drag": 2.5, "material": DriveTerrain.SurfaceKind.MUD}
	var ice := {"road": 0.0, "drag": 1.8, "material": DriveTerrain.SurfaceKind.ICE}
	var road_speed := terminal(road)
	for entry: Array in [["off-road", shoulder], ["ford", water], ["mud", mud], ["ice", ice]]:
		var speed := terminal(entry[1])
		print("RESISTANCE %s speed=%.2f road=%.2f" % [entry[0], speed, road_speed])
		check(speed < road_speed * 0.65 and speed > 1.0, "%s slows a powered vehicle while preserving low-speed escape" % entry[0])
		check(Resistance.deceleration(entry[1], 18.0) > 6.0, "%s dissipates an incoming high-speed crossing" % entry[0])
	check(Resistance.deceleration(water, 0) < 1.0, "Water has no artificial standing brake")
	var profile := DriveTerrain.new()
	var path := profile.mountain_path(3)
	var gain := 0.0
	for p: Vector3 in path: gain = maxf(gain, p.y - path[0].y)
	check(gain > 54.0, "Hairpins climb over 54 metres on the real road")
	var steep_banks := 0
	for x in range(3140, 3400, 8):
		var lanes := profile.road_centers(x)
		if lanes.size() < 2: continue
		for z in range(ceili(lanes[0] + 6.0), floori(lanes[-1] - 6.0)):
			if absf(profile.height_at(x, z + 1) - profile.height_at(x, z)) > 1.2: steep_banks += 1
	check(steep_banks > 25, "Physical banks between hairpin arms prevent a flat off-road shortcut")
	var world := DriveWorld.new()
	root.add_child(world)
	for seed in [1000002, 1000004, 1000006]:
		world.configure(seed)
		world._create_chunk(1)
		var chunk: Node3D = world.chunks[1]
		# Count actual planted roots; old name-based counts included crown meshes
		# and predated the user's reduction of overlapping trees in V4/V5.
		var sites: Array = chunk.get_meta("tree_sites", [])
		var cells := {}
		for site: Dictionary in sites: cells[preload("res://scripts/tree_scatter.gd").cell(site.position)] = true
		if seed == 1000002:
			check(chunk.find_children("SandstoneWall*", "MeshInstance3D", true, false).size() == 24, "Copper canyon has continuous sandstone strata instead of repeated cliff blocks")
			var z := world.center_at(150)
			check(world.height_at(150, z + 31) > world.height_at(150, z) + 9, "Canyon floor rises through eroded sandstone benches")
		else:
			check(not sites.is_empty() and sites.size() <= world.woodland_scenery_attempts + 8 and cells.size() == sites.size(), "Biome %d contains bounded, non-overlapping planted trees" % world.biome_at(150))
			var batches: Dictionary = chunk.get_meta("prop_batches", {})
			check(batches.size() < 70, "Dense trees share instanced meshes instead of per-tree draw calls")
			if seed == 1000006:
				var pools := world.terrain.pools_near(120)
				check(pools.size() >= 3 and chunk.find_children("MarshReeds*", "Node3D", true, false).size() > 5, "Swamp has off-road backwaters, wetland trees and reed beds")
		await process_frame
	world.queue_free()
	await process_frame
	var game = load("res://scenes/main.tscn").instantiate()
	game._ephemeral = true
	root.add_child(game)
	await process_frame
	var outline: Node = game.vehicle.get_node("VisibilityOutline")
	check(not outline.copies.is_empty() and outline.copies.all(func(n: MeshInstance3D) -> bool: return n.mesh == (n.get_parent() as MeshInstance3D).mesh and n.cast_shadow == GeometryInstance3D.SHADOW_CASTING_SETTING_OFF), "Occlusion rims share the real moving car meshes and cast no extra shadows")
	game.set_process(false)
	game.vehicle.set_physics_process(false)
	# A vertical view over the road has no natural canopy/building occluder.
	game.camera.global_position = game.vehicle.global_position + Vector3.UP * 40.0
	game.camera.look_at(game.vehicle.global_position, Vector3.FORWARD)
	var blocker := StaticBody3D.new()
	var shape := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(8, 8, 8)
	shape.shape = box
	blocker.add_child(shape)
	root.add_child(blocker)
	blocker.global_position = game.camera.global_position.lerp(game.vehicle.global_position + Vector3.UP * 0.7, 0.5)
	await physics_frame
	await process_frame
	outline._timer = 0.0
	outline._process(0.1)
	check(outline.occluded and outline.copies.all(func(n: MeshInstance3D) -> bool: return n.visible), "A real occluding body activates the car rim")
	blocker.queue_free()
	await physics_frame
	await process_frame
	outline._timer = 0.0
	outline._process(0.1)
	check(not outline.occluded and outline.copies.all(func(n: MeshInstance3D) -> bool: return not n.visible), "Unobstructed driving disables the rim and its extra draws")
	game.queue_free()
	await process_frame
	print("V3 WORLD failures=", failures)
	quit(1 if failures else 0)
