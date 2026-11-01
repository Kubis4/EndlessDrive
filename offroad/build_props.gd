extends SceneTree
## Builds the Lesná vrchovina props and ground textures in two stages, with a
## Godot import in between (the launcher's Bake action runs all three):
##   -- --stage=textures  model textures resized from the source packs, and six
##                        tileable ground textures generated from noise
##   -- --stage=meshes    one normalised mesh per props.gd entry, with materials
##                        on the imported textures
const Props = preload("res://offroad/props.gd")
const GROUND_SIZE := 1024
## Source materials already converted (instance id -> material) and textures
## already written (source path -> saved path).
var materials := {}
var written := {}
## Tree Animate uses the specular-glossiness workflow, which Godot converts into
## a wrong (orange) colour texture: use the pack's own colour images instead.
## Material name -> [colour image, normal image or ""].
const TREE_ANIMATE_TEXTURES := {"Bark": ["tree_animate_0", "tree_animate_2"], "Leaf": ["tree_animate_3", ""], "Branch": ["tree_animate_7", ""]}
var current_pack := ""

func _initialize() -> void: call_deferred("run")

func run() -> void:
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(Props.TEXTURES))
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(Props.GROUND))
	var stage := "textures"
	for argument in OS.get_cmdline_user_args():
		if argument.begins_with("--stage="): stage = argument.trim_prefix("--stage=")
	var start := Time.get_ticks_msec()
	if stage == "ground":
		# Only the ground textures (no source packs needed); --layers=a,b limits them.
		_ground_textures()
		print("PROPS ground textures %d ms" % (Time.get_ticks_msec() - start))
	elif stage == "textures":
		_model_textures()
		print("PROPS model textures %d ms (%d files)" % [Time.get_ticks_msec() - start, written.size()])
		_ground_textures()
		print("PROPS ground textures %d ms" % (Time.get_ticks_msec() - start))
	else:
		_meshes()
		print("PROPS meshes %d ms" % (Time.get_ticks_msec() - start))
	quit()

func _pack(name: String) -> Node3D:
	var scene: Node3D = (load(Props.SOURCE + name + ".glb") as PackedScene).instantiate()
	root.add_child(scene)
	return scene

## Mesh instances that make up one model: the node's direct mesh children
## (each rock and kit plant is a sibling of the others), or the whole subtree
## for the single-model Tree Animate pack.
func _parts(node: Node) -> Array[MeshInstance3D]:
	var parts: Array[MeshInstance3D] = []
	var deep := node.name == "GLTF_SceneRootNode"
	for child in (node.find_children("*", "MeshInstance3D", true, false) if deep else node.get_children()):
		if child is MeshInstance3D and child.mesh != null: parts.append(child)
	if parts.is_empty() and node is MeshInstance3D: parts.append(node)
	return parts

func _each_model(callback: Callable) -> void:
	var by_pack := {}
	for name: String in Props.MODELS:
		var entry: Array = Props.MODELS[name]
		if not by_pack.has(entry[0]): by_pack[entry[0]] = []
		by_pack[entry[0]].append(name)
	for pack: String in by_pack:
		var scene := _pack(pack)
		current_pack = pack
		for name: String in by_pack[pack]:
			var entry: Array = Props.MODELS[name]
			var node := scene.find_child(entry[1], true, false)
			if node == null:
				push_error("Model %s: node %s not found in %s" % [name, entry[1], pack])
				continue
			callback.call(name, entry, _parts(node))
		scene.free()

# --- stage 1: textures -------------------------------------------------------

func _model_textures() -> void:
	# Start clean, so textures no model uses any more do not linger.
	var folder := DirAccess.open(Props.TEXTURES)
	for file in folder.get_files(): folder.remove(file)
	_each_model(func(_name: String, entry: Array, parts: Array[MeshInstance3D]):
		for part in parts:
			for s in part.mesh.get_surface_count():
				var material := part.get_active_material(s) as BaseMaterial3D
				if material == null: continue
				if current_pack == "tree_animate" and TREE_ANIMATE_TEXTURES.has(material.resource_name):
					for image: String in TREE_ANIMATE_TEXTURES[material.resource_name]:
						if not image.is_empty(): _save_texture(load(Props.SOURCE + image + ".png"), 1024, image.ends_with("_2"))
					continue
				_save_texture(material.albedo_texture, entry[4], false)
				if material.normal_enabled: _save_texture(material.normal_texture, entry[4], true)
				if material.roughness_texture: _save_texture(material.roughness_texture, entry[4], false)
		_save_atlas(_foliage_textures(parts)))

