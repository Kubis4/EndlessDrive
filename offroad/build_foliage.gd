extends SceneTree
## Bakes the foliage sources in assets/source/foliage (exported by
## export_foliage.py, plus wheat.glb) into the props' folder (props.gd MADE):
## grass tufts put together from the pack's sprigs, flowers, wheat, sunflowers.
##   --stage=models   (headless) textures and meshes
##   --stage=ribbons  (needs a window) the fields' far ribbons, rendered from those
##                    wheat and sunflower models, into assets/models/crops
const Props = preload("res://offroad/props.gd")
const SOURCE := "res://assets/source/foliage/"
const CROPS := "res://assets/models/crops/"
const SHADER := preload("res://offroad/foliage.gdshader")
## Texture: colour, alpha map ("" : the colour's own alpha).
const TEXTURES := {
	"wheatgrass": ["WheatGrass_Color", "WheatGrass_Alpha"], "buffalo": ["BuffaloGrass_Color", "BuffaloGrass_Alpha"],
	"longgrass": ["PrairieOnion_Color", "PrairieOnion_Alpha"], "phlox": ["Phlox_Color", "Phlox_Alpha"],
	"yarrow": ["Yarrow_Color", "Yarrow_Alpha"], "blanketflower": ["BlanketFlower_Color", "BlanketFlower_Alpha"],
	"beardtongue": ["BeardTongue_Color", "BeardTongue_Alpha"], "sunflower": ["Sunflowers_Base_Color", "Sunflowers_Opacity"],
}
var rng := RandomNumberGenerator.new()
var materials := {}
var plain := {}

func _initialize() -> void: call_deferred("run")

func run() -> void:
	rng.seed = 4711
	var stage := "models"
	for argument in OS.get_cmdline_user_args():
		if argument.begins_with("--stage="): stage = argument.trim_prefix("--stage=")
	if stage == "ribbons":
		await _ribbons()
	else:
		_models()
	print("FOLIAGE baked %s" % stage)
	quit()

# ---------------------------------------------------------------- models

func _models() -> void:
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(CROPS))
	_wheat_texture()
	_save("pack_wheatgrass", _tuft(["wheategrass_sprig_01", "wheategrass_sprig_02", "wheategrass_sprig_03"], 8, 0.2, 0.85, "wheatgrass"))
	_save("pack_buffalo", _tuft(["buffalograss_sprig_01", "buffalograss_sprig_02"], 12, 0.22, 1.7, "buffalo"))
	_save("pack_longgrass", _tuft(["longgrass_01", "longgrass_02", "longgrass_03"], 6, 0.16, 1.9, "longgrass"))
	_save("pack_phlox", _tuft(["phlox_sprig_01", "phlox_sprig_02"], 8, 0.18, 1.4, "phlox"))
	_save("pack_yarrow", _single("yarrow", "yarrow", 1.0))
	_save("pack_blanketflower", _single("blanketflower", "blanketflower", 1.0))
	_save("pack_beardtongue", _single("beard_tongue", "beardtongue", 1.0))
	_save("crop_wheat", _single("wheat", "wheat", 0.0, 1.0))
	for pair: Array in [["crop_sunflower_1", "sunflower_01"], ["crop_sunflower_2", "sunflower_02"], ["crop_sunflower_3", "sunflower_04"]]:
		_save(pair[0], _single(pair[1], "sunflower", 1.0))

func _save(name: String, mesh: ArrayMesh) -> void:
	ResourceSaver.save(mesh, Props.mesh_path(name), ResourceSaver.FLAG_COMPRESS)
	print("FOLIAGE %s %d triangles size=%s" % [name, mesh.get_faces().size() / 3, mesh.get_aabb().size.snapped(Vector3.ONE * 0.01)])

