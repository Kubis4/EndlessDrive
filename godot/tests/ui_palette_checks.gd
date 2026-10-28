extends SceneTree
## Baseline records rectangles, visibility, touch polygons and content margins
## before retinting; checks a colour-only update without using screenshots.
const BASELINE := "res://../build/ui-layout-before.json"
func _initialize() -> void: call_deferred("_run")
func _collect(node: Node, target: Dictionary) -> void:
	if node is Control:
		var rect: Rect2 = node.get_global_rect()
		var data: Array = [rect.position.x, rect.position.y, rect.size.x, rect.size.y, node.visible]
		if node is Button:
			for side in [SIDE_LEFT, SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM]: data.append(node.get_theme_stylebox("normal").get_content_margin(side))
		target[str(node.get_path())] = data
	for child: Node in node.get_children(): _collect(child, target)
func _run() -> void:
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	game.vehicle.set_physics_process(false)
	var records := {}
	for dimensions: Vector2i in [Vector2i(1280, 720), Vector2i(1920, 1080)]:
		root.size = dimensions
		for touch: int in [1, 2]:
			game.hud.expedition_ui.touch_mode = touch
			game.hud.expedition_ui.set_equipment_open(touch == 1)
			game.hud.expedition_ui._layout()
			for frame in 3: await process_frame
			var data := {}
			_collect(game.hud, data)
			records[str(dimensions) + str(touch)] = data
	if "--record-layout" in OS.get_cmdline_user_args():
		var file := FileAccess.open(BASELINE, FileAccess.WRITE)
		file.store_string(JSON.stringify(records))
		file.close()
		print("UI LAYOUT: baseline recorded")
	else:
		var baseline: Variant = JSON.parse_string(FileAccess.get_file_as_string(BASELINE))
		var differences: Array[String] = []
		for variant: String in records:
			for path: String in records[variant]:
				var previous: Array = baseline.get(variant, {}).get(path, []) if baseline is Dictionary else []
				var current: Array = records[variant][path]
				var equal := previous.size() == current.size()
				if equal:
					for i in current.size():
						if current[i] is bool:
							if current[i] != previous[i]: equal = false
						elif absf(float(current[i]) - float(previous[i])) > 0.01: equal = false
				if not equal: differences.append(variant + " " + path)
		print("UI LAYOUT: ", records.size(), " viewport/input combinations, differences=", differences.size())
		if not differences.is_empty():
			push_error(str(differences.slice(0, 8)))
			quit(1)
			return
	game.queue_free()
	await process_frame
	quit()