## Foliage textures of a model (alpha-cut surfaces), sorted, without repeats.
func _foliage_textures(parts: Array[MeshInstance3D]) -> Array[String]:
	var paths: Array[String] = []
	for part in parts:
		for s in part.mesh.get_surface_count():
			var material := part.get_active_material(s) as BaseMaterial3D
			if material == null or material.transparency == BaseMaterial3D.TRANSPARENCY_DISABLED or material.albedo_texture == null: continue
			var path := material.albedo_texture.resource_path
			if not path.is_empty() and path not in paths: paths.append(path)
	paths.sort()
	return paths

## Atlas file of a set of foliage textures (shared by models that use the same set).
static func atlas_path(paths: Array[String]) -> String:
	return Props.TEXTURES + "atlas_%08x.png" % (hash(",".join(paths)) & 0xffffffff)

## Grid layout of an atlas: cell side in pixels, columns, rows.
static func atlas_grid(count: int) -> Vector3i:
	var columns := ceili(sqrt(float(count)))
	return Vector3i(ATLAS_CELL, columns, ceili(float(count) / columns))

const ATLAS_CELL := 512

## Packs several foliage textures into one, so a tree draws all its leaves in
## one call instead of one per leaf texture.
func _save_atlas(paths: Array[String]) -> void:
	if paths.size() < 2: return
	var path := atlas_path(paths)
	if written.has(path): return
	var grid := atlas_grid(paths.size())
	var atlas := Image.create(grid.x * grid.y, grid.x * grid.z, false, Image.FORMAT_RGBA8)
	for i in paths.size():
		var image := (load(paths[i]) as Texture2D).get_image()
		if image.is_compressed(): image.decompress()
		image.clear_mipmaps()
		image.convert(Image.FORMAT_RGBA8)
		image.resize(grid.x, grid.x, Image.INTERPOLATE_LANCZOS)
		atlas.blit_rect(image, Rect2i(0, 0, grid.x, grid.x), Vector2i(i % grid.y, i / grid.y) * grid.x)
	atlas.save_png(ProjectSettings.globalize_path(path))
	_import_settings(path, false)
	written[path] = path

func _save_texture(texture: Texture2D, largest: int, normal: bool) -> void:
	if texture == null or texture.resource_path.is_empty() or written.has(texture.resource_path): return
	var image := texture.get_image()
	if image.is_compressed(): image.decompress()
	image.clear_mipmaps()
	var side := maxi(image.get_width(), image.get_height())
	if side > largest:
		image.resize(maxi(1, image.get_width() * largest / side), maxi(1, image.get_height() * largest / side), Image.INTERPOLATE_LANCZOS)
	var path := Props.TEXTURES + texture.resource_path.get_file().get_basename() + ".png"
	image.save_png(ProjectSettings.globalize_path(path))
	_import_settings(path, normal)
	written[texture.resource_path] = path

## A headless import cannot detect 3D or normal-map use, so set it up front:
## GPU-compressed with mipmaps (S3TC on desktop, ETC2/ASTC on phones).
func _import_settings(path: String, normal: bool) -> void:
	var file := FileAccess.open(ProjectSettings.globalize_path(path + ".import"), FileAccess.WRITE)
	file.store_string("[remap]\n\nimporter=\"texture\"\ntype=\"CompressedTexture2D\"\n\n[params]\n\ncompress/mode=2\ncompress/normal_map=%d\nmipmaps/generate=true\n" % (1 if normal else 2))
	file.close()

static func step(edge: float, x: float) -> float:
	return 1.0 if x >= edge else 0.0

static func transformed(basis: Basis, box: AABB) -> AABB:
	var result := AABB(basis * box.position, Vector3.ZERO)
	for i in 8: result = result.expand(basis * box.get_endpoint(i))
	return result