## The source's triangles, standing: its tallest axis up (the plant's heavy end
## at the top), base at the origin. A flat list of [position, normal, uv].
func _source(file: String) -> Array:
	var state := GLTFState.new()
	var document := GLTFDocument.new()
	var error := document.append_from_file(ProjectSettings.globalize_path(SOURCE + file + ".glb"), state)
	assert(error == OK, file)
	var scene: Node = document.generate_scene(state)
	root.add_child(scene)
	var positions := PackedVector3Array()
	var normals := PackedVector3Array()
	var uvs := PackedVector2Array()
	for node: MeshInstance3D in scene.find_children("*", "MeshInstance3D", true, false) + ([scene] if scene is MeshInstance3D else []):
		var xf: Transform3D = node.global_transform
		var mesh: Mesh = node.mesh
		for s in mesh.get_surface_count():
			var arrays := mesh.surface_get_arrays(s)
			var v: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			var n: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
			var t: PackedVector2Array = arrays[Mesh.ARRAY_TEX_UV]
			var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX] if arrays[Mesh.ARRAY_INDEX] != null else PackedInt32Array(range(v.size()))
			for i in indices:
				positions.append(xf * v[i])
				normals.append((xf.basis * n[i]).normalized())
				uvs.append(t[i])
	scene.queue_free()
	var box := AABB(positions[0], Vector3.ZERO)
	for p in positions: box = box.expand(p)
	# Tallest axis, and which end is the heavy one.
	var axis := 1
	if box.size.z > box.size.y and box.size.z > box.size.x: axis = 2
	elif box.size.x > box.size.y and box.size.x > box.size.z: axis = 0
	var turn := Basis.IDENTITY
	if axis != 1:
		var mid := box.get_center()[axis]
		var above := 0
		for p in positions:
			if p[axis] > mid: above += 1
		var up_sign := 1.0 if above > positions.size() / 2 else -1.0
		var from := Vector3.ZERO
		from[axis] = up_sign
		turn = Basis(Quaternion(from, Vector3.UP))
	var low := INF
	var centre := Vector3.ZERO
	for p in positions:
		var q := turn * p
		low = minf(low, q.y)
		centre += q
	centre /= positions.size()
	for i in positions.size():
		positions[i] = turn * positions[i] - Vector3(centre.x, low, centre.z)
		normals[i] = turn * normals[i]
	return [positions, normals, uvs]

## Into a surface of `tool`, placed by xf.
func _add(tool: SurfaceTool, source: Array, xf: Transform3D) -> void:
	var positions: PackedVector3Array = source[0]
	var normals: PackedVector3Array = source[1]
	var uvs: PackedVector2Array = source[2]
	for i in range(0, positions.size(), 3):
		for k: int in [i, i + 1, i + 2]:
			tool.set_normal((xf.basis * normals[k]).normalized())
			tool.set_uv(uvs[k])
			tool.add_vertex(xf * positions[k])

## One plant; `tall` above zero makes it that high, else `scale` applies.
func _single(file: String, texture: String, scale: float, tall: float = 0.0) -> ArrayMesh:
	var source := _source(file)
	if tall > 0.0:
		var high := 0.0
		for p: Vector3 in source[0]: high = maxf(high, p.y)
		scale = tall / high
	var tool := SurfaceTool.new()
	tool.begin(Mesh.PRIMITIVE_TRIANGLES)
	_add(tool, source, Transform3D().scaled(Vector3.ONE * scale))
	return _finish(tool, texture)

## Sprigs gathered into a tuft, fanning out a little.
func _tuft(files: Array, count: int, radius: float, scale: float, texture: String) -> ArrayMesh:
	var sources := []
	for file: String in files: sources.append(_source(file))
	var tool := SurfaceTool.new()
	tool.begin(Mesh.PRIMITIVE_TRIANGLES)
	for n in count:
		var angle := rng.randf_range(0.0, TAU)
		var out := Vector3(cos(angle), 0.0, sin(angle))
		var basis := Basis(Vector3.UP, rng.randf_range(0.0, TAU))
		basis = Basis(out.cross(Vector3.UP).normalized(), -rng.randf_range(0.0, 0.3)) * basis
		var at := out * sqrt(rng.randf()) * radius
		_add(tool, sources[n % sources.size()], Transform3D(basis.scaled(Vector3.ONE * scale * rng.randf_range(0.8, 1.15)), at))
	return _finish(tool, texture)

func _finish(tool: SurfaceTool, texture: String) -> ArrayMesh:
	var mesh := tool.commit()
	mesh.surface_set_material(0, _material(texture))
	return mesh

func _material(texture: String) -> ShaderMaterial:
	if materials.has(texture): return materials[texture]
	var path := CROPS + "plant_" + texture + ".res"
	if texture != "wheat":
		var names: Array = TEXTURES[texture]
		var colour := Image.load_from_file(ProjectSettings.globalize_path(SOURCE + "textures/" + names[0] + ".png"))
		var alpha := Image.load_from_file(ProjectSettings.globalize_path(SOURCE + "textures/" + names[1] + ".png"))
		_store(_with_alpha(colour, alpha), path)
	var material := ShaderMaterial.new()
	material.shader = SHADER
	material.set_shader_parameter("plants", load(path))
	materials[texture] = material
	return material

