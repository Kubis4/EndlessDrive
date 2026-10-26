class_name RouteHazard
extends Node3D
## Actual crossing train/barriers and collidable bridge/tunnel structures.
var world: DriveWorld
var kind := ""
var feature_x := 0.0
var _train: AnimatableBody3D
var _train_wheels: Array[Node3D] = []
var _gates: Array[Node3D] = []
var _lamps: Array[StandardMaterial3D] = []
var _bell: AudioStreamPlayer3D
var _beep_time := 0.0
var _streamed := false
static var _bell_sound: AudioStreamWAV
static var _rock_material: ShaderMaterial


static func rock_material() -> ShaderMaterial:
	if _rock_material == null:
		_rock_material = ShaderMaterial.new()
		_rock_material.shader = load("res://shaders/rock_surface.gdshader")
		_rock_material.set_shader_parameter("surface_atlas", load("res://assets/textures/surface-albedo-atlas.png"))
		_rock_material.set_shader_parameter("micro_height", load("res://assets/textures/surface-microheight-2k.png"))
	return _rock_material


func build_streamed() -> void:
	_streamed = true
	set_physics_process(false)
	build()
	_step(set_physics_process.bind(true))


func _step(job: Callable) -> void:
	# Bind to this node: queued work automatically becomes invalid if its
	# owning chunk is unloaded, rather than touching a freed structure.
	if _streamed: world._schedule(_run_step.bind(job), self, kind)
	else: job.call()


func _run_step(job: Callable) -> void:
	job.call()


func build() -> void:
	match kind:
		"bridge": _bridge()
		"rail": _rail()
		"tunnel": _tunnel()


func _point(x: float, offset: float, up: float = 0.0) -> Vector3:
	var z := world.center_at(x) + offset
	return Vector3(x, world.height_at(x, z) + up, z)


func _bridge() -> void:
	var stone := BiomeModels.material(Color("777d78"))
	for side: float in [-1.0, 1.0]:
		for i in 14:
			_step(func() -> void:
				var x := feature_x - 28 + i * 4.0
				var a := _point(x, side * 5.0, 1.0)
				var b := _point(x + 4, side * 5.0, 1.0)
				_beam(self, "BridgeRail", a, b, 0.10, DesertModels.mat("steel"))
				_segment_solid("BridgeRailCollision", a, b, 1.2, 0.22)
				_cylinder(self, "BridgePost", a - Vector3.UP * 0.45, 0.12, 1.1, stone, 6))
		for i in 3:
			_step(func() -> void:
				var p := _point(feature_x - 16 + i * 16, side * 4.8)
				_box(self, "RiverPier", p - Vector3.UP * 3, Vector3(2.0, 6.0, 1.4), stone))


