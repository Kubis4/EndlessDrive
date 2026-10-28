extends SceneTree
## Build a deterministic export allowlist, including native binary dependencies
## and the banks loaded by name. Keep editable source art outside the package.
var files: Dictionary = {}
func _initialize() -> void: call_deferred("_run")
func scan(path: String, extensions: Array) -> void:
	for file in DirAccess.get_files_at(path):
		if file.get_extension() in extensions: add(path.path_join(file))
	for folder in DirAccess.get_directories_at(path): scan(path.path_join(folder), extensions)
func add(path: String) -> void:
	if files.has(path) or not FileAccess.file_exists(path): return
	if "/generated/" in path or "/source_" in path or path.begins_with("res://tests/"): return
	files[path] = true
	for dependency in ResourceLoader.get_dependencies(path):
		var parts := dependency.split("::")
		var source := str(parts[-1])
		if source.begins_with("uid://"): source = ResourceUID.get_id_path(ResourceUID.text_to_id(source))
		add(source)
	if path.get_extension() not in ["gd", "gdshader", "gdshaderinc"]: return
	var expression := RegEx.new()
	expression.compile('res://[^"\\\'\\s)]+')
	for match in expression.search_all(FileAccess.get_file_as_string(path)):
		var resource := match.get_string()
		if FileAccess.file_exists(resource): add(resource)
func _run() -> void:
	scan("res://scripts", ["gd"])
	# Global art/audio classes are resolved by name rather than a preload.
	# Explicitly retain their scripts in the exported global-class cache.
	for path: String in ["res://art/biome_models.gd", "res://art/desert_models.gd", "res://art/stop_models.gd", "res://art/desert_audio.gd"]: add(path)
	scan("res://shaders", ["gdshader", "gdshaderinc"])
	scan("res://assets/ui", ["svg", "png"])
	scan("res://assets/effects", ["tscn"])
	# Scene banks are addressed by name from terrain/scenery workers.
	scan("res://assets/models", ["tscn"])
	for path: String in ["res://scenes/boot.tscn", "res://scenes/main.tscn", "res://scenes/vehicle.tscn", "res://scenes/vehicle_equipment.tscn", "res://scenes/vehicle_visibility.tscn", "res://scenes/world/start_camp.tscn", "res://scenes/world/editor_terrain.tscn"]: add(path)
	scan("res://scenes/ui", ["tscn"])
	var paths := files.keys()
	paths.sort()
	var config := ConfigFile.new()
	assert(config.load("res://export_presets.cfg") == OK)
	var create_baseline := "--baseline" in OS.get_cmdline_user_args()
	for section in config.get_sections():
		if not section.begins_with("preset.") or section.ends_with(".options"): continue
		if create_baseline: continue
		config.set_value(section, "export_filter", "resources")
		config.set_value(section, "export_files", PackedStringArray(paths))
		config.set_value(section, "exclude_filter", "tests/*,art/source_*/*,scenes/world/generated/*,export/*")
	# PCK-only desktop audit needs no Android build or APK generation.
	config.set_value("preset.2", "name", "Runtime Resource Audit")
	config.set_value("preset.2", "platform", "Windows Desktop")
	config.set_value("preset.2", "runnable", false)
	config.set_value("preset.2", "export_filter", "all_resources" if create_baseline else "resources")
	config.set_value("preset.2", "export_files", PackedStringArray(paths))
	config.set_value("preset.2", "include_filter", "locales/*.json,assets/ASSET_LICENSES.md,assets/models/jeep/ATTRIBUTION.md")
	config.set_value("preset.2", "exclude_filter", str(config.get_value("preset.0", "exclude_filter")))
	config.set_value("preset.2", "export_path", "../build/endless-drive-runtime.pck")
	config.set_value("preset.2.options", "binary_format/architecture", "x86_64")
	config.save("res://export_presets.cfg")
	var file := FileAccess.open("res://../build/runtime-resources.json", FileAccess.WRITE)
	file.store_string(JSON.stringify(paths, "\t"))
	print("RUNTIME MANIFEST ", paths.size(), " resources; sources and editor snapshots remain outside runtime")
	quit()
