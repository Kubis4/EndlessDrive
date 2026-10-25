extends Node
## One shared depth-tested x-ray rim material over the actual model. Copies
## follow existing body/wheel transforms; no viewport, raycast loop or rebuild.
var copies: Array[MeshInstance3D] = []
var _vehicle: DriveVehicle
var _timer := 0.0
var occluded := false
func _ready() -> void: call_deferred("_prepare")
func _prepare() -> void:
	var vehicle := get_parent() as DriveVehicle
	if vehicle == null or vehicle._visual == null: return
	_vehicle = vehicle
	var material := ShaderMaterial.new()
	material.shader = preload("res://shaders/vehicle_outline.gdshader")
	material.render_priority = 125
	var sources := vehicle._visual.find_children("*", "MeshInstance3D", true, false)
	for source: MeshInstance3D in sources:
		if source.mesh == null: continue
		var rim := MeshInstance3D.new()
		rim.name = "OccludedCarOutline"
		rim.mesh = source.mesh
		rim.material_override = material
		rim.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		rim.visible = false
		source.add_child(rim)
		copies.append(rim)

func _process(dt: float) -> void:
	_timer -= dt
	if _timer > 0.0 or _vehicle == null or copies.is_empty(): return
	_timer = 0.10
	var camera := get_viewport().get_camera_3d()
	if camera == null: return
	var target := _vehicle.global_position + Vector3.UP * 0.7
	var blocked := false
	var space := _vehicle.get_world_3d().direct_space_state
	for offset: Vector3 in [Vector3.ZERO, Vector3(1.6, 0, 0), Vector3(-1.6, 0, 0)]:
		var query := PhysicsRayQueryParameters3D.create(camera.global_position, target + offset)
		query.exclude = [_vehicle.get_rid()]
		if not space.intersect_ray(query).is_empty():
			blocked = true
			break
	# Foliage has small trunk colliders for driving. Its bounded canopy volume
	# activates the rim, then the depth shader picks only truly hidden pixels.
	if not blocked:
		for tree: Node3D in get_tree().get_nodes_in_group("vehicle_occluder"):
			var delta := tree.global_position - target
			if delta.x * delta.x + delta.z * delta.z > 576.0: continue
			var origin := tree.to_local(camera.global_position)
			var end := tree.to_local(target)
			if AABB(Vector3(-4.5, 3, -4.5), Vector3(9, 10, 9)).intersects_segment(origin, end) != null:
				blocked = true
				break
	if blocked == occluded: return
	occluded = blocked
	for copy in copies: copy.visible = blocked