func _tunnel() -> void:
	var stone := rock_material()
	var cutaway := StandardMaterial3D.new()
	cutaway.albedo_color = Color(0.40, 0.42, 0.37, 0.18)
	cutaway.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	# The overview's translucent cutaway is a visual guide. Lighting it with
	# every tunnel lamp creates many costly alpha/shadow shader combinations.
	cutaway.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	cutaway.roughness = 1.0
	cutaway.cull_mode = BaseMaterial3D.CULL_DISABLED
	var rings: Array[PackedVector3Array] = []
	for i in 25:
		var x := feature_x - 36 + i * 3.0
		var center := _point(x, 0)
		var tangent := (_point(x + 0.5, 0) - _point(x - 0.5, 0)).normalized()
		var right := Vector3(-tangent.z, 0, tangent.x).normalized()
		var ring := PackedVector3Array([center + right * 6.4 - Vector3.UP])
		for arc in 13:
			var angle := arc * PI / 12.0
			ring.append(center + right * (cos(angle) * 6.4) + Vector3.UP * (2.4 + sin(angle) * 4.6))
		ring.append(center - right * 6.4 - Vector3.UP)
		rings.append(ring)
		# Authored masonry follows the same swept road frame as the collision.
		if i % 4 == 0:
			_step(func() -> void:
				var portal := i == 0 or i == 24
				var model := preload("res://scripts/expedition_scenery.gd").instance("tunnel_portal" if portal else "tunnel_rib")
				model.name = "TunnelEntrance" if i == 0 else ("TunnelExit" if i == 24 else "TunnelArchRib")
				model.transform = Transform3D(preload("res://scripts/expedition_scenery.gd").road_basis(tangent), center)
				add_child(model))
		if i % 8 == 4:
			_step(func() -> void:
				var light := OmniLight3D.new()
				light.position = center + Vector3.UP * 3.8
				light.light_color = Color("f1c685")
				light.light_energy = 1.4
				light.omni_range = 14
				add_child(light))
	var opaque := PackedVector3Array()
	var near_side := PackedVector3Array()
	for i in range(rings.size() - 1):
		for arc in range(rings[i].size() - 1):
			var faces := PackedVector3Array([rings[i][arc], rings[i + 1][arc], rings[i][arc + 1], rings[i + 1][arc], rings[i + 1][arc + 1], rings[i][arc + 1]])
			if arc < 7: near_side.append_array(faces)
			else: opaque.append_array(faces)
	_step(_shell.bind("TunnelFarArch", opaque, stone))
	_step(_shell.bind("TunnelCutawayArch", near_side, cutaway))
	var all_faces := opaque.duplicate()
	all_faces.append_array(near_side)
	_step(func() -> void:
		var body := StaticBody3D.new()
		body.name = "TunnelShellCollision"
		add_child(body)
		var shape := ConcavePolygonShape3D.new()
		shape.set_faces(all_faces)
		shape.backface_collision = true
		var collision := CollisionShape3D.new()
		collision.shape = shape
		body.add_child(collision))
	# Seal the shoulders into the boundary escarpments for the full tunnel
	# length. These are actual mountain masses, with collision, not fences.
	for side: float in [-1.0, 1.0]: _step(_tunnel_mountain.bind(side, stone))


func _rail() -> void:
	var center := _rail_center()
	_step(_rail_bed.bind(center))
	for side: float in [-1.0, 1.0]:
		_step(_rail_portal.bind(center, side))
	for side: float in [-0.85, 0.85]:
		_beam(self, "Rail", center + Vector3(side, 0.08, -150), center + Vector3(side, 0.08, 150), 0.04, DesertModels.mat("steel"))
	for i in range(-50, 51):
		_step(func() -> void:
			_box(self, "Sleeper", center + Vector3(0, -0.01, i * 3), Vector3(3.1, 0.16, 0.28), DesertModels.mat("wood")))
	_train = AnimatableBody3D.new()
	_train.sync_to_physics = false
	_train.physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_ON
	_train.name = "MovingTrain"
	add_child(_train)
	for i in 4:
		_step(func() -> void:
			var label: String = ["locomotive", "tank_wagon", "freight_wagon", "timber_wagon"][i]
			var model := preload("res://scripts/expedition_scenery.gd").instance(label)
			model.position.z = -i * 8.0
			_train.add_child(model)
			for wheel: Node in model.find_children("TrainWheel*", "MeshInstance3D", true, false):
				_train_wheels.append(wheel as Node3D)
			var shape := CollisionShape3D.new()
			var box := BoxShape3D.new()
			box.size = Vector3(2.8, 3.2, 7.0)
			shape.shape = box
			shape.position = Vector3(0, 1.65, -i * 8.0)
			_train.add_child(shape))
	for side: float in [-1.0, 1.0]:
		var post := _point(feature_x + side * 10.0, side * 6.0)
		# Seal both shoulders all the way into the existing solid map boundaries.
		for shoulder: float in [-1.0, 1.0]:
			_step(func() -> void:
				var a := Vector3(post.x, center.y + 1.1, world.center_at(post.x) + shoulder * 6.1)
				var b := Vector3(post.x, center.y + 1.1, world.center_at(post.x) + shoulder * (world.terrain.boundary_width(post.x) + 25.0))
				_segment_solid("CrossingShoulderFence", a, b, 3.0, 0.28)
				for level: float in [0.55, 1.35]:
					_beam(self, "CrossingFenceRail", a + Vector3.UP * (level - 1.1), b + Vector3.UP * (level - 1.1), 0.07, DesertModels.mat("steel"))
				for distance in range(6, int(world.terrain.boundary_width(post.x)) + 23, 4):
					_cylinder(self, "CrossingFencePost", Vector3(post.x, center.y + 0.8, world.center_at(post.x) + shoulder * distance), 0.09, 2.0, DesertModels.mat("steel"), 6))
		_cylinder(self, "CrossingPost", post + Vector3.UP, 0.14, 2.0, DesertModels.mat("steel"), 6)
		var gate := AnimatableBody3D.new()
		gate.sync_to_physics = false
		gate.physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_ON
		gate.position = post + Vector3.UP * 1.0
		add_child(gate)
		_gates.append(gate)
		_box(gate, "Barrier", Vector3(0, 0, -side * 5), Vector3(0.18, 0.18, 10.0), BiomeModels.material(Color("e9d2ab")))
		var shape := CollisionShape3D.new()
		var bar := BoxShape3D.new()
		bar.size = Vector3(0.18, 0.18, 10.0)
		shape.shape = bar
		shape.position.z = -side * 5.0
		gate.add_child(shape)
		var lamp := StandardMaterial3D.new()
		lamp.albedo_color = Color("b1281c")
		lamp.emission_enabled = true
		lamp.emission = Color.RED
		_lamps.append(lamp)
		_box(self, "WarningLight", post + Vector3.UP * 2, Vector3(0.4, 0.4, 0.2), lamp)
	_step(_finish_rail)


