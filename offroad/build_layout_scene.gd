extends SceneTree
## Creates the editable layout scene offroad/highlands_layout.tscn from the
## game's current layout (see offroad/layout/layout_editor.gd). Open it in the
## Godot editor to move trails, ponds, test spots and terrain stamps, add
## extras, and re-bake with the "Uložiť a prepiecť" button.
## godot --headless --path . --script res://offroad/build_layout_scene.gd
const SCENE_PATH := "res://offroad/highlands_layout.tscn"

func _initialize() -> void: call_deferred("run")

func run() -> void:
	var layout := Node3D.new()
	layout.name = "RozlozenieVrchoviny"
	layout.set_script(load("res://offroad/layout/layout_editor.gd"))
	root.add_child(layout)
	layout.load_from_game()
	# A sun so the preview reads in the editor.
	var sun := DirectionalLight3D.new()
	sun.name = "Slnko"
	sun.rotation_degrees = Vector3(-50, -30, 0)
	layout.add_child(sun)
	sun.owner = layout
	var scene := PackedScene.new()
	scene.pack(layout)
	print("LAYOUT scene saved=%s trails=%d" % [ResourceSaver.save(scene, SCENE_PATH), layout.get_node("Trasy").get_child_count()])
	quit()
