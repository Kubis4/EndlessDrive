extends SubViewport
## Render representative structure materials before driving. Invisible/offscreen
## viewports still submit real draws, unlike hidden nodes culled by the renderer.
var _frames := 0
var _point_lights: Array[OmniLight3D] = []
var environment_template: Environment
var _camera: Camera3D
var _tunnel_center := Vector3.ZERO
var _tunnel_lights: Array[OmniLight3D] = []
var vehicle_template: Node3D
var _headlights: Array[SpotLight3D] = []
var _warm_environment: Environment
func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	size = Vector2i(32, 32)
	own_world_3d = true
	render_target_update_mode = SubViewport.UPDATE_ALWAYS
	var camera := Camera3D.new()
	_camera = camera
	camera.position = Vector3(0, 0, 9)
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 10
	camera.far = 260
	add_child(camera)
	msaa_3d = Viewport.MSAA_2X
	if environment_template != null:
		var environment_node := WorldEnvironment.new()
		_warm_environment = environment_template.duplicate() as Environment
		environment_node.environment = _warm_environment
		add_child(environment_node)
	for i in 3:
		var light := OmniLight3D.new()
		_point_lights.append(light)
		light.position = Vector3((i - 1) * 2, 3, 4)
		light.omni_range = 20
		add_child(light)
	var directional := DirectionalLight3D.new()
	directional.rotation_degrees = Vector3(-35, -25, 0)
	directional.shadow_enabled = true
	directional.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
	directional.directional_shadow_max_distance = 120
	add_child(directional)
	var plain := BiomeModels.material(Color("77786c"))
	var double_sided := plain.duplicate() as StandardMaterial3D
	double_sided.cull_mode = BaseMaterial3D.CULL_DISABLED
	var transparent := double_sided.duplicate() as StandardMaterial3D
	transparent.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	transparent.albedo_color.a = 0.18
	var lamp := StandardMaterial3D.new()
	lamp.emission_enabled = true
	lamp.emission = Color.RED
	var water := ShaderMaterial.new()
	water.shader = load("res://shaders/water.gdshader")
	var materials: Array[Material] = [plain, double_sided, transparent, DesertModels.mat("steel"), lamp, RouteHazard.rock_material(), water]
	for i in materials.size():
		var mesh := BoxMesh.new()
		var node := MeshInstance3D.new()
		node.mesh = mesh
		node.material_override = materials[i]
		node.position = Vector3((i % 3 - 1) * 2.0, (i / 3 - 0.5) * 2.0, 0)
		add_child(node)
	# Signs use a different text pipeline from HUD labels; warm both outline
	# sizes and their glyphs before a newly streamed building reaches view.
	for font_size: int in [36, 48]:
		var label := Label3D.new()
		label.text = "↑↓ ASFALT KOPCE ŠTRK BROD AUTOSERVIS PALIVO DIELŇA DOM BISTRO"
		label.font_size = font_size
		label.outline_size = 3
		label.pixel_size = 0.002
		label.position = Vector3(0, -2.5 if font_size == 36 else 2.5, 1)
		add_child(label)
	# The tunnel's ArrayMesh vertex layout and three-light shell select extra
	# programs beyond primitive boxes. Submit the real generator once as well.
	var world := DriveWorld.new()
	add_child(world)
	world.configure(24601)
	var tunnel := RouteHazard.new()
	tunnel.world = world
	tunnel.kind = "tunnel"
	tunnel.feature_x = 1704
	world.add_child(tunnel)
	tunnel.build()
	for child in tunnel.get_children():
		if child is OmniLight3D:
			_tunnel_lights.append(child)
			child.visible = false
	_tunnel_center = Vector3(1704, world.height_at(1704, world.center_at(1704)), world.center_at(1704))
	_warm_terrain_patch(world)
	var water_faces := PackedVector3Array()
	for corner: Vector2 in [Vector2(8, 3), Vector2(16, 3), Vector2(8, 9), Vector2(16, 3), Vector2(16, 9), Vector2(8, 9)]:
		water_faces.append(_tunnel_center + Vector3(corner.x, 0.02, corner.y))
	world._create_baked_water(world, water_faces, PackedFloat32Array([0.02, 0.65, 0.1, 0.65, 0.8, 0.1]))
	# The imported car/paint has its own vertex formats too. One model sits
	# beneath a lamp; the other spans the overlap between two tunnel lamps.
	for i in 2:
		var visual := vehicle_template.duplicate() as Node3D if vehicle_template != null else (load("res://assets/models/jeep/jeep_cherokee.tscn") as PackedScene).instantiate() as Node3D
		visual.position = _tunnel_center + Vector3(i * 12.0, 0.6, 0)
		world.add_child(visual)
		for rim in visual.find_children("OccludedCarOutline*", "MeshInstance3D", true, false): rim.visible = true
		for child in visual.find_children("*", "SpotLight3D", true, false):
			child.visible = false
			if i == 0: _headlights.append(child)
	# Outdoor headlight variants also need instanced scenery, signs, the
	# imported vehicle, tyre tracks and lit grit in their actual vertex formats.
	world._place_model(world, "mesa_rock", _tunnel_center + Vector3(20, 0, 5))
	world._place_model(world, "road_delineator", _tunnel_center + Vector3(15, 0, -5))
	preload("res://scripts/canyon_scenery.gd").wall(world, world, _tunnel_center.x + 12.0, 1.0)
	BiomeModels.tree(world, _tunnel_center + Vector3(22, 0, -7), 0.7, false, false)
	var stop := (load("res://scenes/world/start_camp.tscn") as PackedScene).instantiate() as Node3D
	stop.position = _tunnel_center + Vector3(25, 0, 10)
	world.add_child(stop)
	StopModels.roadside_sign(world, _tunnel_center + Vector3(18, 0, -3), "DIELŇA", false)
	# Terrain-conforming yards/decks have normals but no UV/tangent stream;
	# their textured prop shader selects another real renderer variant.
	var yard := PackedVector3Array()
	for corner: Vector2 in [Vector2(8, -3), Vector2(18, -3), Vector2(8, 3), Vector2(18, -3), Vector2(18, 3), Vector2(8, 3)]:
		yard.append(_tunnel_center + Vector3(corner.x, 0.02, corner.y))
	world._create_access_mesh(world, yard, "SideDriveway")
	var effects := DriveEffects.new()
	world.add_child(effects)
	# Prime actual immutable surface banks, including the new grain/clump
	# vertex format and procedural veil, before the first off-road contact.
	for family in 6:
		var bank: Dictionary = effects._surface_pools[0][family]
		for key in ["dust", "grit", "clumps"]:
			var emitter: CPUParticles3D = bank.get(key)
			if emitter == null: continue
			emitter.position = _tunnel_center + Vector3(5 + family * 2, 0.5, 0)
			emitter.emitting = key != "dust" or family != 4
	effects._exhaust.position = _tunnel_center + Vector3(3, 0.3, 0)
	effects._exhaust.emitting = true

