class_name StopModels
extends RefCounted
## Original code-native low-poly stops. Local +Z faces the entrance/road.
## Only walls, columns and equipment collide; bay doors and forecourts stay open.

static var _materials: Dictionary = {}


static func material(color: Color) -> StandardMaterial3D:
	if not _materials.has(color):
		var value := StandardMaterial3D.new()
		value.albedo_color = color
		value.roughness = 0.87
		_materials[color] = value
	return _materials[color]


static func create(kind: String) -> Node3D:
	var root := Node3D.new()
	root.name = "Stop_" + kind
	match kind:
		"home": _home(root)
		"gas": _gas(root)
		"repair": _garage(root, Color("78898b"), Color("bb6349"), "AUTOSERVIS")
		"paint": _garage(root, Color("ded2b1"), Color("559e95"), "LAKOVŇA")
		_: _garage(root, Color("b89971"), Color("ad653d"), "DIELŇA")
	if kind == "paint":
		for i in 4:
			_box(root, "PaintSwatch", Vector3(-3.7 + i * 0.5, 1.7, 3.73), Vector3(0.4, 1.1, 0.10),
				[Color("cb6d58"), Color("e7bb58"), Color("5ca895"), Color("7199ad")][i])
		DesertModels.cylinder(root, "Extractor", Vector3(3.3, 4.5, -1.4), 0.52, 1.3, material(Color("78898b")), 8)
	elif kind == "repair":
		for x: float in [-1.75, 1.75]:
			_solid(root, "LiftColumn", Vector3(x, 1.1, -1.5), Vector3(0.26, 2.2, 0.32), Color("bd654c"))
		for i in 3:
			DesertModels.cylinder(root, "SpareTyre", Vector3(3.7, 0.2 + i * 0.4, 2.3), 0.56, 0.36, material(Color("303937")), 10)
	elif kind == "workshop":
		_box(root, "Chimney", Vector3(-3.3, 4.3, -1.6), Vector3(0.7, 1.2, 0.7), Color("a7825f"))
		for z: float in [-2.2, -0.6, 1.0, 2.6]:
			_box(root, "TimberRoofJoist", Vector3(0, 3.98, z), Vector3(10.5, 0.16, 0.18), Color("6d583f"))
	return root


static func _garage(root: Node3D, wall: Color, accent: Color, title: String) -> void:
	# Preserve the tutorial garage's 10 x 7 m collision footprint, with an open 4.8 m bay.
	_solid(root, "RearWall", Vector3(0, 1.8, -3.3), Vector3(10, 3.6, 0.4), wall)
	for side: float in [-1.0, 1.0]:
		_solid(root, "SideWall", Vector3(side * 4.8, 1.8, 0), Vector3(0.4, 3.6, 7), wall)
		_solid(root, "FrontWall", Vector3(side * 3.7, 1.8, 3.3), Vector3(2.6, 3.6, 0.4), wall)
	_solid(root, "DoorLintel", Vector3(0, 3.35, 3.3), Vector3(4.8, 0.5, 0.4), wall)
	_solid(root, "Roof", Vector3(0, 3.75, 0), Vector3(10.5, 0.3, 7.5), Color("635f54"))
	_box(root, "ServiceBand", Vector3(0, 2.95, 3.56), Vector3(10.1, 0.36, 0.14), accent)
	_box(root, "OpenBayShadow", Vector3(0, 1.5, -3.07), Vector3(4.6, 3.0, 0.025), Color("424940"))
	_box(root, "WorkBench", Vector3(2.9, 0.95, -2.5), Vector3(2.4, 0.2, 1.0), accent)
	var awning := _box(root, "ServiceAwning", Vector3(0, 3.05, 4.35), Vector3(6.2, 0.12, 2.0), accent)
	awning.rotation.x = 0.08
	for side: float in [-1.0, 1.0]:
		_box(root, "BayFrame", Vector3(side * 2.5, 1.4, 3.6), Vector3(0.13, 2.8, 0.12), accent)
	_label(root, title, Vector3(0, 3.45, 3.61), 0.006)
	if title == "AUTOSERVIS":
		for i in 10:
			_box(root, "MetalCladding", Vector3(-4.6 + i * 1.02, 1.8, -3.53), Vector3(0.055, 3.4, 0.05), Color("b3bab0"))


