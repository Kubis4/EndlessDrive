class_name DesertModels
extends RefCounted
## Original low-poly meshes authored for this Godot project. +X is vehicle forward.
## Run tests/build_art.gd to regenerate the editable .tscn assets.

static var _palette: Dictionary = {}


static func mat(key: String) -> Material:
	if _palette.has(key):
		return _palette[key]
	var colors := {
		"paint": Color("b9baaa"), "accent": Color("a66e43"),
		"rubber": Color("242720"), "steel": Color("646a60"),
		"glass": Color("354c50"), "lamp": Color("ecd9a1"),
		"red": Color("9b3624"), "wood": Color("69533a"),
		"adobe": Color("aa9270"), "plaster": Color("c3ad87"),
		"rock": Color("9b8567"), "rock_top": Color("b9a07a"),
		"cactus": Color("606947"), "dry": Color("716343"),
	}
	var material: Material
	if key in ["paint", "steel", "plaster"]:
		var textured := ShaderMaterial.new()
		textured.shader = load("res://shaders/desert_paint.gdshader")
		textured.set_shader_parameter("atlas", load("res://assets/textures/desert-materials.png"))
		textured.set_shader_parameter("tint", colors[key])
		textured.set_shader_parameter("tile", Vector2(0, 1) if key == "steel" else Vector2(1, 1))
		textured.set_shader_parameter("metalness", 0.35 if key == "steel" else 0.05)
		material = textured
	else:
		var plain := StandardMaterial3D.new()
		plain.albedo_color = colors.get(key, Color.WHITE)
		plain.roughness = 0.9
		if key in ["lamp", "red"]:
			plain.emission_enabled = true
			plain.emission = plain.albedo_color
			plain.emission_energy_multiplier = 0.15
		material = plain
	material.resource_name = "Desert_" + key
	_palette[key] = material
	return material


static func box(parent: Node3D, label: String, pos: Vector3, size: Vector3, material: Material) -> MeshInstance3D:
	var mesh := BoxMesh.new()
	mesh.size = size
	return piece(parent, label, mesh, pos, material)


static func piece(parent: Node3D, label: String, mesh: Mesh, pos: Vector3, material: Material) -> MeshInstance3D:
	var node := MeshInstance3D.new()
	node.name = label
	node.mesh = mesh
	node.material_override = material
	node.position = pos
	parent.add_child(node, true)
	preload("res://scripts/world_materials.gd").decorate(node)
	return node


static func cylinder(parent: Node3D, label: String, pos: Vector3, radius: float, height: float, material: Material, sides: int = 8, top: float = -1.0) -> MeshInstance3D:
	var mesh := CylinderMesh.new()
	mesh.bottom_radius = radius
	mesh.top_radius = radius if top < 0.0 else top
	mesh.height = height
	mesh.radial_segments = sides
	return piece(parent, label, mesh, pos, material)


static func beam(parent: Node3D, label: String, a: Vector3, b: Vector3, radius: float, material: Material) -> MeshInstance3D:
	var node := cylinder(parent, label, (a + b) * 0.5, radius, a.distance_to(b), material, 5)
	node.quaternion = Quaternion(Vector3.UP, (b - a).normalized())
	return node


static func collider(parent: Node3D, pos: Vector3, size: Vector3) -> void:
	var body := StaticBody3D.new()
	body.name = "Collision"
	body.position = pos
	var shape := CollisionShape3D.new()
	var bounds := BoxShape3D.new()
	bounds.size = size
	shape.shape = bounds
	body.add_child(shape)
	parent.add_child(body)


