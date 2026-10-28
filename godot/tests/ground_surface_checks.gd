extends SceneTree
## Seamless terrain appearance without changing the wheel/soil simulation.
var checks := 0
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])

func _run() -> void:
	var atlas := load("res://assets/textures/ground-surfaces.png") as TextureLayered
	check(atlas != null and atlas.get_layers() == 4 and atlas.get_width() <= 1024 and atlas.has_mipmaps(), "New ground layers have independent mipmaps and bounded texture memory")
	# Dummy/headless renderers cannot read back Texture2DArray pixels. Inspect
	# the lossless source tile data; the imported array metadata is checked above.
	var source_image := Image.new()
	if source_image.load_png_from_buffer(FileAccess.get_file_as_bytes("res://assets/textures/ground-surfaces.png")) != OK:
		check(false, "Ground source image is readable")
		quit(1)
		return
	var photographic := true
	var edge_error := 0.0
	var tile_size := source_image.get_width() / 2
	for layer in 4:
		var image := source_image.get_region(Rect2i((layer % 2) * tile_size, (layer / 2) * tile_size, tile_size, tile_size))
		var border := Vector4.ZERO
		var count := 0
		var low := 1.0
		var high := 0.0
		for pixel in range(0, tile_size, 3):
			for strip in 16:
				border += Vector4(image.get_pixel(strip, pixel).get_luminance(), image.get_pixel(tile_size - strip - 1, pixel).get_luminance(), image.get_pixel(pixel, strip).get_luminance(), image.get_pixel(pixel, tile_size - strip - 1).get_luminance())
				count += 1
			var luma := image.get_pixel(pixel, (pixel * 7) % tile_size).get_luminance()
			low = minf(low, luma)
			high = maxf(high, luma)
		border /= count
		edge_error = maxf(edge_error, maxf(absf(border.x - border.y), absf(border.z - border.w)))
		photographic = photographic and high - low > 0.15
	check(photographic and edge_error < 0.08, "Photographic materials retain fine structure without broad lighting seams at repeat boundaries")
	var profile := DriveTerrain.new()
	var physical_unchanged := true
	var centres_unchanged := true
	for biome in 6:
		profile.seed_value = 1000001 + biome
		profile.clear_caches()
		var normalized := true
		var maximum_step := 0.0
		var maximum_color_step := 0.0
		for x in range(25, 330, 7):
			for side: float in [-26, -18, -12, -7, 0, 7, 12, 18, 26]:
				var z := profile.center_at(x) + side
				var physical := profile.sample(x, z, false)
				var visual := profile.sample(x, z, false, true)
				var adjacent := profile.sample(x + 0.02, z, false, true)
				var sum := 0.0
				for layer in 14:
					var weight: float = visual.material_weights[layer]
					sum += weight
					normalized = normalized and weight >= 0 and weight <= 1.0001
					maximum_step = maxf(maximum_step, absf(weight - float(adjacent.material_weights[layer])))
				maximum_color_step = maxf(maximum_color_step, Vector3(visual.color.r - adjacent.color.r, visual.color.g - adjacent.color.g, visual.color.b - adjacent.color.b).length())
				normalized = normalized and absf(sum - 1.0) < 0.0001
				for key: String in ["material", "grip", "drag", "road", "paved", "deformable", "soil_softness", "soil_bearing", "soil_depth", "soil_moisture", "roughness", "snow", "rut_depth"]:
					physical_unchanged = physical_unchanged and physical[key] == visual[key]
				if side == 0: centres_unchanged = centres_unchanged and physical.material_weights == visual.material_weights
		check(normalized and maximum_step < 0.08 and maximum_color_step < 0.035, "Biome %d has continuous colours and normalized visual blends (max step %.4f)" % [biome, maximum_step])
	check(physical_unchanged and centres_unchanged, "Visual terrain mixing preserves tyre physics and all existing road-centre layers")
	var transitions := true
	for x: float in [779.99, 959.99, 1139.99, 1739.99, 1919.99]:
		var z := profile.center_at(x) + 15.0
		var a := profile.sample(x, z, false, true)
		var b := profile.sample(x + 0.02, z, false, true)
		for layer in 14: transitions = transitions and absf(a.material_weights[layer] - b.material_weights[layer]) < 0.08
	check(transitions, "Region boundaries retain continuous ground layers, including the midpoint of a biome transition")
	profile.seed_value = 1000003
	profile.clear_caches()
	var a := DriveTerrainBake.bake(profile, 0)
	var b := DriveTerrainBake.bake(profile, 1)
	var same_edges := true
	for col in int(a.width):
		for layer in 14:
			var source: PackedFloat32Array = a.arrays[Mesh.ARRAY_CUSTOM0 + layer / 4]
			var next: PackedFloat32Array = b.arrays[Mesh.ARRAY_CUSTOM0 + layer / 4]
			same_edges = same_edges and is_equal_approx(source[(DriveTerrainBake.ROWS * int(a.width) + col) * 4 + layer % 4], next[col * 4 + layer % 4])
	check(same_edges, "Adjacent baked terrain chunks share exactly the same blended material border")
	var all_previews := true
	for label: String in ["editor_terrain", "bridge", "alpine", "forest", "canyon", "swamp", "mountain", "rail", "tunnel", "deformation", "junction_gallery"]:
		var preview := (load("res://scenes/world/generated/" + label + ".scn") as PackedScene).instantiate()
		for ground: Node in preview.find_children("SandAndTracks", "MeshInstance3D", true, false):
			all_previews = all_previews and ground.material_override.get_shader_parameter("surface_atlas") == atlas
		preview.free()
	check(all_previews, "All saved terrain previews use the same quiet ground atlas as gameplay")
	print("GROUND SURFACES: %d checks, %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