static func _home(root: Node3D) -> void:
	_solid(root, "Cottage", Vector3(0, 1.5, 0), Vector3(8.6, 3.0, 6.6), Color("d7c19a"))
	# Two sloped roof planes give a distinct gabled silhouette.
	for side: float in [-1.0, 1.0]:
		var roof := _box(root, "PitchedRoof", Vector3(0, 3.65, side * 1.85), Vector3(9.4, 0.22, 4.05), Color("975d48"))
		roof.rotation.x = side * 0.38
	_box(root, "Door", Vector3(0, 1.1, 3.34), Vector3(1.2, 2.2, 0.12), Color("68836f"))
	for side: float in [-1.0, 1.0]:
		_box(root, "WindowFrame", Vector3(side * 2.65, 1.85, 3.37), Vector3(1.6, 1.2, 0.16), Color("f1dfb7"))
		_box(root, "WindowGlass", Vector3(side * 2.65, 1.85, 3.47), Vector3(1.3, 0.94, 0.05), Color("426976"))
		_box(root, "WindowMullion", Vector3(side * 2.65, 1.85, 3.51), Vector3(0.08, 0.94, 0.04), Color("f1dfb7"))
	_box(root, "PorchRoof", Vector3(0, 2.65, 4.2), Vector3(3.8, 0.16, 2.0), Color("975d48"))
	for x: float in [-1.7, 1.7]:
		_solid(root, "PorchPost", Vector3(x, 1.25, 4.9), Vector3(0.14, 2.5, 0.14), Color("795f46"))
	_box(root, "Chimney", Vector3(-2.8, 4.3, -0.8), Vector3(0.65, 1.3, 0.65), Color("b38b70"))
	_label(root, "DOM", Vector3(0, 2.8, 3.55), 0.005)


static func _gas(root: Node3D) -> void:
	_solid(root, "StationKiosk", Vector3(0, 1.45, -1.4), Vector3(10, 2.9, 4.2), Color("e2d6b5"))
	_box(root, "KioskWindow", Vector3(-2.5, 1.6, 0.75), Vector3(3.5, 1.35, 0.08), Color("456c72"))
	_box(root, "KioskDoor", Vector3(2.8, 1.1, 0.76), Vector3(1.35, 2.2, 0.10), Color("4b777b"))
	_solid(root, "PumpCanopy", Vector3(0, 3.8, 2.8), Vector3(11.3, 0.3, 7.2), Color("c67546"))
	_box(root, "CanopyStripe", Vector3(0, 3.78, 6.43), Vector3(11.35, 0.2, 0.06), Color("f0deaf"))
	for side: float in [-1.0, 1.0]:
		_solid(root, "CanopyColumn", Vector3(side * 5.0, 1.8, 5.5), Vector3(0.22, 3.6, 0.22), Color("e2d6b5"))
		var p := Vector3(side * 3.3, 0, 3.4)
		_solid(root, "FuelPump", p + Vector3.UP * 0.95, Vector3(0.9, 1.9, 0.85), Color("b5563f"))
		_box(root, "PumpDisplay", p + Vector3(0, 1.3, 0.44), Vector3(0.68, 0.45, 0.04), Color("d6d8a6"))
		DesertModels.beam(root, "FuelHose", p + Vector3(0.5, 1.6, 0), p + Vector3(0.65, 0.35, 0.25), 0.05, material(Color("303937")))
	_label(root, "PALIVO", Vector3(0, 3.8, 6.48), 0.008)


static func roadside_sign(parent: Node3D, pos: Vector3, title: String, positive_side: bool) -> void:
	var root := Node3D.new()
	root.name = "StopSign"
	root.position = pos
	parent.add_child(root)
	DesertModels.cylinder(root, "SignPost", Vector3.UP * 1.9, 0.09, 3.8, material(Color("6c7066")), 6)
	_box(root, "ServiceSign", Vector3(0, 3.5, 0), Vector3(4.6, 1.2, 0.14), Color("466b65"))
	var label := _label(root, title.to_upper() + (" ↓" if positive_side else " ↑"), Vector3(0, 3.5, 0.10), 0.007)
	label.billboard = BaseMaterial3D.BILLBOARD_DISABLED
	label.pixel_size = minf(label.pixel_size, 4.0 / maxf(title.length() * 27.0, 1.0))
	# Small roadside marker has no collider: it cannot catch a turning vehicle.


static func _box(parent: Node3D, label: String, pos: Vector3, size: Vector3, color: Color) -> MeshInstance3D:
	return DesertModels.box(parent, label, pos, size, material(color))


static func _solid(parent: Node3D, label: String, pos: Vector3, size: Vector3, color: Color) -> void:
	_box(parent, label, pos, size, color)
	DesertModels.collider(parent, pos, size)


static func _label(parent: Node3D, text: String, pos: Vector3, pixel_size: float) -> Label3D:
	var label := Label3D.new()
	label.name = "ServiceTitle"
	label.text = text
	label.position = pos
	label.font_size = 48
	label.pixel_size = pixel_size
	label.modulate = Color("f8edcb")
	label.outline_size = 3
	parent.add_child(label)
	return label
