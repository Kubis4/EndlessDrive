extends RefCounted
## Shared textured materials for authored props and procedural scenery.
## Glass, lamps, car paint and already textured specialist surfaces are preserved.
static var _cache: Dictionary = {}
static func decorate(node: MeshInstance3D) -> void:
	var original := node.material_override
	if original == null and node.mesh != null and node.mesh.get_surface_count() > 0:
		original = node.mesh.surface_get_material(0)
	if original == null: return
	var color := Color.WHITE
	if original is StandardMaterial3D:
		if original.transparency != BaseMaterial3D.TRANSPARENCY_DISABLED or original.emission_enabled or original.albedo_texture != null: return
		color = original.albedo_color
	elif original is ShaderMaterial and original.shader.resource_path == "res://shaders/desert_paint.gdshader":
		color = original.get_shader_parameter("tint")
	else: return
	var label := str(node.name).to_lower() + " " + original.resource_name.to_lower()
	if node.get_parent() != null: label += " " + str(node.get_parent().name).to_lower()
	var kind := 1
	if _contains(label, ["glass", "window", "display", "lamp", "light", "snow", "reflect", "line", "shadow"]): return
	if _contains(label, ["foliage", "cactus", "saguaro", "scrub", "branch", "crown", "leaf", "dry"]): kind = 3
	elif _contains(label, ["rock", "stone", "pier", "cliff", "deck", "driveway", "yard"]): kind = 4
	elif _contains(label, ["wire", "tower", "brace"]): kind = 2
	elif _contains(label, ["wood", "trunk", "timber", "crate", "fence", "beam", "door", "bench", "joist", "post"]): kind = 0
	elif _contains(label, ["steel", "metal", "roof", "tank", "canopy", "swatch", "pump", "accent", "body", "cabin", "rail", "barrier", "gate", "awning", "frame", "chimney", "extractor", "column"]): kind = 2
	elif _contains(label, ["rubber", "tyre", "wheel", "hose"]): return
	var key := "%d/%s" % [kind, color.to_html()]
	if not _cache.has(key):
		var material := ShaderMaterial.new()
		material.resource_name = "Detailed_" + str(kind)
		material.shader = load("res://shaders/prop_surface.gdshader")
		material.set_shader_parameter("world_atlas", load("res://assets/textures/world-materials-atlas.png"))
		material.set_shader_parameter("terrain_atlas", load("res://assets/textures/surface-albedo-atlas.png"))
		material.set_shader_parameter("surface_kind", kind)
		material.set_shader_parameter("tint", color)
		material.set_shader_parameter("roughness_value", 0.68 if kind == 2 else 0.9)
		material.set_shader_parameter("metallic_value", 0.25 if kind == 2 else 0.0)
		_cache[key] = material
	node.material_override = _cache[key]
static func apply(node: Node) -> void:
	if node is MeshInstance3D: decorate(node)
	for child: Node in node.get_children(): apply(child)
static func _contains(text: String, words: Array) -> bool:
	for word: String in words:
		if text.contains(word): return true
	return false
