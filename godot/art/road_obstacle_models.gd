extends RefCounted
## Small saved native models; no simulation or model generation during driving.
const KINDS := ["fallen_tree", "wreck", "rubble", "barrier"]

static func build(kind: String) -> Node3D:
	var root := Node3D.new()
	root.name = kind.to_pascal_case()
	root.set_meta("obstacle_kind", kind)
	match kind:
		"fallen_tree": _fallen_tree(root)
		"wreck": _wreck(root)
		"rubble": _rubble(root)
		"barrier": _barrier(root)
	return root

static func _beam(root: Node3D, label: String, a: Vector3, b: Vector3, radius: float, solid: bool) -> void:
	var visual := DesertModels.beam(root, label, a, b, radius, DesertModels.mat("wood"))
	if not solid: return
	var body := StaticBody3D.new()
	body.name = label + "Body"
	root.add_child(body)
	var shape := CollisionShape3D.new()
	var capsule := CapsuleShape3D.new()
	capsule.radius = radius
	capsule.height = a.distance_to(b) + radius * 2
	shape.shape = capsule
	shape.position = visual.position
	shape.quaternion = visual.quaternion
	body.add_child(shape)

static func _fallen_tree(root: Node3D) -> void:
	_beam(root, "FallenTrunk", Vector3(0, 0.42, -6.0), Vector3(0.25, 0.60, 5.8), 0.40, true)
	for i in 7:
		var z := -3.8 + i * 1.25
		var side := -1.0 if i % 2 == 0 else 1.0
		_beam(root, "BrokenBranch%d" % i, Vector3(0.08, 0.5, z), Vector3(side * (1.1 + (i % 3) * 0.3), 0.6 + (i % 2) * 0.55, z + 0.7), 0.09, i % 2 == 0)
	var roots := SphereMesh.new()
	roots.radius = 0.67
	roots.height = 1.1
	roots.radial_segments = 8
	roots.rings = 4
	DesertModels.piece(root, "UprootedBase", roots, Vector3(0, 0.58, -6.0), DesertModels.mat("dry"))
	for i in 5:
		var angle := i * TAU / 5
		_beam(root, "Root%d" % i, Vector3(0, 0.55, -5.9), Vector3(cos(angle) * 0.9, 0.5 + sin(angle) * 0.45, -6.65), 0.08, false)

static func _wreck(root: Node3D) -> void:
	DesertModels.box(root, "RustedChassis", Vector3(0, 0.7, 0), Vector3(4.6, 0.70, 1.95), DesertModels.mat("accent"))
	DesertModels.box(root, "Cabin", Vector3(-0.35, 1.32, 0), Vector3(2.7, 0.85, 1.8), DesertModels.mat("steel"))
	for side: float in [-1, 1]:
		DesertModels.box(root, "BrokenSideGlass", Vector3(-0.3, 1.42, side * 0.915), Vector3(2.1, 0.46, 0.025), DesertModels.mat("glass"))
		for x: float in [-1.55, 1.5]:
			var tyre := DesertModels.cylinder(root, "FlatTyre", Vector3(x, 0.34, side * 0.94), 0.34, 0.24, DesertModels.mat("rubber"), 8)
			tyre.rotation.x = PI * 0.5
	DesertModels.box(root, "ShatteredWindscreen", Vector3(1.02, 1.43, 0), Vector3(0.025, 0.48, 1.45), DesertModels.mat("glass"))
	var hood := DesertModels.box(root, "BuckledHood", Vector3(1.65, 1.08, 0), Vector3(1.25, 0.09, 1.8), DesertModels.mat("accent"))
	hood.rotation.z = -0.14
	for i in 3:
		DesertModels.box(root, "ExposedMetal", Vector3(-1.7 + i * 1.1, 0.72, 0.99), Vector3(0.44, 0.24, 0.035), DesertModels.mat("wood"))
	DesertModels.collider(root, Vector3(0, 0.97, 0), Vector3(4.6, 1.55, 1.95))

static func _rubble(root: Node3D) -> void:
	var packed := load("res://assets/models/mesa_rock.tscn") as PackedScene
	for i in 4:
		var rock := packed.instantiate() as Node3D
		rock.name = "RoadRock%d" % i
		rock.position = Vector3(-0.5 + (i % 2) * 1.1, -0.12, -2.1 + i * 1.3)
		rock.scale = Vector3(0.75, 0.55 + (i % 2) * 0.15, 0.75)
		rock.rotation.y = i * 1.1
		root.add_child(rock)

static func _barrier(root: Node3D) -> void:
	for i in 2:
		var z := -1.5 + i * 3.0
		DesertModels.box(root, "ConcreteFoot", Vector3(0, 0.16, z), Vector3(0.85, 0.32, 2.75), DesertModels.mat("plaster"))
		DesertModels.box(root, "ConcreteBlock", Vector3(0, 0.68, z), Vector3(0.45, 0.9, 2.65), DesertModels.mat("rock_top"))
		for stripe in 4:
			var plate := DesertModels.box(root, "FadedWarning", Vector3(-0.24, 0.72, z - 1.0 + stripe * 0.66), Vector3(0.025, 0.28, 0.28), DesertModels.mat("accent" if stripe % 2 == 0 else "rubber"))
			plate.rotation.x = 0.35
		DesertModels.collider(root, Vector3(0, 0.58, z), Vector3(0.85, 1.16, 2.75))
