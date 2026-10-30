extends SceneTree
## Rebuilds the saved, inspectable scene using deterministic native Godot geometry.
const Surface = preload("res://desert_battlefield/terrain.gd")
const EndlessModels = preload("res://desert_battlefield/endless_models.gd")
var scene: Node3D
var rng := RandomNumberGenerator.new()
var materials := {}
var mesh_cache := {}
## The test-driving build is terrain-only: buildings, fences, poles, figures,
## wrecks and rock fields are omitted (true restores the original diorama).
const BUILD_PROPS := false
## Jeep and IZH van; a third (second Jeep) only patrolled.
const VEHICLES := 2

func _initialize() -> void:
	call_deferred("build")

func material(key: String, color: String, roughness: float = 0.85) -> StandardMaterial3D:
	if materials.has(key): return materials[key]
	var m := StandardMaterial3D.new()
	m.resource_name = key
	m.albedo_color = Color(color)
	m.roughness = roughness
	materials[key] = m
	return m

func group(parent: Node, title: String, point := Vector3.ZERO) -> Node3D:
	var n := Node3D.new()
	n.name = title
	parent.add_child(n)
	n.position = point
	return n

func mesh_node(parent: Node, title: String, mesh: Mesh, mat: Material, point: Vector3) -> MeshInstance3D:
	var n := MeshInstance3D.new()
	n.name = title
	n.mesh = mesh
	n.material_override = mat
	parent.add_child(n)
	n.position = point
	return n

func box(parent: Node, title: String, point: Vector3, size: Vector3, mat: Material) -> MeshInstance3D:
	var key := str(size)
	if not mesh_cache.has(key):
		var m := BoxMesh.new()
		m.size = size
		mesh_cache[key] = m
	return mesh_node(parent, title, mesh_cache[key], mat, point)

func cylinder(parent: Node, title: String, point: Vector3, radius: float, height: float, mat: Material, sides: int = 10) -> MeshInstance3D:
	var key := "c%.3f_%.3f_%d" % [radius, height, sides]
	if not mesh_cache.has(key):
		var m := CylinderMesh.new()
		m.top_radius = radius
		m.bottom_radius = radius
		m.height = height
		m.radial_segments = sides
		m.rings = 1
		mesh_cache[key] = m
	return mesh_node(parent, title, mesh_cache[key], mat, point)

func beam(parent: Node, title: String, a: Vector3, b: Vector3, width: float, mat: Material) -> void:
	var n := cylinder(parent, title, (a + b) * 0.5, width, a.distance_to(b), mat, 6)
	var up := (b - a).normalized()
	var right := up.cross(Vector3.FORWARD)
	if right.length_squared() < 0.01: right = up.cross(Vector3.RIGHT)
	right = right.normalized()
	n.basis = Basis(right, up, right.cross(up))

func grounded(parent: Node, title: String, x: float, z: float, yaw: float = 0) -> Node3D:
	var n := group(parent, title, Vector3(x, Surface.height_at(x, z), z))
	n.rotation.y = yaw
	return n

func solid(parent: Node3D, point: Vector3, size: Vector3) -> void:
	var body := StaticBody3D.new()
	body.name = "Collision"
	parent.add_child(body)
	body.position = point
	var shape := CollisionShape3D.new()
	var bounds := BoxShape3D.new()
	bounds.size = size
	shape.shape = bounds
	body.add_child(shape)

