extends RefCounted
## Small saved native models; no simulation or model generation during driving.
const KINDS := ["fallen_tree", "wreck", "rubble", "barrier"]

static func build(kind: String, wreck_variant: int = 0) -> Node3D:
	var root := Node3D.new()
	root.name = kind.to_pascal_case()
	root.set_meta("obstacle_kind", kind)
	match kind:
		"fallen_tree": _fallen_tree(root)
		"wreck": _wreck(root, wreck_variant)
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

static func _wreck(root: Node3D, variant: int) -> void:
	variant = clampi(variant, 0, 1)
	var model := (load("res://assets/models/wrecks/wreck_%d.tscn" % variant) as PackedScene).instantiate() as Node3D
	model.name = "SuppliedWreck%d" % variant
	# Source assets are lengthwise on Z; road obstacle footprints use X.
	model.rotation.y = PI * 0.5
	root.add_child(model)
	var height := 1.8 if variant == 0 else 1.1
	DesertModels.collider(root, Vector3(0, height * 0.5, 0), Vector3(4.3, height, 1.9))

static func _rubble(root: Node3D) -> void:
	for i in 4:
		var packed := load("res://assets/models/supplied_environment/rock_plain_%d.tscn" % (i + 1)) as PackedScene
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