func _rail_center() -> Vector3:
	return Vector3(feature_x, world.terrain.base_height(feature_x), world.center_at(feature_x))


func _rail_bed(center: Vector3) -> void:
	var faces := PackedVector3Array()
	var section := [Vector2(-2.4, -0.13), Vector2(-1.85, -0.025), Vector2(1.85, -0.025), Vector2(2.4, -0.13)]
	for edge in 3:
		var a := center + Vector3(section[edge].x, section[edge].y, -150)
		var b := center + Vector3(section[edge + 1].x, section[edge + 1].y, -150)
		var c := a + Vector3(0, 0, 300)
		var d := b + Vector3(0, 0, 300)
		faces.append_array(PackedVector3Array([a, b, c, b, d, c]))
	_shell("RailBallast", faces, rock_material())
	var rubber := BiomeModels.material(Color("39413f"))
	# Flush rubber infill carries the road between rails, leaving a narrow
	# visible steel/flange gap. Sleepers sit in ballast rather than in the road.
	for index in 10:
		var offset := -2.25 + index * 0.5
		if absf(absf(offset) - 0.85) < 0.18: continue
		var x := feature_x + offset
		var frame := world.terrain.road_frame(x, world.center_at(x))
		var width := 10.0 * sqrt(1.0 + float(frame.slope) * float(frame.slope))
		_box(self, "CrossingPanel", Vector3(x, center.y + 0.045, world.center_at(x)), Vector3(0.46, 0.05, width), rubber)


func _finish_rail() -> void:
	var center := _point(feature_x, 0)
	_bell = AudioStreamPlayer3D.new()
	if _bell_sound == null:
		_bell_sound = AudioStreamWAV.new()
		_bell_sound.format = AudioStreamWAV.FORMAT_16_BITS
		_bell_sound.mix_rate = 22050
		var bytes := PackedByteArray()
		bytes.resize(4410)
		for i in 2205: bytes.encode_s16(i * 2, int(sin(i * TAU * 880 / 22050.0) * 3500.0 * (1.0 - i / 2205.0)))
		_bell_sound.data = bytes
	_bell.stream = _bell_sound
	_bell.position = center
	_bell.max_distance = 95
	add_child(_bell)
	_physics_process(0.0)
	_train.reset_physics_interpolation()


