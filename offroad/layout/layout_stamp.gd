@tool
extends Marker3D
## Terrain stamp: raises a smooth hill (height > 0) or digs a hollow (< 0)
## of the given radius around this marker before the trails are levelled.
@export_range(2.0, 120.0, 0.5) var radius := 20.0:
	set(value):
		radius = value
		_update_gizmo()
@export_range(-30.0, 60.0, 0.1) var height := 5.0:
	set(value):
		height = value
		_update_gizmo()

func _ready() -> void:
	_update_gizmo()

func _update_gizmo() -> void:
	if not Engine.is_editor_hint() or not is_inside_tree(): return
	var dome := get_node_or_null("Preview") as MeshInstance3D
	if dome == null:
		dome = MeshInstance3D.new()
		dome.name = "Preview"
		var material := StandardMaterial3D.new()
		material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		dome.material_override = material
		add_child(dome)
	var mesh := SphereMesh.new()
	mesh.radius = radius
	mesh.height = maxf(absf(height), 0.5) * 2.0
	dome.mesh = mesh
	(dome.material_override as StandardMaterial3D).albedo_color = Color(0.9, 0.6, 0.2, 0.3) if height >= 0.0 else Color(0.3, 0.4, 0.9, 0.3)

func to_stamp() -> Vector4:
	return Vector4(position.x, position.z, radius, height)
