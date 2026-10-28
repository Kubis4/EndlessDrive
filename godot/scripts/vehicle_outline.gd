extends Node
## One shared depth-tested x-ray rim material over the actual model. Copies
## follow existing body/wheel transforms; no extra viewport or mesh rebuild.
var copies: Array[MeshInstance3D] = []
var _vehicle: DriveVehicle
var _timer := 0.0
var occluded := false
var _material: ShaderMaterial
var _sources: Array[MeshInstance3D] = []
func _ready() -> void: call_deferred("_prepare")
func _prepare() -> void:
	if not copies.is_empty(): return
	var vehicle := get_parent() as DriveVehicle
	if vehicle == null or vehicle._visual == null: return
	_vehicle = vehicle
	_material = ShaderMaterial.new()
	_material.shader = preload("res://shaders/vehicle_outline.gdshader")
	_material.render_priority = 125
	var sources := vehicle._visual.find_children("*", "MeshInstance3D", true, false)
	for source: MeshInstance3D in sources:
		if source.mesh == null: continue
		var rim := MeshInstance3D.new()
		rim.name = "OccludedCarOutline"
		rim.mesh = source.mesh
		rim.material_override = _material
		rim.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		rim.visible = false
		source.add_child(rim)
		copies.append(rim)
		_sources.append(source)

func _process(dt: float) -> void:
	if _vehicle == null or copies.is_empty(): return
	var camera := get_viewport().get_camera_3d()
	if camera == null:
		_set_occluded(false)
		return
	# Camera follow and steering move between the slower occlusion probes.
	# Keep the self-depth rejection plane current on every visible-outline frame.
	if occluded: _update_near_depth(camera)
	_timer -= dt
	if _timer > 0.0: return
	_timer = 0.10
	var target := _vehicle.global_position + Vector3.UP * 0.7
	var blocked := false
	var space := _vehicle.get_world_3d().direct_space_state
	for offset: Vector3 in [Vector3.ZERO, Vector3(1.6, 0, 0), Vector3(-1.6, 0, 0)]:
		if _ray_blocked(space, camera.global_position, target + offset):
			blocked = true
			break
	# Foliage has small trunk colliders for driving. Its bounded canopy volume
	# activates the rim, then the depth shader picks only truly hidden pixels.
	if not blocked:
		var reach := Vector2(camera.global_position.x - target.x, camera.global_position.z - target.z).length()
		for tree: Node3D in get_tree().get_nodes_in_group("vehicle_occluder"):
			if not tree.is_inside_tree() or not tree.is_visible_in_tree() or tree.get_world_3d() != _vehicle.get_world_3d(): continue
			var bounds: AABB = tree.get_meta("occlusion_bounds", AABB())
			if not bounds.has_volume(): continue
			var delta := tree.global_position - target
			var radius := maxf(24.0, reach) + bounds.size.length() * tree.global_basis.get_scale().abs().length()
			if delta.x * delta.x + delta.z * delta.z > radius * radius: continue
			var origin := tree.to_local(camera.global_position)
			var end := tree.to_local(target)
			if bounds.intersects_segment(origin, end) != null:
				blocked = true
				break
	_set_occluded(blocked)
	if blocked: _update_near_depth(camera)

func _ray_blocked(space: PhysicsDirectSpaceState3D, origin: Vector3, target: Vector3) -> bool:
	var query := PhysicsRayQueryParameters3D.create(origin, target)
	var excluded: Array[RID] = [_vehicle.get_rid()]
	# Invisible safety walls can be much taller than their visible scenery.
	# Skip them and continue to the actual tunnel, terrain or building behind.
	for step in 8:
		query.exclude = excluded
		var hit := space.intersect_ray(query)
		if hit.is_empty(): return false
		var body := hit.collider as Node3D
		if body != null and (body.get_meta("outline_ignore", false) or body.name == "MapBoundary" or not body.is_visible_in_tree() or _vehicle.is_ancestor_of(body)):
			excluded.append(hit.rid)
			continue
		return true
	return false

func _set_occluded(value: bool) -> void:
	if value == occluded: return
	occluded = value
	for copy in copies: copy.visible = value

func _update_near_depth(camera: Camera3D) -> void:
	var near_depth := INF
	var view := camera.global_transform.affine_inverse()
	for source: MeshInstance3D in _sources:
		if not is_instance_valid(source) or not source.is_visible_in_tree(): continue
		var bounds := source.mesh.get_aabb()
		var transform := view * source.global_transform
		for corner in 8: near_depth = minf(near_depth, -(transform * bounds.get_endpoint(corner)).z)
	_material.set_shader_parameter("vehicle_near_depth", near_depth if is_finite(near_depth) else 0.0)
	var viewport_size := Vector2(get_viewport().get_visible_rect().size)
	if viewport_size.x <= 0 or viewport_size.y <= 0: return
	for index in 3:
		var local: Vector3 = [Vector3(0, 1.3, 0), Vector3(1.45, 1.0, 0), Vector3(-1.35, 1.2, 0)][index]
		var point := _vehicle.to_global(local)
		var uv := Vector2(-1, -1) if camera.is_position_behind(point) else camera.unproject_position(point) / viewport_size
		_material.set_shader_parameter(["body_probe_center", "body_probe_front", "body_probe_rear"][index], uv)