func _tunnel_mountain(side: float, stone: Material) -> void:
	var rings: Array[PackedVector3Array] = []
	for step in 25:
		var x := feature_x - 36 + step * 3.0
		var center := _point(x, 0)
		var width := world.terrain.boundary_width(x)
		var ring := PackedVector3Array([center + Vector3(0, -5, side * 6.45)])
		var lateral := [6.45, 12.0, 22.0, 31.0, width + 10.0]
		var heights := [6.5, 10.5, 14.0, 10.0, 6.0]
		for edge in lateral.size():
			var variation := sin(x * 0.17 + edge * 1.7 + side) * (0.35 if edge == 0 else 1.2)
			ring.append(center + Vector3(0, float(heights[edge]) + variation, side * float(lateral[edge])))
		ring.append(center + Vector3(0, -5, side * (width + 10)))
		rings.append(ring)
	var faces := PackedVector3Array()
	for row in rings.size() - 1:
		for edge in rings[row].size() - 1:
			faces.append_array(PackedVector3Array([rings[row][edge], rings[row + 1][edge], rings[row][edge + 1], rings[row + 1][edge], rings[row + 1][edge + 1], rings[row][edge + 1]]))
	for end: int in [0, rings.size() - 1]:
		for edge in range(1, rings[end].size() - 1): faces.append_array(PackedVector3Array([rings[end][0], rings[end][edge], rings[end][edge + 1]]))
	_shell("TunnelMountainFillNorth" if side < 0 else "TunnelMountainFillSouth", faces, stone)
	var body := StaticBody3D.new()
	body.name = "TunnelMountainCollisionNorth" if side < 0 else "TunnelMountainCollisionSouth"
	add_child(body)
	var shape := ConcavePolygonShape3D.new()
	shape.backface_collision = true
	shape.set_faces(faces)
	var collision := CollisionShape3D.new()
	collision.shape = shape
	body.add_child(collision)


func _physics_process(dt: float) -> void:
	if kind != "rail" or _train == null: return
	var phase := fposmod(world.clock + float(posmod(world.world_seed, 20)), 45.0)
	var closed := phase < 16.0
	var center := _rail_center()
	_train.position = center + Vector3(0, 0.24, -180 + phase * 24.0)
	_train.visible = phase < 18
	_train.collision_layer = 1 if phase < 18 else 0
	if phase < 18:
		for wheel: Node3D in _train_wheels: wheel.rotate_object_local(Vector3.UP, -24.0 * dt / 0.43)
	for i in _gates.size():
		_gates[i].rotation.x = (0.0 if closed else PI * 0.5) * (-1.0 if i == 0 else 1.0)
	for lamp: StandardMaterial3D in _lamps: lamp.emission_energy_multiplier = 2.0 if closed and fposmod(phase, 0.7) < 0.35 else 0.0
	_beep_time -= dt
	if closed and _beep_time <= 0:
		if DisplayServer.get_name() != "headless": _bell.play()
		_beep_time = 0.7


func _shell(label: String, faces: PackedVector3Array, material: Material) -> void:
	var tool := SurfaceTool.new()
	tool.begin(Mesh.PRIMITIVE_TRIANGLES)
	for p: Vector3 in faces: tool.add_vertex(p)
	tool.generate_normals()
	var node := MeshInstance3D.new()
	node.name = label
	node.mesh = tool.commit()
	node.material_override = material
	if label == "TunnelCutawayArch": node.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(node)


func _segment_solid(label: String, a: Vector3, b: Vector3, height: float, width: float) -> void:
	var body := StaticBody3D.new()
	body.name = label
	var x_axis := (b - a).normalized()
	var z_axis := x_axis.cross(Vector3.UP).normalized()
	body.transform = Transform3D(Basis(x_axis, z_axis.cross(x_axis), z_axis), (a + b) * 0.5)
	add_child(body)
	var collision := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(a.distance_to(b) + 0.2, height, width)
	collision.shape = box
	body.add_child(collision)