func build() -> void:
	rng.seed = 817207
	scene = Node3D.new()
	scene.name = "SiroccoBattlefield"
	scene.set_script(preload("res://desert_battlefield/battlefield.gd"))
	material("Concrete", "a89d83")
	material("ConcreteLight", "c0b499")
	material("Interior", "3f4038")
	material("Metal", "55594e", 0.7)
	material("Olive", "737762")
	material("SandPaint", "ada184")
	material("Canvas", "77735b")
	material("Tyres", "30322f")
	material("Glass", "455858", 0.24)
	material("Wood", "79654a")
	material("Rust", "80634d")
	material("Uniform", "8e8767")
	material("Skin", "bba17d")
	make_terrain()
	make_lighting()
	if BUILD_PROPS: build_props()
	var patrol := group(scene, "Patrol")
	for i in VEHICLES:
		var car := CharacterBody3D.new()
		car.name = "Scout%02d" % (i + 1)
		car.set_script(preload("res://desert_battlefield/vehicle.gd"))
		car.set("lane", i)
		var phase: float = [0.75, 2.85, 4.95][i]
		car.set("route_phase", phase)
		car.set("cruise_speed", 10.5)
		var model_id: String = "izh" if i == 1 else "jeep"
		var model: Dictionary = EndlessModels.Catalog.DATA[model_id]
		car.set("half_track", model.track * 0.5)
		car.set("half_wheelbase", model.wheelbase * 0.5)
		car.set("wheel_radius", model.radius)
		patrol.add_child(car)
		car.position = Surface.route(phase, i) + Vector3.UP * 0.55
		var body := group(car, "Body")
		EndlessModels.vehicle(body, model_id, Color("ada184") if i == 1 else Color("737762"))
		var collision := CollisionShape3D.new()
		collision.name = "CollisionShape3D"
		var bounds := BoxShape3D.new()
		bounds.size = Vector3(2.4, 1.8, 4.6)
		collision.shape = bounds
		collision.position.y = 0.65
		car.add_child(collision)
		make_dust(car)
	assign_owner(scene)
	var packed := PackedScene.new()
	var error := packed.pack(scene)
	if error == OK: error = ResourceSaver.save(packed, "res://desert_battlefield/desert_battlefield.tscn")
	print("BATTLEFIELD BUILD: ", error_string(error), "; nodes=", scene.find_children("*", "", true, false).size())
	scene.free()
	quit(error)

func build_props() -> void:
	var outpost := group(scene, "AbandonedRelayStation")
	bunker(outpost, -21, -14, Vector3(10, 3.5, 6.5), -0.09)
	var workshop := grounded(outpost, "EndlessDriveWorkshop", -8, -19, 0.05)
	EndlessModels.environment(workshop, "res://assets/models/stops/repair.tscn", "RepairBarrack")
	var tank := grounded(outpost, "WaterReservoir", -29, -16)
	cylinder(tank, "ConcreteTank", Vector3(0, 2.0, 0), 2.1, 4.0, materials.Concrete, 20)
	cylinder(tank, "Rim", Vector3(0, 4.0, 0), 2.18, 0.18, materials.ConcreteLight, 20)
	cylinder(tank, "Lid", Vector3(0, 4.14, 0), 0.6, 0.12, materials.Metal)
	for i in 10:
		beam(tank, "LadderRung", Vector3(-0.34, 0.4 + i * 0.36, 2.16), Vector3(0.34, 0.4 + i * 0.36, 2.16), 0.035, materials.Metal)
	for x in [-0.4, 0.4]: beam(tank, "LadderRail", Vector3(x, 0.1, 2.16), Vector3(x, 4.4, 2.16), 0.035, materials.Metal)
	solid(tank, Vector3(0, 2, 0), Vector3(4.2, 4, 4.2))
	var mast := grounded(outpost, "RadioMast", -17, -19)
	beam(mast, "Mast", Vector3.ZERO, Vector3(0, 12, 0), 0.09, materials.Metal)
	for y in [9.0, 10.0, 11.0]: beam(mast, "Antenna", Vector3(-1.6, y, 0), Vector3(1.6, y, 0), 0.04, materials.Metal)
	for v in [Vector3(4, 0, 2), Vector3(-3, 0, 3), Vector3(0, 0, -4)]: beam(mast, "GuyWire", Vector3(0, 8, 0), v, 0.018, materials.Metal)
	for i in 5: crate(outpost, -13.0 + float(i % 3) * 1.15, -8.5 + floorf(float(i) / 3.0) * 1.2, rng.randf_range(-0.1, 0.1))
	for i in 4:
		var barrel := grounded(outpost, "FuelDrum", -30.5 + (i % 2) * 0.9, -10.0 + (i / 2) * 0.9)
		cylinder(barrel, "Drum", Vector3(0, 0.55, 0), 0.35, 1.1, materials.Olive, 12)
		for h in [0.2, 0.85]: cylinder(barrel, "Hoop", Vector3(0, h, 0), 0.36, 0.05, materials.Metal, 12)
	var perimeter := group(scene, "TimberPerimeter")
	fence(perimeter, Vector2(-36, -27), Vector2(-1, -27), 12)
	fence(perimeter, Vector2(-36, -27), Vector2(-36, -5), 7)
	fence(perimeter, Vector2(-2, -27), Vector2(-2, -11), 5)
	for entry in [Vector3(-25, 0, -8), Vector3(-20, 0, -8), Vector3(-7, 0, 2), Vector3(37, 0, -14)]:
		barricade(scene, entry.x, entry.z, -0.12)
	var shelter := grounded(scene, "EndlessDriveShelter", 41, -26, 0.32)
	EndlessModels.environment(shelter, "res://assets/models/stops/repair.tscn", "RepairBarrack")
	var wrecks := group(scene, "AbandonedVehicles")
	var wreck := grounded(wrecks, "BuriedTransport", -52, 23, -0.7)
	wreck.position.y -= 0.36
	wreck.rotation.z = -0.13
	EndlessModels.vehicle(wreck, "izh", Color("80634d"))
	solid(wreck, Vector3(0, 0.8, 0), Vector3(2.6, 2, 5.0))
	var wreck2 := grounded(wrecks, "DesertedScout", 34, -31, 1.2)
	wreck2.position.y += 0.44
	EndlessModels.vehicle(wreck2, "jeep", Color("ada184"))
	solid(wreck2, Vector3(0, 0.8, 0), Vector3(2.6, 2, 5.0))
	make_rocks()
	make_utility_line()
	var people := group(scene, "ScatteredPatrolFigures")
	for p in [Vector2(-17, -8), Vector2(-18.5, -6.5), Vector2(-5, -13), Vector2(-29, -4), Vector2(39, -20), Vector2(42, -18), Vector2(16, 14), Vector2(18, 15)]:
		person(people, p.x, p.y, rng.randf_range(-PI, PI))

