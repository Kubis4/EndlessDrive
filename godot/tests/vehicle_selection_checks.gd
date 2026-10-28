extends SceneTree
var failures := 0
var checks := 0
class RenderedRoad extends DriveWorld:
	func _ready() -> void: pass
	func height_at(_x: float, _z: float) -> float: return 3.0
	func rendered_height_at(_x: float, _z: float) -> float: return 3.4
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", label])
func _run() -> void:
	var profile := DriveProfile.new()
	var run := DriveRun.new()
	profile.prepare_run(run)
	check(run.vehicle_id == "izh" and not run.four_wheel_drive and not run.differential_locked, "Fresh garage starts with FWD IZH")
	var restored := DriveRun.from_data(run.to_data())
	check(restored != null and restored.vehicle_id == "izh", "IZH identity survives a run save")
	var legacy := DriveRun.new().to_data()
	legacy.erase("vehicle_id")
	legacy.version = 5
	check(DriveRun.from_data(legacy).vehicle_id == "jeep", "Existing Jeep saves retain their model")
	var impossible := run.to_data()
	impossible.four_wheel_drive = true
	check(DriveRun.from_data(impossible) == null, "A FWD save cannot acquire 4WD")
	var car := (load("res://scenes/vehicle.tscn") as PackedScene).instantiate() as DriveVehicle
	car.run = run
	root.add_child(car)
	car.set_physics_process(false)
	await process_frame
	check(car._wheels.size() == 4 and is_equal_approx(car.wheel_radius, 0.33208), "Van uses four normalized rigged wheels")
	check(car.get_node("Contact_F_L").position.x > 1.3 and car.wheelbase < 2.7, "Wheel contacts match van geometry")
	car.toggle_drive_mode()
	car.toggle_differential()
	check(not run.four_wheel_drive and not run.differential_locked, "Keyboard requests cannot enable unavailable hardware")
	var van_shape := car.get_node("ChassisCollision").shape as ConvexPolygonShape3D
	check(van_shape != null and van_shape.points.size() < 256, "Offline simplified body collider stays bounded")
	profile.selected_vehicle = "jeep"
	profile.prepare_run(run)
	car.configure_vehicle(run.vehicle_id)
	check(car.vehicle_id == "jeep" and run.four_wheel_drive and car._wheels.size() == 4, "Garage selection installs Jeep with 4WD")
	profile.selected_vehicle = "izh"
	profile.prepare_run(run)
	car.configure_vehicle(run.vehicle_id)
	check(car.is_front_drive() and car._wheels.size() == 4 and car._visual.name == "Visual", "Switching back rebuilds the playable van rig")
	var fx := DriveEffects.new()
	root.add_child(fx)
	check(fx.trails_enabled and fx._surface_pools.all(func(pool: Dictionary) -> bool: return pool.size() == 1), "Guide trails enabled without allocating unused particle banks")
	fx.ensure_particle_bank(1, 4)
	check(fx._surface_pools[1].size() == 2 and fx._surface_pools[2].size() == 1, "Only the contacting wheel allocates a new material bank")
	var track_world := RenderedRoad.new()
	root.add_child(track_world)
	fx.world = track_world
	for material in [DriveTerrain.SurfaceKind.ASPHALT, DriveTerrain.SurfaceKind.SAND, DriveTerrain.SurfaceKind.DIRT, DriveTerrain.SurfaceKind.SNOW, DriveTerrain.SurfaceKind.MUD]:
		var surface := {"material": material, "paved": 1.0 if material == DriveTerrain.SurfaceKind.ASPHALT else 0.0, "snow": 1.0 if material == DriveTerrain.SurfaceKind.SNOW else 0.0, "color": Color("99958a")}
		fx._stamp(Vector3(10, 0, 0), Vector3.RIGHT, surface)
	check(fx._count == 5 and is_equal_approx((fx._track_records[0].transform as Transform3D).origin.y, 3.43), "All road families receive tracks above the rendered mesh rather than the analytic ground")
	check((fx._track_records[0].transform as Transform3D).basis.z.length() < 0.5, "Hard-road tyre prints avoid wide displaced soil shoulders")
	track_world.queue_free()
	var terrain := DriveTerrain.new()
	terrain.seed_value = 24601
	var x := 500.0
	var event := {"kind": "potholes", "x": x, "z": terrain.center_at(x), "strength": 1.0}
	check(terrain.pothole_depth(event, x, terrain.center_at(x) + 1.4) > 0.8, "Large potholes physically lower the road")
	check(terrain.pothole_depth(event, x, terrain.center_at(x) - 3.8) == 0, "Large holes leave room to steer around")
	fx.queue_free()
	car.queue_free()
	await process_frame
	print("VEHICLE SELECTION ", checks, " checks; ", failures, " failures")
	quit(1 if failures else 0)
