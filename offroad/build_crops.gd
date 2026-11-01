extends SceneTree
## Generates the crops' textures (long ribbons of plants, see crop.gdshader and
## endless_world.gd) and the plants the source packs lack: pines, cacti, dry
## shrubs and reeds, into the props' folder (props.gd MADE).
const Props = preload("res://offroad/props.gd")
const CROPS := "res://assets/models/crops/"
var rng := RandomNumberGenerator.new()

func _initialize() -> void: call_deferred("run")

func run() -> void:
	rng.seed = 90210
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(CROPS))
	_save_texture(_corn(), CROPS + "corn.res")
	_save_texture(_cereal(Color(0.72, 0.6, 0.3), Color(0.82, 0.68, 0.32), true), CROPS + "barley.res")
	_save_texture(_cereal(Color(0.7, 0.54, 0.24), Color(0.8, 0.6, 0.24), false), CROPS + "wheat.res")
	_save_texture(_rapeseed(), CROPS + "rapeseed.res")
	_save_texture(_sunflower(), CROPS + "sunflower.res")
	var needles := _needles()
	_save_texture(needles, CROPS + "needles.res")
	_save_texture(_twigs(), CROPS + "twigs.res")
	_save_texture(_reed_blades(), CROPS + "reeds.res")
	_save_texture(_weed_clump(), CROPS + "weeds.res")
	_save_mesh(_pine(9.5, 1.9, 8), "pine_1")
	_save_mesh(_pine(7.0, 2.4, 6), "pine_2")
	_save_mesh(_cactus(4.2, true), "cactus_1")
	_save_mesh(_cactus(2.8, false), "cactus_2")
	_save_mesh(_tuft("twigs", 1.5, 1.1, 5, Color(0.62, 0.55, 0.42)), "shrub_dry")
	_save_mesh(_tuft("reeds", 1.5, 2.2, 3, Color(0.75, 0.8, 0.5)), "reeds")
	_save_mesh(_tuft("weeds", 1.5, 1.05, 3, Color(1, 1, 1)), "weeds")
	print("CROPS done")
	quit()

func _save_texture(image: Image, path: String) -> void:
	# Keep the colour under transparent texels, so mipmaps do not darken the edges.
	image.fix_alpha_edges()
	image.generate_mipmaps()
	ResourceSaver.save(ImageTexture.create_from_image(image), path, ResourceSaver.FLAG_COMPRESS)
	print("CROPS ", path)

func _blank(w: int, h: int, fill: Color) -> Image:
	var image := Image.create(w, h, false, Image.FORMAT_RGBA8)
	fill.a = 0.0
	image.fill(fill)
	return image

## A stroke from a to b, tapering from width w0 to w1; x wraps (tileable).
func _stroke(image: Image, a: Vector2, b: Vector2, w0: float, w1: float, colour: Color) -> void:
	var steps := maxi(1, ceili(a.distance_to(b)))
	var size := image.get_size()
	for s in steps + 1:
		var t := float(s) / steps
		var p := a.lerp(b, t)
		var r := lerpf(w0, w1, t) * 0.5
		var shade := colour
		shade.a = 1.0
		for y in range(floori(p.y - r), ceili(p.y + r) + 1):
			if y < 0 or y >= size.y: continue
			for x in range(floori(p.x - r), ceili(p.x + r) + 1):
				if Vector2(x, y).distance_to(p) <= maxf(r, 0.6): image.set_pixel(posmod(x, size.x), y, shade)

## A curved stroke (a blade or leaf): through control point c.
func _curve(image: Image, a: Vector2, c: Vector2, b: Vector2, w0: float, w1: float, colour: Color) -> void:
	var last := a
	for s in range(1, 13):
		var t := s / 12.0
		var p := a.lerp(c, t).lerp(c.lerp(b, t), t)
		_stroke(image, last, p, lerpf(w0, w1, t - 1.0 / 12.0), lerpf(w0, w1, t), colour)
		last = p

