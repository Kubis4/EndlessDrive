extends RefCounted
## Buildings for the sites along the endless road, one kind per landscape, put
## together from simple solids with weathered planks, logs, stone or clay
## (triplanar, so no unwrapping): forest lodge, barn, clay house, mountain hut,
## fisherman's hut on stilts. Each returns a node with its collision, the door
## facing -Z of the node; `goods` stands the loot in view before it.
const Surface = preload("res://desert_battlefield/terrain.gd")
const Tyres = preload("res://offroad/tyres.gd")
enum { FOREST, MEADOW, DESERT, MOUNTAINS, BOG, FIELDS, STEPPE, TAIGA, FOOTHILLS }

static var textures := {}

## Makes the patterns now (the load), not on the road when a building first shows.
static func prepare() -> void:
	for kind in ["planks", "logs", "stone", "clay"]: _pattern(kind)

## A grey tileable pattern (colour comes from the material): planks or stone.
static func _pattern(kind: String) -> Texture2D:
	if textures.has(kind): return textures[kind]
	var noise := FastNoiseLite.new()
	noise.frequency = 0.06
	noise.fractal_octaves = 4
	const SIZE := 128
	var image := Image.create(SIZE, SIZE, true, Image.FORMAT_RGB8)
	for y in SIZE:
		for x in SIZE:
			var n := 0.5 + 0.5 * noise.get_noise_2d(x * (0.3 if kind == "planks" else 1.0), y * (2.5 if kind == "planks" else 1.0))
			var v := 0.7 + 0.45 * n
			match kind:
				"planks":
					# Boards a sixth of the tile wide, dark gaps, a tone per board.
					var board := y % 21
					v *= 0.86 + 0.14 * sin(float(y / 21) * 12.9)
					if board < 2: v *= 0.35
				"logs":
					var round_log := sin(float(y % 32) / 32.0 * PI)
					v *= 0.45 + 0.6 * round_log
				"stone":
					var row := y / 20
					var column := (x + (row % 2) * 16) % 32
					if y % 20 < 2 or column < 2: v *= 0.45
				"clay":
					v = 0.82 + 0.25 * n
			image.set_pixel(x, y, Color(v, v, v))
	image.generate_mipmaps()
	var texture := ImageTexture.create_from_image(image)
	textures[kind] = texture
	return texture

static func _material(kind: String, color: Color) -> StandardMaterial3D:
	var material := StandardMaterial3D.new()
	material.albedo_color = color
	material.albedo_texture = _pattern(kind)
	material.uv1_triplanar = true
	material.uv1_scale = Vector3.ONE * 0.45
	material.roughness = 0.92
	return material

static func _box(parent: Node3D, size: Vector3, at: Vector3, material: Material) -> MeshInstance3D:
	var node := MeshInstance3D.new()
	var mesh := BoxMesh.new()
	mesh.size = size
	node.mesh = mesh
	node.material_override = material
	node.position = at
	parent.add_child(node)
	return node

## A pitched roof over a footprint (ridge along X), with an overhang.
static func _roof(parent: Node3D, width: float, depth: float, rise: float, y: float, material: Material) -> void:
	var node := MeshInstance3D.new()
	var mesh := PrismMesh.new()
	mesh.size = Vector3(depth + 0.8, rise, width + 0.8)
	node.mesh = mesh
	node.material_override = material
	node.rotation.y = PI * 0.5
	node.position = Vector3(0, y + rise * 0.5, 0)
	parent.add_child(node)

static func _solid(parent: Node3D, size: Vector3, at: Vector3) -> void:
	var body := StaticBody3D.new()
	var shape := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = size
	shape.shape = box
	shape.position = at
	body.add_child(shape)
	parent.add_child(body)

static func _windows(parent: Node3D, width: float, y: float, z: float, count: int) -> void:
	var glass := StandardMaterial3D.new()
	glass.albedo_color = Color(0.06, 0.08, 0.1)
	glass.roughness = 0.15
	for i in count:
		var x := -width * 0.5 + width * (i + 0.5) / count
		if absf(x) < 0.9: continue
		_box(parent, Vector3(0.9, 0.8, 0.06), Vector3(x, y, z), glass)

