extends SceneTree
const Assets = preload("res://scripts/expedition_scenery.gd")
const Materials = preload("res://art/expedition_materials.gd")
var failures := 0
var checks := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", label])
func _run() -> void:
	var atlas := Materials.texture_set()
	check(atlas != null and atlas.get_layers() == 8 and atlas.get_width() == 443 and atlas.get_height() == 443 and atlas.has_mipmaps(), "All eight original image-based material layers load with separate mipmaps")
	if atlas == null or atlas.get_layers() != 8:
		quit(1)
		return
	# Dummy rendering has no GPU readback; inspect the original image instead.
	var texture_source := Image.load_from_file("res://assets/textures/expedition-materials-v1.png")
	var detailed_layers := true
	for layer in 8:
		var start := Vector2i(roundi((layer % 4) * texture_source.get_width() / 4.0), roundi((layer / 4) * texture_source.get_height() / 2.0))
		var image := texture_source.get_region(Rect2i(start, Vector2i(443, 443)))
		var darkest := 1.0
		var lightest := 0.0
		for y in range(10, image.get_height(), 32):
			for x in range(10, image.get_width(), 32):
				var pixel := image.get_pixel(x, y)
				var luma := pixel.r * 0.2126 + pixel.g * 0.7152 + pixel.b * 0.0722
				darkest = minf(darkest, luma)
				lightest = maxf(lightest, luma)
		detailed_layers = detailed_layers and lightest - darkest > 0.08
	check(detailed_layers, "Every material contains real image detail rather than a flat colour swatch")
	for label: String in Assets.MODELS:
		var model := Assets.instance(label)
		var meshes := model.find_children("*", "MeshInstance3D", true, false)
		var valid := not meshes.is_empty()
		for node: MeshInstance3D in meshes:
			valid = valid and node.mesh != null and node.mesh.get_surface_count() > 0 and node.material_override != null and node.transform.is_finite()
		check(valid and meshes.size() <= 32, "%s is a saved, bounded-draw native model with valid geometry/materials" % label)
		var textured := true
		for node: MeshInstance3D in meshes:
			var material: Material = node.material_override
			if not material is ShaderMaterial or material.resource_name == "Glass": continue
			textured = textured and material.get_shader_parameter("texture_enabled") == true and material.get_shader_parameter("model_atlas") is Texture2DArray and material.get_shader_parameter("texture_strength") > 0.0
		check(textured, "%s has actual image textures bound on its non-glass surfaces" % label)
		if label.ends_with("pine") or label.ends_with("tree") or label == "juniper":
			check(not model.find_children("*", "CollisionShape3D", true, false).is_empty(), "%s retains its physical trunk" % label)
		if label == "mountain_pine":
			var foliage := model.get_node("Pine") as MeshInstance3D
			var normals: PackedVector3Array = foliage.mesh.surface_get_arrays(0)[Mesh.ARRAY_NORMAL]
			var upward := 0.0
			for normal: Vector3 in normals: upward += normal.y
			check(upward / normals.size() > 0.1, "Conifer crown normals face out/up so light and snow settle on their visible tops")
		if label == "alpine_peak":
			var mesh := meshes[0] as MeshInstance3D
			check(mesh.mesh.get_aabb().position.z >= 0.0 and mesh.mesh.get_aabb().size.y > 14, "Mountain mass stays outside the road-facing boundary and has a real high ridge")
			check(mesh.material_override.get_shader_parameter("material_layer") == 1, "Alpine rock uses granite, not the sandstone or foliage texture")
		if label == "tunnel_portal":
			var masonry := model.get_node("StoneLight") as MeshInstance3D
			check(masonry.mesh.get_aabb().end.y > 7.8, "Merged portal retains the full high arch alongside its primitive buttress caps")
			check(masonry.material_override.get_shader_parameter("material_layer") == 5, "Portal arch uses its masonry image layer")
		if label in ["locomotive", "tank_wagon", "freight_wagon", "timber_wagon"]:
			check(model.find_children("TrainWheel*", "MeshInstance3D", true, false).size() == 8, "%s has eight separate animated wheel meshes" % label)
		model.free()
	var basis := Assets.road_basis(Vector3(1, 0.13, 0.4))
	check(basis.is_finite() and absf(basis.determinant() - 1.0) < 0.001, "Tunnel placement follows curved/graded roads with an orthonormal frame")
	var world := DriveWorld.new()
	root.add_child(world)
	world.configure(24601)
	var rail := RouteHazard.new()
	rail.world = world
	rail.kind = "rail"
	rail.feature_x = 744
	world.add_child(rail)
	rail.build()
	world.clock = 7.5 - posmod(world.world_seed, 20)
	var wheel := rail._train_wheels[0]
	var before := wheel.basis
	rail._physics_process(1.0 / 60.0)
	check(rail._train_wheels.size() == 32 and not wheel.basis.is_equal_approx(before), "Actual crossing uses all four new cars and turns their 32 wheels")
	check(rail._train.find_children("*", "CollisionShape3D", true, false).size() == 4, "Moving train retains four fitted physical hitboxes")
	rail.free()
	var tunnel := RouteHazard.new()
	tunnel.world = world
	tunnel.kind = "tunnel"
	tunnel.feature_x = 1704
	world.add_child(tunnel)
	tunnel.build()
	check(tunnel.get_node("TunnelEntrance").scene_file_path.ends_with("tunnel_portal.tscn") and tunnel.get_node("TunnelExit").scene_file_path.ends_with("tunnel_portal.tscn"), "Both road-tunnel ends use the editable masonry portal scene")
	check(tunnel.has_node("TunnelShellCollision") and tunnel.has_node("TunnelMountainCollisionNorth") and tunnel.has_node("TunnelMountainCollisionSouth"), "New tunnel models preserve swept collision and both anti-bypass mountain walls")
	tunnel.free()
	world.free()
	var gallery := (load("res://scenes/world/expedition_art_gallery.tscn") as PackedScene).instantiate()
	check(gallery.find_children("*", "Camera3D", true, false).size() == 1, "Saved art gallery can be opened and run directly in Godot")
	gallery.free()
	for label: String in ["editor_terrain", "bridge", "rail", "tunnel", "alpine"]:
		var preview := (load("res://scenes/world/generated/" + label + ".scn") as PackedScene).instantiate()
		var textured_meshes := 0
		for node: MeshInstance3D in preview.find_children("*", "MeshInstance3D", true, false):
			var material: Material = node.material_override
			if material is ShaderMaterial and material.shader.resource_path == Materials.SHADER and material.get_shader_parameter("texture_enabled") == true:
				var image_texture: Variant = material.get_shader_parameter("model_atlas")
				if image_texture is Texture2DArray and image_texture.get_layers() == 8: textured_meshes += 1
		check(textured_meshes > 0, "Saved %s editor preview also binds the new image textures" % label)
		preview.free()
	Assets._scenes.clear()
	for frame in 3: await process_frame
	print("EXPEDITION ART: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
