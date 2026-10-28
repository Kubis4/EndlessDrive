extends SceneTree
## Explicitly regenerates only generated preview resources, never authored main,
## vehicle, UI, camera or stop scenes. Also used by the editor inspector button.
const GENERATED := "res://scenes/world/generated/"
var failed := false
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	DirAccess.make_dir_recursive_absolute(GENERATED)
	var seed := 1000003
	var distance := 0.0
	var output := GENERATED + "editor_terrain.scn"
	var showcases := false
	var geometry_only := false
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--seed="): seed = arg.trim_prefix("--seed=").to_int()
		if arg.begins_with("--distance="): distance = arg.trim_prefix("--distance=").to_float()
		if arg.begins_with("--output="): output = arg.trim_prefix("--output=")
		if arg == "--showcases": showcases = true
		if arg == "--geometry-only": geometry_only = true
	if output not in [GENERATED + "editor_terrain.scn", GENERATED + "bridge.scn", GENERATED + "rail.scn", GENERATED + "tunnel.scn", GENERATED + "alpine.scn", GENERATED + "mountain.scn", GENERATED + "canyon.scn", GENERATED + "forest.scn", GENERATED + "swamp.scn"]:
		push_error("Only known generated preview resources may be replaced")
		quit(1)
		return
	_build(seed, distance, output)
	if showcases:
		if not geometry_only: _wrapper("editor_terrain", seed, distance)
		# Legacy seed determines feature type without rewriting the route logic.
		for entry: Array in [["bridge", 2664.0], ["rail", 744.0], ["tunnel", 1704.0]]:
			_build(24601, entry[1], GENERATED + entry[0] + ".scn")
			if not geometry_only: _wrapper(entry[0], 24601, entry[1])
		_build(1000005, 180.0, GENERATED + "alpine.scn")
		if not geometry_only: _wrapper("alpine", 1000005, 180.0)
		# Every saved showcase embeds geometry, including these older views.
		for entry: Array in [["canyon", 1000002, 180.0], ["forest", 1000004, 180.0], ["swamp", 1000006, 120.0], ["mountain", 24601, 3200.0]]:
			_build(int(entry[1]), float(entry[2]), GENERATED + entry[0] + ".scn")
	quit(1 if failed else 0)

func _build(seed: int, distance: float, path: String) -> void:
	var world := DriveWorld.new()
	root.add_child(world)
	world.configure(seed)
	# Dummy/headless renderers do not round-trip GPU MultiMesh transforms.
	# Keep native prop meshes in the saved editor reference instead.
	world.batch_scenery = false
	world.hazards_enabled = true
	world.clock = 8 - posmod(seed, 20)
	var center := floori(distance / DriveWorld.CHUNK_LENGTH)
	for index in range(center - 1, center + 2): world._create_chunk(index)
	var geometry := Node3D.new()
	geometry.name = "GeneratedTerrain"
	_flatten(world)
	for chunk in world.get_children():
		world.remove_child(chunk)
		geometry.add_child(chunk)
	_strip(geometry, path == GENERATED + "editor_terrain.scn")
	_owners(geometry, geometry)
	var packed := PackedScene.new()
	var error := packed.pack(geometry)
	if error == OK: error = ResourceSaver.save(packed, path, ResourceSaver.FLAG_COMPRESS)
	if error != OK:
		failed = true
		push_error("Cannot save preview: " + error_string(error))
	else: print("UPDATED EDITOR PREVIEW ", path, " seed=", seed, " distance=", distance)
	geometry.free()
	world.free()

func _flatten(node: Node) -> void:
	# The snapshot contains native nodes, so stripping prefab colliders is
	# persisted instead of resurrecting them from linked source scenes.
	for child in node.get_children():
		child.owner = null
		child.scene_file_path = ""
		_flatten(child)

func _strip(node: Node, omit_start_camp: bool = false) -> void:
	for key in node.get_meta_list(): node.remove_meta(key)
	if node is RouteHazard: node.set_script(null)
	for child: Node in node.get_children():
		if child is CollisionShape3D or (omit_start_camp and str(child.name).begins_with("Building_0_")):
			node.remove_child(child)
			child.free()
		elif child is CollisionObject3D:
			# Preserve the train's visible children while removing every collider.
			var visual := Node3D.new()
			visual.name = child.name
			visual.transform = child.transform
			for part in child.get_children():
				child.remove_child(part)
				visual.add_child(part)
			node.remove_child(child)
			child.free()
			node.add_child(visual)
			_strip(visual, omit_start_camp)
		else: _strip(child, omit_start_camp)

func _owners(node: Node, scene_root: Node) -> void:
	for child: Node in node.get_children():
		child.owner = scene_root
		if child.scene_file_path.is_empty(): _owners(child, scene_root)

func _wrapper(label: String, seed: int, distance: float) -> void:
	var preview := Node3D.new()
	preview.name = "EditorTerrainPreview" if label == "editor_terrain" else label.to_pascal_case() + "Preview"
	preview.set_script(load("res://scripts/editor_preview.gd"))
	preview.set("preview_seed", seed)
	preview.set("preview_distance", distance)
	preview.set("geometry_path", GENERATED + label + ".scn")
	var geometry := (load(GENERATED + label + ".scn") as PackedScene).instantiate()
	preview.add_child(geometry)
	geometry.owner = preview
	var packed := PackedScene.new()
	var error := packed.pack(preview)
	if error == OK: error = ResourceSaver.save(packed, "res://scenes/world/" + label + ("" if label == "editor_terrain" else "_preview") + ".tscn")
	if error != OK:
		failed = true
		push_error("Cannot save preview wrapper: " + error_string(error))
	preview.free()
