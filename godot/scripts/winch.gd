extends Node3D
## Anchors belong to streamed static props; all pulling keeps car collisions.
const RANGE := 30.0
const MAX_CABLE := 36.0
var vehicle: DriveVehicle
var world: DriveWorld
var reeling := false
var _anchor: WeakRef
var _cable: MeshInstance3D
var _cable_mesh: CylinderMesh


func _ready() -> void:
	_cable = MeshInstance3D.new()
	_cable.name = "RecoveryCable"
	_cable_mesh = CylinderMesh.new()
	_cable_mesh.top_radius = 0.018
	_cable_mesh.bottom_radius = 0.018
	_cable_mesh.radial_segments = 6
	var steel := StandardMaterial3D.new()
	steel.albedo_color = Color("b4bec4")
	steel.metallic = 0.75
	steel.roughness = 0.45
	_cable_mesh.material = steel
	_cable.mesh = _cable_mesh
	_cable.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_cable.visible = false
	add_child(_cable)
	vehicle.set_meta("winch_state", "idle")


func outlet() -> Vector3:
	var mount := vehicle.get_node_or_null("WinchMount/CableOutlet") as Node3D
	return mount.global_position if mount != null else vehicle.to_global(Vector3(2.4, 0.6, 0))


func anchor_node() -> Node3D:
	if _anchor == null: return null
	var node := _anchor.get_ref() as Node3D
	return node if is_instance_valid(node) and node.is_inside_tree() else null


func _clear_line(target: Node3D) -> bool:
	var query := PhysicsRayQueryParameters3D.create(outlet(), target.global_position, 3, [vehicle.get_rid()])
	var hit := vehicle.get_world_3d().direct_space_state.intersect_ray(query)
	if hit.is_empty(): return true
	var collider := hit.collider as Node
	var owner_node := target.get_parent()
	var prop_owner := owner_node != world and owner_node != vehicle.get_parent() and owner_node != self
	var endpoint_distance := (hit.position as Vector3).distance_to(target.global_position)
	# Only the target's immediate surface may obstruct the ray. Another wall
	# in the same shelter must still block attachment to a post behind it.
	return endpoint_distance < 0.6 or (prop_owner and not owner_node.is_in_group("storm_shelter") and (collider == owner_node or owner_node.is_ancestor_of(collider)) and endpoint_distance < 1.2)


func toggle_attachment() -> String:
	if anchor_node() != null:
		disconnect_cable()
		return "Navijak odpojený."
	if absf(vehicle.speed) > 3.0: return "Pred pripojením navijaka spomaľ."
	var best: Node3D
	var best_score := INF
	var forward := Vector3(cos(vehicle.heading), 0, sin(vehicle.heading))
	for candidate: Node in get_tree().get_nodes_in_group("winch_anchor"):
		if not candidate is Node3D or not candidate.is_inside_tree(): continue
		var delta := (candidate as Node3D).global_position - outlet()
		var distance := delta.length()
		if distance < 2.0 or distance > RANGE or absf(delta.y) > 8.0: continue
		var alignment := delta.normalized().dot(forward)
		if alignment < -0.25: continue
		var score := distance * (1.4 - alignment * 0.4)
		if score < best_score and _clear_line(candidate):
			best = candidate
			best_score = score
	if best == null: return "V dosahu 30 m nie je voľný kotviaci bod."
	_anchor = weakref(best)
	vehicle.cancel_cruise()
	vehicle.set_meta("winch_state", "attached")
	return "Navijak pripojený. H: pritiahnuť."


func toggle_reel() -> String:
	if anchor_node() == null: return "Najprv pripoj navijak klávesom G."
	if vehicle.run.battery < 0.05: return "Navijak potrebuje nabitú batériu."
	reeling = not reeling
	vehicle.set_meta("winch_state", "reeling" if reeling else "attached")
	return "Navijak priťahuje." if reeling else "Navijak zastavený."


func disconnect_cable() -> void:
	_anchor = null
	reeling = false
	vehicle.winch_pull_velocity = Vector3.ZERO
	vehicle.set_meta("winch_state", "idle")
	if _cable != null: _cable.visible = false


func stop_reeling() -> void:
	reeling = false
	vehicle.winch_pull_velocity = Vector3.ZERO
	vehicle.set_meta("winch_state", "attached" if anchor_node() != null else "idle")


func _physics_process(dt: float) -> void:
	vehicle.winch_pull_velocity = Vector3.ZERO
	var anchor := anchor_node()
	if anchor == null:
		disconnect_cable()
		return
	var delta := anchor.global_position - outlet()
	var distance := delta.length()
	if distance > MAX_CABLE or distance < 1.6 or not _clear_line(anchor):
		disconnect_cable()
		return
	if not vehicle.controls_enabled or vehicle.run.battery <= 0.035 or absf(vehicle.speed) > 4.0:
		reeling = false
	vehicle.set_meta("winch_state", "reeling" if reeling else "attached")
	if reeling:
		var direction := delta.normalized()
		vehicle.winch_pull_velocity = Vector3(direction.x, clampf(direction.y, -0.15, 0.2), direction.z) * minf(2.0, distance - 1.5)
		vehicle.run.battery = maxf(0.0, vehicle.run.battery - dt * 0.004)
	_cable.visible = true
	_cable_mesh.height = distance
	_cable.global_position = outlet() + delta * 0.5
	var y_axis := delta.normalized()
	var x_axis := y_axis.cross(Vector3.FORWARD).normalized()
	if x_axis.length_squared() < 0.1: x_axis = Vector3.RIGHT
	_cable.global_basis = Basis(x_axis, y_axis, x_axis.cross(y_axis).normalized())
