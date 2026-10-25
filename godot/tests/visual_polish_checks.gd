extends SceneTree
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func _run() -> void:
	var atlas := load("res://assets/textures/world-materials-atlas.png") as TextureLayered
	check(atlas != null and atlas.get_layers() == 4 and atlas.has_mipmaps(), "New timber, plaster, steel and foliage textures have isolated mipmapped layers")
	var kinds := {}
	var textured := 0
	for file: String in ["mesa_rock", "saguaro", "desert_scrub", "ranch_fence", "utility_pole", "water_tower", "stops/workshop", "stops/gas", "stops/home", "stops/paint", "stops/repair"]:
		var prop := (load("res://assets/models/" + file + ".tscn") as PackedScene).instantiate()
		var count := 0
		for mesh in prop.find_children("*", "MeshInstance3D", true, false):
			var material := mesh.material_override as ShaderMaterial
			if material != null and material.shader.resource_path == "res://shaders/prop_surface.gdshader":
				count += 1
				kinds[int(material.get_shader_parameter("surface_kind"))] = true
		check(count > 0, "Authored %s source has real textured surfaces in the editor" % file)
		textured += count
		prop.free()
	check(kinds.size() == 5 and textured > 70, "Shared materials cover buildings, machinery, timber, vegetation and stone")
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	for tick in 4:
		await physics_frame
		await process_frame
	game.vehicle.set_physics_process(false)
	game.weather.forced_mode = "Dážď"
	for tick in 600: game.weather._process(1.0 / 60.0)
	game._update_lighting()
	check(not game.environment.fog_enabled and game.weather.rain_mix > 0.8 and game.weather._particles.emitting, "Heavy rain keeps its particles and wetness without restoring a grey fog filter")
	game.run.engine_running = true
	game.effects._process(0.016)
	check(game.effects._exhaust.emitting and game.effects._emitters[0].amount >= 80 and game.effects._grit[0].amount >= 24, "Engine exhaust and stronger wheel plumes/grit are active production effects")
	game.origin_rebasing_enabled = true
	game.vehicle.position = Vector3(3000, 1, -200)
	game._camera_focus = game.vehicle.position + Vector3(-4, 0.4, -1)
	game.camera.position = game._camera_focus + game.overview_offset
	game.camera.size = 57
	var focus: Vector3 = game._camera_focus
	var camera_transform: Transform3D = game.camera.transform
	game._rebase_world(true)
	check(game.route_origin.x != 0 and game._camera_focus.is_equal_approx(focus) and game.camera.transform.is_equal_approx(camera_transform) and is_equal_approx(game.camera.size, 57), "Floating-origin shifts preserve the exact camera follow lag, orientation and zoom")
	game.hud.expedition_ui.touch_mode = 1
	game.hud.expedition_ui._layout()
	check(game.hud.expedition_ui.has_node("Speed") and game.hud._pedals.throttle.visible, "Editable variant C speed display and touch driving pedals are present")
	game.queue_free()
	for tick in 2: await process_frame
	print("VISUAL POLISH failures=%d" % failures)
	quit(0 if failures == 0 else 1)
