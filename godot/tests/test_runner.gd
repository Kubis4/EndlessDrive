extends SceneTree
## Run after editor import: godot --headless --fixed-fps 60 --path godot
## --script res://tests/test_runner.gd -- --test-session

var checks := 0
var failures := 0


func _initialize() -> void:
	call_deferred("_run")


func check(condition: bool, description: String) -> void:
	checks += 1
	if not condition:
		failures += 1
		push_error("FAIL: " + description)
	else:
		print("PASS: " + description)


func frames(count: int) -> void:
	for index in count:
		await physics_frame


func _run() -> void:
	_test_survival()
	_test_loot_and_cargo()
	_test_save()
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	var vehicle: DriveVehicle = game.vehicle
	var world: DriveWorld = game.world
	await frames(50)
	check(vehicle.is_on_floor(), "Vehicle settles onto collidable terrain")
	check(vehicle.position.is_finite() and vehicle.position.y > -1.0, "New vehicle model and body stay above terrain")
	check(vehicle._wheels.size() == 4, "All four new wheel meshes have independent pivots")
	check(vehicle._brake_materials.size() == 2, "Both new brake lamps have independently controlled materials")
	check(vehicle._audio.stream is AudioStreamWAV and vehicle._audio.stream.data.size() > 0, "Engine audio is synthesized from the new source")
	check(not FileAccess.file_exists("res://assets/models/car_modular.glb") and not FileAccess.file_exists("res://assets/textures/materials-v1.png") and not FileAccess.file_exists("res://assets/audio/engine_idle.ogg"), "Legacy Android art and audio are absent from the Godot project")
	check(world._models.size() == 7, "All seven new environment model scenes load")
	check(game.camera.projection == Camera3D.PROJECTION_ORTHOGONAL and game.camera.size > 35.0 and game.camera.size < 65.0, "Default camera uses the reference-style wide orthographic view")
	var offset: Vector3 = game.camera.position - game._camera_focus
	check(offset.normalized().y > 0.6 and offset.normalized().y < 0.85, "Camera looks down at the world from a high oblique angle")
	var before_basis: Basis = game.camera.basis
	var before_heading := vehicle.heading
	vehicle.heading += PI * 0.5
	game._update_camera(1.0, true)
	check(game.camera.basis.is_equal_approx(before_basis), "Steering cannot rotate the overview camera azimuth")
	vehicle.heading = before_heading
	check(world.chunks.size() == 7, "Streaming keeps exactly seven terrain chunks")
	var hud: DriveHUD = game.hud
	var screen := hud._root.get_global_rect()
	var all_visible := true
	for pedal: Button in hud._pedals.values():
		all_visible = all_visible and screen.encloses(pedal.get_global_rect())
	check(all_visible, "All touch pedals are anchored inside the viewport")
	var touch := InputEventScreenTouch.new()
	touch.index = 10
	touch.pressed = true
	touch.position = hud._pedals.throttle.get_global_rect().get_center()
	hud._input(touch)
	var second_touch := InputEventScreenTouch.new()
	second_touch.index = 11
	second_touch.pressed = true
	second_touch.position = hud._pedals.left.get_global_rect().get_center()
	hud._input(second_touch)
	check(vehicle._strength("throttle") == 1.0 and vehicle._strength("left") == 1.0, "Touch events map to simultaneous throttle and steering")
	touch.pressed = false
	hud._input(touch)
	check(vehicle._strength("throttle") == 0.0 and vehicle._strength("left") == 1.0, "HUD release keeps the remaining finger active")
	second_touch.pressed = false
	hud._input(second_touch)
	var keyboard := InputEventKey.new()
	keyboard.physical_keycode = KEY_W
	keyboard.pressed = true
	Input.parse_input_event(keyboard)
	Input.flush_buffered_events()
	check(Input.is_action_pressed("throttle"), "Physical W key is mapped to throttle")
	keyboard = keyboard.duplicate()
	keyboard.pressed = false
	Input.parse_input_event(keyboard)
	Input.flush_buffered_events()
	var initial_pose: Vector3 = vehicle.position
	game.run.toggle_engine()
	vehicle.set_touch("throttle", true, 0)
	await frames(100)
	check(vehicle.position.x > initial_pose.x + 8.0, "Throttle drives the vehicle forward using Godot physics")
	check(game.run.fuel < 7.0, "Driving consumes fuel")
	game._action("cruise")
	check(vehicle.cruise_enabled, "Cruise captures manual throttle during forward driving")
	vehicle.set_touch("throttle", false, 0)
	await frames(20)
	check(vehicle.cruise_enabled and vehicle.throttle_input > 0.9, "Cruise keeps throttle applied after the pedal is released")
	Input.action_press("throttle", 0.4)
	await frames(2)
	check(vehicle.cruise_enabled and is_equal_approx(vehicle.throttle_input, 0.4), "Manual analogue throttle overrides the captured cruise setting")
	Input.action_release("throttle")
	await frames(2)
	check(vehicle.throttle_input > 0.9, "Releasing manual override restores the captured throttle")
	game._action("engine")
	check(not vehicle.cruise_enabled and not game.run.engine_running, "Switching the engine off immediately cancels cruise")
	game._action("engine")
	vehicle.set_touch("throttle", true, 0)
	await frames(2)
	game._action("cruise")
	vehicle.set_touch("throttle", false, 0)
	vehicle.set_touch("brake", true, 8)
	await frames(2)
	check(not vehicle.cruise_enabled and vehicle.throttle_input == 0.0, "Brake immediately cancels cruise instead of fighting it")
	vehicle.set_touch("brake", false, 8)
	vehicle.set_touch("throttle", true, 0)
	await frames(5)
	game._action("cruise")
	vehicle.set_touch("right", true, 1)
	await frames(30)
	check(vehicle.heading > 0.05, "Steering changes the vehicle heading")
	vehicle.set_touch("throttle", false, 0)
	check(vehicle._strength("right") == 1.0 and vehicle._strength("throttle") == 0.0, "Releasing one touch preserves the other steering touch")
	game._action("pause")
	check(not vehicle.cruise_enabled, "Opening pause cancels cruise")
	var paused_time: float = game.run.elapsed
	var paused_pose := vehicle.position
	await frames(5)
	check(paused and game.run.elapsed == paused_time and vehicle.position == paused_pose, "Pause freezes survival and vehicle physics")
	game._action("resume")
	check(vehicle._touches.is_empty(), "Resume clears stale touch inputs")
	vehicle.park()
	vehicle.toggle_cruise()
	check(not vehicle.cruise_enabled, "Cruise cannot engage while stopped without a throttle setpoint")
	vehicle.heading = 0.0
	vehicle.position = Vector3(60, world.height_at(60, world.center_at(60)) + 0.55, world.center_at(60))
	await frames(30)
	vehicle.set_touch("brake", true, 5)
	await frames(100)
	check(vehicle.speed < -1.0, "Holding brake while stopped engages reverse after its delay")
	vehicle.park()
	game.run.engine_running = false
	var building: Dictionary = world.buildings[0]
	var bpos: Vector3 = building.position
	vehicle.position = Vector3(bpos.x - 17.0, world.height_at(bpos.x - 17.0, bpos.z) + 0.55, bpos.z)
	vehicle.heading = 0.0
	await frames(35)
	vehicle.speed = 18.0
	var previous_health: float = game.run.parts.suspension
	await frames(90)
	check(vehicle.position.x < bpos.x - 4.9, "Garage wall blocks the vehicle instead of allowing tunnelling")
	check(float(game.run.parts.suspension) < previous_health - 0.001, "A physical impact damages suspension")
	vehicle.park()
	vehicle.position = Vector3(bpos.x, bpos.y + 0.6, bpos.z + 8.0)
	var initial_supplies: float = game.run.inventory.fuel
	game._action("search")
	check(paused and hud._modal_kind == "loot" and game.run.inventory.fuel == initial_supplies, "Searching opens a paused loot window without collecting supplies")
	check(hud._loot_rows.size() == 6 and hud.selected_loot().is_empty() and hud._loot_take.disabled, "Loot window starts with every resource and part unselected")
	var fuel_row: Dictionary = hud._loot_rows["0/fuel"]
	fuel_row.amount.value = 0.5
	fuel_row.check.button_pressed = true
	check(not hud._loot_take.disabled and hud.selected_loot().size() == 1, "Loot UI enables transfer only after an explicit selection")
	hud._loot_take.pressed.emit()
	check(is_equal_approx(float(game.run.inventory.fuel), initial_supplies + 0.5) and game.run.cargo_parts.is_empty(), "Loot button transfers only the selected half litre")
	var remaining: Array = game.run.loot_piles["0"].duplicate(true)
	game._action("resume")
	game._action("search")
	check(game.run.loot_piles["0"] == remaining and hud.selected_loot().is_empty(), "Closing and reopening loot preserves leftovers and clears selection")
	var part_row: Dictionary = hud._loot_rows["0/part/0"]
	part_row.check.button_pressed = true
	hud._loot_take.pressed.emit()
	check(game.run.cargo_parts.size() == 1, "Selected spare part moves into the cargo inventory")
	await frames(2)
	check(screen.encloses(hud._loot.get_global_rect()), "Loot modal fits within the gameplay viewport")
	game._action("service")
	check(paused and game.hud.modal_open, "Service screen pauses the journey")
	var previous_engine: float = game.run.parts.engine
	game._action("repair:engine")
	check(float(game.run.parts.engine) > previous_engine, "Service action repairs the actual vehicle state")
	await frames(2)
	check(screen.encloses(hud._service.get_global_rect()), "Vehicle and cargo UI fits within the gameplay viewport")
	game._action("resume")
	var first_building: Vector3 = world.buildings[0].position
	world.update_stream(10000.0)
	check(world.chunks.size() == 7 and not world.chunks.has(0), "Old chunks and collisions unload on a long journey")
	world.update_stream(0.0)
	check(world.buildings[0].position == first_building, "Returning recreates the identical seeded building")
	check(game.run.searched.has("0"), "Loot persistence survives chunk unloading")
	check(game.run.loot_piles["0"].size() == 5, "Partially collected loot persists through world streaming")
	for side: float in [-1.0, 1.0]:
		var x := 96.0 if side < 0 else 192.0
		world.update_stream(x)
		vehicle.park()
		var z := world.center_at(x) + side * (DriveWorld.SAFE_HALF_WIDTH - 4.0)
		vehicle.position = Vector3(x, world.height_at(x, z) + 0.6, z)
		vehicle.heading = side * PI * 0.5
		vehicle.speed = 40.0
		await frames(90)
		check(absf(vehicle.position.z - world.center_at(vehicle.position.x)) <= DriveWorld.SAFE_HALF_WIDTH + 0.01, "High-speed travel cannot escape map side %d at a chunk seam" % int(side))
		check(vehicle.position.y > world.height_at(vehicle.position.x, vehicle.position.z) - 1.0, "Vehicle remains above collidable terrain at map side %d" % int(side))
	vehicle.position = Vector3(193, -500, world.center_at(193) + 200)
	await frames(2)
	check(absf(vehicle.position.z - world.center_at(vehicle.position.x)) <= DriveWorld.SAFE_HALF_WIDTH and vehicle.position.y > 0, "An already fallen or out-of-bounds vehicle recovers safely")
	await TerrainChecks.run(game, self)
	game.queue_free()
	await process_frame
	print("RESULT: %d checks, %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)


func _test_survival() -> void:
	var run := DriveRun.new()
	run.fuel = 0.0
	run.toggle_engine()
	check(not run.engine_running, "An empty tank prevents starting")
	run.fuel = 1.0
	run.battery = 0.0
	run.toggle_engine()
	check(not run.engine_running, "A flat battery prevents starting")
	run.battery = 1.0
	run.toggle_engine()
	check(run.engine_running and run.battery < 1.0, "Cranking consumes battery charge")
	run.fuel = 0.00001
	run.advance(1.0, 20.0, 1.0, false)
	check(run.fuel == 0.0 and not run.engine_running, "Fuel exhaustion switches the engine off")
	run.fuel = 39.0
	run.inventory.fuel = 6.0
	run.top_up("fuel")
	check(run.fuel == 40.0 and run.inventory.fuel == 5.0, "Refuelling respects tank capacity and conserves supplies")
	run.inventory.scrap = 0.0
	var health: float = run.parts.engine
	run.repair("engine")
	check(run.parts.engine == health, "Repairs cannot create free health without scrap")
	var second := DriveRun.new()
	run.search_building(6)
	second.search_building(6)
	check(run.loot_piles["6"] == second.loot_piles["6"], "Seeded loot is repeatable across runs")
	var manual := DriveRun.new()
	var cruise := DriveRun.new()
	manual.engine_running = true
	cruise.engine_running = true
	manual.advance(10.0, 15.0, 1.0, false)
	cruise.advance(10.0, 15.0, 1.0, false, true)
	check(cruise.fuel < manual.fuel, "Automatic cruise throttle has the original extra fuel cost")
	var invalid := second.to_data()
	invalid.parts = "broken"
	check(DriveRun.from_data(invalid) == null, "Malformed nested save state is rejected")
	invalid = second.to_data()
	invalid.fuel = NAN
	check(DriveRun.from_data(invalid) == null, "Non-finite save numbers are rejected")
	invalid = second.to_data()
	invalid.seed = {"bad": true}
	check(DriveRun.from_data(invalid) == null, "Malformed save seed cannot bypass backup recovery")
	invalid = second.to_data()
	invalid.version = []
	check(DriveRun.from_data(invalid) == null, "Malformed save version is rejected safely")
	var timer := second.elapsed
	second.advance(-1.0, 30.0, 1.0, false)
	check(second.elapsed == timer, "Invalid timesteps do not alter survival state")


func _test_loot_and_cargo() -> void:
	var run := DriveRun.new()
	var before := run.inventory.duplicate()
	var pile := run.open_loot(0)
	check(run.inventory == before and pile.size() == 6, "Opening a building reveals loot without changing inventory")
	var initial_fuel := float(pile[0].amount)
	var result := run.take_loot(0, "0/fuel", 0.5)
	check(result.ok and is_equal_approx(run.inventory.fuel, float(before.fuel) + 0.5), "An explicitly selected fluid quantity is transferred")
	check(is_equal_approx(float(run.open_loot(0)[0].amount), initial_fuel - 0.5), "The unselected fluid quantity stays in the same container")
	check(run.inventory.oil == before.oil and run.inventory.coolant == before.coolant and run.inventory.scrap == before.scrap, "Taking fuel leaves every other supply untouched")
	var after := run.to_data().duplicate(true)
	for bad: float in [-1.0, 0.0, INF, NAN, 10000.0]:
		result = run.take_loot(0, "0/fuel", bad)
		check(not result.ok and run.to_data() == after, "Invalid loot amount cannot alter either inventory or source: " + str(bad))
	result = run.take_loot(0, "missing", 1.0)
	check(not result.ok, "Stale or missing loot IDs cannot duplicate supplies")
	var part: Dictionary = pile[4]
	var found_health := float(part.condition)
	result = run.take_loot(0, str(part.uid), 1.0)
	check(result.ok and run.cargo_parts.size() == 1 and run.loot_piles["0"].size() == 5, "Taking a spare removes precisely that part from its building")
	var old_health := float(run.parts.engine)
	run.install_spare(str(run.cargo_parts[0].uid))
	check(run.parts.engine == found_health and run.cargo_parts[0].condition == old_health, "Installing a spare keeps the replaced component in the same cargo slot")
	var scrap_before := float(run.inventory.scrap)
	run.salvage_spare(str(run.cargo_parts[0].uid))
	check(run.cargo_parts.is_empty() and run.inventory.scrap > scrap_before, "Scrapping a spare frees its slot and grants repair material")
	var loaded := DriveRun.from_data(run.to_data())
	check(loaded != null and loaded.open_loot(0) == run.open_loot(0), "Save roundtrip preserves partially looted containers")
	var full := DriveRun.new()
	var full_pile := full.open_loot(3)
	for index in 7:
		full.cargo_parts.append({"uid": "test/%d" % index, "kind": "part", "slot": "alternator", "amount": 1.0, "condition": 0.7})
	check(full.cargo_used_slots() == DriveRun.CARGO_SLOTS and not full.can_take(full_pile[4], 1.0), "Full cargo slots block additional components")
	check(full.can_take(full_pile[0], 0.5), "Fluid stacks merge without using another cargo slot")
	full.inventory.fuel = 400.0
	check(not full.can_take(full_pile[0], 0.5), "Weight capacity blocks even an existing fluid stack")
	check(full.can_take(full_pile[3], 1.0), "Scrap remains collectable even when cargo is full or overweight")
	var malformed := run.to_data()
	malformed.loot_piles = {"0": [{"uid": "bad", "kind": "fuel", "amount": NAN}]}
	check(DriveRun.from_data(malformed) == null, "Invalid nested remaining-loot data is rejected")
	malformed = run.to_data()
	malformed.cargo_parts = [{"uid": "bad", "kind": "part", "slot": "unknown", "amount": 1, "condition": 0.8}]
	check(DriveRun.from_data(malformed) == null, "Unknown spare-part slots cannot enter a restored run")
	var legacy := DriveRun.new().to_data()
	legacy.version = 1
	legacy.searched = {"0": true}
	legacy.erase("loot_piles")
	legacy.erase("cargo_parts")
	loaded = DriveRun.from_data(legacy)
	check(loaded != null and loaded.open_loot(0).is_empty() and loaded.inventory.fuel == 6.0, "V1 saves retain supplies without respawning already collected loot")


func _test_save() -> void:
	var folder := ProjectSettings.globalize_path("res://../build/godot-tests")
	DirAccess.make_dir_recursive_absolute(folder)
	var path := folder.path_join("roundtrip.json")
	# Dedicated disposable test file; never touches the player's user:// save.
	var run := DriveRun.new()
	run.search_building(0)
	run.take_loot(0, "0/fuel", 0.5)
	run.take_loot(0, "0/part/0", 1.0)
	run.engine_running = true
	var pose := Vector3(120.0, 3.0, -7.0)
	check(DriveSave.store(run, pose, 0.23, path) == OK, "Save writes an atomic primary file")
	var restored := DriveSave.restore(path)
	check(not restored.is_empty() and restored.vehicle.z == -7.0, "Save restores lateral position and heading")
	if not restored.is_empty():
		var loaded := DriveRun.from_data(restored)
		check(loaded.searched.has("0") and not loaded.engine_running, "Restored run preserves loot and parks with the engine off")
		check(loaded.cargo_parts.size() == 1 and loaded.loot_piles["0"].size() == 5, "Disk save restores both cargo parts and leftover building loot")
	check(DriveSave.store(run, pose, 0.23, path) == OK, "Saving again safely replaces the primary and rotates a backup")
	var file := FileAccess.open(path, FileAccess.WRITE)
	file.store_string("{ corrupted")
	file.close()
	check(not DriveSave.restore(path).is_empty(), "A corrupt primary recovers from the previous valid backup")
	check(DriveSave.store(run, Vector3(INF, 0, 0), 0, path) == ERR_INVALID_DATA, "Non-finite vehicle poses cannot be saved")