## Seamless 0..1 noise sampled as a flat array.
func _noise(type: int, frequency: float, octaves: int, seed: int, cellular_return: int = -1) -> PackedFloat32Array:
	var noise := FastNoiseLite.new()
	noise.noise_type = type
	noise.frequency = frequency
	noise.fractal_octaves = octaves
	noise.fractal_type = FastNoiseLite.FRACTAL_FBM if octaves > 1 else FastNoiseLite.FRACTAL_NONE
	noise.seed = seed
	if cellular_return >= 0:
		noise.cellular_return_type = cellular_return
		noise.cellular_jitter = 0.9
	var image := noise.get_seamless_image(GROUND_SIZE, GROUND_SIZE, false, false, 0.1, true)
	image.convert(Image.FORMAT_RF)
	return image.get_data().to_float32_array()

## Tileable ground textures: albedo (roughness in alpha) and a normal map
## derived from a matching height field, one pair per ground layer.
func _ground_textures() -> void:
	var n := GROUND_SIZE * GROUND_SIZE
	var broad := _noise(FastNoiseLite.TYPE_SIMPLEX_SMOOTH, 0.004, 4, 11)
	var fine := _noise(FastNoiseLite.TYPE_VALUE, 0.09, 2, 12)
	var blades := _noise(FastNoiseLite.TYPE_SIMPLEX, 0.06, 3, 13)
	var patches := _noise(FastNoiseLite.TYPE_SIMPLEX_SMOOTH, 0.007, 3, 14)
	var cell_value := _noise(FastNoiseLite.TYPE_CELLULAR, 0.02, 1, 15, FastNoiseLite.RETURN_CELL_VALUE)
	var cell_edge := _noise(FastNoiseLite.TYPE_CELLULAR, 0.02, 1, 15, FastNoiseLite.RETURN_DISTANCE2_SUB)
	var cell_dist := _noise(FastNoiseLite.TYPE_CELLULAR, 0.02, 1, 15, FastNoiseLite.RETURN_DISTANCE)
	var pebble_edge := _noise(FastNoiseLite.TYPE_CELLULAR, 0.06, 1, 16, FastNoiseLite.RETURN_DISTANCE2_SUB)
	var pebble_value := _noise(FastNoiseLite.TYPE_CELLULAR, 0.06, 1, 16, FastNoiseLite.RETURN_CELL_VALUE)
	var crack := _noise(FastNoiseLite.TYPE_CELLULAR, 0.008, 2, 17, FastNoiseLite.RETURN_DISTANCE2_SUB)
	var only := PackedStringArray()
	for argument in OS.get_cmdline_user_args():
		if argument.begins_with("--layers="): only = argument.trim_prefix("--layers=").split(",")
	for layer: String in Props.GROUND_LAYERS:
		if not only.is_empty() and layer not in only: continue
		var albedo := Image.create(GROUND_SIZE, GROUND_SIZE, false, Image.FORMAT_RGBA8)
		var height := PackedFloat32Array()
		height.resize(n)
		for i in n:
			var x := i % GROUND_SIZE
			var y := i / GROUND_SIZE
			var b := broad[i]
			var f := fine[i]
			var c := Color()
			var h := 0.0
			var rough := 0.9
			match layer:
				"grass":
					var blade := clampf(blades[i] * 1.4 - 0.2, 0.0, 1.0)
					c = Color(0.12, 0.19, 0.06).lerp(Color(0.3, 0.4, 0.12), b)
					c = c * (0.7 + 0.55 * blade)
					c = c.lerp(Color(0.44, 0.41, 0.22), smoothstep(0.6, 0.75, patches[i]) * 0.6)
					c = c.lerp(Color(0.18, 0.13, 0.08), smoothstep(0.82, 0.9, f) * 0.6)
					h = blade * 0.7 + b * 0.3
				"forest":
					var needles := clampf(blades[i] * 1.6 - 0.3, 0.0, 1.0)
					c = Color(0.17, 0.12, 0.07).lerp(Color(0.32, 0.23, 0.13), needles)
					c = c.lerp(Color(0.2, 0.25, 0.1), smoothstep(0.64, 0.76, patches[i]) * 0.55)
					var twig := 1.0 - smoothstep(0.0, 0.05, pebble_edge[i])
					c = c.lerp(Color(0.38, 0.29, 0.18), twig * 0.5)
					h = needles * 0.5 + twig * 0.5
				"dirt":
					c = Color(0.31, 0.23, 0.15).lerp(Color(0.43, 0.33, 0.22), b * 0.7 + f * 0.3)
					# Embedded pebbles: a fifth of the small cells, with a dark rim.
					var stone := step(0.88, pebble_value[i]) * smoothstep(0.04, 0.14, pebble_edge[i])
					c = c.lerp(Color(0.42, 0.37, 0.31) * (0.85 + 0.3 * f), stone * 0.8)
					c = c.lerp(Color(0.2, 0.15, 0.1), (1.0 - smoothstep(0.0, 0.03, crack[i])) * 0.6)
					h = b * 0.4 + stone * 0.6 + f * 0.1
					rough = 0.88
				"mud":
					c = Color(0.12, 0.09, 0.06).lerp(Color(0.24, 0.17, 0.1), b)
					var wet := smoothstep(0.5, 0.62, patches[i])
					c = c.lerp(c * 0.7, wet)
					h = b * 0.6 + f * 0.15 - wet * 0.3
					# Damp, not glossy: wet mud must not read as water on the banks.
					rough = lerpf(0.78, 0.5, wet)
				"bank":
					# River bank: dry, cracked clay (the shader adds the mud near the water).
					var clay := Color(0.4, 0.29, 0.19).lerp(Color(0.5, 0.37, 0.25), b * 0.6 + f * 0.4)
					var cracks := 1.0 - smoothstep(0.0, 0.035, crack[i])
					clay = clay.lerp(Color(0.22, 0.15, 0.1), cracks * 0.75)
					var mud := Color(0.13, 0.1, 0.07).lerp(Color(0.21, 0.15, 0.1), f)
					# Mostly dry clay; the shader blends it into mud towards the water,
					# so only faint damp spots here.
					var wet := smoothstep(0.55, 0.75, patches[i]) * 0.35
					c = clay.lerp(mud, wet)
					h = lerpf(0.55 - cracks * 0.4 + f * 0.1, b * 0.3, wet)
					rough = lerpf(0.9, 0.55, wet)
				"trail_mud":
					# Churned mud on the tracks: lighter clods, glossy standing water.
					var clod := 1.0 - smoothstep(0.0, 0.1, pebble_edge[i])
					c = Color(0.25, 0.19, 0.13).lerp(Color(0.36, 0.27, 0.18), b * 0.5 + pebble_value[i] * 0.5)
					c = c.lerp(Color(0.18, 0.13, 0.09), clod * 0.6)
					# A few large pools rather than many small ones.
					var puddle := smoothstep(0.66, 0.74, b * 0.8 + patches[i] * 0.2)
					c = c.lerp(Color(0.12, 0.1, 0.08), puddle * 0.8)
					h = (1.0 - clod) * 0.4 + b * 0.3 - puddle * 0.5
					rough = lerpf(0.6, 0.04, puddle)
				"gravel":
					var tone := pebble_value[i]
					var gap := 1.0 - smoothstep(0.0, 0.12, pebble_edge[i])
					c = Color(0.29, 0.28, 0.26).lerp(Color(0.5, 0.48, 0.44), tone)
					c = c.lerp(Color(0.46, 0.38, 0.3), step(0.85, tone) * 0.6)
					c = c * (0.9 + 0.2 * f)
					c = c.lerp(Color(0.12, 0.11, 0.1), gap * 0.8)
					h = (1.0 - gap) * (0.6 + 0.4 * tone)
					rough = 0.85
				"rock":
					# Bedrock, not paving: wavering strata (running along x, so level
					# on cliffs), thin bedding between them, long fractures across
					# them, rough fine grain; lichen and rust stains in patches.
					var v := float(y) / GROUND_SIZE * TAU
					var warp := (b - 0.5) * 7.0 + (patches[i] - 0.5) * 4.0
					var strata := 0.5 + 0.5 * sin(v * 5.0 + warp)
					var bedding := 0.5 + 0.5 * sin(v * 19.0 + warp * 1.6 + (blades[i] - 0.5) * 1.5)
					bedding = bedding * bedding
					var fracture := 1.0 - smoothstep(0.0, 0.05, crack[i])
					var split := 1.0 - smoothstep(0.0, 0.035, pebble_edge[i])
					split *= smoothstep(0.35, 0.7, patches[i])
					h = strata * 0.34 + bedding * 0.18 + b * 0.26 + blades[i] * 0.14 + f * 0.08
					h = maxf(0.0, h - fracture * 0.55 - split * 0.18)
					c = Color(0.23, 0.22, 0.21).lerp(Color(0.5, 0.47, 0.42), clampf(h * 1.25, 0.0, 1.0))
					c = c.lerp(Color(0.44, 0.33, 0.22), smoothstep(0.55, 0.8, broad[(i + n / 3) % n]) * 0.4)
					c = c * (0.86 + 0.28 * f)
					c = c.lerp(Color(0.09, 0.09, 0.085), maxf(fracture * 0.9, split * 0.55))
					c = c.lerp(Color(0.42, 0.45, 0.27), smoothstep(0.68, 0.8, patches[i]) * smoothstep(0.45, 0.7, h) * 0.5)
					rough = 0.86
				"snow":
					# Settled snow: near-uniform white (no large blotches, which read as
					# clouds), shallow wind ripples, fine crystal grain and sparkles.
					var u := float(x) / GROUND_SIZE * TAU
					var v := float(y) / GROUND_SIZE * TAU
					# Ripples across the wind, wavering with the broad noise (tileable).
					var ripple := 0.5 + 0.5 * sin(u * 9.0 + v * 4.0 + (b - 0.5) * 6.0 + sin(v * 3.0) * 1.5)
					ripple = pow(ripple, 3.0)
					var grain := blades[i]
					var sparkle := step(0.965, fine[i]) * step(0.6, grain)
					c = Color(0.9, 0.92, 0.95).lerp(Color(0.97, 0.975, 0.985), 0.5 + (grain - 0.5) * 0.5)
					# Faint blue in the ripple troughs only.
					c = c.lerp(Color(0.84, 0.88, 0.95), (1.0 - ripple) * 0.25 * (0.6 + 0.4 * b))
					c = c.lerp(Color(1.0, 1.0, 1.0), sparkle)
					h = ripple * 0.35 + grain * 0.25 + b * 0.1
					rough = lerpf(0.82, 0.35, sparkle)
				"ice":
					# Glazed ice: dark blue-grey, white frost streaks and fine cracks.
					var frost := smoothstep(0.55, 0.8, patches[i] * 0.6 + f * 0.4)
					var cracks := 1.0 - smoothstep(0.0, 0.02, crack[i])
					c = Color(0.36, 0.44, 0.5).lerp(Color(0.52, 0.6, 0.66), b)
					c = c.lerp(Color(0.86, 0.9, 0.94), frost * 0.7)
					c = c.lerp(Color(0.9, 0.94, 0.97), cracks * 0.5)
					h = b * 0.15 + frost * 0.2 - cracks * 0.2
					rough = lerpf(0.06, 0.5, frost)
				"sand":
					# Desert sand: warm, wind ripples (tileable, wavering), fine grain,
					# a few darker pebbles and paler wind-blown streaks.
					var u := float(x) / GROUND_SIZE * TAU
					var v := float(y) / GROUND_SIZE * TAU
					var ripple := 0.5 + 0.5 * sin(u * 14.0 + v * 5.0 + (b - 0.5) * 8.0 + sin(v * 2.0) * 1.2)
					ripple = pow(ripple, 2.0)
					var grain := blades[i]
					var pebble := step(0.978, pebble_value[i]) * smoothstep(0.05, 0.15, pebble_edge[i])
					c = Color(0.62, 0.48, 0.31).lerp(Color(0.74, 0.6, 0.41), b * 0.6 + grain * 0.4)
					c = c.lerp(c * 0.82, (1.0 - ripple) * 0.45)
					c = c.lerp(Color(0.8, 0.69, 0.52), smoothstep(0.62, 0.78, patches[i]) * 0.35)
					c = c.lerp(Color(0.36, 0.29, 0.21), pebble * 0.7)
					h = ripple * 0.45 + grain * 0.2 + b * 0.15 + pebble * 0.3
					rough = 0.92
				"ploughed":
					# Field soil: straight furrows (six to a tile), clods, straw.
					var v := float(y) / GROUND_SIZE * TAU
					var furrow := 0.5 + 0.5 * sin(v * 6.0 + (b - 0.5) * 0.8)
					var straw := step(0.93, blades[i]) * smoothstep(0.4, 0.7, patches[i])
					c = Color(0.17, 0.12, 0.08).lerp(Color(0.34, 0.25, 0.17), furrow * 0.7 + f * 0.3)
					c = c.lerp(Color(0.6, 0.52, 0.32), straw * 0.7)
					h = furrow * 0.65 + f * 0.25 + b * 0.1
					rough = 0.95
			c.a = rough
			albedo.set_pixel(x, y, c)
			height[i] = h
		albedo.save_png(ProjectSettings.globalize_path(Props.GROUND + layer + "_albedo.png"))
		_import_settings(Props.GROUND + layer + "_albedo.png", false)
		_save_normal(height, Props.GROUND + layer + "_normal.png", 3.0 if layer in ["gravel", "rock", "dirt"] else 2.0)
		_save_height(height, Props.GROUND + layer + "_height.png")
		print("PROPS ground %s" % layer)

