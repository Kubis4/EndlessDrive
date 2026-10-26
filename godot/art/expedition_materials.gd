extends RefCounted
## Real image textures for the expedition set; no mesh rebuilding is required.
const ATLAS := "res://assets/textures/expedition-materials-v1.res"
const SHADER := "res://shaders/expedition_surface.gdshader"
const LAYERS := {
	"stone": 0, "stone_light": 5, "stone_dark": 5, "snow": 7,
	"bark": 2, "bark_light": 4, "pine": 3, "pine_light": 3,
	"leaf": 3, "leaf_light": 3, "reed": 3, "reed_head": 4,
	"steel": 6, "dark": 6, "teal": 6, "ochre": 6, "rust": 6,
	"ivory": 6, "glass": 6,
}
static var _atlas: Texture2DArray
static func texture_set() -> Texture2DArray:
	if _atlas == null: _atlas = load(ATLAS) as Texture2DArray
	return _atlas

static func configure(material: ShaderMaterial, key: String, model_name := "") -> void:
	var atlas := texture_set()
	var layer := int(LAYERS.get(key, 6))
	if key == "stone" and model_name in ["alpine_peak", "snowy_peak"]: layer = 1
	if key == "stone_light" and model_name == "freight_wagon": layer = 6
	var scales := [0.0625, 0.0625, 0.28, 0.20, 0.24, 0.32, 0.25, 0.13]
	var means: Array = atlas.get_meta("layer_means")
	material.set_shader_parameter("model_atlas", atlas)
	material.set_shader_parameter("texture_enabled", key != "glass")
	material.set_shader_parameter("material_layer", layer)
	material.set_shader_parameter("texture_average", means[layer])
	material.set_shader_parameter("snow_average", means[7])
	material.set_shader_parameter("texture_scale", float(scales[layer]))
	material.set_shader_parameter("texture_strength", 0.75 if layer != 6 else 0.6)
	material.set_shader_parameter("texture_roughness", [0.98, 0.94, 0.93, 0.94, 0.9, 0.95, 0.64, 0.92][layer])

static func attach(node: Node, model_name: String) -> void:
	if node is MeshInstance3D:
		var material: Material = node.material_override
		if material is ShaderMaterial and material.shader.resource_path == SHADER:
			var textured := material.duplicate() as ShaderMaterial
			configure(textured, material.resource_name.to_snake_case(), model_name)
			node.material_override = textured
	for child: Node in node.get_children(): attach(child, model_name)