func _warm_terrain_patch(parent: Node3D) -> void:
	# Tunnel lamps also light the deformable ground. Its four custom vertex
	# streams select a different program from the stone shell or a plain box.
	var vertices := PackedVector3Array()
	for corner: Vector2 in [Vector2(-48, -60), Vector2(48, -60), Vector2(-48, 60), Vector2(48, -60), Vector2(48, 60), Vector2(-48, 60)]:
		vertices.append(_tunnel_center + Vector3(corner.x, -0.1, corner.y))
	var arrays: Array = []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	var normals := PackedVector3Array()
	normals.resize(6)
	normals.fill(Vector3.UP)
	arrays[Mesh.ARRAY_NORMAL] = normals
	var colors := PackedColorArray()
	colors.resize(6)
	colors.fill(Color("c5b28e"))
	arrays[Mesh.ARRAY_COLOR] = colors
	var uv := PackedVector2Array()
	uv.resize(6)
	arrays[Mesh.ARRAY_TEX_UV] = uv
	arrays[Mesh.ARRAY_TEX_UV2] = uv
	arrays[Mesh.ARRAY_INDEX] = PackedInt32Array([0, 1, 2, 3, 4, 5])
	for channel in 4:
		var weights := PackedFloat32Array()
		weights.resize(24)
		if channel == 0:
			for vertex in 6: weights[vertex * 4] = 1
		arrays[Mesh.ARRAY_CUSTOM0 + channel] = weights
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays, [], {}, Mesh.ARRAY_FLAG_USE_DYNAMIC_UPDATE | DriveTerrainBake.MATERIAL_FORMAT)
	var material := ShaderMaterial.new()
	material.shader = load("res://shaders/desert_surface.gdshader")
	material.set_shader_parameter("surface_data_enabled", true)
	material.set_shader_parameter("surface_atlas", load("res://assets/textures/surface-albedo-atlas.png"))
	material.set_shader_parameter("micro_height", load("res://assets/textures/surface-microheight-2k.png"))
	var ground := MeshInstance3D.new()
	ground.mesh = mesh
	ground.material_override = material
	ground.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	parent.add_child(ground)
func _process(_dt: float) -> void:
	_frames += 1
	# Fog/sky, sun shadows and 0–3 overlapping tunnel lights select different
	# Compatibility shader variants. Prime the actual gameplay combinations.
	if _frames in [4, 8, 12]: _point_lights[_frames / 4 - 1].visible = false
	if _frames == 16:
		_camera.size = 53
		_camera.position = _tunnel_center + Vector3(-36, 52, 44)
		_camera.look_at(_tunnel_center)
	if _frames in [20, 24, 28]: _tunnel_lights[(_frames - 20) / 4].visible = true
	if _frames == 32:
		for light in _tunnel_lights: light.visible = false
	if _frames == 36 and _headlights.size() > 0: _headlights[0].visible = true
	if _frames == 40 and _headlights.size() > 1: _headlights[1].visible = true
	# Then cover the mixed case: entering a lit tunnel at night.
	if _frames in [44, 48, 52]: _tunnel_lights[(_frames - 44) / 4].visible = true
	# Reverse lamps can overlap both headlights. Dust visibility also enables
	# a fog program; prime it privately, never by changing the live environment.
	if _frames == 54 and _headlights.size() > 2: _headlights[2].visible = true
	if _frames == 56 and _headlights.size() > 3: _headlights[3].visible = true
	if _frames == 60 and _warm_environment != null:
		_warm_environment.fog_enabled = true
		_warm_environment.fog_density = 0.008
	if _frames >= 70: queue_free()
