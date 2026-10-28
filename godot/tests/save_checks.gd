extends SceneTree
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, text: String) -> void:
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", text])
func _run() -> void:
	var path := "res://../build/vehicle-save-check.json"
	var garage_path := "res://../build/vehicle-garage-check.json"
	var garage := DriveProfile.new()
	garage.selected_vehicle = "jeep"
	garage.bank = 123
	garage.upgrades.engine = 2
	check(garage.store(garage_path) == OK, "Garage selection writes atomically")
	var loaded_garage := DriveProfile.restore(garage_path)
	check(loaded_garage.selected_vehicle == "jeep" and loaded_garage.bank == 123 and loaded_garage.upgrades.engine == 2, "Vehicle choice preserves scrap and upgrades")
	var run := DriveRun.new()
	garage.selected_vehicle = "izh"
	garage.prepare_run(run)
	check(DriveSave.store(run, Vector3(15, 2, 3), 0.2, path) == OK, "A van journey writes through the production save path")
	var saved := DriveSave.restore(path)
	var restored := DriveRun.from_data(saved)
	check(restored != null and restored.vehicle_id == "izh" and not restored.four_wheel_drive and is_equal_approx(float(saved.vehicle.x), 15), "Run restoration preserves FWD and the saved location")
	garage.selected_vehicle = "jeep"
	garage.prepare_run(run)
	check(DriveSave.store(run, Vector3(25, 2, 3), 0.3, path) == OK, "Changing model rotates the previous valid save")
	var bad := FileAccess.open(path, FileAccess.WRITE)
	bad.store_string("{ corrupted")
	bad.close()
	check(DriveRun.from_data(DriveSave.restore(path)).vehicle_id == "izh", "Corrupt Jeep primary recovers the previous van backup")
	for file: String in [path, garage_path]:
		for suffix: String in ["", ".tmp", ".bak"]:
			if FileAccess.file_exists(file + suffix): DirAccess.remove_absolute(file + suffix)
	quit(1 if failures else 0)
