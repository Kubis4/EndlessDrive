extends RefCounted
## Reuse the game's native meshes/materials; adapt +X-forward rigs to -Z-forward.
const Catalog = preload("res://scripts/vehicle_catalog.gd")

static func vehicle(parent: Node3D, id: String, tint: Color) -> void:
	var rig := (load(Catalog.DATA[id].scene) as PackedScene).instantiate() as Node3D
	# Save the adapted hierarchy as native nodes, retaining shared mesh resources.
	# Keeping the source instance would resurrect its original wheel hierarchy.
	rig.scene_file_path = ""
	rig.name = "EndlessDrive_" + id
	rig.rotation.y = PI * 0.5
	parent.add_child(rig)
	var body := rig.get_node("Body") as MeshInstance3D
	var paint := body.material_override.duplicate() as ShaderMaterial
	paint.set_shader_parameter("tint", tint)
	body.material_override = paint
	var wheels := Node3D.new()
	wheels.name = "Wheels"
	parent.add_child(wheels)
	for wheel_name in ["Wheel_FL", "Wheel_RL", "Wheel_FR", "Wheel_RR"]:
		var original := rig.get_node(wheel_name) as Node3D
		original.owner = null
		for child in original.find_children("*", "", true, false): child.owner = null
		var hub := Node3D.new()
		hub.name = wheel_name
		hub.position = rig.basis * original.position
		hub.set_meta("front", wheel_name in ["Wheel_FL", "Wheel_FR"])
		wheels.add_child(hub)
		var spin := Node3D.new()
		spin.name = "Spin"
		hub.add_child(spin)
		rig.remove_child(original)
		spin.add_child(original)
		original.position = Vector3.ZERO
		original.basis = rig.basis * original.basis
	parent.set_meta("endless_drive_model", id)

static func environment(parent: Node3D, path: String, title: String) -> Node3D:
	var instance := (load(path) as PackedScene).instantiate() as Node3D
	instance.scene_file_path = ""
	instance.name = title
	parent.add_child(instance)
	return instance
