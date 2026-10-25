extends SceneTree
## Run only with APPDATA redirected to build/menu-flow-user; never player saves.
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func _run() -> void:
	if not OS.get_environment("APPDATA").replace("\\", "/").ends_with("/build/menu-flow-user"):
		push_error("Use the isolated build/menu-flow-user APPDATA for this disk-save probe")
		quit(1)
		return
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	await process_frame
	# Startup is ephemeral; disk saving is then explicitly enabled only inside
	# the dedicated throwaway user-data directory verified above.
	game._ephemeral = false
	game._session_started = true
	game.run.distance = 1234.0
	game._action("menu")
	var saved := DriveSave.restore()
	check(not saved.is_empty() and float(saved.distance) == 1234.0 and game._in_menu and paused, "Main menu writes the current journey before pausing")
	game._menu_action("continue")
	check(not paused and not game._in_menu and game.vehicle.controls_enabled, "Continue restores driving controls after the saved menu")
	game.run.distance = 5678.0
	# If Save+Quit ever stops exiting, the always-processing timeout fails.
	create_timer(3.0, true).timeout.connect(func() -> void:
		push_error("Save and quit did not terminate the application")
		quit(1))
	game._action("quit")
	saved = DriveSave.restore()
	check(not saved.is_empty() and float(saved.distance) == 5678.0, "Save and quit writes the latest journey before exiting")
	if failures: quit(1)
