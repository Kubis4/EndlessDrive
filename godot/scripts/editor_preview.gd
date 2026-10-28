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
var _watch_elapsed := 0.0

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

func _process(dt: float) -> void:
	if not Engine.is_editor_hint() or geometry_path.is_empty(): return
	if _builder_pid <= 0:
		# A rebuilt .scn does not replace meshes already instantiated in the
		# editor. Reload generated geometry as well as the hot-reloaded shader.
		_watch_elapsed += dt
		if _watch_elapsed < 1.0: return
		_watch_elapsed = 0.0
		if FileAccess.get_modified_time(geometry_path) != _previous_modified:
			_reload_geometry()
		return
	if OS.is_process_running(_builder_pid): return
	_builder_pid = -1
	if FileAccess.get_modified_time(geometry_path) <= _previous_modified:
		push_error("Generovanie náhľadu zlyhalo. Skontroluj Output; pôvodný náhľad zostal zachovaný.")
		return
	_reload_geometry()

func _reload_geometry() -> void:
	# Include external mesh/model dependencies: a cached source scene can
	# otherwise put old pointed peaks back into an updated snapshot.
	var packed := ResourceLoader.load(geometry_path, "PackedScene", ResourceLoader.CACHE_MODE_IGNORE_DEEP) as PackedScene
	if packed == null:
		push_error("Náhľad sa nepodarilo obnoviť: " + geometry_path)
		return
	var geometry := packed.instantiate()
	var previous := get_node_or_null("GeneratedTerrain")
	if previous != null:
		remove_child(previous)
		previous.queue_free()
	add_child(geometry)
	# This is an editor-only view. Serialising it into main.tscn would make
	# gameplay load the entire snapshot before immediately throwing it away.
	# The independent generated scene remains fully editable on disk.
	_preview_shadows(geometry)
	_previous_modified = FileAccess.get_modified_time(geometry_path)

func _ready() -> void:
	if Engine.is_editor_hint():
		set_process(not geometry_path.is_empty())
		if not geometry_path.is_empty(): _reload_geometry.call_deferred()
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
