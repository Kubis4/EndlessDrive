extends SceneTree
## Render a real GPU frame without touching the player's save.


func _initialize() -> void:
	call_deferred("_capture")


func _capture() -> void:
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	var output := "res://../build/godot-preview.png"
	for argument: String in OS.get_cmdline_user_args():
		if argument == "--car-close":
			game.overview_size = 15
			game.overview_offset = Vector3(12, 10, 12)
			game._update_camera(1, true)
		if argument == "--car-rear":
			game.overview_size = 15
			game.overview_offset = Vector3(-12, 8, 12)
			game._update_camera(1, true)
		if argument == "--brake-lights":
			game.vehicle.set_touch("brake", true, 9876)
		if argument == "--no-shadows": game.sun.shadow_enabled = false
		if argument.begins_with("--clock="):
			game.world.clock = argument.trim_prefix("--clock=").to_float()
		if argument == "--hazards":
			game.world.hazards_enabled = true
		if argument.begins_with("--route="):
			game.world.terrain.route_choices = {"0": int(argument.trim_prefix("--route="))}
			game.world.terrain.clear_caches()
		if argument == "--storm":
			game.weather.storm_active = true
			game.weather._storm_left = 60
			game.weather.storm_edge = game.vehicle.position.x + 10
			game.weather.intensity = 1
		if argument.begins_with("--weather="):
			game.weather.forced_mode = argument.trim_prefix("--weather=")
		if argument == "--fx":
			game.vehicle.speed = 12
		if argument == "--upgrades":
			game._open_menu(false)
			game.profile.bank = 35
			game._show_upgrades()
		if argument == "--menu":
			game._session_started = false
			game._had_save = false
			game._open_menu(false)
		if argument == "--settings":
			game.menu.show_panel("settings")
		if argument == "--howto":
			game._open_menu(false)
			game.menu.show_panel("howto")
		if argument.begins_with("--shop="):
			var kind := argument.trim_prefix("--shop=")
			for region in range(1, 7):
				var stop := DriveStops.next_stop(region * 960.0, game.run.world_seed)
				if stop.kind != kind: continue
				game.world.update_stream(float(stop.x))
				var target: Vector3 = game.world.buildings[int(stop.id)].position
				game.vehicle.position = target + Vector3(0, 0.6, -signf(float(stop.offset)) * 8.0)
				game.vehicle.park()
				game._update_camera(1.0, true)
				game._action("search")
				break
		if argument == "--shop-service":
			game._action("service")
			game.hud._service_tabs.current_tab = 2
		if argument.begins_with("--at="):
			var x := argument.trim_prefix("--at=").to_float()
			game.world.update_stream(x)
			var z: float = game.world.terrain.road_centers(x)[-1]
			game.vehicle.position = Vector3(x, game.world.height_at(x, z) + 0.6, z)
			game.run.distance = x
			game.hud._message_time = 0.0
			game.vehicle.park()
			game._update_camera(1.0, true)
		if argument.begins_with("--output="):
			output = argument.trim_prefix("--output=")
		if argument == "--service":
			game._action("service")
		if argument == "--clean":
			game.hud.visible = false
		if argument == "--loot":
			var building: Dictionary = game.world.buildings[0]
			var pose: Vector3 = building.position + Vector3(0, 0.6, 8)
			game.vehicle.position = pose
			game.vehicle.park()
			game._update_camera(1.0, true)
			game._action("search")
		if argument == "--selected" and game.hud._modal_kind == "loot":
			game.hud._loot_rows["0/fuel"].amount.value = 1.5
			game.hud._loot_rows["0/fuel"].check.button_pressed = true
			game.hud._loot_rows["0/part/0"].check.button_pressed = true
		if argument == "--cargo":
			game.run.open_loot(0)
			game.run.take_loot(0, "0/part/0", 1.0)
			game._action("service")
			game.hud._service_tabs.current_tab = 1
	for frame in 30:
		await process_frame
	await RenderingServer.frame_post_draw
	var result := root.get_texture().get_image().save_png(ProjectSettings.globalize_path(output))
	print("CAPTURE: ", error_string(result), " ", output)
	game.queue_free()
	await process_frame
	quit(0 if result == OK else 1)