func make_terrain() -> void:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	const COUNT := 480
	const STEP := 0.75
	for z in range(COUNT + 1):
		for x in range(COUNT + 1):
			var px := (x - COUNT / 2) * STEP
			var pz := (z - COUNT / 2) * STEP
			# Pure dunes: the test grounds are separate runtime meshes (test_course.gd).
			st.set_normal(Surface.dune_normal_at(px, pz))
			st.set_uv(Vector2(px, pz) * 0.1)
			st.add_vertex(Vector3(px, Surface.dune_height(px, pz), pz))
	for z in COUNT:
		for x in COUNT:
			var a := z * (COUNT + 1) + x
			for index in [a, a + 1, a + COUNT + 1, a + 1, a + COUNT + 2, a + COUNT + 1]: st.add_index(index)
	st.generate_tangents()
	var terrain_mesh := st.commit()
	ResourceSaver.save(terrain_mesh, "res://desert_battlefield/dune_terrain.res")
	var sand := ShaderMaterial.new()
	sand.shader = preload("res://desert_battlefield/sand.gdshader")
	mesh_node(scene, "SculptedDunes", load("res://desert_battlefield/dune_terrain.res"), sand, Vector3.ZERO)

func make_lighting() -> void:
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color("c6b89b")
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color("b9c6cc")
	env.ambient_light_energy = 0.4
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.tonemap_exposure = 1.0
	env.ssao_enabled = true
	env.ssao_radius = 2.1
	env.ssao_intensity = 1.5
	env.ssao_detail = 0.7
	env.ssil_enabled = true
	env.ssil_radius = 5.0
	env.ssil_intensity = 0.3
	env.fog_enabled = true
	env.fog_light_color = Color("dbbf94")
	env.fog_light_energy = 0.75
	env.fog_density = 0.0003
	env.fog_sky_affect = 0.0
	env.volumetric_fog_enabled = true
	env.volumetric_fog_density = 0.00055
	env.volumetric_fog_albedo = Color("cbb99c")
	env.volumetric_fog_length = 200.0
	env.volumetric_fog_detail_spread = 2.0
	var world := WorldEnvironment.new()
	world.name = "DesertAtmosphere"
	world.environment = env
	scene.add_child(world)
	var sun := DirectionalLight3D.new()
	sun.name = "LateAfternoonSun"
	sun.rotation_degrees = Vector3(-28, -43, 0)
	sun.light_color = Color("ffe7c2")
	sun.light_energy = 1.45
	sun.light_angular_distance = 0.7
	sun.shadow_enabled = true
	sun.shadow_bias = 0.06
	sun.shadow_normal_bias = 1.5
	sun.directional_shadow_max_distance = 250.0
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_4_SPLITS
	sun.directional_shadow_blend_splits = true
	scene.add_child(sun)
	var camera := Camera3D.new()
	camera.name = "Camera3D"
	camera.physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_OFF
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 91.0
	camera.near = 0.5
	camera.far = 500.0
	camera.position = Vector3(41, 121, 62)
	camera.rotation_degrees = Vector3(-59.4, 35.3, 0)
	camera.current = true
	scene.add_child(camera)