## Corn: 2.4 m stalks with broad arching leaves, tassels and ears (3 m of row).
func _corn() -> Image:
	var image := _blank(512, 512, Color(0.2, 0.32, 0.1))
	for plant in 9:
		var x := (plant + rng.randf_range(0.2, 0.8)) * 512.0 / 9.0
		var top := rng.randf_range(30.0, 70.0)
		var green := Color(0.17, 0.32, 0.09).lerp(Color(0.32, 0.44, 0.14), rng.randf())
		_stroke(image, Vector2(x, 512), Vector2(x + rng.randf_range(-6, 6), top), 9.0, 4.0, green * 0.9)
		var leaves := 9
		for l in leaves:
			var y := lerpf(470.0, top + 40.0, float(l) / leaves)
			var side := 1.0 if l % 2 == 0 else -1.0
			var reach := rng.randf_range(55.0, 85.0) * side
			var colour := green.lerp(Color(0.5, 0.52, 0.2), rng.randf() * 0.35)
			_curve(image, Vector2(x, y), Vector2(x + reach * 0.5, y - rng.randf_range(50, 80)), Vector2(x + reach, y + rng.randf_range(15, 55)), 13.0, 2.0, colour)
		# Tassel.
		for t in 7:
			_curve(image, Vector2(x, top + 6), Vector2(x + (t - 3) * 3.0, top - 14), Vector2(x + (t - 3) * 7.0, top + 2), 2.4, 1.0, Color(0.72, 0.62, 0.34))
		# Ear in its husk.
		var ear := rng.randf_range(250.0, 330.0)
		_stroke(image, Vector2(x + 3, ear), Vector2(x + 14, ear - 44), 13.0, 5.0, Color(0.56, 0.58, 0.26))
	return image

## Barley (nodding heads with long awns) or wheat (upright ears): 1.6 m of field.
func _cereal(stem: Color, head: Color, awns: bool) -> Image:
	var image := _blank(512, 256, stem)
	for s in 110:
		var x := rng.randf() * 512.0
		var top := rng.randf_range(14.0, 90.0)
		var lean := rng.randf_range(-16.0, 16.0)
		var colour := stem.lerp(Color(0.5, 0.48, 0.2), rng.randf() * 0.4)
		var end := Vector2(x + lean, top)
		_curve(image, Vector2(x, 256), Vector2(x + lean * 0.2, (top + 256.0) * 0.5), end, 2.4, 1.6, colour)
		var nod := (1.0 if lean > 0.0 else -1.0) * (rng.randf_range(10.0, 18.0) if awns else rng.randf_range(0.0, 3.0))
		var tip := end + Vector2(nod, 22.0 if awns else -24.0)
		var body := head.lerp(Color(0.95, 0.82, 0.45), rng.randf() * 0.4)
		_stroke(image, end, tip, 5.0, 3.0, body)
		if awns:
			for a in 6:
				var from := end.lerp(tip, a / 5.0)
				_stroke(image, from, from + Vector2(nod * 0.7 + rng.randf_range(-5, 5), rng.randf_range(14, 28)), 1.0, 0.6, body * 1.05)
	return image

## Rapeseed: green stems below, a dense yellow froth of flowers above.
func _rapeseed() -> Image:
	var image := _blank(512, 256, Color(0.25, 0.36, 0.1))
	for s in 70:
		var x := rng.randf() * 512.0
		var top := rng.randf_range(16.0, 110.0)
		var lean := rng.randf_range(-22.0, 22.0)
		_curve(image, Vector2(x, 256), Vector2(x, (top + 256.0) * 0.5), Vector2(x + lean, top), 3.4, 1.6, Color(0.22, 0.36, 0.11).lerp(Color(0.36, 0.46, 0.14), rng.randf()))
		for leaf in 3:
			var y := rng.randf_range(top + 60.0, 240.0)
			_curve(image, Vector2(x, y), Vector2(x + rng.randf_range(-24, 24), y - 18.0), Vector2(x + rng.randf_range(-44, 44), y + 10.0), 8.0, 1.5, Color(0.2, 0.33, 0.13))
		for b in 16:
			var at := Vector2(x + lean + rng.randf_range(-20, 20), top + rng.randf_range(-12, 44))
			_stroke(image, at, at + Vector2(1, 1), rng.randf_range(4.0, 7.0), 3.0, Color(0.95, 0.82, 0.08).lerp(Color(1.0, 0.92, 0.25), rng.randf()))
	return image