static func rover() -> Node3D:
	var root := Node3D.new()
	root.name = "DuneCourier"
	box(root, "LadderChassis", Vector3(0, 0.03, 0), Vector3(4.55, 0.24, 1.53), mat("steel"))
	box(root, "LowerBody", Vector3(-0.05, 0.40, 0), Vector3(4.45, 0.48, 1.84), mat("paint"))
	box(root, "Bonnet", Vector3(1.37, 0.79, 0), Vector3(1.65, 0.25, 1.80), mat("paint"))
	box(root, "CabinCore", Vector3(-0.22, 1.12, 0), Vector3(1.70, 0.91, 1.70), mat("glass"))
	box(root, "Roof", Vector3(-0.27, 1.63, 0), Vector3(1.94, 0.16, 1.89), mat("paint"))
	for side: float in [-1.0, 1.0]:
		box(root, "Door", Vector3(-0.14, 0.78, side * 0.94), Vector3(1.83, 0.39, 0.09), mat("paint"))
		box(root, "DoorAccent", Vector3(-0.14, 0.61, side * 0.992), Vector3(1.83, 0.11, 0.025), mat("accent"))
		box(root, "WindowPillar", Vector3(-0.22, 1.28, side * 0.89), Vector3(0.085, 0.64, 0.10), mat("paint"))
		box(root, "DoorHandle", Vector3(-0.67, 0.92, side * 1.0), Vector3(0.24, 0.045, 0.05), mat("steel"))
		box(root, "WingMirror", Vector3(0.65, 1.12, side * 1.10), Vector3(0.18, 0.18, 0.22), mat("rubber"))
		for x: float in [-1.08, 0.65]:
			box(root, "CabinPillar", Vector3(x, 1.23, side * 0.87), Vector3(0.12, 0.77, 0.12), mat("paint"))
		box(root, "CargoSide", Vector3(-1.73, 0.76, side * 0.90), Vector3(1.30, 0.41, 0.10), mat("paint"))
		for x: float in [-1.48, 1.48]:
			box(root, "Mudguard", Vector3(x, 0.58, side * 0.97), Vector3(1.15, 0.13, 0.31), mat("rubber"))
	box(root, "CargoFloor", Vector3(-1.74, 0.62, 0), Vector3(1.26, 0.10, 1.64), mat("wood"))
	box(root, "Tailgate", Vector3(-2.35, 0.78, 0), Vector3(0.12, 0.42, 1.88), mat("paint"))
	cylinder(root, "SpareTyre", Vector3(-1.64, 0.83, 0.12), 0.43, 0.30, mat("rubber"), 12)
	cylinder(root, "SpareRim", Vector3(-1.64, 0.99, 0.12), 0.22, 0.025, mat("steel"), 8)
	box(root, "SupplyCrate", Vector3(-1.7, 0.95, -0.57), Vector3(0.60, 0.54, 0.38), mat("accent"))
	for x: float in [-2.44, 2.32]:
		box(root, "Bumper", Vector3(x, 0.22, 0), Vector3(0.19, 0.24, 2.02), mat("steel"))
	box(root, "RadiatorGrille", Vector3(2.21, 0.65, 0), Vector3(0.08, 0.33, 1.05), mat("rubber"))
	for slot in 6:
		box(root, "GrilleSlat", Vector3(2.26, 0.65, -0.42 + slot * 0.17), Vector3(0.025, 0.25, 0.035), mat("steel"))
	for side: float in [-0.72, 0.72]:
		var lamp := cylinder(root, "Headlight", Vector3(2.24, 0.67, side), 0.15, 0.10, mat("lamp"), 10)
		lamp.rotation.z = PI * 0.5
		box(root, "BrakeLamp", Vector3(-2.43, 0.69, side), Vector3(0.07, 0.15, 0.23), mat("red"))
	for front: bool in [true, false]:
		for left: bool in [true, false]:
			var wheel := Node3D.new()
			wheel.name = "Wheel_" + ("F" if front else "R") + ("L" if left else "R")
			wheel.position = Vector3(1.48 if front else -1.48, 0, 0.99 if left else -0.99)
			root.add_child(wheel)
			var tyre := cylinder(wheel, "Tyre", Vector3.ZERO, 0.44, 0.34, mat("rubber"), 12)
			tyre.rotation.x = PI * 0.5
			for side: float in [-0.18, 0.18]:
				var rim := cylinder(wheel, "WheelRim", Vector3(0, 0, side), 0.25, 0.025, mat("steel"), 8)
				rim.rotation.x = PI * 0.5
				box(wheel, "HubSpoke", Vector3(0, 0, side * 1.09), Vector3(0.34, 0.055, 0.025), mat("rubber"))
	return root