func bunker(parent: Node, x: float, z: float, size: Vector3, yaw: float) -> void:
	var n := grounded(parent, "ConcreteShelter", x, z, yaw)
	box(n, "Foundation", Vector3(0, 0.16, 0), Vector3(size.x + 0.7, 0.32, size.z + 0.7), materials.Concrete)
	box(n, "Walls", Vector3(0, size.y * 0.5, 0), size, materials.Concrete)
	box(n, "RoofSlab", Vector3(0, size.y, 0), Vector3(size.x + 0.45, 0.28, size.z + 0.45), materials.ConcreteLight)
	box(n, "RecessedRoof", Vector3(0, size.y + 0.16, 0), Vector3(size.x - 0.65, 0.06, size.z - 0.65), materials.SandPaint)
	for side in [-1, 1]:
		box(n, "Parapet", Vector3(side * size.x * 0.5, size.y + 0.32, 0), Vector3(0.22, 0.5, size.z), materials.ConcreteLight)
	box(n, "DoorRecess", Vector3(-size.x * 0.24, 1.0, size.z * 0.5 + 0.015), Vector3(1.1, 2, 0.04), materials.Interior)
	box(n, "DoorLintel", Vector3(-size.x * 0.24, 2.1, size.z * 0.5 + 0.13), Vector3(1.45, 0.2, 0.3), materials.ConcreteLight)
	box(n, "WindowShadow", Vector3(size.x * 0.21, 1.7, size.z * 0.5 + 0.02), Vector3(size.x * 0.32, 0.5, 0.05), materials.Interior)
	box(n, "WindowSill", Vector3(size.x * 0.21, 1.42, size.z * 0.5 + 0.15), Vector3(size.x * 0.34, 0.12, 0.35), materials.ConcreteLight)
	box(n, "Vent", Vector3(size.x * 0.5 + 0.02, 1.65, 0), Vector3(0.05, 0.45, 1.2), materials.Interior)
	box(n, "RoofVent", Vector3(1, size.y + 0.45, -0.5), Vector3(1.1, 0.6, 0.9), materials.Metal)
	solid(n, Vector3(0, size.y * 0.5, 0), size)

func crate(parent: Node, x: float, z: float, yaw: float) -> void:
	var n := grounded(parent, "SupplyCrate", x, z, yaw)
	box(n, "Case", Vector3(0, 0.45, 0), Vector3(0.95, 0.9, 0.85), materials.Wood)
	for side in [-0.33, 0.33]: box(n, "Strap", Vector3(side, 0.47, 0), Vector3(0.075, 0.94, 0.88), materials.Metal)

func fence(parent: Node, a: Vector2, b: Vector2, count: int) -> void:
	for i in count + 1:
		var p := a.lerp(b, float(i) / count)
		var n := grounded(parent, "FencePost", p.x, p.y)
		box(n, "Post", Vector3(0, 0.8, 0), Vector3(0.15, 1.6, 0.15), materials.Wood)
		if i == count or i == 4: continue
		var q := a.lerp(b, float(i + 1) / count)
		for h in [0.58, 1.17]:
			beam(parent, "FenceRail", Vector3(p.x, Surface.height_at(p.x, p.y) + h, p.y), Vector3(q.x, Surface.height_at(q.x, q.y) + h, q.y), 0.06, materials.Wood)