## Sunflowers: stalks, big leaves and heads (3 m of row).
func _sunflower() -> Image:
	var image := _blank(512, 512, Color(0.22, 0.32, 0.11))
	for plant in 5:
		var x := (plant + rng.randf_range(0.25, 0.75)) * 512.0 / 5.0
		var top := rng.randf_range(50.0, 110.0)
		_stroke(image, Vector2(x, 512), Vector2(x, top), 9.0, 6.0, Color(0.27, 0.37, 0.13))
		for l in 8:
			var y := lerpf(460.0, top + 60.0, l / 8.0)
			var side := 1.0 if l % 2 == 0 else -1.0
			_curve(image, Vector2(x, y), Vector2(x + side * 34.0, y - 26.0), Vector2(x + side * 72.0, y + 16.0), 30.0, 3.0, Color(0.19, 0.33, 0.1).lerp(Color(0.28, 0.4, 0.13), rng.randf()))
		var head := Vector2(x + rng.randf_range(-5, 5), top)
		for p in 22:
			var angle := TAU * p / 22.0
			_stroke(image, head + Vector2.from_angle(angle) * 18.0, head + Vector2.from_angle(angle) * 37.0, 9.0, 3.0, Color(0.96, 0.75, 0.08).lerp(Color(1.0, 0.86, 0.2), rng.randf()))
		_stroke(image, head, head + Vector2(0.5, 0.0), 38.0, 38.0, Color(0.21, 0.13, 0.06))
	return image

## A pine bough skirt: dark needles down to a frayed edge (u round the tree, v
## from the trunk out).
func _needles() -> Image:
	var image := _blank(256, 256, Color(0.08, 0.17, 0.08))
	for s in 520:
		var x := rng.randf() * 256.0
		var from := Vector2(x, rng.randf_range(0.0, 200.0))
		var colour := Color(0.055, 0.13, 0.065).lerp(Color(0.14, 0.26, 0.11), rng.randf())
		var length := rng.randf_range(26.0, 64.0)
		_stroke(image, from, from + Vector2(rng.randf_range(-12, 12), length), 4.0, 1.0, colour)
	return image

## Dry twigs for desert shrubs.
func _twigs() -> Image:
	var image := _blank(256, 256, Color(0.45, 0.38, 0.28))
	for s in 40:
		var root_at := Vector2(128.0 + rng.randf_range(-22, 22), 256)
		var end := Vector2(rng.randf() * 256.0, rng.randf_range(10.0, 180.0))
		var colour := Color(0.36, 0.3, 0.22).lerp(Color(0.62, 0.55, 0.4), rng.randf())
		_curve(image, root_at, root_at.lerp(end, 0.5) + Vector2(rng.randf_range(-20, 20), 10.0), end, 3.2, 1.0, colour)
		for leaf in 5:
			var at := root_at.lerp(end, rng.randf_range(0.5, 1.0))
			_stroke(image, at, at + Vector2(rng.randf_range(-12, 12), rng.randf_range(-12, 6)), 2.0, 0.8, colour * 1.1)
	return image

## Reeds with cattail heads.
func _reed_blades() -> Image:
	var image := _blank(256, 512, Color(0.35, 0.42, 0.18))
	for s in 46:
		var x := rng.randf() * 256.0
		var top := rng.randf_range(20.0, 190.0)
		var lean := rng.randf_range(-38.0, 38.0)
		_curve(image, Vector2(x, 512), Vector2(x, (top + 512.0) * 0.5), Vector2(x + lean, top), 7.0, 1.0, Color(0.28, 0.38, 0.14).lerp(Color(0.55, 0.55, 0.26), rng.randf()))
		if s % 4 == 0:
			var head := Vector2(x + lean * 0.4, top + 30.0)
			_stroke(image, head, head + Vector2(0, 42), 10.0, 10.0, Color(0.27, 0.16, 0.09))
	return image

