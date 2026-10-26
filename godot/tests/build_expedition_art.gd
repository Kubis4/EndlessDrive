extends SceneTree
const Art = preload("res://art/expedition_models.gd")
const OUTPUT := "res://assets/models/expedition/"
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	DirAccess.make_dir_recursive_absolute(OUTPUT)
	var assets := {}
	for key: String in ["mountain_pine", "alpine_pine", "juniper", "woodland_tree", "wetland_tree", "dead_tree"]: assets[key] = Art.tree(key)
	for key: String in ["marsh_reeds", "fern_patch", "meadow_shrub"]: assets[key] = Art.undergrowth(key)
	for key: String in ["locomotive", "freight_wagon", "tank_wagon", "timber_wagon"]: assets[key] = Art.train(key)
	assets["mountain_ridge"] = Art.mountain()
	assets["alpine_peak"] = Art.mountain(true)
	assets["rocky_peak"] = Art.peak()
	assets["snowy_peak"] = Art.peak(true)
	assets["tunnel_portal"] = Art.tunnel()
	assets["tunnel_rib"] = Art.tunnel(false)
	assets["rail_portal"] = Art.rail_portal()
	for key: String in assets:
		if OS.get_cmdline_user_args().has("--only-peaks") and key not in ["rocky_peak", "snowy_peak"]:
			assets[key].free()
			continue
		var node: Node3D = assets[key]
		_own(node, node)
		var scene := PackedScene.new()
		var error := scene.pack(node)
		if error == OK: error = ResourceSaver.save(scene, OUTPUT + key + ".tscn")
		node.free()
		if error != OK:
			push_error("Cannot save expedition model: " + key)
			quit(1)
			return
		print("BUILT expedition model: ", key)
	if not OS.get_cmdline_user_args().has("--only-peaks"): _gallery()
	quit()
func _own(node: Node, scene_root: Node) -> void:
	for child in node.get_children():
		child.owner = scene_root
		# Linked models keep their own internal ownership. Re-owning their
		# children serializes duplicate inherited nodes and orphan resources.
		if child.scene_file_path.is_empty(): _own(child, scene_root)

func _gallery() -> void:
	var scene := Node3D.new()
	scene.name = "ExpeditionArtGallery"
	var environment := WorldEnvironment.new()
	environment.environment = Environment.new()
	environment.environment.background_mode = Environment.BG_COLOR
	environment.environment.background_color = Color("a8b6b5")
	environment.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.environment.ambient_light_color = Color("d5ddda")
	environment.environment.ambient_light_energy = 0.4
	scene.add_child(environment)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-48, -30, 0)
	sun.light_color = Color("fff0d2")
	sun.light_energy = 0.85
	sun.shadow_enabled = true
	sun.shadow_bias = 0.1
	sun.directional_shadow_max_distance = 180
	scene.add_child(sun)
	var display_base := Art.box(scene, "SandDisplayBase", Vector3(0, -0.6, 0), Vector3(100, 0.8, 74), "stone_light")
	display_base.material_override = display_base.material_override.duplicate()
	display_base.material_override.set_shader_parameter("texture_enabled", false)
	var rows := [
		["mountain_ridge", "alpine_peak", "rocky_peak", "snowy_peak", "tunnel_portal", "tunnel_rib", "rail_portal"],
		["mountain_pine", "alpine_pine", "juniper", "woodland_tree", "wetland_tree", "dead_tree"],
		["locomotive", "tank_wagon", "freight_wagon", "timber_wagon", "marsh_reeds", "fern_patch", "meadow_shrub"],
	]
	for row in rows.size():
		for i in rows[row].size():
			var key: String = rows[row][i]
			var model := (load(OUTPUT + key + ".tscn") as PackedScene).instantiate() as Node3D
			model.position = Vector3((i - (rows[row].size() - 1) * 0.5) * (14 if row == 0 else 12), 0, -24 + row * 23)
			if row == 0 and i < 4: model.position.z -= 8
			if key in ["rocky_peak", "snowy_peak"]: model.scale = Vector3.ONE * 0.55
			if row == 2 and i < 4: model.rotation.y = PI * 0.5
			scene.add_child(model)
			var label := Label3D.new()
			label.text = key.replace("_", " ").to_upper()
			label.font_size = 32
			label.pixel_size = 0.025
			label.position = model.position + Vector3(0, 0.08, 6.0 if row != 0 else 11.0)
			label.rotation_degrees.x = -65
			label.modulate = Color("303b38")
			label.outline_size = 0
			scene.add_child(label)
	var camera := Camera3D.new()
	camera.name = "GalleryCamera"
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 108
	camera.far = 300
	camera.position = Vector3(20, 85, 100)
	camera.basis = Basis.looking_at(-camera.position.normalized())
	scene.add_child(camera)
	_own(scene, scene)
	var packed := PackedScene.new()
	var error := packed.pack(scene)
	if error == OK: error = ResourceSaver.save(packed, "res://scenes/world/expedition_art_gallery.tscn")
	if error != OK: push_error("Cannot save expedition gallery")
	scene.free()