func barricade(parent: Node, x: float, z: float, yaw: float) -> void:
	var n := grounded(parent, "WoodenRoadblock", x, z, yaw)
	for side in [-1.5, 1.5]:
		beam(n, "Trestle", Vector3(side, 0, -0.65), Vector3(side, 1.4, 0.4), 0.11, materials.Wood)
		beam(n, "Trestle", Vector3(side, 0, 0.65), Vector3(side, 1.4, -0.4), 0.11, materials.Wood)
	for h in [0.65, 1.1]: box(n, "Rail", Vector3(0, h, 0), Vector3(4.0, 0.23, 0.14), materials.Wood)
	solid(n, Vector3(0, 0.7, 0), Vector3(4, 1.4, 1.1))

func make_dust(car: Node3D) -> void:
	var dust := GPUParticles3D.new()
	dust.name = "Dust"
	dust.amount = 144
	dust.lifetime = 6.0
	dust.preprocess = 0.0
	dust.local_coords = false
	dust.visibility_aabb = AABB(Vector3(-55, -5, -55), Vector3(110, 25, 110))
	dust.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var particle := ParticleProcessMaterial.new()
	particle.emission_shape = ParticleProcessMaterial.EMISSION_SHAPE_BOX
	particle.emission_box_extents = Vector3(0.95, 0.12, 0.4)
	particle.direction = Vector3(0.8, 0.6, 0.2)
	particle.spread = 35
	particle.initial_velocity_min = 0.6
	particle.initial_velocity_max = 1.9
	particle.gravity = Vector3(0.38, 0.13, 0.14)
	particle.damping_min = 0.15
	particle.damping_max = 0.3
	particle.angle_min = -180.0
	particle.angle_max = 180.0
	particle.scale_min = 1.0
	particle.scale_max = 2.3
	var curve := Curve.new()
	curve.add_point(Vector2(0, 0.18))
	curve.add_point(Vector2(0.25, 0.65))
	curve.add_point(Vector2(1, 1))
	var curve_tex := CurveTexture.new()
	curve_tex.curve = curve
	particle.scale_curve = curve_tex
	var gradient := Gradient.new()
	gradient.offsets = PackedFloat32Array([0.0, 0.12, 0.5, 1.0])
	gradient.colors = PackedColorArray([Color(0.45, 0.33, 0.20, 0), Color(0.51, 0.39, 0.25, 0.62), Color(0.58, 0.46, 0.31, 0.3), Color(0.63, 0.52, 0.37, 0)])
	var ramp := GradientTexture1D.new()
	ramp.gradient = gradient
	particle.color_ramp = ramp
	dust.process_material = particle
	var quad := QuadMesh.new()
	quad.size = Vector2(4.5, 4.5)
	var mat := ShaderMaterial.new()
	mat.shader = preload("res://desert_battlefield/dust.gdshader")
	quad.material = mat
	dust.draw_pass_1 = quad
	car.add_child(dust)
	for side in ["Left", "Right"]:
		var jet := dust.duplicate() as GPUParticles3D
		jet.name = "WheelDust" + side
		jet.amount = 88
		jet.lifetime = 1.8
		var jet_process := particle.duplicate() as ParticleProcessMaterial
		jet_process.emission_box_extents = Vector3(0.13, 0.05, 0.18)
		jet_process.gravity = Vector3(0.2, -0.35, 0.1)
		jet_process.scale_min = 0.5
		jet_process.scale_max = 1.05
		jet.process_material = jet_process
		car.add_child(jet)
		var grains := GPUParticles3D.new()
		grains.name = "SandGrains" + side
		grains.amount = 100
		grains.lifetime = 0.65
		grains.local_coords = false
		grains.visibility_aabb = dust.visibility_aabb
		grains.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		var grit := ParticleProcessMaterial.new()
		grit.emission_shape = ParticleProcessMaterial.EMISSION_SHAPE_BOX
		grit.emission_box_extents = Vector3(0.16, 0.03, 0.2)
		grit.spread = 26.0
		grit.angle_min = -180
		grit.angle_max = 180
		grit.angular_velocity_min = -120
		grit.angular_velocity_max = 120
		grit.gravity = Vector3(0, -9.8, 0)
		grit.scale_min = 0.4
		grit.scale_max = 1.5
		grit.damping_min = 0.4
		grit.damping_max = 0.8
		var speck_gradient := Gradient.new()
		speck_gradient.offsets = PackedFloat32Array([0.0, 0.15, 0.65, 1.0])
		speck_gradient.colors = PackedColorArray([Color(0.48, 0.36, 0.21, 0), Color(0.58, 0.46, 0.29, 0.9), Color(0.65, 0.53, 0.35, 0.65), Color(0.65, 0.53, 0.35, 0)])
		var speck_ramp := GradientTexture1D.new()
		speck_ramp.gradient = speck_gradient
		grit.color_ramp = speck_ramp
		grains.process_material = grit
		var grain := QuadMesh.new()
		grain.size = Vector2(0.045, 0.09)
		var grain_material := ShaderMaterial.new()
		grain_material.shader = preload("res://desert_battlefield/grain.gdshader")
		grain.material = grain_material
		grains.draw_pass_1 = grain
		car.add_child(grains)
		if side == "Left":
			var airborne := grains.duplicate() as GPUParticles3D
			airborne.name = "AirborneSpecks"
			airborne.amount = 64
			airborne.lifetime = 2.4
			var floating := grit.duplicate() as ParticleProcessMaterial
			floating.emission_box_extents = Vector3(1, 0.14, 0.35)
			floating.direction = Vector3(0.35, 0.5, 0.12)
			floating.spread = 48.0
			floating.initial_velocity_min = 0.5
			floating.initial_velocity_max = 1.6
			floating.gravity = Vector3(0.42, -0.10, 0.16)
			floating.damping_min = 0.2
			floating.damping_max = 0.5
			floating.scale_min = 0.3
			floating.scale_max = 0.8
			airborne.process_material = floating
			car.add_child(airborne)

