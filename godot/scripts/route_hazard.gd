class_name RouteHazard
extends Node3D
## Actual crossing train/barriers and collidable bridge/tunnel structures.
var world: DriveWorld
var kind := ""
var feature_x := 0.0
var _train: AnimatableBody3D
var _gates: Array[Node3D] = []
var _lamps: Array[StandardMaterial3D] = []
var _bell: AudioStreamPlayer3D
var _beep_time := 0.0


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
			var x := feature_x - 28 + i * 4.0
			var a := _point(x, side * 5.0, 1.0)
			var b := _point(x + 4, side * 5.0, 1.0)
			DesertModels.beam(self, "BridgeRail", a, b, 0.10, DesertModels.mat("steel"))
			_segment_solid("BridgeRailCollision", a, b, 1.2, 0.22)
			DesertModels.cylinder(self, "BridgePost", a - Vector3.UP * 0.45, 0.12, 1.1, stone, 6)
		for i in 3:
			var p := _point(feature_x - 16 + i * 16, side * 4.8)
			DesertModels.box(self, "RiverPier", p - Vector3.UP * 3, Vector3(2.0, 6.0, 1.4), stone)


func _tunnel() -> void:
	var stone := BiomeModels.material(Color("77786c"))
	var cutaway := StandardMaterial3D.new()
	cutaway.albedo_color = Color(0.40, 0.42, 0.37, 0.18)
	cutaway.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
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
		# Visible ribs at both portals and spaced along the swept curve.
		if i % 4 == 0:
			for arc in range(ring.size() - 1):
				DesertModels.beam(self, "TunnelArchRib", ring[arc], ring[arc + 1], 0.12, stone)
		if i % 8 == 4:
			var light := OmniLight3D.new()
			light.position = center + Vector3.UP * 3.8
			light.light_color = Color("f1c685")
			light.light_energy = 1.4
			light.omni_range = 14
			add_child(light)
	var opaque := PackedVector3Array()
	var near_side := PackedVector3Array()
	for i in range(rings.size() - 1):
		for arc in range(rings[i].size() - 1):
			var faces := PackedVector3Array([rings[i][arc], rings[i + 1][arc], rings[i][arc + 1], rings[i + 1][arc], rings[i + 1][arc + 1], rings[i][arc + 1]])
			if arc < 7: near_side.append_array(faces)
			else: opaque.append_array(faces)
	_shell("TunnelFarArch", opaque, stone)
	_shell("TunnelCutawayArch", near_side, cutaway)
	var body := StaticBody3D.new()
	body.name = "TunnelShellCollision"
	add_child(body)
	var shape := ConcavePolygonShape3D.new()
	opaque.append_array(near_side)
	shape.set_faces(opaque)
	shape.backface_collision = true
	var collision := CollisionShape3D.new()
	collision.shape = shape
	body.add_child(collision)


func _rail() -> void:
	var center := _point(feature_x, 0)
	for side: float in [-0.85, 0.85]:
		DesertModels.beam(self, "Rail", center + Vector3(side, 0.05, -150), center + Vector3(side, 0.05, 150), 0.06, DesertModels.mat("steel"))
	for i in range(-50, 51):
		DesertModels.box(self, "Sleeper", center + Vector3(0, -0.06, i * 3), Vector3(3.1, 0.12, 0.28), DesertModels.mat("wood"))
	_train = AnimatableBody3D.new()
	_train.sync_to_physics = false
	_train.physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_ON
	_train.name = "MovingTrain"
	add_child(_train)
	for i in 4:
		var p := Vector3(0, 1.5, -i * 8.0)
		DesertModels.box(_train, "Locomotive" if i == 0 else "Wagon", p, Vector3(2.8, 2.8, 7.0), BiomeModels.material(Color("765a43") if i > 0 else Color("395e5a")))
		var shape := CollisionShape3D.new()
		var box := BoxShape3D.new()
		box.size = Vector3(2.8, 2.8, 7.0)
		shape.shape = box
		shape.position = p
		_train.add_child(shape)
		for z: float in [-2.4, 2.4]:
			for side: float in [-1.3, 1.3]:
				var wheel := DesertModels.cylinder(_train, "TrainWheel", Vector3(side, 0.3, p.z + z), 0.42, 0.16, DesertModels.mat("rubber"), 8)
				wheel.rotation.z = PI * 0.5
	for side: float in [-1.0, 1.0]:
		var post := _point(feature_x + side * 10.0, side * 6.0)
		# Seal both shoulders all the way into the existing solid map boundaries.
		for shoulder: float in [-1.0, 1.0]:
			var a := Vector3(post.x, center.y + 1.1, world.center_at(post.x) + shoulder * 6.1)
			var b := Vector3(post.x, center.y + 1.1, world.center_at(post.x) + shoulder * (world.terrain.boundary_width(post.x) + 25.0))
			_segment_solid("CrossingShoulderFence", a, b, 3.0, 0.28)
			for level: float in [0.55, 1.35]:
				DesertModels.beam(self, "CrossingFenceRail", a + Vector3.UP * (level - 1.1), b + Vector3.UP * (level - 1.1), 0.07, DesertModels.mat("steel"))
			for distance in range(6, int(world.terrain.boundary_width(post.x)) + 23, 4):
				DesertModels.cylinder(self, "CrossingFencePost", Vector3(post.x, center.y + 0.8, world.center_at(post.x) + shoulder * distance), 0.09, 2.0, DesertModels.mat("steel"), 6)
		DesertModels.cylinder(self, "CrossingPost", post + Vector3.UP, 0.14, 2.0, DesertModels.mat("steel"), 6)
		var gate := AnimatableBody3D.new()
		gate.sync_to_physics = false
		gate.physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_ON
		gate.position = post + Vector3.UP * 1.0
		add_child(gate)
		_gates.append(gate)
		DesertModels.box(gate, "Barrier", Vector3(0, 0, -side * 5), Vector3(0.18, 0.18, 10.0), BiomeModels.material(Color("e9d2ab")))
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
		DesertModels.box(self, "WarningLight", post + Vector3.UP * 2, Vector3(0.4, 0.4, 0.2), lamp)
	_bell = AudioStreamPlayer3D.new()
	var sound := AudioStreamWAV.new()
	sound.format = AudioStreamWAV.FORMAT_16_BITS
	sound.mix_rate = 22050
	var bytes := PackedByteArray()
	bytes.resize(4410)
	for i in 2205: bytes.encode_s16(i * 2, int(sin(i * TAU * 880 / 22050.0) * 3500.0 * (1.0 - i / 2205.0)))
	sound.data = bytes
	_bell.stream = sound
	_bell.position = center
	_bell.max_distance = 95
	add_child(_bell)
	_physics_process(0.0)
	_train.reset_physics_interpolation()


func _physics_process(dt: float) -> void:
	if kind != "rail" or _train == null: return
	var phase := fposmod(world.clock + float(posmod(world.world_seed, 20)), 45.0)
	var closed := phase < 16.0
	var center := _point(feature_x, 0)
	_train.position = center + Vector3(0, 0, -180 + phase * 24.0)
	_train.visible = phase < 18
	_train.collision_layer = 1 if phase < 18 else 0
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