## Height field stretched to 0..1, for the ground shader's parallax relief.
func _save_height(height: PackedFloat32Array, path: String) -> void:
	var low := INF
	var high := -INF
	for v in height:
		low = minf(low, v)
		high = maxf(high, v)
	var bytes := PackedByteArray()
	bytes.resize(height.size())
	for i in height.size(): bytes[i] = clampi(roundi((height[i] - low) / maxf(high - low, 0.0001) * 255.0), 0, 255)
	Image.create_from_data(GROUND_SIZE, GROUND_SIZE, false, Image.FORMAT_L8, bytes).save_png(ProjectSettings.globalize_path(path))
	_import_settings(path, false)

func _save_normal(height: PackedFloat32Array, path: String, strength: float) -> void:
	var size := GROUND_SIZE
	var image := Image.create(size, size, false, Image.FORMAT_RGB8)
	for y in size:
		for x in size:
			var dx := height[y * size + (x + 1) % size] - height[y * size + (x + size - 1) % size]
			var dy := height[((y + 1) % size) * size + x] - height[((y + size - 1) % size) * size + x]
			var normal := Vector3(-dx * strength, -dy * strength, 1.0).normalized()
			image.set_pixel(x, y, Color(normal.x * 0.5 + 0.5, normal.y * 0.5 + 0.5, normal.z * 0.5 + 0.5))
	image.save_png(ProjectSettings.globalize_path(path))
	_import_settings(path, true)

