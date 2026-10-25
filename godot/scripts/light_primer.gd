extends Node
## Prime the actual streamed vertex formats beneath the startup menu. A tiny
## isolated preview alone cannot cover every live mesh/light combination.
var vehicle: DriveVehicle
var environment: Environment
var _frame := 0
var _fog_enabled := false
var _fog_density := 0.0
var allow_driving := false # Only rendering benchmarks opt in.
func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	process_priority = 100
	_fog_enabled = environment.fog_enabled
	_fog_density = environment.fog_density
func _process(_dt: float) -> void:
	if not is_instance_valid(vehicle): queue_free(); return
	if not get_tree().paused and not allow_driving:
		_restore()
		return
	_frame += 1
	var count := 0 if _frame < 3 else (1 if _frame < 6 else (2 if _frame < 10 else 4))
	for i in vehicle._lights.size(): vehicle._lights[i].visible = i < count
	for i in vehicle._reverse_lights.size(): vehicle._reverse_lights[i].visible = count > 2
	if _frame == 12:
		environment.fog_enabled = true
		environment.fog_density = 0.006
	if _frame >= 16: _restore()
func _restore() -> void:
	for light in vehicle._lights: light.visible = vehicle.run.headlights and vehicle.run.battery > 0.01
	for light in vehicle._reverse_lights: light.visible = false
	vehicle._last_light_mode = -1
	environment.fog_enabled = _fog_enabled
	environment.fog_density = _fog_density
	queue_free()
func _exit_tree() -> void:
	if environment != null:
		environment.fog_enabled = _fog_enabled
		environment.fog_density = _fog_density