func _with_alpha(colour: Image, alpha: Image) -> Image:
	const SIDE := 512
	colour.convert(Image.FORMAT_RGBA8)
	colour.resize(SIDE, SIDE, Image.INTERPOLATE_LANCZOS)
	alpha.convert(Image.FORMAT_RGBA8)
	alpha.resize(SIDE, SIDE, Image.INTERPOLATE_LANCZOS)
	for y in SIDE:
		for x in SIDE:
			var c := colour.get_pixel(x, y)
			c.a = alpha.get_pixel(x, y).r
			colour.set_pixel(x, y, c)
	return colour

func _store(image: Image, path: String) -> void:
	image.fix_alpha_edges()
	image.generate_mipmaps()
	ResourceSaver.save(ImageTexture.create_from_image(image), path, ResourceSaver.FLAG_COMPRESS)

## wheat.glb's colour, with its alpha, from inside the file.
func _wheat_texture() -> void:
	var state := GLTFState.new()
	var error := GLTFDocument.new().append_from_file(ProjectSettings.globalize_path(SOURCE + "wheat.glb"), state)
	assert(error == OK)
	var image: Image = state.get_images()[0].get_image()
	if image.is_compressed(): image.decompress()
	image.clear_mipmaps()
	image.convert(Image.FORMAT_RGBA8)
	var side := mini(1024, image.get_width())
	image.resize(side, side, Image.INTERPOLATE_LANCZOS)
	print("FOLIAGE wheat texture %s alpha=%s" % [image.get_size(), image.detect_alpha()])
	_store(image, CROPS + "plant_wheat.res")

# ---------------------------------------------------------------- ribbons

## The far ribbons of a crop: a row of its plants rendered flat-on.
func _ribbons() -> void:
	await _ribbon(CROPS + "wheat.res", ["crop_wheat"], Vector2(2.0, 1.0), 16, 1024, 512)
	await _ribbon(CROPS + "sunflower.res", ["crop_sunflower_1", "crop_sunflower_2", "crop_sunflower_3"], Vector2(3.0, 2.15), 4, 1024, 734)

func _ribbon(path: String, models: Array, span: Vector2, plants: int, width: int, height: int) -> void:
	var viewport := SubViewport.new()
	viewport.size = Vector2i(width, height)
	viewport.transparent_bg = true
	viewport.own_world_3d = true
	viewport.world_3d = World3D.new()
	viewport.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	viewport.msaa_3d = Viewport.MSAA_4X
	root.add_child(viewport)
	var camera := Camera3D.new()
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.keep_aspect = Camera3D.KEEP_HEIGHT
	camera.size = span.y
	camera.position = Vector3(span.x * 0.5, span.y * 0.5, 10.0)
	viewport.add_child(camera)
	var seen := {}
	for n in plants:
		var mesh: Mesh = load(Props.mesh_path(models[n % models.size()]))
		var x := (n + rng.randf_range(0.15, 0.85)) * span.x / plants
		var turn := rng.randf_range(-0.5, 0.5) if models.size() > 1 else rng.randf_range(0.0, TAU)
		var size := rng.randf_range(0.82, 1.0)
		# Twice, a span apart, so the ribbon tiles.
		for shift: float in [0.0, -span.x, span.x]:
			var node := MeshInstance3D.new()
			node.mesh = mesh
			node.material_override = _flat_material(mesh.surface_get_material(0))
			node.transform = Transform3D(Basis(Vector3.UP, turn).scaled(Vector3.ONE * size), Vector3(x + shift, 0.0, 0.0))
			viewport.add_child(node)
		seen[mesh] = true
	for i in 12: await process_frame
	await RenderingServer.frame_post_draw
	var image := viewport.get_texture().get_image()
	image.convert(Image.FORMAT_RGBA8)
	_store(image, path)
	print("FOLIAGE ribbon %s alpha=%s" % [path, image.detect_alpha()])
	viewport.queue_free()

func _flat_material(source: Material) -> StandardMaterial3D:
	if plain.has(source): return plain[source]
	var material := StandardMaterial3D.new()
	material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	material.albedo_texture = (source as ShaderMaterial).get_shader_parameter("plants")
	material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
	material.alpha_scissor_threshold = 0.45
	material.cull_mode = BaseMaterial3D.CULL_DISABLED
	plain[source] = material
	return material
