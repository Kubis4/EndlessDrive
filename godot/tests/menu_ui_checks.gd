extends SceneTree
var checks := 0
var failures := 0


func _initialize() -> void:
	call_deferred("_run")


func frames(count: int) -> void:
	for i in count: await process_frame


func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error("FAIL: " + label)


func _run() -> void:
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	await frames(4)
	var hud: DriveHUD = game.hud
	check(hud._drive_state != null and hud._road_assist != null and hud._lights_button != null, "Saved editable HUD binds the new gear, road assist and light controls")
	game._action("lights")
	await frames(2)
	check(hud._lights_button.button_pressed == game.run.headlights, "Keyboard light toggle updates the visible dashboard control")
	game._action("lights")
	var original_pose: Vector3 = game.vehicle.position
	game.vehicle.position = Vector3(280, game.world.height_at(280, game.world.center_at(280) + 22) + 0.6, game.world.center_at(280) + 22)
	game.vehicle.heading = 0
	hud._update_road_assist()
	check(not hud._assist_panel.visible and hud._road_assist.text.is_empty(), "Off-road driving keeps direction helper labels hidden")
	game.vehicle.position = original_pose
	check(not hud._road_info.get_parent().get_global_rect().intersects(hud._stats.fuel.value.get_parent().get_parent().get_parent().get_parent().get_global_rect()), "Compact driving status and road panels do not overlap")
	game._session_started = false
	game._had_save = false
	game._open_menu(false)
	var elapsed: float = game.run.elapsed
	await frames(4)
	check(paused and game.menu.menu_open and not game.hud.visible and game.run.elapsed == elapsed, "Main menu freezes survival and vehicle while showing the world")
	var background := game.menu._stage.get_node("PaperRoad") as TextureRect
	check(background.texture.resource_path.ends_with("paper-road-izh.png") and not game.menu._vehicle_preview.visible, "IZH uses the fully composed menu illustration")
	game.menu.set_menu_vehicle("jeep", Color.WHITE)
	check(background.texture.resource_path.ends_with("paper-road-empty.png") and game.menu._vehicle_preview.visible and game.menu._vehicle_preview.vehicle_id == "jeep", "Jeep retains its own selectable model in the menu")
	game.menu.set_menu_vehicle("izh", Color.WHITE)
	check(game.menu._continue_button.disabled, "Fresh install cannot continue an invented save")
	game.menu.show_panel("settings")
	await frames(2)
	var reset := game.menu._panels.settings.get_node("ObnoviťPredvolené") as Button
	check(game.menu._root.get_global_rect().encloses(reset.get_global_rect()), "Graphics and FPS settings leave the reset button fully on screen")
	game._settings_changed("volume", 0.25)
	game._settings_changed("camera", 1.15)
	check(is_equal_approx(game._volume_preference, 0.25) and is_equal_approx(game._camera_preference, 1.15), "Settings apply without writing a player's preferences during tests")
	var previous_biome := DriveTerrain.starting_biome(game.run.world_seed)
	game.effects._count = 12
	game._menu_action("new")
	await frames(4)
	check(not paused and not game.menu.menu_open and game.hud.visible and game._session_started, "New ride enters the game and enables controls")
	check(game.run.world_seed >= DriveTerrain.VARIED_SEED_MIN and game.world.biome_at(0) != previous_biome, "New ride actually installs a different starting environment")
	check(game.effects._count == 0 and game.effects._tracks.multimesh.visible_instance_count == 0, "New journey clears the preceding world's visual tracks")
	game._open_menu()
	check(not game.menu._continue_button.disabled, "Current ride enables Continue without requiring a fake disk save")
	check(game.menu._vehicle_preview.vehicle_id == game.run.vehicle_id and game.menu._vehicle_preview._model.scene_file_path == preload("res://scripts/vehicle_catalog.gd").DATA[game.run.vehicle_id].scene, "Main menu uses the active journey's real vehicle rig")
	game._menu_action("garage")
	await frames(4)
	check(paused and game._garage_from_menu and game.hud._modal_kind == "service", "Menu garage opens the real car/cargo maintenance panel")
	game._action("resume")
	check(paused and game.menu.menu_open and not game.hud.visible, "Closing menu garage returns to the menu, not an unrequested drive")
	game._menu_action("continue")
	check(not paused and not game._in_menu and game.vehicle.controls_enabled, "Continue resumes a safely parked car")
	game.vehicle.park()
	game._action("service")
	await frames(3)
	var screen: Rect2 = game.hud._root.get_global_rect()
	check(screen.encloses(game.hud._service.get_global_rect()), "Expanded component/cargo service panel fits the viewport")
	check(game.hud._car_diagram.conditions == game.run.parts, "Component diagram shows the actual installed component conditions")
	check(game.hud._car_diagram.vehicle_id == game.run.vehicle_id, "Service diagram shows the active vehicle model")
	game.run.inventory.paintcan = 1
	game._action("paint:a54e43")
	check(game._paint_materials.size() > 0 and (game._paint_materials[0].get_shader_parameter("tint") as Color).is_equal_approx(Color("a54e43")), "Painting visibly changes newly authored car body materials")
	game.run.cargo_parts.append({"uid": "ui/worn", "kind": "part", "slot": "brakes", "variant": "disc", "condition": 0.1, "amount": 1.0})
	var health: float = game.run.parts.brakes
	game.hud._request_install(game.run.cargo_parts[-1])
	check(game.hud._install_confirmation.visible and game.run.parts.brakes == health, "Installing a worse component needs confirmation and does not mutate the car early")
	game.hud._install_confirmation.hide()
	game._action("resume")
	var building: Dictionary = game.world.buildings[0]
	game.vehicle.position = building.position + Vector3(0, 0.6, 8)
	game._action("search")
	await frames(3)
	check(screen.encloses(game.hud._loot.get_global_rect()), "Expanded loot icons/comparison panel fits the viewport")
	game.hud._loot_rows["0/fuel"].amount.value = 0.5
	game.hud._loot_rows["0/fuel"].check.button_pressed = true
	game.hud._loot_filter = "parts"
	game.hud.refresh_loot(true)
	check(game.hud.selected_loot().size() == 1 and game.hud.selected_loot()[0].amount == 0.5, "Category filters preserve the player's explicit loot selection and quantity")
	game._action("resume")
	game.queue_free()
	await frames(2)
	print("MENU/UI: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