func make_rocks() -> void:
	var rocks := group(scene, "WindCarvedRockFields")
	for variant in 5:
		var st := SurfaceTool.new()
		st.begin(Mesh.PRIMITIVE_TRIANGLES)
		st.set_smooth_group(-1)
		var verts: Array[Vector3] = []
		for level in 3:
			for j in 7:
				var angle := TAU * float(j) / 7 + float(level) * 0.21
				var r: float = [0.95, 1.05, 0.58][level] * rng.randf_range(0.8, 1.2)
				verts.append(Vector3(cos(angle) * r, [0.0, 0.5, 1.1][level] + rng.randf_range(-0.08, 0.08), sin(angle) * r))
		for level in 2:
			for j in 7:
				var a := level * 7 + j
				var b := level * 7 + (j + 1) % 7
				for index in [a, b, a + 7, b, b + 7, a + 7]: st.add_vertex(verts[index])
		for j in 7:
			st.add_vertex(verts[14 + j])
			st.add_vertex(verts[14 + (j + 1) % 7])
			st.add_vertex(Vector3(0.1, 1.14, -0.1))
		st.set_smooth_group(-1)
		st.generate_normals()
		var mesh := st.commit()
		var mat := material("Limestone%d" % variant, ["a3957b", "96886f", "b0a185", "9c8e76", "bbae94"][variant])
		mesh.surface_set_material(0, mat)
		# Shared meshes auto-instance in Forward+. Individual nodes also serialize
		# correctly with the headless renderer and remain editable in the scene.
		for i in 38:
			var center: Vector2 = [Vector2(-48, 18), Vector2(38, -38), Vector2(57, 24), Vector2(-58, -42), Vector2(8, 58)][i % 5]
			var size := rng.randf_range(0.35, 1.5)
			if i < 5: size *= 2.6
			var p := center + Vector2(rng.randfn(0, 8), rng.randfn(0, 5))
			for attempt in 60:
				if clear_of_patrol(p, size * 1.5 + 3.5): break
				p = center + Vector2(rng.randfn(0, 13), rng.randfn(0, 10))
			if not clear_of_patrol(p, size * 1.5 + 3.5): continue
			if i % 5 == 0:
				var supplied := grounded(rocks, "EndlessDriveRock%d_%d" % [variant, i], p.x, p.y, rng.randf_range(-PI, PI))
				supplied.position.y -= 0.08
				var rock := EndlessModels.environment(supplied, "res://assets/models/supplied_environment/rock_desert_%d.tscn" % (variant % 4 + 1), "DesertRock")
				rock.scale = Vector3.ONE * size * 1.8
				continue
			var basis := Basis(Vector3.UP, rng.randf_range(-PI, PI)).scaled(Vector3(size * 1.5, size * 0.8, size))
			var instance := mesh_node(rocks, "Limestone%d_%02d" % [variant, i], mesh, mat, Vector3(p.x, Surface.height_at(p.x, p.y) - 0.1, p.y))
			instance.basis = basis
			if size > 1.1:
				var body := StaticBody3D.new()
				body.name = "RockCollision"
				rocks.add_child(body)
				body.position = instance.position + Vector3.UP * size * 0.4
				var shape := CollisionShape3D.new()
				var sphere := SphereShape3D.new()
				sphere.radius = size * 0.8
				shape.shape = sphere
				body.add_child(shape)

