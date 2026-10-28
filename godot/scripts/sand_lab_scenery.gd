extends Node3D
## Sparse, deterministic set dressing for the playable sand laboratory.
var ground: DriveWorld
var sand_stone := _material(Color("a89575"))
var concrete := _material(Color("a99c82"))
var shadow := _material(Color("514c3f"))
var wood := _material(Color("786448"))
var metal := _material(Color("6f725b"))

func _material(color: Color) -> StandardMaterial3D:
	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mat.roughness = 0.94
	return mat

func build(world: DriveWorld) -> void:
	ground = world
	name = "DesertScenery"
	# A small outpost sits to one side of the open driving corridor.
	_building(Vector2(-15, 13), Vector3(5.8, 2.8, 4.0), 0.18)
	_building(Vector2(7, -16), Vector3(4.5, 2.4, 3.4), -0.3)
	for point in [Vector2(-20, 17), Vector2(-10, 16), Vector2(10, -12), Vector2(14, -14), Vector2(17, 14), Vector2(-30, -15), Vector2(26, -18)]:
		_rock(point, 0.7 + absf(sin(point.x)) * 0.9)
	for point in [Vector2(-28, 15), Vector2(-2, 19), Vector2(22, -19)]:
		_pole(point)
	_fence(Vector2(-25, 11), 6, 0.1)
	_fence(Vector2(13, -18), 5, -0.2)
	_barricade(Vector2(-8, 12), 0.2)
	_barricade(Vector2(11, -10), -0.35)
	_wreck(Vector2(15, 15), -0.4)
	_wreck(Vector2(-29, -13), 0.55)
	for point in [Vector2(-17, 9), Vector2(-12, 11), Vector2(5, -12), Vector2(19, 14), Vector2(-24, 15)]:
		_person(point)

func _site(point: Vector2, angle := 0.0) -> Node3D:
	var root := Node3D.new()
	root.position = Vector3(point.x, ground.height_at(point.x, point.y), point.y)
	root.rotation.y = angle
	add_child(root)
	return root

func _box(parent: Node3D, size: Vector3, pos: Vector3, mat: Material) -> MeshInstance3D:
	var mesh := BoxMesh.new()
	mesh.size = size
	var instance := MeshInstance3D.new()
	instance.mesh = mesh
	instance.material_override = mat
	instance.position = pos
	parent.add_child(instance)
	return instance

func _building(point: Vector2, size: Vector3, angle: float) -> void:
	var root := _site(point, angle)
	_box(root, size, Vector3(0, size.y * 0.5, 0), concrete)
	_box(root, Vector3(size.x + 0.18, 0.16, size.z + 0.18), Vector3(0, size.y, 0), sand_stone)
	_box(root, Vector3(1.0, 1.7, 0.05), Vector3(-size.x * 0.23, 0.85, -size.z * 0.5 - 0.03), shadow)
	_box(root, Vector3(1.0, 0.48, 0.05), Vector3(size.x * 0.2, 1.65, -size.z * 0.5 - 0.03), shadow)

func _rock(point: Vector2, size: float) -> void:
	var root := _site(point, point.x)
	var mesh := SphereMesh.new()
	mesh.radial_segments = 7
	mesh.rings = 4
	mesh.radius = size
	mesh.height = size * 1.15
	var instance := MeshInstance3D.new()
	instance.mesh = mesh
	instance.material_override = sand_stone
	instance.position.y = size * 0.35
	instance.scale = Vector3(1.3, 0.8, 0.85)
	root.add_child(instance)

func _pole(point: Vector2) -> void:
	var root := _site(point)
	_box(root, Vector3(0.16, 4.8, 0.16), Vector3(0, 2.4, 0), wood)
	_box(root, Vector3(1.7, 0.12, 0.13), Vector3(0, 4.25, 0), wood)
	for x in [-0.6, 0.6]:
		_box(root, Vector3(0.12, 0.22, 0.12), Vector3(x, 4.42, 0), shadow)

func _fence(point: Vector2, length: int, angle: float) -> void:
	var root := _site(point, angle)
	for i in range(length + 1):
		_box(root, Vector3(0.13, 1.35, 0.13), Vector3(i * 1.6, 0.67, 0), wood)
	for y in [0.5, 1.05]:
		_box(root, Vector3(length * 1.6, 0.09, 0.08), Vector3(length * 0.8, y, 0), wood)

func _barricade(point: Vector2, angle: float) -> void:
	var root := _site(point, angle)
	for x in [-1.0, 1.0]:
		var leg := _box(root, Vector3(0.14, 1.5, 0.14), Vector3(x, 0.65, 0), wood)
		leg.rotation.z = x * 0.18
	for y in [0.7, 1.1]:
		_box(root, Vector3(2.6, 0.25, 0.14), Vector3(0, y, 0), wood)

func _wreck(point: Vector2, angle: float) -> void:
	var root := _site(point, angle)
	_box(root, Vector3(3.4, 0.65, 1.7), Vector3(0, 0.8, 0), metal)
	_box(root, Vector3(1.35, 0.55, 1.5), Vector3(-0.4, 1.4, 0), metal)
	_box(root, Vector3(0.08, 0.32, 1.3), Vector3(0.3, 1.43, 0), shadow)
	for x in [-1.05, 1.05]:
		for z in [-0.9, 0.9]:
			_box(root, Vector3(0.65, 0.65, 0.32), Vector3(x, 0.4, z), shadow)

func _person(point: Vector2) -> void:
	var root := _site(point, point.x)
	_box(root, Vector3(0.42, 0.65, 0.25), Vector3(0, 1.05, 0), metal)
	_box(root, Vector3(0.26, 0.28, 0.25), Vector3(0, 1.52, 0), sand_stone)
	for x in [-0.11, 0.11]:
		_box(root, Vector3(0.14, 0.65, 0.16), Vector3(x, 0.33, 0), shadow)
