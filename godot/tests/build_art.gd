extends SceneTree


func _initialize() -> void:
	call_deferred("_build")


func _build() -> void:
	var assets := {
		"dune_courier": DesertModels.rover(),
		"adobe_workshop": DesertModels.workshop(),
		"saguaro": DesertModels.cactus(),
		"desert_scrub": DesertModels.scrub(),
		"mesa_rock": DesertModels.rock(),
		"utility_pole": DesertModels.pole(),
		"ranch_fence": DesertModels.fence(),
		"water_tower": DesertModels.tower(),
	}
	for label: String in assets:
		var model: Node3D = assets[label]
		_assign_owner(model, model)
		var packed := PackedScene.new()
		var result := packed.pack(model)
		if result == OK:
			result = ResourceSaver.save(packed, "res://assets/models/" + label + ".tscn")
		model.free()
		if result != OK:
			push_error("Cannot build " + label + ": " + error_string(result))
			quit(1)
			return
		print("BUILT original model: ", label)
	DesertModels._palette.clear()
	quit()


func _assign_owner(node: Node, asset_root: Node) -> void:
	for child: Node in node.get_children():
		child.owner = asset_root
		_assign_owner(child, asset_root)
