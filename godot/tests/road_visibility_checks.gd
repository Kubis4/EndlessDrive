extends SceneTree
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	print("%s %s" % ["PASS" if ok else "FAIL", title])
	if not ok: failures += 1
func _run() -> void:
	var world := DriveWorld.new()
	root.add_child(world)
	var vehicle := (load("res://scenes/vehicle.tscn") as PackedScene).instantiate() as DriveVehicle
	vehicle.run = DriveRun.new()
	vehicle.world = world
	root.add_child(vehicle)
	vehicle.set_physics_process(false)
	var camera := Camera3D.new()
	root.add_child(camera)
	camera.position = Vector3(-15, 20, 15)
	camera.look_at(Vector3.ZERO)
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 53
	await physics_frame
	world._terrain_visibility_probe_ms = 0
	world.probe_terrain_visibility(camera.global_position, vehicle)
	check(not world._terrain_blocks_road, "Open view does not cut holes in visible terrain")
	var mesh := MeshInstance3D.new()
	mesh.name = "SandAndTracks"
	root.add_child(mesh)
	var body := StaticBody3D.new()
	mesh.add_child(body)
	var collision := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(8, 8, 8)
	collision.shape = box
	body.add_child(collision)
	body.global_position = camera.global_position.lerp(vehicle.global_position + Vector3.UP * 0.7, 0.5)
	await physics_frame
	world._terrain_visibility_probe_ms = 0
	world.probe_terrain_visibility(camera.global_position, vehicle)
	check(world._terrain_blocks_road, "Terrain blocking the actual camera ray triggers the raised camera")
	var data := world.road_visibility(camera, vehicle, 5.5)
	check(float(data.road_view_radius) > 0 and (data.road_view_start as Vector2).is_finite() and (data.road_view_end as Vector2).is_finite(), "Window projects the road with bounded width")
	mesh.queue_free()
	vehicle.queue_free()
	camera.queue_free()
	world.queue_free()
	await process_frame
	quit(1 if failures else 0)