# --- stage 2: meshes ---------------------------------------------------------

func _meshes() -> void:
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(Props.DIR))
	_each_model(func(name: String, entry: Array, parts: Array[MeshInstance3D]):
		_build_model(name, entry, parts))

func _build_model(name: String, entry: Array, parts: Array[MeshInstance3D]) -> void:
	if parts.is_empty(): return
	# Merge the parts in pack space (the packs' own root transforms make them upright).
	var surfaces := []
	var bounds := AABB()
	var first := true
	for part in parts:
		var xf := part.global_transform
		for s in part.mesh.get_surface_count():
			var arrays := part.mesh.surface_get_arrays(s)
			var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			for i in vertices.size():
				vertices[i] = xf * vertices[i]
				if first: bounds = AABB(vertices[i], Vector3.ZERO); first = false
				else: bounds = bounds.expand(vertices[i])
			arrays[Mesh.ARRAY_VERTEX] = vertices
			if arrays[Mesh.ARRAY_NORMAL] != null:
				var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
				for i in normals.size(): normals[i] = (xf.basis * normals[i]).normalized()
				arrays[Mesh.ARRAY_NORMAL] = normals
			arrays[Mesh.ARRAY_TANGENT] = null
			# Static props: no skinning (Tree Animate is rigged for wind) or custom data.
			for slot in [Mesh.ARRAY_BONES, Mesh.ARRAY_WEIGHTS, Mesh.ARRAY_CUSTOM0, Mesh.ARRAY_CUSTOM1, Mesh.ARRAY_CUSTOM2, Mesh.ARRAY_CUSTOM3]: arrays[slot] = null
			surfaces.append([arrays, part.get_active_material(s)])
	# Normalise: upright (Tree Animate lies along X), real height, base at the origin.
	var fix := Basis.IDENTITY
	if entry[1] == "GLTF_SceneRootNode" and bounds.size.x > bounds.size.y * 1.6:
		fix = _upright(surfaces, bounds)
	var turned := transformed(fix, bounds)
	var target: float = entry[2]
	var scale := target / turned.size.y if target > 0.0 else 1.0
	var origin := Vector3(turned.get_center().x, turned.position.y, turned.get_center().z)
	var transform := Transform3D(fix.scaled(Vector3.ONE * scale), -origin * scale)
	for surface: Array in surfaces:
		var arrays: Array = surface[0]
		var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
		for i in vertices.size(): vertices[i] = transform * vertices[i]
		arrays[Mesh.ARRAY_VERTEX] = vertices
		if arrays[Mesh.ARRAY_NORMAL] != null:
			var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
			for i in normals.size(): normals[i] = (fix * normals[i]).normalized()
			arrays[Mesh.ARRAY_NORMAL] = normals
		surface.append(_material(surface[1], entry[4]))
	surfaces = _merge_foliage(surfaces)
	var importer := ImporterMesh.new()
	for surface: Array in surfaces:
		var arrays: Array = surface[0]
		var material: StandardMaterial3D = surface[2]
		if material.normal_enabled and arrays[Mesh.ARRAY_TEX_UV] != null:
			arrays = _with_tangents(arrays)
		importer.add_surface(Mesh.PRIMITIVE_TRIANGLES, arrays, [], {}, material)
	var triangles := 0
	for surface: Array in surfaces: triangles += (surface[0][Mesh.ARRAY_INDEX] as PackedInt32Array).size() / 3 if surface[0][Mesh.ARRAY_INDEX] != null else 0
	# Heavy trees get automatic levels of detail for distant batches.
	if triangles > 4000: importer.generate_lods(25.0, 60.0, [])
	var mesh := importer.get_mesh()
	ResourceSaver.save(mesh, Props.mesh_path(name), ResourceSaver.FLAG_COMPRESS)
	print("PROPS %s tris=%d size=%s surfaces=%d" % [name, triangles, (mesh.get_aabb().size).snapped(Vector3.ONE * 0.01), mesh.get_surface_count()])

