extends SceneTree
var failures := 0
class FlatTerrain extends DriveTerrain:
	func sample(_x: float, _z: float, _include_water: bool = true, _visual_blend: bool = false) -> Dictionary:
		return {"road": 1.0, "grip": 1.0, "drag": 0.0, "roughness": 0.0, "paved": 0.0, "label": "Piesok", "water": 0.0, "snow": 0.0, "material": SurfaceKind.SAND, "soil_softness": 0.5, "soil_depth": 0.2, "soil_bearing": 2.0, "deformable": true}
class FlatWorld extends DriveWorld:
	var compact_shears: Array[float] = []
	func _ready() -> void: pass
	func height_at(_x: float, _z: float) -> float: return 0.0
	func compact_tyres(_points: Array[Vector3], _surface: Dictionary, _pressure: float, _direction := Vector2.RIGHT, _shear := 0.0, wheel_shear: Array[float] = []) -> void: compact_shears = wheel_shear.duplicate()
	func load_tyres(_points: Array[Vector3], _pressure: float, _slip: float, _effort: float, _dt: float) -> void: pass
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, label: String) -> void:
	print("%s %s" % ["PASS" if ok else "FAIL", label])
	if not ok: failures += 1
func frames(count: int) -> void:
	for i in count:
		await physics_frame
		await process_frame
func rear(car: DriveVehicle) -> Vector3:
	return car.position - Vector3(cos(car.heading), 0, sin(car.heading)) * car.wheelbase * 0.5
func _run() -> void:
	for action: String in ["right", "left", "throttle", "brake", "handbrake"]:
		if not InputMap.has_action(action): InputMap.add_action(action)
	var world := FlatWorld.new()
	world.terrain = FlatTerrain.new()
	root.add_child(world)
	var floor_node := MeshInstance3D.new()
	floor_node.name = "SandAndTracks"
	var plane := PlaneMesh.new()
	plane.size = Vector2(200, 200)
	floor_node.mesh = plane
	world.add_child(floor_node)
	floor_node.create_trimesh_collision()
	var run := DriveRun.new()
	run.vehicle_id = "izh"
	run.four_wheel_drive = false
	var car := (load("res://scenes/vehicle.tscn") as PackedScene).instantiate() as DriveVehicle
	car.run = run
	car.world = world
	car.boundary_recovery_enabled = false
	root.add_child(car)
	car.position = Vector3(0, 0.6, 0)
	await frames(35)
	run.engine_running = true
	car.speed = 2.5
	Input.action_press("right", 0.7)
	Input.action_press("throttle", 0.05)
	await frames(30)
	var lateral := 0.0
	var travelled := 0.0
	for tick in 90:
		car.speed = 2.0
		var before := rear(car)
		var right := Vector3(-sin(car.heading), 0, cos(car.heading))
		await frames(1)
		var movement := rear(car) - before
		lateral += absf(movement.dot(right))
		travelled += movement.length()
	Input.action_release("right")
	Input.action_release("throttle")
	print("REAR AXLE lateral=", lateral, " travelled=", travelled)
	check(travelled > 0.5 and absf(car.heading) > 0.15 and lateral / travelled < 0.03, "Rear axle follows the low-speed steering arc without pivoting sideways")
	car.park()
	await frames(4)
	car.set_physics_process(false)
	car.speed = 0
	car.soil_slip = 0.9
	var fx := DriveEffects.new()
	fx.world = world
	fx.vehicle = car
	root.add_child(fx)
	fx._process(0.1)
	var correct := true
	for i in 4:
		correct = correct and fx._grit[i].emitting == bool(car._wheels[i].front)
	check(correct, "Stationary IZH wheelspin throws soil from its front wheels only")
	car.speed = 2.0
	car._lateral_speed = 0.0
	car._yaw_rate = 0.0
	car._last_compaction = Vector3.INF
	car._visual_last_speed = 0.0
	car._update_visual(1.0 / 60.0, 0.0)
	var shear_ok := world.compact_shears.size() == 4
	if shear_ok:
		for i in 4: shear_ok = shear_ok and (world.compact_shears[i] > 0.5 if car._wheels[i].front else world.compact_shears[i] < 0.01)
	check(shear_ok, "Wheelspin shears soil at driven wheels, not the rolling axle")
	check(car._visual_load_pitch > 0.001, "Acceleration visibly loads the suspension")
	run.impact(18.0)
	var restored := DriveRun.from_data(run.to_data())
	check(restored != null and run.body_damage > 0.0 and is_equal_approx(restored.body_damage, run.body_damage), "Impact damage persists with the journey")
	run.parts.tires = 0.04
	car._update_visual(1.0 / 60.0, 0.0)
	var rr := car._visual.get_node("Wheel_RR") as Node3D
	check(rr.scale.y < 0.8 and (car._visual.get_node("Body") as MeshInstance3D).material_override.get_shader_parameter("body_damage") > 0.0, "Critical tyre wear and collision damage change the visible model")
	run.parts.tires = 1.0
	car._update_visual(1.0 / 60.0, 0.0)
	check(is_equal_approx(rr.scale.y, 1.0), "Restored tyre condition removes the flat-tyre visual")
	fx.queue_free()
	car.queue_free()
	world.queue_free()
	await process_frame
	quit(1 if failures else 0)
