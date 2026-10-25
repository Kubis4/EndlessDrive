extends SceneTree
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var scene := load("res://scenes/world/deformation_preview.tscn").instantiate() as Node3D
	root.add_child(scene)
	for tick in 180: await process_frame
	await RenderingServer.frame_post_draw
	var error := root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/deformation-editor-preview.png"))
	print("DEFORMATION PREVIEW CAPTURE: ", error_string(error))
	quit(0 if error == OK else 1)