## Field weeds: thistles with purple heads, rusty dock spikes, grass gone to seed.
func _weed_clump() -> Image:
	var image := _blank(256, 256, Color(0.3, 0.36, 0.14))
	for s in 34:
		var x := rng.randf() * 256.0
		var top := rng.randf_range(50.0, 170.0)
		var lean := rng.randf_range(-26.0, 26.0)
		_curve(image, Vector2(x, 256), Vector2(x + lean * 0.3, (top + 256.0) * 0.5), Vector2(x + lean, top), 3.0, 1.0, Color(0.34, 0.38, 0.15).lerp(Color(0.55, 0.52, 0.26), rng.randf()))
	for s in 13:
		var x := rng.randf() * 256.0
		var top := rng.randf_range(16.0, 90.0)
		var lean := rng.randf_range(-14.0, 14.0)
		var end := Vector2(x + lean, top)
		_stroke(image, Vector2(x, 256), end, 4.0, 2.0, Color(0.24, 0.32, 0.13))
		for leaf in 5:
			var y := rng.randf_range(top + 40.0, 235.0)
			var side := 1.0 if leaf % 2 == 0 else -1.0
			_curve(image, Vector2(x + lean * (256.0 - y) / (256.0 - top), y), Vector2(x + side * 16.0, y - 16.0), Vector2(x + side * 34.0, y + 6.0), 7.0, 1.0, Color(0.2, 0.31, 0.15))
		match s % 3:
			0:
				# Thistle.
				_stroke(image, end, end + Vector2(0.5, 0.0), 11.0, 11.0, Color(0.27, 0.34, 0.15))
				for p in 7:
					_stroke(image, end, end + Vector2.from_angle(-PI * (p + 1) / 8.0) * 11.0, 2.4, 1.4, Color(0.62, 0.24, 0.62))
			1:
				# Dock.
				for b in 12:
					_stroke(image, end + Vector2(0.0, b * 5.0), end + Vector2(rng.randf_range(-6, 6), b * 5.0 + 3.0), 3.4, 2.4, Color(0.47, 0.25, 0.12))
			2:
				# White umbels.
				for b in 12:
					_stroke(image, end, end + Vector2(rng.randf_range(-16, 16), rng.randf_range(-11, -2)), 1.4, 2.8, Color(0.92, 0.91, 0.82))
	return image

func _plant_material(texture: String) -> StandardMaterial3D:
	var material := StandardMaterial3D.new()
	material.albedo_texture = load(CROPS + texture + ".res")
	material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
	material.alpha_scissor_threshold = 0.45
	material.cull_mode = BaseMaterial3D.CULL_DISABLED
	material.roughness = 0.9
	return material

func _save_mesh(mesh: ArrayMesh, name: String) -> void:
	ResourceSaver.save(mesh, Props.mesh_path(name), ResourceSaver.FLAG_COMPRESS)
	print("CROPS %s %d triangles" % [name, mesh.get_faces().size() / 3])

func _tri(st: SurfaceTool, a: Vector3, b: Vector3, c: Vector3, ua: Vector2, ub: Vector2, uc: Vector2, normal: Vector3) -> void:
	for pair: Array in [[a, ua], [c, uc], [b, ub]]:
		st.set_normal(normal)
		st.set_uv(pair[1])
		st.add_vertex(pair[0])

## A pine: a thin trunk and overlapping skirts of boughs, narrower upwards.
func _pine(height: float, radius: float, tiers: int) -> ArrayMesh:
	var boughs := SurfaceTool.new()
	boughs.begin(Mesh.PRIMITIVE_TRIANGLES)
	const SIDES := 9
	for t in tiers:
		var share := float(t) / tiers
		var top_y := lerpf(height * 0.34, height, pow(share + 1.0 / tiers, 0.95))
		var skirt_y := lerpf(height * 0.14, height * 0.9, pow(share, 0.95))
		var r := radius * (1.0 - share * 0.88)
		for s in SIDES:
			var a0 := TAU * s / SIDES + t * 0.7
			var a1 := TAU * (s + 1) / SIDES + t * 0.7
			var ring0 := Vector3(cos(a0) * r, skirt_y + sin(a0 * 3.0) * r * 0.08, sin(a0) * r)
			var ring1 := Vector3(cos(a1) * r, skirt_y + sin(a1 * 3.0) * r * 0.08, sin(a1) * r)
			var tip := Vector3(0.0, top_y, 0.0)
			var mid := (ring0 + ring1) * 0.5
			var normal := Vector3(mid.x, r * 0.8, mid.z).normalized()
			_tri(boughs, tip, ring0, ring1, Vector2(0.5, 0.0), Vector2(0.0, 1.0), Vector2(1.0, 1.0), normal)
	var mesh := ArrayMesh.new()
	boughs.generate_tangents()
	mesh = boughs.commit(mesh)
	mesh.surface_set_material(0, _plant_material("needles"))
	var trunk := SurfaceTool.new()
	trunk.begin(Mesh.PRIMITIVE_TRIANGLES)
	var bark := Vector2.ZERO
	for s in 6:
		var a0 := TAU * s / 6.0
		var a1 := TAU * (s + 1) / 6.0
		var n0 := Vector3(cos(a0), 0.0, sin(a0))
		var n1 := Vector3(cos(a1), 0.0, sin(a1))
		var low := height * 0.03 * 3.0
		var mid := (n0 + n1).normalized()
		_tri(trunk, n0 * low - Vector3.UP * 0.3, n1 * low - Vector3.UP * 0.3, n0 * 0.03 + Vector3.UP * height * 0.95, bark, bark, bark, mid)
		_tri(trunk, n1 * low - Vector3.UP * 0.3, n1 * 0.03 + Vector3.UP * height * 0.95, n0 * 0.03 + Vector3.UP * height * 0.95, bark, bark, bark, mid)
	mesh = trunk.commit(mesh)
	var wood := StandardMaterial3D.new()
	wood.albedo_color = Color(0.2, 0.14, 0.1)
	wood.roughness = 0.95
	mesh.surface_set_material(1, wood)
	return mesh