func clear_of_patrol(p: Vector2, clearance: float) -> bool:
	for lane in 3:
		for i in 90:
			var path := Surface.route(float(i) / 90.0 * TAU, lane)
			if p.distance_squared_to(Vector2(path.x, path.z)) < clearance * clearance: return false
	return true

func make_utility_line() -> void:
	var poles := group(scene, "SilentUtilityLine")
	var previous := Vector3.ZERO
	for i in 7:
		var x := -65.0 + i * 18.0
		var z := -40.0 + i * 1.8
		var n := grounded(poles, "UtilityPole", x, z)
		cylinder(n, "Pole", Vector3(0, 4.1, 0), 0.13, 8.2, materials.Wood, 7)
		box(n, "Crossbar", Vector3(0, 7.4, 0), Vector3(0.18, 0.2, 2.6), materials.Wood)
		for offset in [-1.05, 1.05]: cylinder(n, "Insulator", Vector3(0, 7.63, offset), 0.13, 0.27, materials.ConcreteLight, 8)
		var top := n.position + Vector3(0, 7.8, 0)
		if i > 0:
			for side in [-1.05, 1.05]:
				for segment in 10:
					var t := float(segment) / 10
					var u := float(segment + 1) / 10
					var a := previous.lerp(top, t) + Vector3(0, -sin(t * PI) * 1.3, side)
					var b := previous.lerp(top, u) + Vector3(0, -sin(u * PI) * 1.3, side)
					beam(poles, "SaggingCable", a, b, 0.025, materials.Metal)
		previous = top

func person(parent: Node, x: float, z: float, yaw: float) -> void:
	var n := grounded(parent, "PatrolFigure", x, z, yaw)
	box(n, "Torso", Vector3(0, 1.12, 0), Vector3(0.44, 0.62, 0.27), materials.Uniform)
	for side in [-1.0, 1.0]:
		beam(n, "Leg", Vector3(side * 0.13, 0.85, 0), Vector3(side * 0.16, 0.14, side * 0.13), 0.09, materials.Uniform)
		box(n, "Boot", Vector3(side * 0.16, 0.08, side * 0.13 - 0.06), Vector3(0.18, 0.16, 0.32), materials.Tyres)
		beam(n, "Arm", Vector3(side * 0.28, 1.35, 0), Vector3(side * 0.24, 0.95, -0.18), 0.075, materials.Uniform)
	cylinder(n, "Head", Vector3(0, 1.57, 0), 0.14, 0.27, materials.Skin, 8)
	cylinder(n, "Helmet", Vector3(0, 1.72, 0), 0.18, 0.17, materials.Olive, 8)
	box(n, "Pack", Vector3(0, 1.17, 0.23), Vector3(0.34, 0.42, 0.2), materials.Canvas)

func assign_owner(parent: Node) -> void:
	for child in parent.get_children():
		child.owner = scene
		assign_owner(child)
