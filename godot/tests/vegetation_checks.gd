extends SceneTree
var failures := 0
var checks := 0

func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
	if not ok: failures += 1

func _run() -> void:
	for label: String in ["broadleaf_a", "broadleaf_b", "broadleaf_c", "calocedrus", "bush", "grass"]:
		var scene := BiomeModels.supplied(label)
		var mesh := scene.get_node("Foliage") as MeshInstance3D
		check(mesh.mesh.get_surface_count() == 1 and mesh.mesh.resource_path.ends_with(".res"), label + " uses one shared baked surface")
		check(int(scene.get_meta("triangles")) <= (3700 if label == "calocedrus" else 420), label + " respects its measured triangle budget")
		check(mesh.material_override.get_shader_parameter("plants_atlas") is Texture2D, label + " uses the supplied textures")
		if label == "broadleaf_a":
			var config := ConfigFile.new()
			config.load("res://assets/models/vegetation/plants-atlas.png.import")
			check(bool(config.get_value("params", "mipmaps/generate", false)), "Distant alpha-clipped foliage has mipmaps to suppress sparkle")
		if label not in ["bush", "grass"]:
			check(scene.has_node("TrunkBody/TrunkCollision"), label + " has a fitted trunk collider")
		scene.free()
	var world := DriveWorld.new()
	root.add_child(world)
	world.set_process(false)
	var total := 0
	for seed in [24601, 1000004]:
		world.configure(seed)
		var first := -1
		for index in range(0, 100):
			if world.terrain.biome_at(index * 96 + 48) == 3 and world.terrain.biome_at((index + 1) * 96 + 48) == 3:
				first = index
				break
		check(first >= 0, "Forest fixture exists for seed %d" % seed)
		if first < 0: continue
		world._create_chunk(first)
		world._create_chunk(first + 1)
		var sites := _sites(world)
		total += sites.size()
		var separated := true
		var grounded := true
		for i in sites.size():
			var p: Vector3 = sites[i].position
			grounded = grounded and absf(world.rendered_height_at(p.x, p.z) - p.y) <= 0.60
			for j in range(i + 1, sites.size()):
				var q: Vector3 = sites[j].position
				if Vector2(p.x, p.z).distance_to(Vector2(q.x, q.z)) < float(sites[i].radius) + float(sites[j].radius) + 0.5: separated = false
		check(separated, "Crowns have clearance within and across chunks for seed %d" % seed)
		check(grounded, "Snapped tree roots follow the rendered terrain for seed %d" % seed)
		check(sites.size() > 0 and sites.size() <= 64, "Two forest chunks retain a bounded sparse population for seed %d: %d" % [seed, sites.size()])
		var signatures := _signatures(sites)
		world.configure(seed)
		world._create_chunk(first + 1)
		world._create_chunk(first)
		check(signatures == _signatures(_sites(world)), "Chunk generation order preserves the same trees for seed %d" % seed)
		check(world.get_tree().get_nodes_in_group("winch_anchor").any(func(node: Node) -> bool: return world.is_ancestor_of(node) and node.get_meta("anchor_kind", "") == "tree"), "Sparse forest still supplies gameplay winch anchors")
	check(total > 8, "Spacing checks actually cover populated forests")
	world.queue_free()
	await process_frame
	print("VEGETATION: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)

func _sites(world: DriveWorld) -> Array:
	var result: Array = []
	for chunk: Node3D in world.chunks.values(): result.append_array(chunk.get_meta("tree_sites", []))
	return result

func _signatures(sites: Array) -> Array:
	var result: Array = []
	for site: Dictionary in sites: result.append(str(site.position) + "/" + str(site.variant))
	result.sort()
	return result