func _filled_span(label: String, a: Vector3, b: Vector3, height: float, width: float, material: Material, visible_mesh := true) -> void:
	_segment_solid(label + "Collision", a, b, height, width)
	if not visible_mesh: return
	var node := Node3D.new()
	node.name = label
	var axis := (b - a).normalized()
	var side := axis.cross(Vector3.UP).normalized()
	node.transform = Transform3D(Basis(axis, side.cross(axis), side), (a + b) * 0.5)
	add_child(node)
	_box(node, "RockMass", Vector3.ZERO, Vector3(a.distance_to(b) + 0.2, height, width), material)


func _rail_portal(center: Vector3, side: float) -> void:
	var label := "RailPortalNorth" if side < 0 else "RailPortalSouth"
	var portal := Node3D.new()
	portal.name = label
	add_child(portal)
	var stone := rock_material()
	var dark := BiomeModels.material(Color("1b201f"))
	dark.cull_mode = BaseMaterial3D.CULL_DISABLED
	var front := center + Vector3(0, 0, side * (world.terrain.boundary_width(feature_x) - 5.0))
	var back := front + Vector3(0, 0, side * 10.0)
	var masonry := preload("res://scripts/expedition_scenery.gd").instance("rail_portal")
	masonry.position = front
	masonry.rotation.y = PI if side > 0 else 0.0
	portal.add_child(masonry)
	var faces := PackedVector3Array()
	var ring := PackedVector3Array([Vector3(2.5, -1, 0)])
	for arc in 13:
		var angle := arc * PI / 12.0
		ring.append(Vector3(cos(angle) * 2.5, 3.2 + sin(angle) * 2.9, 0))
	ring.append(Vector3(-2.5, -1, 0))
	for index in range(ring.size() - 1):
		var a := front + ring[index]
		var b := front + ring[index + 1]
		var depth := back - front
		faces.append_array(PackedVector3Array([a, b, a + depth, b, b + depth, a + depth]))
	_shell(label + "Interior", faces, dark)
	# Wing walls replace the removed cliff tiles on either side of the opening.
	for edge: float in [-1, 1]:
		_filled_span(label + "Wing", front + Vector3(edge * 3.0, 3.0, 0), front + Vector3(edge * 9.0, 3.0, 0), 12, 12, stone, false)
		var hillside := preload("res://scripts/expedition_scenery.gd").instance("mountain_ridge")
		hillside.position = front + Vector3(edge * 6.2, 0, -side * 5.5)
		hillside.scale = Vector3(0.55, 1.0, side * 0.65)
		portal.add_child(hillside)
	_filled_span(label + "Crown", front + Vector3(-3.5, 8.0, 0), front + Vector3(3.5, 8.0, 0), 4, 12, stone, false)
	# Dark depth backdrop prevents the player driving out of the map down the
	# railway; the kinematic train emerges through it from the hidden staging area.
	_filled_span(label + "Depth", back + Vector3(-2.6, 2.6, 0), back + Vector3(2.6, 2.6, 0), 7.4, 0.5, dark)


static var _unit_box: BoxMesh
static var _unit_cylinders: Dictionary = {}

func _box(parent: Node3D, label: String, pos: Vector3, size: Vector3, material: Material) -> MeshInstance3D:
	if _unit_box == null:
		_unit_box = BoxMesh.new()
	var node := DesertModels.piece(parent, label, _unit_box, pos, material)
	node.scale = size
	return node

func _cylinder(parent: Node3D, label: String, pos: Vector3, radius: float, height: float, material: Material, sides: int = 8) -> MeshInstance3D:
	if not _unit_cylinders.has(sides):
		var mesh := CylinderMesh.new()
		mesh.bottom_radius = 1
		mesh.top_radius = 1
		mesh.height = 1
		mesh.radial_segments = sides
		_unit_cylinders[sides] = mesh
	var node := DesertModels.piece(parent, label, _unit_cylinders[sides], pos, material)
	node.scale = Vector3(radius, height, radius)
	return node

func _beam(parent: Node3D, label: String, a: Vector3, b: Vector3, radius: float, material: Material) -> MeshInstance3D:
	var node := _cylinder(parent, label, (a + b) * 0.5, radius, a.distance_to(b), material, 5)
	node.quaternion = Quaternion(Vector3.UP, (b - a).normalized())
	return node
