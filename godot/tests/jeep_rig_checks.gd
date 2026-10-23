extends SceneTree
var checks := 0
var failures := 0

func _initialize() -> void: call_deferred("_run")

func check(ok: bool, message: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", message])

func frames(n: int) -> void:
	for i in n:
		await physics_frame
		await process_frame

func _run() -> void:
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	await frames(20)
	var car: DriveVehicle = game.vehicle
	var visual := car._visual
	check(visual.scene_file_path == DriveVehicle.MODEL_PATH and DriveVehicle.MODEL_PATH.contains("jeep_cherokee"), "Real main scene uses the supplied Jeep, not the legacy rover")
	check(visual.get_meta("model_author", "").contains("Renafox") and visual.get_meta("model_license", "") == "CC-BY-NC-SA-4.0", "Native rig retains supplied attribution/license metadata")
	var body := visual.get_node("Body") as MeshInstance3D
	var bounds := body.mesh.get_aabb()
	check(bounds.size.x > 4.5 and bounds.size.x < 5.0 and bounds.size.z > 1.9 and bounds.size.z < 2.3 and bounds.size.y < 1.6, "Jeep is metre-scale and fits the existing game's roads/garages")
	check(car._wheels.size() == 4 and car.get_node("ChassisCollision").shape is ConvexPolygonShape3D, "Four independent wheels and mesh-fitted convex body replace the old capsule")
	var triangle_count: int = body.mesh.surface_get_array_index_len(0) / 3
	var aligned := true
	var centred := true
	for wheel: Dictionary in car._wheels:
		var pivot: Node3D = wheel.pivot
		var contact: CollisionShape3D = wheel.contact
		var tyre := wheel.spin.get_child(0) as MeshInstance3D
		triangle_count += tyre.mesh.surface_get_array_index_len(0) / 3
		aligned = aligned and contact != null and absf(contact.position.x - pivot.position.x) < 0.0001 and absf(contact.position.z - pivot.position.z) < 0.0001
		aligned = aligned and contact.shape is CylinderShape3D and absf((contact.shape as CylinderShape3D).radius - DriveVehicle.WHEEL_RADIUS) < 0.0001
		centred = centred and tyre.position.is_zero_approx() and tyre.mesh.get_aabb().get_center().length() < 0.0001
	check(aligned, "Each physical tyre matches its measured visual axle, track and rolling radius")
	check(centred, "Wheel geometry is centred on its spin pivot; tyres cannot orbit the car")
	var source := (load("res://assets/models/jeep/jeep_cherokee.glb") as PackedScene).instantiate()
	var source_mesh := source.find_children("*", "MeshInstance3D", true, false)[0] as MeshInstance3D
	check(triangle_count == source_mesh.mesh.surface_get_array_index_len(0) / 3, "Splitting wheels preserves every original triangle without duplicates or missing body parts")
	source.free()
	var paint := body.material_override as ShaderMaterial
	check(paint != null and paint.get_shader_parameter("albedo_map") is Texture2D and paint.get_shader_parameter("orm_map") is Texture2D and paint.get_shader_parameter("normal_map") is Texture2D, "Original colour/ORM/normal maps remain attached after integration")
	game.run.paint_color = "547660"
	game._apply_paint()
	check((paint.get_shader_parameter("tint") as Color).is_equal_approx(Color("547660")), "Existing paint shop changes the new textured body")
	car.set_physics_process(false)
	car.wheel_speed = 4.0
	car.wheel_angle = 0.30
	car.brake_input = 1.0
	game.run.headlights = true
	var body_transform := body.global_transform
	var before_spin := float(car._wheels[0].spin.rotation.z)
	car._update_visual(0.1, 0)
	var forward_spin := float(car._wheels[0].spin.rotation.z)
	check(forward_spin < before_spin - 0.5, "Forward travel visibly spins the extracted tyres about their axle")
	var steering_ok := true
	for wheel: Dictionary in car._wheels:
		var expected := -0.30 if bool(wheel.front) else 0.0
		steering_ok = steering_ok and is_equal_approx(float(wheel.pivot.rotation.y), expected) and is_equal_approx(float(wheel.contact.rotation.y), expected)
	check(steering_ok, "Only front tyres steer, with matching physical contact orientation")
	car.wheel_speed = -4.0
	car._update_visual(0.1, 0)
	check(float(car._wheels[0].spin.rotation.z) > forward_spin + 0.5, "Reverse reverses tyre rotation")
	check(body.global_transform.origin.distance_to(body_transform.origin) < 0.01, "Tyre rotation never moves the body mesh")
	check(car._brake_materials.size() == 2 and car._brake_materials[0].emission_energy_multiplier > 2.0 and car._lights.all(func(light: SpotLight3D) -> bool: return light.visible and light.get_parent() is Marker3D), "Brake lamps react to pedal and headlights follow the fitted light markers")
	var second := DriveVehicle.new()
	second.run = DriveRun.new()
	second.world = game.world
	second.position = Vector3(8, 4, 0)
	root.add_child(second)
	second.set_physics_process(false)
	check(second._visual.scene_file_path == DriveVehicle.MODEL_PATH and second._wheels.size() == 4 and second.get_node("ChassisCollision").shape is ConvexPolygonShape3D, "Code-created test/spawn vehicles use the same rig and hitbox as the authored scene")
	second.queue_free()
	game.queue_free()
	await frames(2)
	print("JEEP RIG: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
