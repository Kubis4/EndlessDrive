extends Node3D
## Starts as an uninterrupted, HUD-free moving diorama. Space enables player driving.
var elapsed := 0.0
var driving := false
var paused := false
var focus := Vector3(0, 1, 4)
var camera_size := 91.0
var soil := preload("res://desert_battlefield/soil.gd").new()
@onready var camera: Camera3D = $Camera3D
@onready var lead: CharacterBody3D = $Patrol/Scout01

func _ready() -> void:
	soil.configure(self)
	get_viewport().msaa_3d = Viewport.MSAA_4X
	get_viewport().screen_space_aa = Viewport.SCREEN_SPACE_AA_FXAA
	if DisplayServer.get_name() != "headless":
		get_window().size = Vector2i(1920, 1080)

func _process(delta: float) -> void:
	if paused: return
	soil.update(delta)
	elapsed += delta
	var target := lead.position if driving else Vector3(sin(elapsed * 0.035) * 4.0, 1, 4.0 + sin(elapsed * 0.027) * 3.0)
	focus = focus.lerp(target, 1.0 - exp(-delta * 1.8))
	camera.position = focus + Vector3(41, 120, 58)
	camera.look_at(focus)
	camera.size = lerpf(camera.size, camera_size, 1.0 - exp(-delta * 4.0))

func _unhandled_key_input(event: InputEvent) -> void:
	if not event.is_pressed() or event.is_echo(): return
	if event.keycode == KEY_SPACE:
		driving = not driving
		lead.manual = driving
		camera_size = 65.0 if driving else 91.0
	if event.keycode == KEY_R: lead.reset_to_route()
	if event.keycode == KEY_P:
		paused = not paused
		for vehicle in $Patrol.get_children():
			vehicle.set_physics_process(not paused)
			for particle in vehicle.find_children("*", "GPUParticles3D", true, false):
				particle.speed_scale = 0.0 if paused else 1.0
	if event.keycode == KEY_ESCAPE:
		driving = false
		lead.manual = false
		camera_size = 91.0
	if event.keycode == KEY_EQUAL: camera_size = maxf(40, camera_size - 8)
	if event.keycode == KEY_MINUS: camera_size = minf(115, camera_size + 8)
