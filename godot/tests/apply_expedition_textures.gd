extends SceneTree
## Explicit material-only patch. Preserves existing model geometry/transforms,
## wheel pivots, collisions, manual node edits and native scene ownership.
const Assets = preload("res://scripts/expedition_scenery.gd")
const Materials = preload("res://art/expedition_materials.gd")
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	for label: String in Assets.MODELS:
		var path := Assets.DIRECTORY + label + ".tscn"
		var model := (load(path) as PackedScene).instantiate() as Node3D
		var before := geometry_signature(model)
		Materials.attach(model, label)
		if geometry_signature(model) != before:
			push_error("Texture patch changed geometry unexpectedly: " + label)
			model.free()
			quit(1)
			return
		var scene := PackedScene.new()
		var error := scene.pack(model)
		if error == OK: error = ResourceSaver.save(scene, path)
		model.free()
		if error != OK:
			push_error("Cannot apply textures to " + label)
			quit(1)
			return
		print("TEXTURED saved model: ", label)
	print("TEXTURED: all 20 model scenes; geometry and hitboxes preserved")
	quit()

func geometry_signature(node: Node) -> Array:
	var result: Array = [node.name]
	if node is Node3D: result.append(node.transform)
	if node is MeshInstance3D: result.append(node.mesh)
	if node is CollisionShape3D: result.append(node.shape)
	for child: Node in node.get_children(): result.append(geometry_signature(child))
	return result
