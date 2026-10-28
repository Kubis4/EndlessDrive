extends SceneTree
var checks := 0
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, label: String) -> void:
	checks += 1
	print("%s %s" % ["PASS" if ok else "FAIL", label])
	if not ok: failures += 1
func _run() -> void:
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	game._focus_pause_enabled = false
	root.add_child(game)
	game.set_process(false)
	game.vehicle.set_physics_process(false)
	game.world.set_process(false)
	game.world.configure(1000004)
	game.world.update_stream(180)
	var leaf_total := 0
	var twig_total := 0
	var limits := true
	var grounded := true
	var leaf_point := Vector3.INF
	for chunk: Node3D in game.world.chunks.values():
		var litter: Dictionary = chunk.get_meta("road_litter", {})
		if litter.is_empty(): continue
		leaf_total += int(litter.leaves)
		twig_total += int(litter.twigs)
		limits = limits and int(litter.leaves) <= 42 and int(litter.twigs) <= 6
		for point: Vector3 in litter.contacts:
			grounded = grounded and absf(point.y - game.world.rendered_height_at(point.x, point.z) - 0.018) < 0.001
			leaf_point = point
	check(leaf_total > 0 and twig_total > 0, "The real forest road contains scattered leaves and small branches")
	check(limits and grounded, "Ground litter follows terrain with bounded per-chunk geometry")
	print("FOREST LITTER leaves=", leaf_total, " twigs=", twig_total)
	var emitter: Node3D = game.road_leaves
	check(emitter.scene_file_path == "res://assets/effects/road_leaves.tscn", "Leaf effect is a saved editable Godot scene")
	game.vehicle.position = Vector3(180, game.world.height_at(180, game.world.center_at(180)) + 0.6, game.world.center_at(180))
	game.vehicle.speed = 6
	emitter._process(0.3)
	check(emitter.foliage_context and emitter.particles.emitting, "Moving through trees lifts airborne leaves")
	game.vehicle.speed = 0
	game.world.wind_force = 0
	emitter._process(0.3)
	check(not emitter.particles.emitting, "Still air and a parked car stop leaf emission")
	game.world.wind_force = 1
	emitter._process(0.3)
	check(emitter.particles.emitting, "Wind can carry leaves while the car is parked")
	game._settings_changed("quality", 0)
	check(emitter.particles.amount <= 12 and emitter.particles.cast_shadow == GeometryInstance3D.SHADOW_CASTING_SETTING_OFF, "Low quality reduces the one local shadowless leaf emitter")
	game._settings_changed("quality", 2)
	check(emitter.particles.amount == 32, "High quality restores the bounded 32-leaf budget")
	if leaf_point.is_finite():
		game.vehicle.position = leaf_point + Vector3.UP * 0.6
		emitter.scatter_ground_leaves()
		var moved := 0
		for chunk: Node3D in game.world.chunks.values(): moved += chunk.get_meta("road_litter", {}).get("cleared", {}).size()
		check(moved > 0, "The car scatters the flat leaves underneath it without adding physics bodies")
		var leaf_chunk: Node3D = game.world.chunks[floori(leaf_point.x / DriveWorld.CHUNK_LENGTH)]
		var leaf_data: Dictionary = leaf_chunk.get_meta("road_litter", {})
		var old_count: int = leaf_data.cleared.size()
		(leaf_data.batch as MultiMeshInstance3D).free()
		emitter.scatter_ground_leaves()
		check(leaf_data.cleared.size() == old_count, "Unloaded leaf visuals are safely ignored even while their chunk metadata remains")
	emitter.reset_particles()
	check(not emitter.particles.emitting, "Restart and origin-rebase reset airborne leaves")
	game.world.configure(1000001)
	game.world.update_stream(180)
	game.vehicle.position = Vector3(180, 1, game.world.center_at(180))
	game.vehicle.speed = 8
	emitter._process(0.3)
	check(not emitter.foliage_context and not emitter.particles.emitting, "The leaf effect stays off in the bare desert")
	var desert_leaves := 0
	for chunk: Node3D in game.world.chunks.values(): desert_leaves += int(chunk.get_meta("road_litter", {}).get("leaves", 0))
	check(desert_leaves == 0, "The desert road has no deciduous leaf carpet")
	game.queue_free()
	await process_frame
	print("FOREST DETAILS: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