## Merges a model's foliage surfaces into one on the atlas built in stage 1
## (when every foliage UV lies within its texture, so it can be remapped into
## the atlas cell). Surfaces are [arrays, source material, project material].
func _merge_foliage(surfaces: Array) -> Array:
	var paths: Array[String] = []
	var foliage := []
	for surface: Array in surfaces:
		var source := surface[1] as BaseMaterial3D
		if source == null or source.transparency == BaseMaterial3D.TRANSPARENCY_DISABLED or source.albedo_texture == null: continue
		if surface[0][Mesh.ARRAY_TEX_UV] == null: return surfaces
		for uv: Vector2 in surface[0][Mesh.ARRAY_TEX_UV]:
			if uv.x < -0.01 or uv.y < -0.01 or uv.x > 1.01 or uv.y > 1.01: return surfaces
		foliage.append(surface)
		if source.albedo_texture.resource_path not in paths: paths.append(source.albedo_texture.resource_path)
	paths.sort()
	if paths.size() < 2 or not ResourceLoader.exists(atlas_path(paths)): return surfaces
	var grid := atlas_grid(paths.size())
	var size := Vector2(grid.x * grid.y, grid.x * grid.z)
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	var uvs := PackedVector2Array()
	var indices := PackedInt32Array()
	for surface: Array in foliage:
		var arrays: Array = surface[0]
		var cell := paths.find((surface[1] as BaseMaterial3D).albedo_texture.resource_path)
		var offset := Vector2(cell % grid.y, cell / grid.y) * grid.x
		var base := vertices.size()
		vertices.append_array(arrays[Mesh.ARRAY_VERTEX])
		normals.append_array(arrays[Mesh.ARRAY_NORMAL] if arrays[Mesh.ARRAY_NORMAL] != null else PackedVector3Array())
		# One-pixel inset so filtering never reaches the neighbouring cell.
		for uv: Vector2 in arrays[Mesh.ARRAY_TEX_UV]:
			uvs.append((offset + Vector2(1, 1) + uv.clamp(Vector2.ZERO, Vector2.ONE) * (grid.x - 2)) / size)
		if arrays[Mesh.ARRAY_INDEX] != null:
			for index in arrays[Mesh.ARRAY_INDEX]: indices.append(base + index)
		else:
			for i in (arrays[Mesh.ARRAY_VERTEX] as PackedVector3Array).size(): indices.append(base + i)
	var merged := []
	merged.resize(Mesh.ARRAY_MAX)
	merged[Mesh.ARRAY_VERTEX] = vertices
	if normals.size() == vertices.size(): merged[Mesh.ARRAY_NORMAL] = normals
	merged[Mesh.ARRAY_TEX_UV] = uvs
	merged[Mesh.ARRAY_INDEX] = indices
	var material := (foliage[0][2] as StandardMaterial3D).duplicate() as StandardMaterial3D
	material.albedo_texture = load(atlas_path(paths))
	var result := surfaces.filter(func(s: Array) -> bool: return not foliage.has(s))
	result.append([merged, foliage[0][1], material])
	return result

