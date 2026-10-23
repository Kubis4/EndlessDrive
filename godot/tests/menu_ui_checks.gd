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
	game._session_started = false
	game._had_save = false
	game._open_menu(false)
	var elapsed: float = game.run.elapsed
	await frames(4)
	check(paused and game.menu.menu_open and not game.hud.visible and game.run.elapsed == elapsed, "Main menu freezes survival and vehicle while showing the world")
	check(game.menu._continue_button.disabled, "Fresh install cannot continue an invented save")
	game.menu.show_panel("settings")
	game._settings_changed("volume", 0.25)
	game._settings_changed("camera", 1.15)
	check(is_equal_approx(game._volume_preference, 0.25) and is_equal_approx(game._camera_preference, 1.15), "Settings apply without writing a player's preferences during tests")
	game._menu_action("new")
	await frames(4)
	check(not paused and not game.menu.menu_open and game.hud.visible and game._session_started, "New ride enters the game and enables controls")
	game._open_menu()
	check(not game.menu._continue_button.disabled, "Current ride enables Continue without requiring a fake disk save")
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
