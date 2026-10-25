extends SceneTree
func _initialize() -> void: call_deferred("_build")
func _build() -> void:
	var hud := preload("res://scripts/expedition_hud.gd").new()
	hud.name = "ExpeditionHUD"
	root.add_child(hud)
	hud.touch_mode = 1
	hud._layout()
	_own(hud, hud)
	var scene := PackedScene.new()
	var error := scene.pack(hud)
	if error == OK: error = ResourceSaver.save(scene, "res://scenes/ui/expedition_hud.tscn")
	print("HUD C authored scene: ", error_string(error))
	quit(0 if error == OK else 1)
func _own(node: Node, owner_node: Node) -> void:
	for child in node.get_children():
		child.owner = owner_node
		_own(child, owner_node)
