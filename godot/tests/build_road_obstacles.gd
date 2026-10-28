extends SceneTree
const Models = preload("res://art/road_obstacle_models.gd")
func _initialize() -> void:
	DirAccess.make_dir_recursive_absolute("res://assets/models/obstacles")
	for kind: String in Models.KINDS:
		var model := Models.build(kind)
		_own(model, model)
		var packed := PackedScene.new()
		var code := packed.pack(model)
		if code == OK: code = ResourceSaver.save(packed, "res://assets/models/obstacles/" + kind + ".tscn")
		model.free()
		if code != OK:
			push_error("Cannot save obstacle " + kind)
			quit(1)
			return
		print("BUILT ROAD OBSTACLE ", kind)
	quit()
func _own(node: Node, owner_node: Node) -> void:
	# Bake native nodes, not an inherited scene plus duplicated owned children.
	# Otherwise a nested rock instance can acquire ghost children when batched.
	node.scene_file_path = ""
	for child: Node in node.get_children():
		child.owner = owner_node
		_own(child, owner_node)
