extends SceneTree
## Actual outdoor light toggle after the production warmup; no player saves.
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	game._focus_pause_enabled = false
	root.add_child(game)
	game.run.day_time = 0.82
	game.run.headlights = false
	game.vehicle.controls_enabled = false
	game.weather.forced_mode = "Jasno"
	var warmup := load("res://scripts/render_warmup.gd").new() as SubViewport
	warmup.set("environment_template", game.environment)
	warmup.set("vehicle_template", game.vehicle._visual)
	root.add_child(warmup)
	var primer := preload("res://scripts/light_primer.gd").new()
	primer.vehicle = game.vehicle
	primer.environment = game.environment
	primer.allow_driving = true
	root.add_child(primer)
	for i in 90: await process_frame
	var first := 0.0
	var repeated := 0.0
	var high := 0.0
	var first_frame := 0
	var last := Time.get_ticks_usec()
	for i in 240:
		if i in [30, 90, 120, 180]: game._action("lights")
		await physics_frame
		await process_frame
		var now := Time.get_ticks_usec()
		var ms := (now - last) / 1000.0
		last = now
		if i >= 30 and i < 60 and ms > first:
			first = ms
			first_frame = i
		if i >= 90 and i < 120: high = maxf(high, ms)
		if i >= 180 and i < 210: repeated = maxf(repeated, ms)
	print("HEADLIGHTS first low max=%.2f ms; high max=%.2f ms; repeated low max=%.2f ms; actual lights=%d" % [first, high, repeated, game.vehicle._lights.size()])
	print("FIRST LOW MAX FRAME ", first_frame)
	game.queue_free()
	await process_frame
	await process_frame
	quit()
