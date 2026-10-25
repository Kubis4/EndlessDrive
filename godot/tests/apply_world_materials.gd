extends SceneTree
## Explicit source-material migration: preserves source geometry and collisions.
func _initialize() -> void:
	var folders := ["res://assets/models/", "res://assets/models/stops/"]
	for folder: String in folders:
		for file: String in DirAccess.get_files_at(folder):
			if not file.ends_with(".tscn") or file == "dune_courier.tscn": continue
			var path := folder + file
			var scene := (load(path) as PackedScene).instantiate()
			preload("res://scripts/world_materials.gd").apply(scene)
			var packed := PackedScene.new()
			var error := packed.pack(scene)
			if error == OK: error = ResourceSaver.save(packed, path)
			print("MATERIAL SOURCE ", path, " ", error_string(error))
			scene.free()
			if error != OK:
				quit(1)
				return
	var camp_path := "res://scenes/world/start_camp.tscn"
	var camp := (load(camp_path) as PackedScene).instantiate()
	preload("res://scripts/world_materials.gd").apply(camp)
	var camp_scene := PackedScene.new()
	var camp_error := camp_scene.pack(camp)
	if camp_error == OK: camp_error = ResourceSaver.save(camp_scene, camp_path)
	camp.free()
	if camp_error != OK:
		quit(1)
		return
	quit()
