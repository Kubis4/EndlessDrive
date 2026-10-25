@tool
extends Node3D
## Native, saved preview using the same meshes/materials as gameplay. Shared
## textures/shaders hot-reload; the inspector button refreshes generated geometry.
## F5 removes this snapshot before physics; gameplay uses DriveWorld instead.
@export var preview_seed := 1000003
@export_range(0.0, 200000.0, 1.0) var preview_distance := 0.0
@export_file("*.scn") var geometry_path := ""
@export_tool_button("Obnoviť terén zo skriptov", "Reload") var refresh_preview: Callable = _refresh_preview
var _builder_pid := -1
var _previous_modified := 0

func _refresh_preview() -> void:
	if not Engine.is_editor_hint() or geometry_path.is_empty() or _builder_pid > 0: return
	_previous_modified = FileAccess.get_modified_time(geometry_path)
	# A separate headless process keeps terrain baking out of the editor UI.
	_builder_pid = OS.create_process(OS.get_executable_path(), PackedStringArray([
		"--headless", "--path", ProjectSettings.globalize_path("res://"),
		"--script", "res://tests/refresh_editor_preview.gd", "--",
		"--seed=" + str(preview_seed), "--distance=" + str(preview_distance),
		"--output=" + geometry_path]), false)
	set_process(_builder_pid > 0)

func _process(_dt: float) -> void:
	if not Engine.is_editor_hint() or _builder_pid <= 0: return
	if OS.is_process_running(_builder_pid): return
	_builder_pid = -1
	set_process(false)
	if FileAccess.get_modified_time(geometry_path) <= _previous_modified:
		push_error("Generovanie náhľadu zlyhalo. Skontroluj Output; pôvodný náhľad zostal zachovaný.")
		return
	var packed := ResourceLoader.load(geometry_path, "PackedScene", ResourceLoader.CACHE_MODE_REPLACE) as PackedScene
	if packed == null:
		push_error("Náhľad sa nepodarilo obnoviť: " + geometry_path)
		return
	var previous := get_node_or_null("GeneratedTerrain")
	if previous != null:
		remove_child(previous)
		previous.queue_free()
	var geometry := packed.instantiate()
	add_child(geometry)
	geometry.owner = get_tree().edited_scene_root
	_preview_shadows(geometry)
	var editor := Engine.get_singleton("EditorInterface")
	if editor != null: editor.get_resource_filesystem().scan()

func _ready() -> void:
	if Engine.is_editor_hint():
		set_process(false)
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
