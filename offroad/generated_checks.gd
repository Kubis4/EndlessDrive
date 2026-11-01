extends SceneTree
const G = preload("res://offroad/generated_terrain.gd")
const Surface = preload("res://desert_battlefield/terrain.gd")
var failures := 0
func check(ok: bool, label: String) -> void:
	if ok: print("PASS: ", label)
	else:
		failures += 1
		push_error(label)
func road_metrics() -> Vector2:
	var steepest := 0.0
	var deepest := 0.0
	for line: PackedVector2Array in G.paths:
		for i in range(1, line.size()):
			var a := line[i - 1]
			var b := line[i]
			var d := a.distance_to(b)
			if d > 0.1:
				steepest = maxf(steepest, absf(G.height_at(a.x, a.y) - G.height_at(b.x, b.y)) / d)
			deepest = maxf(deepest, G.water_depth_at(b.x, b.y))
	return Vector2(steepest, deepest)
func _initialize() -> void: call_deferred("run")
func run() -> void:
	G.generate(123)
	var h1 := hash(G.heights)
	var g1 := hash(G.ground)
	var p1 := hash(G.paths)
	var c1 := G.CAMP
	G.generate(123)
	check(hash(G.heights) == h1 and hash(G.ground) == g1 and hash(G.paths) == p1 and G.CAMP == c1, "same seed generates identical terrain, surfaces and roads")
	G.generate(456)
	check(hash(G.heights) != h1 and hash(G.paths) != p1, "different seed generates different terrain and roads")
	var metrics := road_metrics()
	check(metrics.x < 0.32 and metrics.y < 0.6, "seed 456 has bounded road grade and shallow dry fords")
	var game = load("res://offroad/generated_expedition.tscn").instantiate()
	root.add_child(game)
	var e = game.get_node("Expedition")
	e.save_path = "res://build/generated-test-save.json"
	var first_road: PackedVector2Array = G.paths[0]
	for progress in [0.0, 0.04, 0.10]:
		var p: Vector2 = first_road[roundi(progress * (first_road.size() - 1))]
		check(G.surface_at(p.x, p.y) == Surface.ROAD, "starting road is physical asphalt at %.0f%%" % (progress * 100.0))
	var dry_grip := Surface.grip(Surface.ROAD)
	Surface.wetness = 1.0
	check(Surface.grip(Surface.ROAD) < dry_grip and Surface.deform(Surface.ROAD) == 0.0, "wet asphalt loses grip but does not rut")
	Surface.wetness = 0.0
	check(game.soil.fine_material.shader.resource_path == "res://offroad/highlands_ground.gdshader", "fine rut surface uses the shared highlands ground shader")
	check(game.soil.fine_material.get_shader_parameter("asphalt_albedo") != null, "asphalt texture is bound")
	check(e.seed_value == G.seed_value and e.sites.size() == 8, "scene generates eight seeded sites")
	metrics = road_metrics()
	print("ROAD maximum sampled grade=", metrics.x, " ford depth=", metrics.y)
	check(metrics.x < 0.32 and metrics.y < 0.6, "seed 123 has bounded road grade and shallow dry fords")
	check(G.paths.size() >= 6 and G.paths[0][0].distance_to(G.paths[0][-1]) > 300.0, "open route, bypass and branches exist")
	var car = game.lead
	var fuel: float = e.fuel
	var oil_before: float = e.oil
	e.previous_position = car.position - Vector3(10, 0, 0)
	e._process(1.0)
	check(e.fuel < fuel and e.oil < oil_before, "fuel and oil consumed by movement")
	e.oil = 2.0
	e.coolant = 4.0
	e.service()
	check(e.oil > 2.0 and e.coolant > 4.0, "roadside service replenishes fluids from cargo")
	var site: Dictionary = e.sites[2]
	car.place_on_ground(Vector3(site.point.x, 0, site.point.y), 0)
	car.speed = 0
	e.interact()
	check(2 in e.explored and e.cargo.tank == 1, "site yields upgrade and loot")
	e.condition = 60.0
	e.fuel = 30.0
	car.place_on_ground(Vector3(e.camp.x, 0, e.camp.y), 0)
	car.speed = 0
	e.interact()
	check(e.upgraded and e.fuel > 30.0 and e.condition > 60.0 and e.completed_trips == 1, "return completes trip; camp installs upgrade, refuels and repairs")
	var before: float = e.fuel
	var saved_oil: float = e.oil
	var saved_coolant: float = e.coolant
	e._save_game()
	e.fuel = 0
	e.oil = 0
	e.coolant = 0
	e.explored.clear()
	e._load_game()
	check(e.fuel == before and e.oil == saved_oil and e.coolant == saved_coolant and e.upgraded and 2 in e.explored, "save restores expedition and fluids")
	var destination: Vector2 = G.paths[0][-1]
	car.place_on_ground(Vector3(destination.x, 0, destination.y), 0)
	car.speed = 0
	e.interact()
	check(e.reached_goal, "destination completes the road trip")
	e.reached_goal = false
	e._load_game()
	check(e.reached_goal, "destination completion persists")
	var weather = game.test_grounds.weather
	weather._enter(3)
	for i in 600: weather.update(0.1)
	check(Surface.wetness > 0.3 and G.flood > 0.0, "downpour wets terrain and raises generated river")
	print("GENERATED CHECK failures=", failures)
	quit(1 if failures else 0)
