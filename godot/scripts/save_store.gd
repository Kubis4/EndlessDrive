class_name DriveSave
extends RefCounted

const PATH := "user://journey-v1.json"


static func store(run: DriveRun, pose: Vector3, heading: float, path: String = PATH) -> Error:
	if not pose.is_finite() or not is_finite(heading):
		return ERR_INVALID_DATA
	var data := run.to_data()
	data["vehicle"] = {"x": pose.x, "y": pose.y, "z": pose.z, "heading": heading}
	var file := FileAccess.open(path + ".tmp", FileAccess.WRITE)
	if file == null:
		return FileAccess.get_open_error()
	file.store_string(JSON.stringify(data))
	file.flush()
	var write_error := file.get_error()
	file.close()
	if write_error != OK:
		return write_error
	# Only replace the backup when the previous primary is itself valid.
	if FileAccess.file_exists(path) and not _read(path).is_empty():
		var copy_error := DirAccess.copy_absolute(path, path + ".bak")
		if copy_error != OK:
			return copy_error
	return DirAccess.rename_absolute(path + ".tmp", path)


static func restore(path: String = PATH) -> Dictionary:
	for candidate: String in [path, path + ".bak"]:
		var data := _read(candidate)
		if not data.is_empty():
			return data
	return {}


static func _read(path: String) -> Dictionary:
	if not FileAccess.file_exists(path):
		return {}
	var file := FileAccess.open(path, FileAccess.READ)
	if file == null:
		return {}
	var parser := JSON.new()
	if parser.parse(file.get_as_text()) != OK:
		return {}
	var parsed: Variant = parser.data
	if not parsed is Dictionary or DriveRun.from_data(parsed) == null:
		return {}
	var vehicle: Variant = parsed.get("vehicle", {})
	if not vehicle is Dictionary:
		return {}
	for axis: String in ["x", "y", "z", "heading"]:
		var number: Variant = vehicle.get(axis)
		if not (number is float or number is int) or not is_finite(float(number)) or absf(float(number)) > 100000000.0:
			return {}
	return parsed
