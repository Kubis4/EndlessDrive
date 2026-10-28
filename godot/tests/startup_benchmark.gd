extends SceneTree
## Use an isolated APPDATA. Measures the real production startup, including warmup.
var started := Time.get_ticks_msec()
func _initialize() -> void:
	call_deferred("_run")
func _run() -> void:
	if not OS.get_environment("APPDATA").replace("\\", "/").ends_with("/build/startup-profile"):
		push_error("Startup benchmark requires build/startup-profile APPDATA")
		quit(1)
		return
	var game: Node
	if "--boot" in OS.get_cmdline_user_args():
		var boot := (load("res://scenes/boot.tscn") as PackedScene).instantiate()
		root.add_child(boot)
		current_scene = boot
		await process_frame
		if DisplayServer.get_name() != "headless": await RenderingServer.frame_post_draw
		print("STARTUP boot_visible_ms=", Time.get_ticks_msec() - started)
		while current_scene == boot: await process_frame
		game = current_scene
	else:
		var at := Time.get_ticks_msec()
		var scene := load("res://scenes/main.tscn") as PackedScene
		print("STARTUP load_ms=", Time.get_ticks_msec() - at)
		at = Time.get_ticks_msec()
		game = scene.instantiate()
		print("STARTUP instantiate_ms=", Time.get_ticks_msec() - at)
		at = Time.get_ticks_msec()
		root.add_child(game)
		print("STARTUP ready_call_ms=", Time.get_ticks_msec() - at)
	await process_frame
	if DisplayServer.get_name() != "headless": await RenderingServer.frame_post_draw
	print("STARTUP menu_visible_ms=", Time.get_ticks_msec() - started)
	_watch(game)
	if "startup_ready" in game and not game.startup_ready:
		await game.startup_finished
	await process_frame
	if DisplayServer.get_name() != "headless": await RenderingServer.frame_post_draw
	print("STARTUP menu_ready_ms=", Time.get_ticks_msec() - started)
	print("STARTUP chunks=", game.world.chunks.size(), " menu=", game._in_menu, " paused=", paused)
	paused = false
	game._ephemeral = true
	game.queue_free()
	await process_frame
	quit()

func _watch(game: Node) -> void:
	await create_timer(30, true).timeout
	if not is_instance_valid(game) or game.startup_ready: return
	print("STARTUP WAIT stage=", game.menu._card_status.text, " chunks=", game.world.chunks.keys(), " pending=", game.world._pending, " jobs=", game.world._jobs.size(), " worker=", game.world._worker, " frames=", Engine.get_process_frames())
	quit(1)