## Rotates a lying model upright: the crown (leaf surfaces) goes up.
func _upright(surfaces: Array, bounds: AABB) -> Basis:
	var crown := 0.0
	var count := 0
	for surface: Array in surfaces:
		var material = surface[1]
		if material is BaseMaterial3D and material.transparency != BaseMaterial3D.TRANSPARENCY_DISABLED:
			for v: Vector3 in surface[0][Mesh.ARRAY_VERTEX]:
				crown += v.x
				count += 1
	var crown_high := count > 0 and crown / count > bounds.get_center().x
	# +X up when the crown lies towards +X, otherwise -X up.
	return Basis(Vector3.BACK, PI * 0.5 if crown_high else -PI * 0.5)

func _with_tangents(arrays: Array) -> Array:
	var st := SurfaceTool.new()
	st.create_from_arrays(arrays)
	st.generate_tangents()
	return st.commit_to_arrays()

func _texture(source: Texture2D) -> Texture2D:
	if source == null or source.resource_path.is_empty(): return null
	var path := Props.TEXTURES + source.resource_path.get_file().get_basename() + ".png"
	return load(path) if ResourceLoader.exists(path) else null

## Project material for a pack material: imported (resized) textures; foliage
## cut out with alpha scissor instead of sorted blending (cheaper, and it casts
## leaf-shaped shadows), double-sided with a little light through the leaves.
func _material(source: Material, _largest: int) -> StandardMaterial3D:
	if source == null: return StandardMaterial3D.new()
	if materials.has(source.get_instance_id()): return materials[source.get_instance_id()]
	var from := source as BaseMaterial3D
	var material := StandardMaterial3D.new()
	material.resource_name = source.resource_name
	material.albedo_color = from.albedo_color
	material.albedo_texture = _texture(from.albedo_texture)
	var override: Array = TREE_ANIMATE_TEXTURES.get(source.resource_name, []) if current_pack == "tree_animate" else []
	if not override.is_empty():
		material.albedo_color = Color.WHITE
		material.albedo_texture = load(Props.TEXTURES + override[0] + ".png")
	material.roughness = maxf(from.roughness, 0.75)
	material.metallic = 0.0
	material.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	# Per-instance tint from the MultiMesh colour (white where none is set).
	material.vertex_color_use_as_albedo = true
	if not override.is_empty():
		if not override[1].is_empty():
			material.normal_enabled = true
			material.normal_texture = load(Props.TEXTURES + override[1] + ".png")
	elif from.normal_enabled and _texture(from.normal_texture) != null:
		material.normal_enabled = true
		material.normal_texture = _texture(from.normal_texture)
		material.normal_scale = from.normal_scale
	if from.roughness_texture and _texture(from.roughness_texture) != null:
		material.roughness_texture = _texture(from.roughness_texture)
		material.roughness_texture_channel = from.roughness_texture_channel
		material.roughness = 1.0
	if from.transparency != BaseMaterial3D.TRANSPARENCY_DISABLED:
		material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
		material.alpha_scissor_threshold = 0.42
		material.cull_mode = BaseMaterial3D.CULL_DISABLED
		material.backlight_enabled = true
		material.backlight = Color(0.22, 0.26, 0.1)
		material.roughness = 0.85
	elif from.cull_mode == BaseMaterial3D.CULL_DISABLED:
		material.cull_mode = BaseMaterial3D.CULL_DISABLED
	materials[source.get_instance_id()] = material
	return material
