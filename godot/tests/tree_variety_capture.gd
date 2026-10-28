extends SceneTree
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var profile := DriveTerrain.new()
	var examples := {}
	for seed in [24601, 1000004]:
		profile.seed_value = seed
		profile.clear_caches()
		for region in 8:
			for site: Dictionary in profile.obstacles_in_region(region):
				if site.kind == "fallen_tree" and not examples.has(site.tree_coverage): examples[site.tree_coverage] = {"seed": seed, "site": site}
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	game._focus_pause_enabled = false
	root.add_child(game)
	game.set_process(false)
	game.world.set_process(false)
	game.vehicle.set_physics_process(false)
	game.weather.set_process(false)
	game.world.hazards_enabled = true
	game.overview_size = 38
	for label: String in ["across", "angled", "partial"]:
		if not examples.has(label):
			push_error("Missing tree variant " + label)
			quit(1)
			return
		var example: Dictionary = examples[label]
		var site: Dictionary = example.site
		game.run.world_seed = int(example.seed)
		game.world.configure(example.seed)
		game.world.update_stream(float(site.x))
		var x := float(site.x) - 11.0
		var z: float = game.world.center_at(x)
		game.vehicle.park()
		game.vehicle.position = Vector3(x, game.world.height_at(x, z) + 0.6, z)
		game.vehicle.heading = atan(float(game.world.terrain.road_frame(x, z).slope))
		game._update_camera(1, true)
		game._update_lighting()
		game.world.wind_force = 0.8
		game.road_leaves.reset_particles()
		game.road_leaves._process(0.3)
		for frame in 25: await process_frame
		await RenderingServer.frame_post_draw
		root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/visibility-fix/tree-" + label + ".png"))
		print("TREE CAPTURE ", label, " seed=", example.seed, " x=", site.x, " yaw=", rad_to_deg(float(site.tree_yaw)), " scale=", site.tree_scale)
	game.queue_free()
	await process_frame
	quit()
