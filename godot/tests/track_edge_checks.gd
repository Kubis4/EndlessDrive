extends SceneTree
const Edges = preload("res://scripts/track_edges.gd")
var checks := 0
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func frames(n := 2) -> void:
	for i in n:
		await physics_frame
		await process_frame
func _run() -> void:
	var world := DriveWorld.new()
	root.add_child(world)
	world.batch_scenery = false
	world.configure(24601)
	for index in range(-1, 4): world._create_chunk(index)
	var ids := {}
	var posts := {}
	var unique := true
	var grounded := true
	var joined := true
	var pairs := 0
	var owners := true
	for index: int in world.chunks:
		var chunk: Node3D = world.chunks[index]
		for id: int in chunk.get_meta("fence_sites", {}):
			var site: Dictionary = chunk.get_meta("fence_sites")[id]
			unique = unique and not ids.has(id)
			ids[id] = site
			owners = owners and id * 5.0 >= index * 96.0 and id * 5.0 < (index + 1) * 96.0
			for point: Vector3 in site.points:
				grounded = grounded and absf(point.y - world.rendered_height_at(point.x, point.z)) < 0.04
			for point: Vector3 in site.posts:
				var key := Vector2i(roundi(point.x * 1000), roundi(point.z * 1000))
				unique = unique and not posts.has(key)
				posts[key] = true
		for fence: Node in chunk.find_children("RanchFence_*", "Node3D", true, false):
			for post: MeshInstance3D in fence.find_children("FencePost*", "MeshInstance3D", true, false):
				var base := world.to_local(post.to_global(Vector3.DOWN * 0.5))
				grounded = grounded and absf(base.y - world.rendered_height_at(base.x, base.z) + 0.08) < 0.04
	for id: int in ids:
		if not ids.has(id + 1): continue
		pairs += 1
		joined = joined and (ids[id].points[-1] as Vector3).distance_to(ids[id + 1].points[0]) < 0.04
	check(ids.size() > 20 and unique and owners, "Fence spans/posts have one global owner across five adjacent chunks")
	check(grounded and pairs > 15 and joined, "Real posts seat in the rendered slope and adjoining fence wires share endpoints")
	var chunk: Node3D = world.chunks[0]
	var count := chunk.get_child_count()
	var fence_id: int = chunk.get_meta("fence_sites", {}).keys()[0]
	world._place_fence(chunk, fence_id * 5.0, [])
	check(chunk.get_child_count() == count, "Publishing an already-owned fence cannot create a second parallel panel")
	var asset_shapes := {}
	var valid_assets := true
	for snowy: bool in [false, true]:
		for kind: String in Edges.KINDS:
			var asset := (load("res://assets/models/boundary/" + kind + ("_snow" if snowy else "") + ".tscn") as PackedScene).instantiate()
			var visual := asset.get_node("TexturedRock") as MeshInstance3D
			var vertices: PackedVector3Array = visual.mesh.surface_get_arrays(0)[Mesh.ARRAY_VERTEX]
			var material := visual.material_override as ShaderMaterial
			var hull := asset.get_node("BoundaryRockBody/RockHull").shape as ConvexPolygonShape3D
			valid_assets = valid_assets and vertices.size() > 100 and vertices.size() < 2400 and hull.points.size() >= 20
			valid_assets = valid_assets and material.get_shader_parameter("rock_albedo") is Texture2D and material.shader.resource_path.ends_with("rock_surface.gdshader")
			asset_shapes[kind + "/" + str(vertices.size())] = true
			asset.free()
	check(valid_assets and asset_shapes.size() == 5, "Five distinct native rock models and snow variants contain real textures and convex collisions")
	var kinds := {}
	var heights := {}
	var deterministic := true
	var profile := DriveTerrain.new()
	for biome in 6:
		profile.seed_value = 1000001 + biome
		profile.clear_caches()
		for slot in 24:
			for side: float in [-1, 1]:
				var a := Edges.site(profile, slot * 8.0, side, false)
				var b := Edges.site(profile, slot * 8.0, side, false)
				kinds[a.kind] = true
				heights[snappedf((a.transform as Transform3D).basis.y.length(), 0.5)] = true
				deterministic = deterministic and a == b and (a.transform as Transform3D).basis.determinant() > 0
	check(kinds.size() == 5 and heights.size() > 8 and deterministic, "Seeded boundaries mix all five shapes, heights and positive-scale rotations reproducibly")
	for fixture: Array in [[1000004, 96.0], [1000006, 192.0], [24601, 3200.0]]:
		world.configure(int(fixture[0]))
		var index := floori(float(fixture[1]) / 96.0)
		for owner_index in range(index - 1, index + 2): world._create_chunk(owner_index)
		await frames()
		var rocks := world.get_tree().get_nodes_in_group("boundary_rock")
		var excluded: Array[RID] = []
		for object: CollisionObject3D in world.find_children("*", "CollisionObject3D", true, false):
			if not object.is_in_group("boundary_rock"): excluded.append(object.get_rid())
		var blocked := true
		var visible := world.find_children("BoundaryFoothills", "MeshInstance3D", true, false).is_empty()
		for owner_chunk: Node3D in world.chunks.values(): visible = visible and owner_chunk.has_node("BoundaryRocks")
		for dx: float in [-1, 0, 1, 7, 8, 9]:
			var x := float(fixture[1]) + dx
			for side: float in [-1, 1]:
				var from := Edges.edge(world.terrain, x, side, -13.0) + Vector3.UP * 1.2
				var to := Edges.edge(world.terrain, x, side, 14.0) + Vector3.UP * 1.2
				var query := PhysicsRayQueryParameters3D.create(world.to_global(from), world.to_global(to))
				query.exclude = excluded
				var hit := world.get_world_3d().direct_space_state.intersect_ray(query)
				var solid: bool = not hit.is_empty() and hit.collider.is_in_group("boundary_rock")
				if not solid: print("ROCK GAP x=", x, " side=", side)
				blocked = blocked and solid
		check(visible and blocked and rocks.size() >= 60, "Visible rock hulls close both sides at x=%.0f and chunk joins, without the grey wall" % float(fixture[1]))
		var open := true
		var shape := SphereShape3D.new()
		shape.radius = 1.4
		for x: float in [float(fixture[1]) - 8, float(fixture[1]), float(fixture[1]) + 8]:
			for center: float in world.terrain.road_centers(x):
				var query := PhysicsShapeQueryParameters3D.new()
				query.shape = shape
				query.transform.origin = world.to_global(Vector3(x, world.height_at(x, center) + 1.0, center))
				query.exclude = excluded
				open = open and world.get_world_3d().direct_space_state.intersect_shape(query).is_empty()
		check(open, "Rock collisions leave every road/hairpin lane clear at x=%.0f" % float(fixture[1]))
	# Disable the distant fallback belt: only the visible rock hull may stop this car.
	world.configure(1000004)
	for index in range(0, 3): world._create_chunk(index)
	for boundary: Node in get_nodes_in_group("track_boundary"):
		if boundary is CollisionObject3D and not boundary.is_in_group("boundary_rock"): boundary.collision_layer = 0
	var state := DriveRun.new()
	for slot: String in state.parts: state.parts[slot] = 1.0
	state.fuel = 20
	var car := DriveVehicle.new()
	car.run = state
	car.world = world
	car.boundary_recovery_enabled = false
	var start := Edges.edge(world.terrain, 96, 1, -15)
	car.position = start + Vector3.UP * 0.6
	root.add_child(car)
	await frames(20)
	car.heading = PI * 0.5
	car.speed = 16.0
	await frames(110)
	print("ROCK IMPACT position=", car.position, " impacts=", car.obstacle_impacts, " rescues=", car.boundary_recoveries)
	check(car.obstacle_impacts > 0 and car.boundary_recoveries == 0 and car.position.z - world.terrain.corridor_center(car.position.x) < world.terrain.boundary_width(car.position.x), "A real car is stopped by the rock itself with the fallback belt and recovery disabled")
	car.free()
	world.free()
	var updated := true
	for label: String in ["editor_terrain", "bridge", "alpine", "forest", "canyon", "swamp", "mountain", "rail", "tunnel", "junction_gallery"]:
		var preview := (load("res://scenes/world/generated/" + label + ".scn") as PackedScene).instantiate()
		updated = updated and preview.find_children("BoundaryFoothills", "MeshInstance3D", true, false).is_empty()
		updated = updated and not preview.find_children("BoundaryRocks", "Node3D", true, false).is_empty()
		preview.free()
	check(updated, "Saved environment previews contain the varied rocks and no grey ribbon")
	print("TRACK EDGES: %d checks, %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
