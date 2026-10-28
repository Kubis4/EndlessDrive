extends SceneTree
const Models = preload("res://art/road_obstacle_models.gd")
func _initialize() -> void:
	DirAccess.make_dir_recursive_absolute("res://assets/models/obstacles")
	for kind: String in Models.KINDS:
		if "--only-wreck" in OS.get_cmdline_user_args() and kind != "wreck": continue
		if "--only-rubble" in OS.get_cmdline_user_args() and kind != "rubble": continue
		if not _save(kind, 0): quit(1); return
	if "--only-rubble" not in OS.get_cmdline_user_args():
		if not _save("wreck", 1): quit(1); return
	quit()
func _save(kind: String, variant: int) -> bool:
		var model := Models.build(kind, variant)
		_own(model, model)
		var packed := PackedScene.new()
		var code := packed.pack(model)
		var filename := kind + ("_1" if kind == "wreck" and variant == 1 else "")
		if code == OK: code = ResourceSaver.save(packed, "res://assets/models/obstacles/" + filename + ".tscn")
		model.free()
		if code != OK:
			push_error("Cannot save obstacle " + kind)
			return false
		print("BUILT ROAD OBSTACLE ", filename)
		return true
func _own(node: Node, owner_node: Node) -> void:
	# Bake native nodes, not an inherited scene plus duplicated owned children.
	# Otherwise a nested rock instance can acquire ghost children when batched.
	node.scene_file_path = ""
	for child: Node in node.get_children():
		child.owner = owner_node
		_own(child, owner_node)
