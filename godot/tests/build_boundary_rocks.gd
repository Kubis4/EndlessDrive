extends SceneTree
const Models = preload("res://art/boundary_rock_models.gd")
func _initialize() -> void:
	DirAccess.make_dir_recursive_absolute("res://assets/models/boundary")
	for snowy: bool in [false, true]:
		for kind: String in Models.KINDS:
			var root := Models.build(kind, snowy)
			_own(root, root)
			var packed := PackedScene.new()
			var result := packed.pack(root)
			var name := kind + ("_snow" if snowy else "")
			if result == OK: result = ResourceSaver.save(packed, "res://assets/models/boundary/" + name + ".tscn")
			root.free()
			if result != OK:
				push_error("Cannot save boundary rock " + name)
				quit(1)
				return
			print("BUILT BOUNDARY ROCK ", name)
	quit()
func _own(node: Node, root: Node) -> void:
	for child: Node in node.get_children():
		child.owner = root
		_own(child, root)
