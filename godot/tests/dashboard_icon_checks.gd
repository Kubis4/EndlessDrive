extends SceneTree
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	print("%s %s" % ["PASS" if ok else "FAIL", title])
	if not ok: failures += 1
func _run() -> void:
	var game = load("res://scenes/main.tscn").instantiate()
	game._ephemeral = true
	root.add_child(game)
	game.vehicle.set_physics_process(false)
	var ui: Control = game.hud.expedition_ui
	ui.touch_mode = 1
	ui.set_equipment_open(true)
	var icons_fit := true
	var captions_fit := true
	for resolution: Vector2i in [Vector2i(1280, 720), Vector2i(844, 390), Vector2i(915, 412)]:
		root.size = resolution
		await process_frame
		await process_frame
		ui._layout()
		for key in ["drive_mode", "differential", "winch"]:
			var sector: Button = ui.get_node(key)
			var icon: TextureRect = sector.get_node("Icon")
			for corner in [Vector2.ZERO, Vector2(icon.size.x, 0), icon.size, Vector2(0, icon.size.y)]:
				icons_fit = icons_fit and sector._has_point(icon.position + corner)
			var caption: Label = sector.get_node("Caption")
			var font := caption.get_theme_font("font")
			var extent := font.get_string_size(caption.text, HORIZONTAL_ALIGNMENT_LEFT, -1, caption.get_theme_font_size("font_size"))
			captions_fit = captions_fit and extent.x <= caption.size.x
		for key in ["lights", "camera", "pause", "brake", "handbrake", "throttle", "engine", "service", "cruise", "InputMode"]:
			var button: Button = ui.get_node(key)
			icons_fit = icons_fit and button.get_minimum_size().x <= button.size.x + 0.01 and button.get_minimum_size().y <= button.size.y + 0.01
	check(icons_fit, "Every dashboard icon fits its button or curved sector at desktop and phone sizes")
	check(captions_fit, "Equipment labels fit their own sector without clipping")
	game.run.headlights = true
	game.run.high_beams = false
	ui.refresh(game.hud)
	check(ui.get_node("lights").icon.resource_path.ends_with("headlights.svg") and ui.get_node("lights").get_theme_color("icon_normal_color").g > 0.8, "Low beam uses the sloped-ray symbol and a green indicator")
	game.run.high_beams = true
	ui.refresh(game.hud)
	check(ui.get_node("lights").icon.resource_path.ends_with("headlights_high.svg") and ui.get_node("lights").get_theme_color("icon_normal_color").b > 0.9, "High beam switches to the straight-ray symbol and a blue indicator")
	var common := true
	for key in ["fuel", "oil", "coolant", "battery", "engine", "brakes", "radiator"]:
		var icon := DriveIcons.texture(key)
		common = common and icon != null and icon.get_width() >= 128 and icon.resource_path.ends_with(key + ".svg")
	check(common, "Vehicle service and driving HUD share the sharp, authored dashboard symbols")
	check(ui.get_node("Trip").get_theme_stylebox("panel") is StyleBoxTexture, "Dashboard plates use the shared scalable material skins")
	game.queue_free()
	await process_frame
	print("DASHBOARD ICONS failures=", failures)
	quit(1 if failures else 0)
