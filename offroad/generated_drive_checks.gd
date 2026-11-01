extends SceneTree
const G = preload("res://offroad/generated_terrain.gd")
var game
var car
var exp
func _initialize() -> void: call_deferred("run")
func run() -> void:
	game = load("res://offroad/generated_expedition.tscn").instantiate()
	root.add_child(game)
	game.soil.threaded = false
	exp = game.get_node("Expedition")
	exp.save_path = "res://build/generated-drive-save.json"
	car = game.lead
	await physics_frame
	var line: PackedVector2Array = G.paths[0]
	var target: Vector2 = exp.sites[0].point
	var goal := 0
	var best := INF
	for i in line.size():
		var d := line[i].distance_squared_to(target)
		if d < best:
			best = d
			goal = i
	print("DRIVE goal index=", goal, " distance=", target.distance_to(exp.camp))
	var out_ok := drive(line, 0, goal, 1)
	print("DRIVE outbound=", out_ok, " position=", car.position)
	if out_ok:
		for i in 180: car.simulate(1.0 / 30.0, 0.0, 1.0, 0.0, false)
		exp.interact()
		var looted: bool = 0 in exp.explored
		# Turn the car around as a deliberate manoeuvre, preserving its location.
		# The return traversal still uses the real force integrator and collisions.
		car.place_on_ground(car.position, car.heading + PI)
		var return_ok := drive(line, goal, 0, -1)
		# Independently exercise the detour; the controller starts at its junction.
		var detour: PackedVector2Array = G.paths[1]
		var direction := detour[1] - detour[0]
		car.place_on_ground(Vector3(detour[0].x, 0, detour[0].y), atan2(-direction.x, -direction.y))
		var detour_ok := drive(detour, 0, detour.size() - 1, 1)
		print("DRIVE loot=", looted, " return=", return_ok, " detour=", detour_ok, " start=", detour[0], " goal=", detour[-1], " position=", car.position, " speed=", car.speed)
		game.queue_free()
		await process_frame
		quit(0 if looted and return_ok and detour_ok else 1)
	else: quit(1)
func drive(line: PackedVector2Array, start: int, finish: int, direction: int) -> bool:
	var index := start
	var goal: Vector2 = line[finish]
	for tick in 3600:
		var pos := Vector2(car.position.x, car.position.z)
		if pos.distance_to(goal) < 8.0: return true
		while index != finish and pos.distance_to(line[index]) < 8.0:
			index = clampi(index + direction, mini(start, finish), maxi(start, finish))
		var look := clampi(index + direction * 3, mini(start, finish), maxi(start, finish))
		var delta: Vector2 = line[look] - pos
		var angle := wrapf(atan2(-delta.x, -delta.y) - car.heading, -PI, PI)
		var steering := atan2(4.0 * car.half_wheelbase * sin(angle), maxf(4.0, delta.length()))
		var turn: float = clampf(steering / car.steering_limit(), -1.0, 1.0)
		var throttle: float = clampf((5.0 - car.speed) * 0.6, 0.0, 0.8)
		var brake: float = clampf((car.speed - 5.5) * 0.5, 0.0, 1.0)
		car.simulate(1.0 / 30.0, throttle, brake, turn, false)
	return false
