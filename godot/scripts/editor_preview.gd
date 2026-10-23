@tool
extends Node3D
## Static editor-only reference of generated terrain. No @tool rebuilding.
## F5 removes this snapshot before physics; gameplay uses DriveWorld instead.

func _ready() -> void:
	if Engine.is_editor_hint():
		_preview_shadows(self)
		return
	hide()
	_disable_collisions(self)
	queue_free()


func _preview_shadows(node: Node) -> void:
	if node is MeshInstance3D and node.name == "SandAndTracks": node.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	for child: Node in node.get_children(): _preview_shadows(child)


func _disable_collisions(node: Node) -> void:
	if node is CollisionObject3D:
		node.collision_layer = 0
		node.collision_mask = 0
	for child: Node in node.get_children(): _disable_collisions(child)
