extends SceneTree
var failures := 0
var checks := 0

func _initialize() -> void:
	call_deferred("_run")

func frames(count: int) -> void:
	for index in count: await process_frame

func check(value: bool, title: String) -> void:
	checks += 1
	if not value:
		failures += 1
		push_error("FAIL: " + title)

func _run() -> void:
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	check(game.get_node("EditorTerrainPreview").get_child_count() > 0, "Main scene contains visible terrain before running scripts")
	check(game.get_node("Vehicle/Visual").get_child_count() > 0, "Vehicle model and chassis exist in the scene file")
	var pedal := game.get_node("HUD/Root/PedalsLeft") as Button
	pedal.name = "MyRenamedPedal"
	pedal.offset_left += 17
	pedal.offset_right += 17
	pedal.text = "MOJA ŠÍPKA"
	var expected_offset := pedal.offset_left
	var toast := game.get_node("HUD/Root/ToastPanel") as PanelContainer
	toast.offset_top += 19
	toast.offset_bottom += 19
	var expected_toast_offset := toast.offset_top
	var label := game.get_node("MainMenu/Root/Stage/Home/OtvorenáCestaTvojPríbeh") as Label
	label.position += Vector2(13, 7)
	label.text = "MOJE MENU"
	var expected_position := label.position
	game.get_node("Camera3D").position = Vector3(-30, 48, 48)
	game.get_node("Camera3D").size = 61
	root.add_child(game)
	await frames(5)
	check(game.hud.has_meta("authored_layout") and game.menu.has_meta("authored_layout"), "Game consumes the actual editable UI scenes")
	check(game.hud._pedals.left == pedal and pedal.text == "MOJA ŠÍPKA" and is_equal_approx(pedal.offset_left, expected_offset), "Manual move, rename and text survive startup and binding")
	check(label.text == "MOJE MENU" and label.position.is_equal_approx(expected_position), "Menu layout is not rebuilt or repositioned at runtime")
	check(game.overview_offset == Vector3(-30, 48, 48) and game.overview_size == 61, "Follow camera uses hand-authored rig position and lens size")
	check(not game.has_node("EditorTerrainPreview") and not game.has_node("StartCampPreview"), "Editor snapshot never duplicates runtime terrain or collisions")
	var actions: Array[String] = []
	game.hud.requested.connect(func(action: String) -> void: actions.append(action))
	game.hud._engine_button.pressed.emit()
	check(actions == ["engine"] and game.run.engine_running, "Saved button signal remains connected to gameplay")
	var touches: Array = []
	game.hud.touch_changed.connect(func(action: String, down: bool, _source: int) -> void: touches.append([action, down]))
	pedal.button_down.emit()
	pedal.button_up.emit()
	check(touches == [["left", true], ["left", false]], "Serialized pedal bindings keep press/release behavior after renaming")
	game._open_menu(false)
	game.menu.show_panel("settings")
	game.menu._volume_slider.value = 0.37
	check(is_equal_approx(game._volume_preference, 0.37), "Editable settings slider still changes volume")
	var settings := game.menu._panels.settings as Node
	game.menu.show_panel("howto")
	game.menu.show_panel("settings")
	check(game.menu._panels.settings == settings, "Switching menu panels preserves authored nodes instead of rebuilding")
	game.menu._distance = 12340
	game.menu._has_ride = true
	game.menu.show_panel("new")
	check(game.menu._confirmation_distance.text.contains("12,3"), "Native confirmation updates actual distance instead of retaining preview data")
	game.menu.show_panel("garage")
	check(not game.menu._vehicle_button.disabled, "Native car-inspection button updates its availability")
	game.profile.bank = 20
	game._show_upgrades()
	var upgrade := game.menu._upgrade_buttons.engine as Button
	upgrade.pressed.emit()
	check(game.profile.upgrades.engine == 1 and game.menu._upgrade_buttons.engine == upgrade, "Authored garage buttons purchase upgrades without rebuilding the layout")
	game._menu_action("continue")
	game.vehicle.park()
	var building: Dictionary = game.world.buildings[0]
	check(game.world.chunks[0].get_node("Building_0_workshop").scene_file_path == "res://scenes/world/start_camp.tscn", "Start camp prefab is used in the real streamed game")
	game.vehicle.position = building.position + Vector3(0, 0.6, 8)
	game._action("search")
	await frames(5)
	check(game.hud._root.get_global_rect().encloses(game.hud._loot.get_global_rect()), "Authored loot layout fits the viewport")
	game._action("resume")
	check(is_equal_approx(toast.offset_top, expected_toast_offset), "Authored toast position is restored after leaving a modal")
	game.queue_free()
	await frames(2)
	print("EDITOR SCENES: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
