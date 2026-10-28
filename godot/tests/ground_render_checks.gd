extends SceneTree
## Real-GPU regression for discontinuous sine-hash terrain colour panels.
var failures := 0
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	if DisplayServer.get_name() == "headless":
		print("SKIP ground render checks require a real renderer")
		quit()
		return
	var common := FileAccess.get_file_as_string("res://shaders/desert_surface_common.gdshaderinc")
	var start := common.find("float hash(")
	var end := common.find("vec3 surface_tile(")
	var functions := common.substr(start, end - start)
	var viewport := SubViewport.new()
	viewport.size = Vector2i(128, 128)
	viewport.disable_3d = true
	viewport.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	root.add_child(viewport)
	var rect := ColorRect.new()
	rect.size = Vector2(128, 128)
	viewport.add_child(rect)
	for mode: String in ["current", "legacy"]:
		if mode == "legacy" and not OS.get_cmdline_user_args().has("--compare-legacy"): continue
		var code := functions
		if mode == "legacy":
			code = "float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }\n" + functions.substr(functions.find("float noise("))
		var shader := Shader.new()
		shader.code = "shader_type canvas_item;\nrender_mode unshaded;\n" + code + """
void fragment() {
	vec2 cell = floor(UV * 128.0) * vec2(137.0, 61.0) - vec2(8000.0, 4000.0);
	vec2 across_x = cell + vec2(1.0, 0.37);
	vec2 across_y = cell + vec2(0.63, 1.0);
	float dx = abs(noise(across_x - vec2(0.002, 0.0)) - noise(across_x + vec2(0.002, 0.0)));
	float dy = abs(noise(across_y - vec2(0.0, 0.002)) - noise(across_y + vec2(0.0, 0.002)));
	COLOR = vec4(vec3(max(dx, dy)), 1.0);
}
"""
		var material := ShaderMaterial.new()
		material.shader = shader
		rect.material = material
		for frame in 4: await process_frame
		await RenderingServer.frame_post_draw
		var image := viewport.get_texture().get_image()
		var maximum := 0.0
		var discontinuities := 0
		for x in 128:
			for y in 128:
				var value := image.get_pixel(x, y).r
				maximum = maxf(maximum, value)
				if value > 0.015: discontinuities += 1
		print("GROUND GPU %s: max border difference=%.6f; discontinuities=%d/16384" % [mode, maximum, discontinuities])
		if mode == "current":
			if maximum > 0.015 or discontinuities > 0: failures += 1
	viewport.queue_free()
	await process_frame
	print("GROUND GPU: failures=", failures)
	quit(0 if failures == 0 else 1)