static func workshop() -> Node3D:
	var root := Node3D.new()
	root.name = "AdobeWorkshop"
	box(root, "StoneFooting", Vector3(0, -0.12, 0), Vector3(10.3, 0.24, 7.3), mat("rock"))
	box(root, "EarthenWalls", Vector3(0, 1.75, 0), Vector3(10, 3.5, 7), mat("adobe"))
	box(root, "PlasterFront", Vector3(0, 1.70, 3.52), Vector3(9.90, 3.32, 0.08), mat("plaster"))
	box(root, "RoofParapet", Vector3(0, 3.57, 0), Vector3(10.5, 0.30, 7.5), mat("rock_top"))
	box(root, "WorkshopDoor", Vector3(0, 1.25, 3.60), Vector3(3.9, 2.5, 0.09), mat("wood"))
	for x: float in [-1.9, 1.9]:
		box(root, "DoorPost", Vector3(x, 1.3, 3.68), Vector3(0.12, 2.6, 0.16), mat("steel"))
	for x: float in [-3.65, 3.65]:
		box(root, "WindowRecess", Vector3(x, 1.9, 3.62), Vector3(1.3, 0.9, 0.12), mat("glass"))
		box(root, "WindowLintel", Vector3(x, 2.41, 3.68), Vector3(1.58, 0.14, 0.20), mat("wood"))
	var awning := box(root, "CorrugatedAwning", Vector3(0, 2.9, 4.4), Vector3(5.4, 0.12, 2.1), mat("steel"))
	awning.rotation.x = 0.11
	for x: float in [-2.45, 2.45]:
		beam(root, "AwningSupport", Vector3(x, 0, 5.15), Vector3(x, 2.75, 5.15), 0.07, mat("wood"))
	for z: float in [-2.4, -1.1, 0.2, 1.5]:
		box(root, "RoofJoist", Vector3(0, 3.80, z), Vector3(10.7, 0.14, 0.13), mat("wood"))
	box(root, "Chimney", Vector3(-3.4, 4.15, -1.8), Vector3(0.65, 1.2, 0.65), mat("adobe"))
	box(root, "FadedSign", Vector3(0, 3.1, 3.70), Vector3(3.5, 0.48, 0.10), mat("accent"))
	var sign := Label3D.new()
	sign.name = "WorkshopSign"
	sign.text = "SERVIS"
	sign.font_size = 48
	sign.pixel_size = 0.006
	sign.position = Vector3(0, 3.1, 3.77)
	sign.modulate = Color("e3cfa5")
	root.add_child(sign)
	collider(root, Vector3(0, 1.75, 0), Vector3(10, 3.5, 7))
	return root


static func cactus() -> Node3D:
	var root := Node3D.new()
	root.name = "Saguaro"
	cylinder(root, "Trunk", Vector3(0, 1.6, 0), 0.23, 3.2, mat("cactus"), 7, 0.15)
	for side: float in [-1.0, 1.0]:
		var y := 1.3 if side < 0 else 1.8
		beam(root, "Arm", Vector3(0, y, 0), Vector3(side * 0.65, y + 0.12, 0), 0.14, mat("cactus"))
		cylinder(root, "ArmTip", Vector3(side * 0.65, y + 0.55, 0), 0.145, 1.1, mat("cactus"), 7, 0.10)
	collider(root, Vector3(0, 1.4, 0), Vector3(0.45, 2.8, 0.45))
	return root


static func scrub() -> Node3D:
	var root := Node3D.new()
	root.name = "DesertScrub"
	for blade in 7:
		var angle := blade * TAU / 7.0
		var end := Vector3(cos(angle) * 0.46, 0.35 + (blade % 3) * 0.14, sin(angle) * 0.46)
		beam(root, "DryLeaf", Vector3.ZERO, end, 0.035, mat("dry"))
		beam(root, "LeafTip", end, end * 1.14 + Vector3.UP * 0.17, 0.018, mat("dry"))
	return root


