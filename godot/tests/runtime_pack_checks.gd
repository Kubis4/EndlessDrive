extends SceneTree
## Invoke this saved script by its absolute path with --main-pack and an
## empty --path directory. No source-project fallback can mask missing assets.
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var catalog := load("res://scripts/vehicle_catalog.gd") as Script
	for id: String in ["izh", "jeep"]:
		var scene := load(str(catalog.DATA[id].scene)) as PackedScene
		assert(scene != null)
		var visual := scene.instantiate()
		assert(visual.get_node_or_null("Wheel_FL") != null)
		visual.free()
	for model: String in ["vegetation/broadleaf_a", "vegetation/calocedrus", "boundary/jagged_crag", "obstacles/fallen_tree", "expedition/locomotive"]:
		# Every dynamically named runtime bank must resolve in a stand-alone pack.
		var path := "res://assets/models/" + model + ".tscn"
		if not ResourceLoader.exists(path):
			push_error("Missing runtime model: " + path)
			quit(1)
			return
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate()
	game.set("_ephemeral", true)
	root.add_child(game)
	for frame in 3: await process_frame
	assert(game.get("vehicle") != null and game.get("world") != null)
	game.call("_open_menu", false)
	game.call("_show_upgrades")
	assert(game.get("menu")._vehicle_choices != null)
	paused = false
	game.queue_free()
	await process_frame
	print("RUNTIME PACK: vehicles, named model banks, gameplay and garage loaded without source files")
	quit()
