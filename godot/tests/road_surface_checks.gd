extends SceneTree
## Road banks/materials must agree across chunks and during soil refinement.
var checks := 0
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])

func _run() -> void:
	var texture := load("res://assets/textures/road-weathering.png") as Texture2D
	check(texture != null and texture.get_size() == Vector2(1024, 1024) and texture.get_image().has_mipmaps(), "Packed road wear has mipmaps for distant driving views")
	var profile := DriveTerrain.new()
	var other := DriveTerrain.new()
	for biome in 6:
		profile.seed_value = 1000001 + biome
		profile.clear_caches()
		other.seed_value = profile.seed_value
		var minimum := 1.0
		var maximum := 0.0
		var asymmetric := 0.0
		var stable := true
		var normalized := true
		for x in range(24, 330, 3):
			var center := profile.center_at(x)
			var left := profile.road_coverage(x, center - 5.25, -5.25)
			var right := profile.road_coverage(x, center + 5.25, 5.25)
			minimum = minf(minimum, left)
			maximum = maxf(maximum, left)
			asymmetric = maxf(asymmetric, absf(left - right))
			stable = stable and is_equal_approx(left, other.road_coverage(x, center - 5.25, -5.25))
			stable = stable and profile.road_coverage(x, center, 0) == 1.0 and profile.road_coverage(x, center + 9.0, 9.0) == 0.0
			for offset: float in [-7, -5, -2, 0, 2, 5, 7]:
				var data := profile.sample(x, center + offset, false)
				var sum := 0.0
				for weight: float in data.material_weights:
					sum += weight
					normalized = normalized and weight >= 0 and weight <= 1.0001
				normalized = normalized and absf(sum - 1.0) < 0.0001
		check(maximum - minimum > 0.25 and asymmetric > 0.12 and stable, "Biome %d has seeded asymmetric banks and a continuous usable centre" % biome)
		check(normalized, "Biome %d retains normalized surface blends through worn shoulders" % biome)
	profile.seed_value = 1000003
	profile.clear_caches()
	var a := DriveTerrainBake.bake(profile, 0)
	var b := DriveTerrainBake.bake(profile, 1)
	var width: int = a.width
	var colors_a: PackedColorArray = a.arrays[Mesh.ARRAY_COLOR]
	var colors_b: PackedColorArray = b.arrays[Mesh.ARRAY_COLOR]
	var seam := true
	var coverage := true
	var normals_a: PackedVector3Array = a.arrays[Mesh.ARRAY_NORMAL]
	var normals_b: PackedVector3Array = b.arrays[Mesh.ARRAY_NORMAL]
	for col in width:
		seam = seam and colors_a[DriveTerrainBake.ROWS * width + col].is_equal_approx(colors_b[col])
		seam = seam and normals_a[DriveTerrainBake.ROWS * width + col].is_equal_approx(normals_b[col])
	for i in colors_a.size():
		var point: Vector3 = a.arrays[Mesh.ARRAY_VERTEX][i]
		coverage = coverage and is_equal_approx(colors_a[i].a, float(profile.sample(point.x, point.z, false).road))
	check(seam and coverage, "Shared chunk edges carry identical lighting normals, colour and physical road coverage")
	var road_point := Vector3(40, 0, profile.center_at(40) + 6)
	profile.deformation.load_soil(road_point, {"soil_softness": 0.8, "soil_bearing": 0.6, "soil_depth": 0.7}, 1.2, 0.9, 1.0, 1.0)
	var band := floori(road_point.x / DriveTerrainBake.BAND_ROWS)
	var refined := preload("res://scripts/terrain_detail.gd").build(profile.deformation, a.arrays, a.rest_heights, width, band)
	var stable_coverage := true
	for color: Color in refined.arrays[Mesh.ARRAY_COLOR]: stable_coverage = stable_coverage and color.a >= 0 and color.a <= 1.0
	check(refined.refined_quads > 0 and stable_coverage, "Physical rut refinement preserves bounded road coverage without new painted tracks")
	var world := DriveWorld.new()
	root.add_child(world)
	var materials_bound := true
	for tier in 3:
		world.set_graphics_quality(tier)
		var material := ShaderMaterial.new()
		world._configure_ground_material(material)
		materials_bound = materials_bound and material.get_shader_parameter("road_weathering") == texture and material.get_shader_parameter("weathered_asphalt") != null
	check(materials_bound, "All three graphics tiers bind the same road wear and asphalt sources")
	world.free()
	var previews_bound := true
	for label: String in ["editor_terrain", "bridge", "alpine", "forest", "canyon", "swamp", "mountain", "rail", "tunnel", "deformation", "junction_gallery"]:
		var preview := (load("res://scenes/world/generated/" + label + ".scn") as PackedScene).instantiate()
		var found := false
		for ground: Node in preview.find_children("SandAndTracks", "MeshInstance3D", true, false):
			found = true
			var material: ShaderMaterial = ground.material_override
			previews_bound = previews_bound and material.get_shader_parameter("road_weathering") == texture
		previews_bound = previews_bound and found
		preview.free()
	check(previews_bound, "All eleven saved road/deformation/junction previews bind the updated road wear")
	print("ROAD SURFACES: %d checks, %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