static func rock() -> Node3D:
	var root := Node3D.new()
	root.name = "WeatheredMesaRock"
	var tool := SurfaceTool.new()
	tool.begin(Mesh.PRIMITIVE_TRIANGLES)
	var bottom: Array[Vector3] = []
	var top: Array[Vector3] = []
	for corner in 7:
		var angle := corner * TAU / 7.0
		var radius := 1.0 + sin(corner * 17.3) * 0.15
		bottom.append(Vector3(cos(angle) * radius, 0, sin(angle) * radius))
		top.append(Vector3(cos(angle) * radius * 0.72 + 0.1, 1.25 + sin(corner * 2.1) * 0.12, sin(angle) * radius * 0.72))
	for corner in 7:
		var next := (corner + 1) % 7
		_triangle(tool, bottom[corner], bottom[next], top[corner])
		_triangle(tool, bottom[next], top[next], top[corner])
		_triangle(tool, Vector3(0.1, 1.25, 0), top[corner], top[next])
	piece(root, "FacetedSandstone", tool.commit(), Vector3.ZERO, mat("rock"))
	collider(root, Vector3(0, 0.55, 0), Vector3(1.5, 1.1, 1.5))
	return root


static func _triangle(tool: SurfaceTool, a: Vector3, b: Vector3, c: Vector3) -> void:
	var normal := -(b - a).cross(c - a).normalized()
	for point: Vector3 in [a, b, c]:
		tool.set_normal(normal)
		tool.set_uv(Vector2(point.x, point.z))
		tool.add_vertex(point)


static func pole() -> Node3D:
	var root := Node3D.new()
	root.name = "DesertUtilityPole"
	cylinder(root, "TimberPole", Vector3(0, 4.8, 0), 0.16, 9.6, mat("wood"), 6, 0.10)
	box(root, "CrossArm", Vector3(0, 8.85, 0), Vector3(0.16, 0.18, 3.1), mat("wood"))
	for z: float in [-1.25, 0.0, 1.25]:
		cylinder(root, "Insulator", Vector3(0, 9.08, z), 0.10, 0.28, mat("glass"), 6)
	collider(root, Vector3(0, 4.5, 0), Vector3(0.32, 9, 0.32))
	return root


static func fence() -> Node3D:
	var root := Node3D.new()
	root.name = "RanchWireFence"
	for x: float in [0.0, 5.0]:
		beam(root, "FencePost", Vector3(x, 0, 0), Vector3(x - 0.14, 1.85, 0), 0.065, mat("wood"))
	for y: float in [0.55, 1.1, 1.65]:
		beam(root, "FenceWire", Vector3(0, y, 0), Vector3(2.5, y - 0.13, 0), 0.016, mat("steel"))
		beam(root, "FenceWire", Vector3(2.5, y - 0.13, 0), Vector3(5, y, 0), 0.016, mat("steel"))
	return root


static func tower() -> Node3D:
	var root := Node3D.new()
	root.name = "OutpostWaterTower"
	for x: float in [-1.0, 1.0]:
		for z: float in [-1.0, 1.0]:
			beam(root, "TowerLeg", Vector3(x * 1.4, 0, z * 1.4), Vector3(x, 5.7, z), 0.09, mat("wood"))
	for side: float in [-1.0, 1.0]:
		beam(root, "DiagonalBrace", Vector3(-1.3, 1, side * 1.3), Vector3(1, 5.1, side), 0.055, mat("wood"))
	cylinder(root, "WaterTank", Vector3(0, 6.3, 0), 1.8, 2.8, mat("steel"), 10)
	cylinder(root, "TankRoof", Vector3(0, 7.85, 0), 1.95, 0.38, mat("rock_top"), 10, 0.6)
	collider(root, Vector3(0, 3.2, 0), Vector3(2.5, 6.4, 2.5))
	return root
