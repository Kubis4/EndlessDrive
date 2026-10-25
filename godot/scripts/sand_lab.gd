extends Node3D
## Playable isolated laboratory. No journey save or procedural biome changes.
const LabWorld = preload("res://scripts/sand_lab_world.gd")
var world: LabWorld
var vehicle: DriveVehicle
var run := DriveRun.new()
var effects: DriveEffects
var dust_enabled := false
var dust_button: Button
var status: Label
var _status_timer := 0.0

func _ready() -> void:
	$EditorSand.hide()
	$EditorSand.queue_free()
	$Camera3D.look_at(Vector3.ZERO)
	for entry: Array in [["throttle", KEY_W, KEY_UP], ["brake", KEY_S, KEY_DOWN], ["left", KEY_A, KEY_LEFT], ["right", KEY_D, KEY_RIGHT]]:
		if not InputMap.has_action(entry[0]): InputMap.add_action(entry[0])
		for code: int in entry.slice(1):
			var event := InputEventKey.new()
			event.physical_keycode = code
			if not InputMap.action_has_event(entry[0], event): InputMap.action_add_event(entry[0], event)
	world = LabWorld.new()
	world.name = "SandWorld"
	add_child(world)
	vehicle = $Vehicle
	vehicle.world = world
	vehicle.run = run
	vehicle.boundary_recovery_enabled = false
	run.engine_running = true
	run.fuel = 40
	run.four_wheel_drive = true
	run.variants.tires = "allterrain"
	for slot: String in run.parts: run.parts[slot] = 1.0
	reset_car()
	effects = DriveEffects.new()
	effects.trails_enabled = false
	effects.vehicle = vehicle
	effects.world = world
	add_child(effects)
	effects.set_process(false)
	effects.particles_enabled = false
	effects._tracks.hide()
	_build_ui()

func _build_ui() -> void:
	var layer := CanvasLayer.new()
	layer.name = "LabControls"
	add_child(layer)
	var panel := PanelContainer.new()
	panel.position = Vector2(18, 18)
	layer.add_child(panel)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 10)
	panel.add_child(column)
	var title := Label.new()
	title.text = "PIESKOVÝ TEST · 4×4\nWASD / šípky: jazda · R: vrátiť auto\nP: spray piesku · C: čistý piesok"
	title.add_theme_font_size_override("font_size", 20)
	column.add_child(title)
	dust_button = _button(column, "Spray piesku: VYPNUTÝ", toggle_dust)
	_button(column, "Vrátiť auto · zachovať koľaje", reset_car)
	_button(column, "Vyčistiť piesok", reset_sand)
	status = Label.new()
	status.add_theme_font_size_override("font_size", 18)
	column.add_child(status)

func _button(parent: Node, text: String, action: Callable) -> Button:
	var button := Button.new()
	button.text = text
	button.custom_minimum_size = Vector2(350, 48)
	button.focus_mode = Control.FOCUS_NONE
	button.pressed.connect(action)
	parent.add_child(button)
	return button

func reset_car() -> void:
	vehicle.park()
	vehicle.position = Vector3(-22, world.height_at(-22, -4) + 0.6, -4)
	vehicle.heading = 0
	vehicle.reset_physics_interpolation()
	world.previous_contacts.clear()

func reset_sand() -> void:
	world.reset_sand()
	world.flush_deformation()
	effects.reset_trails()
	reset_car()

func toggle_dust() -> void:
	dust_enabled = not dust_enabled
	dust_button.text = "Spray piesku: ZAPNUTÝ" if dust_enabled else "Spray piesku: VYPNUTÝ"
	_sync_effects()

func _sync_effects() -> void:
	effects.particles_enabled = dust_enabled
	effects.set_process(dust_enabled)
	if not dust_enabled: effects.reset_particles()

func _process(dt: float) -> void:
	if vehicle == null: return
	_status_timer -= dt
	if _status_timer <= 0:
		_status_timer = 0.25
		status.text = "%d FPS · %d km/h · mriežka 25 cm\nKoľaje tvorí deformácia piesku." % [Engine.get_frames_per_second(), roundi(absf(vehicle.speed) * 3.6)]
	# Explicit reset only: the test must never hide a physics failure by rescuing.
	if absf(vehicle.position.x) > 30 or absf(vehicle.position.z) > 22:
		vehicle.park()
		status.text = "Okraj testovacej plochy · R vráti auto"

func _unhandled_key_input(event: InputEvent) -> void:
	if not event.is_pressed() or event.is_echo(): return
	match event.keycode:
		KEY_R: reset_car()
		KEY_C: reset_sand()
		KEY_P: toggle_dust()
