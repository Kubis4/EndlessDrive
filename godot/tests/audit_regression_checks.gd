extends SceneTree
## Behavioural regressions for the audit fixes; isolated by --test-session.
var failures := 0
var checks := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func frames(count: int) -> void:
	for tick in count:
		await physics_frame
		await process_frame
func _run() -> void:
	if not OS.get_cmdline_user_args().has("--test-session"):
		quit(2)
		return
	var manual := DriveRun.new()
	var cruise := DriveRun.new()
	manual.engine_running = true
	cruise.engine_running = true
	for tick in 600:
		manual.advance(0.1, 15, 0.4, false, false)
		cruise.advance(0.1, 15, 0.4, false, true)
	check(is_equal_approx(manual.fuel, cruise.fuel), "Equal load/speed consumes equal fuel with manual throttle and cruise")
	manual.odometer = 123.0
	manual.distance = 60.0
	var saved := manual.to_data(false)
	check(DriveRun.from_data(saved).odometer == 123.0 and DriveRun.from_data(saved).distance == 60.0, "Odometer and maximum route progress persist independently")
	saved.erase("odometer")
	check(DriveRun.from_data(saved).odometer == manual.wear_distance, "Existing saves migrate their existing travelled wear distance")
	saved.odometer = NAN
	check(DriveRun.from_data(saved) == null, "A non-finite saved odometer is rejected")
	var soil := DriveDeformation.new()
	soil._store(Vector2i(40, 0), 0.2)
	check(soil.overlaps_x(9, 11) and not soil.overlaps_x(96, 192), "Historical ruts need no snapshot copy for an untouched future chunk")
	soil.restore([])
	check(not soil.overlaps_x(0, 100), "Restoring an empty field also clears its conservative bounds")
	soil.cells = {Vector2i(0, 0): 0.2}
	soil._store(Vector2i(1000, 0), 0.3)
	check(soil.overlaps_x(0, 1), "Externally supplied snapshot cells cannot disappear from the bounds check")
	var game = load("res://scenes/main.tscn").instantiate()
	game._ephemeral = true
	root.add_child(game)
	game.weather.set_process(false)
	game.world.deformation_enabled = false
	var car: DriveVehicle = game.vehicle
	var run: DriveRun = game.run
	for chunk: Node3D in game.world.chunks.values():
		for node: Node in chunk.get_children():
			if node.name != "SandAndTracks": node.queue_free()
	await frames(20)
	run.engine_running = true
	run.fuel = 40
	car.park()
	car.heading = 0
	Input.action_press("throttle", 0.8)
	await frames(120)
	var outward := run.odometer
	car.park()
	car.heading = PI
	await frames(120)
	Input.action_release("throttle")
	check(outward > 1.0 and run.odometer > run.distance + 1.0, "Real backward motion adds odometer distance without awarding route progress")
	var odometer := run.odometer
	car.park()
	car.position.x += 50
	await frames(1)
	check(run.odometer - odometer < 1.0, "An external relocation does not award its teleport distance")
	car.park()
	car.handbrake_input = 1
	check(car.motion_diagnostic().contains("brzda"), "A held handbrake has an actionable diagnostic")
	car.handbrake_input = 0
	car.brake_input = 0
	car.stationary_time = 1
	run.engine_running = false
	run.fuel = 0
	check(car.motion_diagnostic().contains("nádrž"), "An empty tank explains a failed start instead of reporting generic terrain")
	run.fuel = 40
	run.engine_running = true
	car.stop_reason = "bogged"
	check(car.motion_diagnostic().contains("navijak"), "Bogging suggests a real recovery action")
	check(car.has_node("WinchMount/CableOutlet"), "The lightweight authored winch has a cable socket")
	var foreign_view := SubViewport.new()
	foreign_view.own_world_3d = true
	root.add_child(foreign_view)
	var forward := Vector3(cos(car.heading), 0, sin(car.heading))
	var ghost := Marker3D.new()
	ghost.position = car.global_position + forward * 8
	ghost.add_to_group("winch_anchor")
	foreign_view.add_child(ghost)
	var real := Marker3D.new()
	game.world.add_child(real)
	real.global_position = car.global_position + forward * 14
	real.add_to_group("winch_anchor")
	game.winch.toggle_attachment()
	check(game.winch.anchor_node() == real, "Private render-primer anchors cannot attract the real car's winch")
	game.winch.disconnect_cable()
	foreign_view.queue_free()
	real.queue_free()
	var original := []
	for p: CPUParticles3D in game.effects.find_children("*", "CPUParticles3D", true, false): original.append(p.amount)
	game._settings_changed("quality", 0)
	check(not game.sun.shadow_enabled, "Low graphics disables the directional shadow cost")
	var ground := game.world.chunks[0].get_node("SandAndTracks") as MeshInstance3D
	check(ground.material_override.shader.resource_path.ends_with("desert_surface_low.gdshader") and ground.material_override.get_shader_parameter("surface_data_enabled") == true and not ground.get_child(0).get_child(0).shape.get_faces().is_empty(), "Low-detail rendering keeps biome data and actual road collision in place")
	var low := []
	for p: CPUParticles3D in game.effects.find_children("*", "CPUParticles3D", true, false): low.append(p.amount)
	check(low.size() > 0 and int(low[0]) < int(original[0]), "Low graphics reduces real particle populations")
	game._settings_changed("quality", 2)
	var restored := []
	for p: CPUParticles3D in game.effects.find_children("*", "CPUParticles3D", true, false): restored.append(p.amount)
	check(restored == original and game.sun.shadow_enabled, "High graphics restores original particles without compounding reductions")
	check(game.menu._quality_select.owner != null and game.menu._fps_select.owner != null, "Graphics controls are saved, manually editable scene nodes")
	for shelter: Node in get_nodes_in_group("storm_shelter"): shelter.remove_from_group("storm_shelter")
	game.world.buildings.clear()
	game.weather.reset_weather()
	game.weather.start_sandstorm()
	check(not game.weather.storm_active and not game.weather.has_shelter_target, "A chase is deferred when no loaded reachable shelter exists")
	var health: float = run.parts.radiator
	game.weather._process(1)
	check(run.parts.radiator == health, "A deferred storm does not damage an exposed car")
	game.queue_free()
	await frames(2)
	print("AUDIT REGRESSION: %d checks, %d failures" % [checks, failures])
	quit(1 if failures else 0)
