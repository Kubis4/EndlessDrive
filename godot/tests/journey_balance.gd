extends SceneTree
## Resource-planning Monte Carlo, NOT a replacement for the separate real-physics drive.
## Follows the upper road, stops at all buildings, collects only useful stock, repairs,
## and uses full cruise consumption. All runs are in memory; no player's save is touched.

const HORIZON := 120000.0
var failures := 0
var minimum_fuel := INF
var minimum_health := INF
var longest_gap := 0.0
var checked_stops := 0
var kind_counts: Dictionary = {}


func _initialize() -> void:
	call_deferred("_run")


func _run() -> void:
	var distances: Array[float] = []
	for seed_index in 64:
		var state := DriveRun.new()
		state.world_seed = DriveTerrain.VARIED_SEED_MIN + seed_index * 7919
		DriveProfile.new().prepare_run(state)
		var profile := DriveTerrain.new()
		profile.seed_value = state.world_seed
		var next := DriveStops.next_stop(0, state.world_seed)
		var previous_stop := 0.0
		var x := 0.0
		while x < HORIZON:
			var fork := profile.fork_at(x)
			if not fork.is_empty() and x >= float(fork.start) + 96 and not profile.route_choices.has(str(fork.id)):
				profile.route_choices[str(fork.id)] = -1 if posmod(state.world_seed + int(fork.id) * 17, 3) == 0 else 1
				profile.clear_caches()
				state.route_choices = profile.route_choices.duplicate()
				next = DriveStops.next_stop(x, profile.seed_at(x + 200))
			if x >= float(next.x):
				longest_gap = maxf(longest_gap, float(next.x) - previous_stop)
				previous_stop = float(next.x)
				_visit(state, next)
				next = DriveStops.next_stop(float(next.x) + 1.0, profile.seed_at(float(next.x) + 1.0))
			if state.fuel < 3.0: state.top_up("fuel")
			if state.oil < 2.4: state.top_up("oil")
			if state.coolant < 3.8: state.top_up("coolant")
			var z := profile.road_centers(x)[0]
			var surface := profile.sample(x, z)
			var slope := (profile.base_height(x + 1) - profile.base_height(x - 1)) * 0.5
			# Conservative travel speed; hills and water take longer and incur more idle cost.
			var speed := clampf(18.0 - absf(slope) * 18.0 - float(surface.drag) * 1.5, 5.0, 18.0)
			state.engine_running = state.fuel > 0.0 and float(state.parts.engine) > 0.05
			if not state.engine_running: break
			state.advance(50.0 / speed, speed, 1.0, false, true)
			state.distance = x
			minimum_fuel = minf(minimum_fuel, state.fuel + float(state.inventory.fuel))
			minimum_health = minf(minimum_health, float(state.parts.engine))
			x += 50.0
		distances.append(x)
		if x < HORIZON:
			failures += 1
			push_error("BALANCE: seed %d stopped at %.1f km fuel=%.2f engine=%.2f" % [state.world_seed, x / 1000.0, state.fuel, state.parts.engine])
	# Distances/parts/quality are modelled; steering/obstacles are covered by driving_regression.gd.
	print("BALANCE: %d seeds x %.0f km; completed=%d; failures=%d; stops=%d" % [distances.size(), HORIZON / 1000.0, distances.size() - failures, failures, checked_stops])
	print("BALANCE: longest stop gap %.1f m; minimum combined fuel reserve %.2f l; minimum engine condition %.1f %%" % [longest_gap, minimum_fuel, minimum_health * 100])
	print("BALANCE: stop mix ", kind_counts)
	var no_stops := DriveRun.new()
	DriveProfile.new().prepare_run(no_stops)
	no_stops.engine_running = true
	var control_distance := 0.0
	while control_distance < HORIZON:
		if no_stops.fuel < 0.1: no_stops.top_up("fuel")
		if no_stops.fuel <= 0 or float(no_stops.parts.engine) <= 0.02: break
		no_stops.engine_running = true
		no_stops.advance(50.0 / 15.0, 15.0, 1.0, false, true)
		control_distance += 50
	print("BALANCE CONTROL: no building stops, initial supplies only: %.2f km; fuel %.2f l; engine %.1f %%" % [control_distance / 1000.0, no_stops.fuel, float(no_stops.parts.engine) * 100])
	# One service per district, with 28..100 m jitter. This remains a spacing
	# bound, not evidence that a car physically drove the planned journey.
	quit(0 if failures == 0 and longest_gap <= DriveStops.REGION_LENGTH + 72.0 else 1)


func _visit(state: DriveRun, stop: Dictionary) -> void:
	checked_stops += 1
	kind_counts[stop.kind] = int(kind_counts.get(stop.kind, 0)) + 1
	state.engine_running = false
	var contents := state.open_loot(int(stop.id)).duplicate(true)
	for item: Dictionary in contents:
		var amount := float(item.amount)
		if item.kind in DriveRun.FLUIDS:
			var reserve: float = {"fuel": 14.0, "oil": 2.0, "coolant": 4.0}[item.kind]
			amount = minf(amount, maxf(0.0, reserve - float(state.inventory[item.kind])))
		elif item.kind == "paintcan":
			continue
		elif item.kind in ["repairkit", "batterycell"]:
			amount = minf(amount, maxf(0.0, 2.0 - float(state.inventory[item.kind])))
		elif item.kind == "part":
			if float(item.condition) < float(state.parts[item.slot]) + 0.05: continue
		if amount <= 0 or not state.can_take(item, amount): continue
		state.take_loot(int(stop.id), str(item.uid), amount)
		if item.kind == "part":
			state.install_spare(str(item.uid))
			state.salvage_spare(str(item.uid))
	for kind: String in DriveRun.FLUIDS: state.top_up(kind)
	for slot: String in state.parts:
		if float(state.parts[slot]) < 0.6:
			if float(state.inventory.repairkit) >= 1.0: state.use_supply("repairkit", slot)
			elif float(state.inventory.scrap) >= 2.0: state.repair(slot)
	if state.battery < 0.3 and float(state.inventory.batterycell) >= 1.0: state.use_supply("batterycell")
	if stop.kind == "gas" and state.fuel < 12.0: state.buy_fuel(int(stop.id), 10.0)
