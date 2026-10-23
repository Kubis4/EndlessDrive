class_name DriveEffects
extends Node3D
## Bounded sprite particles and tyre detail; DriveWorld owns physical rutting.
var vehicle: DriveVehicle
var world: DriveWorld
var _emitters: Array[CPUParticles3D] = []
var _tracks: MultiMeshInstance3D
var _cursor := 0
var _count := 0
var _last := Vector3.INF


func _ready() -> void:
	physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_OFF
	var image := Image.new()
	image.load_svg_from_string('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><defs><radialGradient id="g"><stop stop-color="white" stop-opacity=".8"/><stop offset="1" stop-color="white" stop-opacity="0"/></radialGradient></defs><circle cx="16" cy="16" r="16" fill="url(#g)"/></svg>')
	var material := StandardMaterial3D.new()
	material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	material.vertex_color_use_as_albedo = true
	material.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	material.albedo_texture = ImageTexture.create_from_image(image)
	var quad := QuadMesh.new()
	quad.size = Vector2.ONE
	quad.material = material
	for side: float in [-DriveVehicle.WHEEL_TRACK * 0.5, DriveVehicle.WHEEL_TRACK * 0.5]:
		var particles := CPUParticles3D.new()
		particles.mesh = quad
		particles.amount = 24
		particles.lifetime = 0.7
		particles.local_coords = false
		particles.direction = Vector3(-1, 0.3, 0)
		particles.spread = 28
		particles.gravity = Vector3(0, 0.8, 0)
		particles.initial_velocity_min = 1.0
		particles.initial_velocity_max = 3.0
		particles.scale_amount_min = 0.15
		particles.scale_amount_max = 0.70
		particles.position = Vector3(-DriveVehicle.WHEELBASE * 0.5, -DriveVehicle.WHEEL_RADIUS * 0.60, side)
		particles.emitting = false
		vehicle.add_child(particles)
		_emitters.append(particles)
	_tracks = MultiMeshInstance3D.new()
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.use_colors = true
	var mark := BoxMesh.new()
	mark.size = Vector3(0.85, 0.012, 0.19)
	var track_material := StandardMaterial3D.new()
	track_material.vertex_color_use_as_albedo = true
	track_material.roughness = 1.0
	mark.material = track_material
	mm.mesh = mark
	mm.instance_count = 640
	mm.visible_instance_count = 0
	_tracks.multimesh = mm
	_tracks.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(_tracks)


func _process(_dt: float) -> void:
	if vehicle == null or vehicle.surface_info.is_empty(): return
	var sample := vehicle.surface_info
	var water := float(sample.get("water", 0.0))
	var snow := float(sample.get("snow", 0.0))
	var loose := float(sample.get("paved", 0.0)) < 0.5
	var active := vehicle.is_on_floor() and (absf(vehicle.speed) > 2.5 or vehicle.soil_slip > 0.20) and (loose or water > 0.02)
	var color := Color("aa916e")
	if snow > 0.5: color = Color("b9c9ce")
	if water > 0.02: color = Color("8db2b5")
	for emitter: CPUParticles3D in _emitters:
		emitter.emitting = active
		emitter.color = Color(color, 0.36)
		emitter.gravity = Vector3(0, -5 if water > 0.02 else 0.8, 0)
	if not active or _last.distance_to(vehicle.position) < 0.75: return
	_last = vehicle.position
	var forward := Vector3(cos(vehicle.heading), 0, sin(vehicle.heading))
	var right := Vector3(-forward.z, 0, forward.x)
	for side: float in [-DriveVehicle.WHEEL_TRACK * 0.5, DriveVehicle.WHEEL_TRACK * 0.5]:
		var p := vehicle.position - forward * (DriveVehicle.WHEELBASE * 0.5) + right * side
		p.y = world.height_at(p.x, p.z) + 0.025
		var ahead := world.height_at(p.x + forward.x, p.z + forward.z)
		var back := world.height_at(p.x - forward.x, p.z - forward.z)
		var basis := Basis(Vector3.UP, -vehicle.heading) * Basis(Vector3.FORWARD, atan((ahead - back) * 0.5))
		_tracks.multimesh.set_instance_transform(_cursor, Transform3D(basis, p))
		_tracks.multimesh.set_instance_color(_cursor, Color("9faeb5") if snow > 0.5 else Color("70644f"))
		_cursor = (_cursor + 1) % 640
		_count = mini(_count + 1, 640)
	_tracks.multimesh.visible_instance_count = _count