static func build(biome: int) -> Node3D:
	var root := Node3D.new()
	var door := StandardMaterial3D.new()
	door.albedo_color = Color(0.11, 0.08, 0.06)
	door.roughness = 0.9
	match biome:
		MEADOW, FIELDS:
			# Barn: tall boards, big dark doorway, a steep roof.
			var walls := _material("planks", Color(0.5, 0.25, 0.17))
			_box(root, Vector3(8.0, 3.6, 6.0), Vector3(0, 1.8, 0), walls)
			_roof(root, 8.0, 6.0, 2.4, 3.6, _material("planks", Color(0.3, 0.27, 0.25)))
			_box(root, Vector3(3.0, 2.9, 0.08), Vector3(0, 1.45, -3.02), door)
			_solid(root, Vector3(8.0, 4.0, 6.0), Vector3(0, 2.0, 0))
		DESERT, STEPPE:
			# Clay house: thick walls, flat roof with a parapet, a water tank.
			var walls := _material("clay", Color(0.72, 0.58, 0.42))
			_box(root, Vector3(6.5, 3.0, 5.5), Vector3(0, 1.5, 0), walls)
			for side in [-1.0, 1.0]:
				_box(root, Vector3(6.5, 0.4, 0.3), Vector3(0, 3.2, side * 2.6), walls)
				_box(root, Vector3(0.3, 0.4, 5.5), Vector3(side * 3.1, 3.2, 0), walls)
			_box(root, Vector3(1.2, 2.2, 0.08), Vector3(0, 1.1, -2.77), door)
			_windows(root, 6.5, 1.9, -2.77, 3)
			var tank := MeshInstance3D.new()
			var cylinder := CylinderMesh.new()
			cylinder.top_radius = 0.7
			cylinder.bottom_radius = 0.7
			cylinder.height = 1.3
			tank.mesh = cylinder
			tank.material_override = _material("clay", Color(0.42, 0.42, 0.4))
			tank.position = Vector3(1.6, 3.65, 1.0)
			root.add_child(tank)
			_solid(root, Vector3(6.5, 3.4, 5.5), Vector3(0, 1.7, 0))
		MOUNTAINS, FOOTHILLS:
			# Mountain hut: stone ground floor, boards above, a steep snowy roof.
			_box(root, Vector3(7.0, 2.2, 5.5), Vector3(0, 1.1, 0), _material("stone", Color(0.46, 0.45, 0.43)))
			_box(root, Vector3(7.0, 1.6, 5.5), Vector3(0, 3.0, 0), _material("planks", Color(0.33, 0.24, 0.16)))
			_roof(root, 7.0, 5.5, 2.8, 3.8, _material("clay", Color(0.86, 0.88, 0.92)))
			_box(root, Vector3(1.2, 2.1, 0.08), Vector3(0, 1.05, -2.77), door)
			_windows(root, 7.0, 3.0, -2.77, 3)
			_solid(root, Vector3(7.0, 4.0, 5.5), Vector3(0, 2.0, 0))
		BOG:
			# Fisherman's hut on stilts, with a ramp up to the door.
			var boards := _material("planks", Color(0.36, 0.31, 0.24))
			for x in [-2.3, 2.3]:
				for z in [-1.8, 1.8]:
					_box(root, Vector3(0.22, 1.8, 0.22), Vector3(x, 0.6, z), boards)
			_box(root, Vector3(5.2, 0.15, 4.2), Vector3(0, 1.45, 0), boards)
			_box(root, Vector3(4.8, 2.2, 3.8), Vector3(0, 2.6, 0), boards)
			_roof(root, 4.8, 3.8, 1.3, 3.7, _material("planks", Color(0.22, 0.2, 0.18)))
			_box(root, Vector3(1.0, 1.8, 0.08), Vector3(0, 2.4, -1.93), door)
			var ramp := _box(root, Vector3(1.2, 0.1, 3.4), Vector3(0, 0.72, -3.55), boards)
			ramp.rotation.x = -0.46
			_solid(root, Vector3(5.2, 3.8, 4.2), Vector3(0, 2.2, 0))
		_:
			# Forest lodge of logs with a porch.
			var logs := _material("logs", Color(0.36, 0.25, 0.16))
			_box(root, Vector3(6.5, 2.7, 5.0), Vector3(0, 1.35, 0), logs)
			_roof(root, 6.5, 5.0, 2.0, 2.7, _material("planks", Color(0.2, 0.2, 0.17)))
			_box(root, Vector3(1.1, 2.0, 0.08), Vector3(0, 1.0, -2.52), door)
			_windows(root, 6.5, 1.6, -2.52, 3)
			_box(root, Vector3(4.0, 0.12, 1.6), Vector3(0, 0.1, -3.3), logs)
			for x in [-1.9, 1.9]: _box(root, Vector3(0.16, 2.3, 0.16), Vector3(x, 1.15, -3.95), logs)
			_box(root, Vector3(4.3, 0.1, 1.9), Vector3(0, 2.35, -3.25), _material("planks", Color(0.2, 0.2, 0.17)))
			_solid(root, Vector3(6.5, 3.4, 5.0), Vector3(0, 1.7, 0))
	# A foundation down into the slope, so it never floats.
	if biome != BOG: _box(root, Vector3(8.5, 6.0, 6.5), Vector3(0, -3.0, 0), _material("stone", Color(0.35, 0.34, 0.32)))
	return root

## The goods standing outside: a crate, and tyre sets stacked where there are any.
static func goods(parent: Node3D, tyres: Array, rubber: Material, front: float = 4.4) -> void:
	var crate := _box(parent, Vector3(1.1, 0.8, 0.9), Vector3(3.2, 0.4, -front), _material("planks", Color(0.5, 0.41, 0.28)))
	crate.rotation.y = 0.3
	_solid(parent, Vector3(1.2, 0.8, 1.1), Vector3(3.2, 0.4, -front))
	var slot := 0
	for kind: String in tyres:
		for i in 4:
			var tyre := MeshInstance3D.new()
			tyre.mesh = Tyres.mesh_of(kind)
			tyre.set_surface_override_material(0, rubber)
			tyre.rotation.x = PI * 0.5
			tyre.position = Vector3(-3.2 - slot * 1.0, 0.18 + i * 0.34, -front)
			parent.add_child(tyre)
		_solid(parent, Vector3(0.9, 1.4, 0.9), Vector3(-3.2 - slot * 1.0, 0.7, -front))
		slot += 1