## A saguaro: a ribbed column, with arms if it is old enough.
func _cactus(height: float, arms: bool) -> ArrayMesh:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	_column(st, Vector3.ZERO, Vector3(0, height, 0), 0.27, 0.2)
	if arms:
		for side in [-1.0, 1.0]:
			var from := Vector3(0.0, height * (0.42 if side < 0.0 else 0.55), 0.0)
			var elbow := from + Vector3(side * 0.75, 0.25, 0.1 * side)
			_column(st, from, elbow, 0.14, 0.14)
			_column(st, elbow, elbow + Vector3(0.0, height * 0.28, 0.0), 0.15, 0.1)
	var mesh := ArrayMesh.new()
	mesh = st.commit(mesh)
	var skin := StandardMaterial3D.new()
	skin.albedo_color = Color(0.2, 0.31, 0.15)
	skin.roughness = 0.75
	mesh.surface_set_material(0, skin)
	return mesh

## A column with 10 ribs and a rounded top.
func _column(st: SurfaceTool, a: Vector3, b: Vector3, r0: float, r1: float) -> void:
	const RIBS := 10
	var axis := (b - a).normalized()
	var across := axis.cross(Vector3.FORWARD if absf(axis.z) < 0.9 else Vector3.RIGHT).normalized()
	var other := axis.cross(across)
	var uv := Vector2.ZERO
	for s in RIBS:
		var a0 := TAU * s / RIBS
		var a1 := TAU * (s + 1) / RIBS
		var k0 := 1.0 if s % 2 == 0 else 0.86
		var k1 := 0.86 if s % 2 == 0 else 1.0
		var d0 := (across * cos(a0) + other * sin(a0)) * k0
		var d1 := (across * cos(a1) + other * sin(a1)) * k1
		var normal := (d0 + d1).normalized()
		_tri(st, a + d0 * r0, a + d1 * r0, b + d0 * r1, uv, uv, uv, normal)
		_tri(st, a + d1 * r0, b + d1 * r1, b + d0 * r1, uv, uv, uv, normal)
		_tri(st, b + d0 * r1, b + d1 * r1, b + axis * r1 * 0.9, uv, uv, uv, (normal + axis).normalized())

## Crossed quads with a plant's texture.
func _tuft(texture: String, width: float, height: float, quads: int, tint: Color) -> ArrayMesh:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for q in quads:
		var angle := PI * q / quads + 0.3
		var side := Vector3(cos(angle), 0.0, sin(angle)) * width * 0.5
		var a := -side
		var b := side
		var c := side + Vector3.UP * height
		var d := -side + Vector3.UP * height
		var normal := Vector3.UP
		_tri(st, a, b, c, Vector2(0, 1), Vector2(1, 1), Vector2(1, 0), normal)
		_tri(st, a, c, d, Vector2(0, 1), Vector2(1, 0), Vector2(0, 0), normal)
	var mesh := ArrayMesh.new()
	mesh = st.commit(mesh)
	var material := _plant_material(texture)
	material.albedo_color = tint
	mesh.surface_set_material(0, material)
	return mesh
