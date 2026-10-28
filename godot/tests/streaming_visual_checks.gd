extends SceneTree
var failures := 0
var checks := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", label])
func nodes(node: Node) -> Array[Node]:
	var result: Array[Node] = [node]
	for child in node.get_children(): result.append_array(nodes(child))
	return result
func signature(node: Node) -> Dictionary:
	var result := {}
	for child in nodes(node):
		var key := child.get_class()
		result[key] = int(result.get(key, 0)) + 1
	return result
func _run() -> void:
	var detail := load("res://assets/textures/surface-microheight-2k.png") as Texture2D
	var dust := load("res://assets/textures/dust-plume-512.png") as Texture2D
	var albedo := load("res://assets/textures/surface-albedo-atlas.png") as TextureLayered
	var ground_atlas := load("res://assets/textures/ground-surfaces.png") as TextureLayered
	check(albedo != null and albedo.get_layers() == 4 and albedo.get_width() == 627 and albedo.has_mipmaps(), "Four independently mipmapped albedo layers cannot bleed across material boundaries")
	check(detail.get_size() == Vector2(2048, 2048) and detail.get_image().has_mipmaps(), "Native 2K detail map has mipmaps for distant terrain")
	check(dust.get_size() == Vector2(512, 512) and dust.get_image().has_mipmaps(), "512px dust plume has mipmaps")
	var effect := DriveEffects.new()
	root.add_child(effect)
	check(effect._emitters.size() == 4 and effect._emitters[0].scene_file_path == "res://assets/effects/wheel_dust.tscn" and effect._grit[0].scene_file_path == "res://assets/effects/wheel_grit.tscn", "Gameplay instantiates the actual editable dust/grit effect scenes")
	effect.free()
	for label: String in ["editor_terrain", "bridge", "rail", "tunnel", "alpine"]:
		var geometry := (load("res://scenes/world/generated/" + label + ".scn") as PackedScene).instantiate()
		var collision_free := true
		var grounds := 0
		var bound_textures := true
		var native_meshes := 0
		var batches := 0
		for node in nodes(geometry):
			if node is MeshInstance3D: native_meshes += 1
			if node is MultiMeshInstance3D: batches += 1
			collision_free = collision_free and not node is CollisionObject3D and not node is CollisionShape3D
			if node is MeshInstance3D and node.name == "SandAndTracks":
				grounds += 1
				var material := node.material_override as ShaderMaterial
				bound_textures = bound_textures and material.get_shader_parameter("surface_data_enabled") == true and material.get_shader_parameter("surface_atlas") == ground_atlas and material.get_shader_parameter("micro_height") != null and material.get_shader_parameter("road_weathering") != null and material.get_shader_parameter("weathered_asphalt") != null
		check(collision_free and grounds == 3 and bound_textures, "Saved %s preview contains current textured terrain and no gameplay colliders" % label)
		check(native_meshes > 100 and batches == 0, "Saved %s preview preserves visible native rocks, fences and pole geometry" % label)
		geometry.free()
	var world := DriveWorld.new()
	root.add_child(world)
	world.configure(24601)
	for kind: String in ["bridge", "rail", "tunnel"]:
		world.async_streaming = false
		var reference := RouteHazard.new()
		reference.world = world
		reference.kind = kind
		reference.feature_x = 1704
		world.add_child(reference)
		reference.build()
		var expected := signature(reference)
		reference.free()
		world.async_streaming = true
		var streamed := RouteHazard.new()
		streamed.world = world
		streamed.kind = kind
		streamed.feature_x = 1704
		world.add_child(streamed)
		streamed.build_streamed()
		check(not world._jobs.is_empty(), "%s construction is split into queued steps" % kind)
		var frames := 0
		while not world._jobs.is_empty() and frames < 120:
			await process_frame
			frames += 1
		check(world._jobs.is_empty() and signature(streamed) == expected, "Streamed %s completes with all synchronous visuals, lights and collision shapes" % kind)
		streamed.free()
	var cancelled := RouteHazard.new()
	cancelled.world = world
	cancelled.kind = "tunnel"
	cancelled.feature_x = 1704
	world.add_child(cancelled)
	cancelled.build_streamed()
	cancelled.free()
	for i in 10: await process_frame
	check(world._jobs.is_empty(), "Unloading an unfinished structure safely cancels its queued work")
	var owner_node := Node3D.new()
	world.add_child(owner_node)
	world._schedule(func() -> void: owner_node.name = "ShouldNeverRun", owner_node, "cancelled_chunk")
	owner_node.free()
	for i in 2: await process_frame
	check(world._jobs.is_empty(), "Freed chunk owners discard lambdas before captured nodes are dereferenced")
	print("STREAMING max commit %.2f ms; max individual step %.2f ms" % [world.max_commit_ms, world.max_job_ms])
	world.free()
	print("STREAMING VISUALS: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
