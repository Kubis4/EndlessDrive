extends SceneTree
## Compares 4x4 / 4x2 and open / locked differentials on the test lanes:
## distance covered in a fixed time from standstill at full throttle.
func _initialize() -> void: call_deferred("run")

func trial(game, lane_name: String, four: bool, locked: bool, seconds: float) -> Array:
	var spots: Array = game.test_spots
	var index := -1
	for i in spots.size(): if spots[i][0] == lane_name: index = i
	game.teleport_to_test(index)
	var car = game.lead
	car.four_wheel_drive = four
	car.diff_locked = locked
	var start: Vector3 = car.position
	var spin := 0.0
	var steps := int(seconds * 60.0)
	for i in steps:
		await physics_frame
		car.simulate(1.0 / 60.0, 1.0, 0.0, 0.0, false)
		for w in 4: spin = maxf(spin, car.drive_slip[w])
	return [Vector2(car.position.x - start.x, car.position.z - start.z).length(), car.speed * 3.6, spin]

func run() -> void:
	var game = (load("res://desert_battlefield/desert_battlefield.tscn") as PackedScene).instantiate()
	root.add_child(game)
	await process_frame
	game.lead.set_physics_process(false)
	var failures := []
	var results := {}
	for lane in ["Bahno", "Hrboľe a skok", "Ľad", "Sneh"]:
		for mode in [[true, true], [true, false], [false, true], [false, false]]:
			var r: Array = await trial(game, lane, mode[0], mode[1], 6.0 if lane != "Hrboľe a skok" else 9.0)
			var key := "%s %s %s" % [lane, "4x4" if mode[0] else "4x2", "lock" if mode[1] else "open"]
			results[key] = r
			print("%-28s dist=%6.1f m  speed=%5.1f km/h  max_slip=%.2f" % [key, r[0], r[1], r[2]])
	for lane in ["Bahno", "Ľad", "Sneh"]:
		if results[lane + " 4x4 lock"][0] < results[lane + " 4x2 open"][0]: failures.append(lane + ": 4x4 locked slower than 4x2 open")
	if results["Ľad 4x4 open"][0] <= results["Ľad 4x2 open"][0]: failures.append("ice: 4x4 must accelerate better than 4x2")
	print("DRIVETRAIN CHECKS ", "PASS" if failures.is_empty() else "FAIL " + str(failures))
	quit()
