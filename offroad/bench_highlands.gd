extends SceneTree
## Frame-time benchmark of Lesná vrchovina: a chase view driving from the camp
## and the overview, after a warm-up. Prints median / p95 frame time and the
## renderer's draw calls and primitives.
## godot --path . --resolution 1920x1080 --script res://offroad/bench_highlands.gd -- --quality=reference
var game

func _initialize() -> void: call_deferred("run")

func measure(label: String, frames: int, drive: bool) -> void:
	for i in 60: await process_frame
	var times := PackedFloat32Array()
	var last := Time.get_ticks_usec()
	for i in frames:
		if drive: game.lead.simulate(1.0 / 60.0, 0.5, 0.0, 0.0, false)
		await RenderingServer.frame_post_draw
		var now := Time.get_ticks_usec()
		times.append((now - last) / 1000.0)
		last = now
	times.sort()
	var draws := RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_DRAW_CALLS_IN_FRAME)
	var primitives := RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_PRIMITIVES_IN_FRAME)
	print("BENCH %s median=%.2f ms (%d FPS) p95=%.2f ms draws=%d primitives=%d" % [label, times[times.size() / 2], roundi(1000.0 / times[times.size() / 2]), times[int(times.size() * 0.95)], draws, primitives])

func run() -> void:
	game = (load("res://offroad/highlands.tscn") as PackedScene).instantiate()
	root.add_child(game)
	await process_frame
	game.lead.set_physics_process(false)
	game.teleport_to_test(0)
	game._set_camera_mode(2)
	await measure("chase", 300, true)
	game.teleport_to_test(4)
	game._set_camera_mode(2)
	await measure("forest", 300, true)
	game._set_camera_mode(0)
	game.camera_size = 120.0
	await measure("overview", 200, false)
	quit()
