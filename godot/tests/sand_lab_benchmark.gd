extends SceneTree
## Uncapped native rendering, deterministic 60 Hz simulation; compare identical routes.
class ProfileCar extends DriveVehicle:
	var total_ms := 0.0
	var visual_ms := 0.0
	func _physics_process(dt: float) -> void:
		var time := Time.get_ticks_usec()
		super._physics_process(dt)
		total_ms = (Time.get_ticks_usec() - time) / 1000.0
	func _update_visual(dt: float, slope: float) -> void:
		var time := Time.get_ticks_usec()
		super._update_visual(dt, slope)
		visual_ms = (Time.get_ticks_usec() - time) / 1000.0
func _initialize() -> void: call_deferred("_run")

func _run() -> void:
	DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_DISABLED)
	Engine.max_fps = 0
	var lab = load("res://scenes/sand_lab.tscn").instantiate()
	lab.get_node("Vehicle").set_script(ProfileCar)
	root.add_child(lab)
	for tick in 90: await process_frame
	var results := {}
	for phase in (["driving"] if "--drive-only" in OS.get_cmdline_user_args() else ["idle", "driving", "dust"]):
		lab.reset_sand()
		if phase == "dust": lab.toggle_dust()
		var samples: Array[float] = []
		var car_cpu: Array[float] = []
		var contacts_cpu: Array[float] = []
		var previous := Time.get_ticks_usec()
		for tick in 600:
			if phase != "idle":
				if lab.vehicle.position.x > 23: lab.reset_car()
				Input.action_press("throttle", 0.7 if lab.vehicle.speed < 6 else 0.15)
			await process_frame
			var now := Time.get_ticks_usec()
			if tick >= 60:
				samples.append((now - previous) / 1000.0)
				car_cpu.append(lab.vehicle.total_ms)
				contacts_cpu.append(lab.vehicle.visual_ms)
			previous = now
		Input.action_release("throttle")
		samples.sort()
		car_cpu.sort()
		contacts_cpu.sort()
		var total := 0.0
		for sample in samples: total += sample
		results[phase] = {"fps": 1000.0 / (total / samples.size()), "frame_p50_ms": samples[270], "frame_p95_ms": samples[513], "car_p95_ms": car_cpu[513], "contacts_p95_ms": contacts_cpu[513], "draw_calls": Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME), "triangles": Performance.get_monitor(Performance.RENDER_TOTAL_PRIMITIVES_IN_FRAME)}
		print("BENCH %s %s" % [phase, JSON.stringify(results[phase])])
		print("CPU car p95=", car_cpu[513], " contacts p95=", contacts_cpu[513])
	var name := "sand-lab-benchmark"
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--label="): name += "-" + arg.trim_prefix("--label=")
	var file := FileAccess.open("res://../build/%s.json" % name, FileAccess.WRITE)
	file.store_string(JSON.stringify(results, "\t"))
	file.close()
	lab.queue_free()
	await process_frame
	quit()
