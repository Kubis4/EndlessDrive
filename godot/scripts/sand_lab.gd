extends Node3D
## Playable isolated laboratory. No journey save or procedural biome changes.
const LabWorld = preload("res://scripts/sand_lab_world.gd")
var world: LabWorld
var vehicle: DriveVehicle
var run := DriveRun.new()
var effects: DriveEffects
var sand_grains: Node3D
var dust_enabled := true
var follow_camera := true
var dust_button: Button
var status: Label
var _status_timer := 0.0

func _ready() -> void:
	$EditorSand.hide()
	$EditorSand.queue_free()
	$EditorVehicle.hide()
	$EditorVehicle.queue_free()
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
	$TestObjects.setup(world)
	var scenery = preload("res://scripts/sand_lab_scenery.gd").new()
	scenery.build(world)
	add_child(scenery)
	vehicle = $Vehicle
	vehicle.show()
	vehicle.world = world
	vehicle.run = run
	vehicle.configure_vehicle(run.vehicle_id)
	vehicle.boundary_recovery_enabled = false
	vehicle.sand_lab_drift = 1.0
	run.engine_running = true
	run.fuel = 40
	run.four_wheel_drive = true
	run.variants.tires = "allterrain"
	for slot: String in run.parts: run.parts[slot] = 1.0
	reset_car()
	effects = DriveEffects.new()
	effects.trails_enabled = false
	effects.sand_lab_plume = true
	effects.vehicle = vehicle
	effects.world = world
	add_child(effects)
	sand_grains = preload("res://scripts/sand_lab_grains.gd").new()
	sand_grains.vehicle = vehicle
	sand_grains.world = world
	add_child(sand_grains)
	effects.particles_enabled = dust_enabled
	effects._tracks.hide()
	_build_ui()
	$LabControls.hide()
	_sync_effects()

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
	title.text = "PIESKOVÝ TEST · 4×4\nWASD / šípky: jazda · R: vrátiť auto\nP: piesok · C: čistý piesok · O: objekty"
	title.add_theme_font_size_override("font_size", 20)
	column.add_child(title)
	dust_button = _button(column, "Spray piesku: ZAPNUTÝ", toggle_dust)
	_button(column, "Vrátiť auto · zachovať koľaje", reset_car)
	_button(column, "Vyčistiť piesok", reset_sand)
	_button(column, "Obnoviť testovacie objekty", reset_objects)
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
	if follow_camera:
		var focus := Vector3(clampf(vehicle.position.x, -10.0, 10.0), vehicle.position.y, clampf(vehicle.position.z, -4.0, 4.0))
		$Camera3D.position = focus + Vector3(22, 52, 25)
		$Camera3D.look_at(focus)

func reset_sand() -> void:
	world.reset_sand()
	world.flush_deformation()
	effects.reset_trails()
	reset_car()

func reset_objects() -> void:
	$TestObjects.reset_objects()

func toggle_dust() -> void:
	dust_enabled = not dust_enabled
	dust_button.text = "Spray piesku: ZAPNUTÝ" if dust_enabled else "Spray piesku: VYPNUTÝ"
	_sync_effects()

func _sync_effects() -> void:
	effects.particles_enabled = dust_enabled
	effects.set_process(dust_enabled)
	sand_grains.enabled = dust_enabled
	if not dust_enabled:
		effects.reset_particles()
		sand_grains.clear()

func _process(dt: float) -> void:
	if vehicle == null: return
	if follow_camera:
		var camera := $Camera3D as Camera3D
		var focus := vehicle.position + Vector3(cos(vehicle.heading), 0, sin(vehicle.heading)) * minf(absf(vehicle.speed) * 0.35, 4.0)
		focus.x = clampf(focus.x, -10.0, 10.0)
		focus.z = clampf(focus.z, -4.0, 4.0)
		camera.position = camera.position.lerp(focus + Vector3(22, 52, 25), 1.0 - exp(-3.0 * dt))
		camera.look_at(focus)
	_status_timer -= dt
	if _status_timer <= 0:
		_status_timer = 0.25
		status.text = "%d FPS · %d km/h · mriežka 25 cm\nKoľaje tvorí deformácia piesku." % [Engine.get_frames_per_second(), roundi(absf(vehicle.speed) * 3.6)]
	# Explicit reset only: the test must never hide a physics failure by rescuing.
	if absf(vehicle.position.x) > 46 or absf(vehicle.position.z) > 30:
		vehicle.park()
		status.text = "Okraj testovacej plochy · R vráti auto"

func _unhandled_key_input(event: InputEvent) -> void:
	if not event.is_pressed() or event.is_echo(): return
	match event.keycode:
		KEY_R: reset_car()
		KEY_C: reset_sand()
		KEY_P: toggle_dust()
		KEY_O: reset_objects()


