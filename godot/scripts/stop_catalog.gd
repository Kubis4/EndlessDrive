class_name DriveStops
extends RefCounted
## Pure, random-access stop metadata; IDs remain the owning terrain chunk index.
## One stop per 3.84 km district, jittered within 28..100 m.
## No streaming, scene tree, global RNG or previous-stop traversal is required.

const CHUNK_LENGTH := 96.0
const REGION_CHUNKS := 40
const REGION_LENGTH := CHUNK_LENGTH * REGION_CHUNKS
const KINDS := ["workshop", "home", "gas", "repair", "paint"]
const TITLES := {
	"workshop": "Cestná dielňa", "home": "Dom pri ceste",
	"gas": "Čerpacia stanica", "repair": "Autoservis", "paint": "Lakovňa",
}


static func for_chunk(index: int, seed: int) -> Dictionary:
	if index < 0 or index % REGION_CHUNKS > 1:
		return {}
	var stop := _in_region(int(index / REGION_CHUNKS), seed)
	return stop if int(stop.id) == index else {}


static func by_id(id: int, seed: int) -> Dictionary:
	var stop := for_chunk(id, seed)
	if not stop.is_empty():
		return stop
	# Old saves/loot tests can refer to former every-third-chunk workshops.
	# Resolve their original metadata without adding them to the sparse world.
	return _metadata(id, "workshop", id * CHUNK_LENGTH + 42.0, -18.0)


static func next_stop(x: float, seed: int) -> Dictionary:
	if not is_finite(x):
		return {}
	var region := maxi(0, int(floor(x / REGION_LENGTH)))
	var stop := _in_region(region, seed)
	if float(stop.x) < x:
		stop = _in_region(region + 1, seed)
	stop["distance"] = maxf(0.0, float(stop.x) - x)
	return stop


static func _in_region(region: int, seed: int) -> Dictionary:
	if region == 0:
		return _metadata(0, "workshop", 42.0, -18.0)
	var rng := RandomNumberGenerator.new()
	rng.seed = seed + region * 104729
	var cycle := _kind_cycle(seed)
	var phase := posmod(region - 1, 5)
	var others := cycle.filter(func(value: String) -> bool: return value != "gas")
	# Fixed fuel-service cadence remains fair even when a route changes the seed.
	var kind: String = "gas" if phase == 1 else others[phase if phase < 1 else phase - 1]
	var profile := DriveTerrain.new()
	profile.seed_value = seed
	var side := -1.0 if posmod(region + seed, 2) == 0 else 1.0
	# The region's opening stretch is well before its fork (360..600 m).
	# Reject steep lateral bends that could put a building corner in the rocks.
	for attempt in 24:
		var x := region * REGION_LENGTH + rng.randf_range(28.0, 100.0)
		var offset := side * rng.randf_range(18.0, 23.0)
		if _clear_footprint(profile, x, offset):
			return _metadata(int(floor(x / CHUNK_LENGTH)), kind, x, offset)
	# A narrow, predictable fallback on the calm start of the section.
	var x := region * REGION_LENGTH + 28.0
	return _metadata(int(floor(x / CHUNK_LENGTH)), kind, x, side * 18.0)


static func _clear_footprint(profile: DriveTerrain, x: float, offset: float) -> bool:
	var z := profile.center_at(x) + offset
	for dx: float in [-6.0, 0.0, 6.0]:
		var lateral := absf(z - profile.center_at(x + dx))
		if lateral + 4.0 > profile.boundary_width(x + dx) - 9.0:
			return false
		if lateral - 4.0 < 9.0 or not profile.fork_at(x + dx).is_empty():
			return false
	return true


static func _kind_cycle(seed: int) -> Array:
	# Repeat one seeded Fisher-Yates permutation. Reshuffling each group could
	# separate two gas stations by nine stops; repeating bounds the gap to five.
	var cycle := KINDS.duplicate()
	var rng := RandomNumberGenerator.new()
	rng.seed = seed + 6029
	for i in range(cycle.size() - 1, 0, -1):
		var j := rng.randi_range(0, i)
		var previous: String = cycle[i]
		cycle[i] = cycle[j]
		cycle[j] = previous
	return cycle


static func _metadata(id: int, kind: String, x: float, offset: float) -> Dictionary:
	return {"id": id, "kind": kind, "title": TITLES[kind],
		"local_x": x - id * CHUNK_LENGTH, "offset": offset, "x": x}
