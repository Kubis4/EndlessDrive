@tool
extends Marker3D
## A pond: its centre is this marker, its water level the marker's height.
@export_range(4.0, 40.0, 0.5) var radius := 16.0:
	set(value):
		radius = value
		_update_gizmo()

func _ready() -> void:
	_update_gizmo()

func _update_gizmo() -> void:
	if not Engine.is_editor_hint() or not is_inside_tree(): return
	var disc := get_node_or_null("Preview") as MeshInstance3D
	if disc == null:
		disc = MeshInstance3D.new()
		disc.name = "Preview"
		var material := StandardMaterial3D.new()
		material.albedo_color = Color(0.2, 0.45, 0.6, 0.55)
		material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		disc.material_override = material
		add_child(disc)
	var mesh := CylinderMesh.new()
	mesh.top_radius = radius
	mesh.bottom_radius = radius
	mesh.height = 0.05
	disc.mesh = mesh

func to_pond() -> Vector4:
	return Vector4(position.x, position.z, radius, position.y)
