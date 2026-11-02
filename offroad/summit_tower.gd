extends Node3D
## The transmitter on the summit, the end of the journey: a red and white lattice mast
## with its dishes, a hut at its foot and a beacon that blinks, seen from far below.
const HEIGHT := 46.0
const SECTIONS := 12
const FOOT := 3.4
const TIP := 0.55
const BLINK := 1.6
var lamp: StandardMaterial3D
var glow: OmniLight3D
var clock := 0.0

func _init() -> void:
	name = "Transmitter"
	_build_mast()
	_build_hut()
	_build_beacon()
	_build_collision()

func _process(delta: float) -> void:
	clock = fmod(clock + delta, BLINK)
	var on := clock < BLINK * 0.4
	lamp.emission_energy_multiplier = 7.0 if on else 0.3
	glow.light_energy = 2.5 if on else 0.0

func _half_at(y: float) -> float:
	return lerpf(FOOT, TIP, y / HEIGHT)

func _build_mast() -> void:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var section_height := HEIGHT / SECTIONS
	var red := Color(0.72, 0.13, 0.09)
	var white := Color(0.86, 0.85, 0.82)
	for s in SECTIONS:
		var low := s * section_height
		var high := low + section_height
		st.set_color(red if s % 2 == 0 else white)
		var a := _half_at(low)
		var b := _half_at(high)
		var corners := [Vector2(1, 1), Vector2(-1, 1), Vector2(-1, -1), Vector2(1, -1)]
		for c in 4:
			var one: Vector2 = corners[c]
			var two: Vector2 = corners[(c + 1) % 4]
			var foot := Vector3(one.x * a, low, one.y * a)
			var head := Vector3(one.x * b, high, one.y * b)
			var foot_next := Vector3(two.x * a, low, two.y * a)
			var head_next := Vector3(two.x * b, high, two.y * b)
			_beam(st, foot, head, 0.11)
			_beam(st, head, head_next, 0.05)
			_beam(st, foot, head_next, 0.04)
			_beam(st, foot_next, head, 0.04)
	# Dishes and the aerial on top.
	st.set_color(white)
	_beam(st, Vector3(0, HEIGHT - 0.5, 0), Vector3(0, HEIGHT + 6.0, 0), 0.05)
	var mesh := st.commit()
	var mast := MeshInstance3D.new()
	mast.mesh = mesh
	var material := StandardMaterial3D.new()
	material.vertex_color_use_as_albedo = true
	material.roughness = 0.7
	mast.material_override = material
	add_child(mast)
	for dish: Array in [[30.0, 0.5], [36.0, 2.6], [24.0, 4.4]]:
		var bowl := MeshInstance3D.new()
		var shape := CylinderMesh.new()
		shape.top_radius = 1.1
		shape.bottom_radius = 0.35
		shape.height = 0.45
		shape.radial_segments = 16
		bowl.mesh = shape
		var out := Vector3(cos(dish[1]), 0.0, sin(dish[1]))
		bowl.position = Vector3(0.0, dish[0], 0.0) + out * (_half_at(dish[0]) + 0.5)
		# (Its top, the wide side, looks out.)
		bowl.basis = Basis(out.cross(Vector3.UP).normalized(), -PI * 0.5)
		var paint := StandardMaterial3D.new()
		paint.albedo_color = Color(0.82, 0.82, 0.8)
		bowl.material_override = paint
		add_child(bowl)

## A square bar from a to b.
func _beam(st: SurfaceTool, a: Vector3, b: Vector3, half: float) -> void:
	var along := (b - a).normalized()
	var one := along.cross(Vector3.FORWARD if absf(along.y) > 0.9 else Vector3.UP).normalized() * half
	var two := along.cross(one).normalized() * half
	var rim := [one + two, one - two, -one - two, -one + two]
	for i in 4:
		var p: Vector3 = rim[i]
		var q: Vector3 = rim[(i + 1) % 4]
		st.set_normal((p + q).normalized())
		for corner: Vector3 in [a + p, b + p, a + q, a + q, b + p, b + q]:
			st.add_vertex(corner)

func _build_hut() -> void:
	var hut := MeshInstance3D.new()
	var shape := BoxMesh.new()
	shape.size = Vector3(5.0, 2.8, 3.6)
	hut.mesh = shape
	hut.position = Vector3(8.5, 1.4, 0.0)
	var wall := StandardMaterial3D.new()
	wall.albedo_color = Color(0.56, 0.55, 0.5)
	wall.roughness = 0.95
	hut.material_override = wall
	add_child(hut)
	var roof := MeshInstance3D.new()
	var slab := BoxMesh.new()
	slab.size = Vector3(5.4, 0.2, 4.0)
	roof.mesh = slab
	roof.position = Vector3(8.5, 2.9, 0.0)
	var dark := StandardMaterial3D.new()
	dark.albedo_color = Color(0.2, 0.2, 0.2)
	roof.material_override = dark
	add_child(roof)

func _build_beacon() -> void:
	lamp = StandardMaterial3D.new()
	lamp.albedo_color = Color(0.9, 0.08, 0.05)
	lamp.emission_enabled = true
	lamp.emission = Color(1.0, 0.1, 0.06)
	var bulb := MeshInstance3D.new()
	var sphere := SphereMesh.new()
	sphere.radius = 0.5
	sphere.height = 1.0
	sphere.radial_segments = 10
	sphere.rings = 5
	bulb.mesh = sphere
	bulb.position.y = HEIGHT + 6.4
	bulb.material_override = lamp
	bulb.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(bulb)
	glow = OmniLight3D.new()
	glow.light_color = Color(1.0, 0.15, 0.1)
	glow.omni_range = 18.0
	glow.position.y = HEIGHT + 6.4
	add_child(glow)

func _build_collision() -> void:
	var body := StaticBody3D.new()
	for part: Array in [[Vector3(FOOT * 2.0 + 0.6, 5.0, FOOT * 2.0 + 0.6), Vector3(0.0, 2.5, 0.0)], [Vector3(5.0, 3.0, 3.6), Vector3(8.5, 1.5, 0.0)]]:
		var shape := BoxShape3D.new()
		shape.size = part[0]
		var collision := CollisionShape3D.new()
		collision.shape = shape
		collision.position = part[1]
		body.add_child(collision)
	add_child(body)
