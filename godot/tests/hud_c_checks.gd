extends SceneTree
const Locale = preload("res://scripts/localization.gd")
var checks := 0
var failures := 0

func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error("FAIL: " + title)
func frames(count := 2) -> void:
	for tick in count: await process_frame
func touch(hud: DriveHUD, index: int, position: Vector2, down: bool) -> void:
	var event := InputEventScreenTouch.new()
	event.index = index
	event.position = position
	event.pressed = down
	hud._input(event)
func click(position: Vector2) -> void:
	for down in [true, false]:
		var event := InputEventMouseButton.new()
		event.position = position
		event.button_index = MOUSE_BUTTON_LEFT
		event.pressed = down
		root.push_input(event, true)
		await process_frame

func _run() -> void:
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	await frames(4)
	game.vehicle.set_physics_process(false)
	var hud: DriveHUD = game.hud
	var ui: Control = hud.expedition_ui
	ui.touch_mode = 1
	ui._layout()
	check(ui.scene_file_path == "res://scenes/ui/expedition_hud.tscn", "HUD uses the editor-visible saved scene")
	check(not ui.equipment_open and not ui.get_node("drive_mode").visible, "Driving starts with equipment collapsed")
	check(not hud._dial.is_visible_in_tree(), "Old oversized dashboard is hidden")
	hud._update_equipment_controls()
	check(not hud._root.get_node("EquipmentControls").visible, "HUD refresh keeps the duplicate equipment strip hidden")
	hud.show_modal("")
	check(not hud._root.get_node("EquipmentControls").visible, "Returning from menus also keeps the duplicate equipment strip hidden")
	var camera_mode: int = game._camera_mode
	await click(ui.get_node("camera").get_global_rect().get_center())
	check(game._camera_mode == (camera_mode + 1) % 3, "Camera icon button still cycles the gameplay camera")
	await click(ui.get_node("Tools").get_global_rect().get_center())
	check(ui.equipment_open, "Real mouse routing opens the equipment fan")
	for key: String in ["Tools", "service"]:
		var button := ui.get_node(key) as Button
		var glyph := button.get_node("Glyph") as TextureRect
		check(glyph.get_rect().get_center().distance_to(button.size * 0.5) < 0.1 and glyph.mouse_filter == Control.MOUSE_FILTER_IGNORE, key + " wrench stays centered and does not consume clicks")
	var previous: bool = game.run.four_wheel_drive
	var fan: Button = ui.get_node("drive_mode")
	var local_point := fan.size * 0.5 + Vector2.from_angle(deg_to_rad(215)) * 140
	check(fan._has_point(local_point) and not fan._has_point(Vector2(180, 180)), "Fan hit region excludes the central hub")
	await click(fan.global_position + local_point)
	check(game.run.four_wheel_drive != previous, "Fan clicks reach drivetrain gameplay instead of overlapping sectors")
	await click(ui.get_node("engine").get_global_rect().get_center())
	check(game.run.engine_running, "Engine can be started through the equipment controls")
	ui.set_equipment_open(false)
	check(ui.get_node("Tools/Glyph").visible, "Collapsed and expanded equipment retain the centered wrench")
	var left: Vector2 = hud._pedals.left.get_global_rect().get_center()
	var gas: Vector2 = hud._pedals.throttle.get_global_rect().get_center()
	touch(hud, 11, left, true)
	touch(hud, 12, gas, true)
	check(game.vehicle._touches.get(11) == "left" and game.vehicle._touches.get(12) == "throttle", "Two fingers steer and accelerate together")
	var tools_center: Vector2 = ui.get_node("Tools").get_global_rect().get_center()
	touch(hud, 14, tools_center, true)
	touch(hud, 14, tools_center, false)
	check(ui.equipment_open and game.vehicle._touches.has(12), "Another finger can open equipment while throttle remains held")
	var before_touch: bool = game.run.four_wheel_drive
	touch(hud, 14, fan.global_position + local_point, true)
	touch(hud, 14, fan.global_position + local_point, false)
	check(game.run.four_wheel_drive != before_touch, "Direct touch activates the correct fan sector")
	ui.set_equipment_open(false)
	var canceled := InputEventScreenTouch.new()
	canceled.index = 15
	canceled.position = tools_center
	canceled.canceled = true
	touch(hud, 15, tools_center, true)
	hud._input(canceled)
	check(not ui.equipment_open, "System-canceled touches do not trigger equipment actions")
	touch(hud, 11, left, false)
	check(not game.vehicle._touches.has(11) and game.vehicle._touches.has(12), "Releasing steering preserves the accelerator finger")
	touch(hud, 11, left, true)
	var drag := InputEventScreenDrag.new()
	drag.index = 11
	drag.position = ui.size * 0.5
	hud._input(drag)
	check(not game.vehicle._touches.has(11), "Dragging outside the steering button releases it")
	game._action("pause")
	check(game.vehicle._touches.is_empty() and hud._active_fingers.is_empty() and not ui.visible, "Pause clears all held controls and hides the driving HUD")
	game._action("resume")
	touch(hud, 12, gas, true)
	ui._cycle_input_mode()
	check(ui.touch_mode == 2 and game.vehicle._touches.is_empty(), "Changing touch mode releases any held pedal")
	touch(hud, 13, gas, true)
	check(not game.vehicle._touches.has(13) and not hud._pedals.throttle.visible, "Desktop mode does not intercept touches in hidden pedal locations")
	ui.touch_mode = 1
	ui._layout()
	game.vehicle.set_meta("winch_state", "attached")
	ui.refresh(hud)
	check(ui.get_node("winch_pull").visible, "Attached winch exposes the reel control even with fan closed")
	game.vehicle.set_meta("winch_state", "idle")
	game.run.temperature = 115
	game.run.fuel = 1
	Locale.configure(1)
	ui.refresh(hud)
	check(ui.get_node("Alert").text.begins_with("Engine overheating"), "Only the highest priority warning is shown and localized")
	check(ui.get_node("service").tooltip_text == "VEHICLE" and ui.get_node("InputMode").tooltip_text == "TOUCH · ON", "New equipment labels switch to English")
	Locale.configure(0)
	ui.set_equipment_open(true)
	for dimensions: Vector2i in [Vector2i(1280, 720), Vector2i(844, 390), Vector2i(915, 412)]:
		root.size = dimensions
		await frames(3)
		ui._layout()
		var screen := Rect2(Vector2.ZERO, ui.size)
		for key: String in ["Trip", "lights", "camera", "pause", "left", "right", "brake", "throttle", "Tools", "service", "engine", "cruise", "InputMode"]:
			var button := ui.get_node(key) as Control
			check(screen.encloses(button.get_rect()), "%s stays inside viewport at %s" % [key, dimensions])
		check(not ui.get_node("engine").get_rect().intersects(ui.get_node("left").get_rect()), "Equipment avoids steering at %s" % dimensions)
		check(not ui.get_node("InputMode").get_rect().intersects(ui.get_node("brake").get_rect()), "Equipment avoids pedals at %s" % dimensions)
	game.queue_free()
	await frames()
	print("HUD C: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
