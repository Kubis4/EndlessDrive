extends Node
## The sound of the drive, from the recordings in assets/audio: the engine (idle and
## load loops pitched by an engine speed worked out through the M1009's three-speed
## automatic), the ground under the wheels, wind, rain and thunder, skids, landings and
## knocks. Everything is mixed by the state of the car and the weather, every frame.
const Surface = preload("res://desert_battlefield/terrain.gd")
const DIR := "res://assets/audio/"
## The M1009's three-speed TH-400, the 3.08 axle, the tyre (m), and engine speeds (rpm).
const GEARS := [2.48, 1.48, 1.0]
const AXLE := 3.08
const TYRE := 0.415
const IDLE_RPM := 680.0
const MAX_RPM := 3600.0
const STALL_RPM := 1900.0
## Engine speed of the higher loop: the same steady engine as idle, at twice the speed.
const REV_RPM := 1360.0
## A shift waits this long after the last (s) and takes this long to settle; the engine's
## speed changes no faster than these (rpm/s): a heavy diesel behind a converter.
const SHIFT_WAIT := 3.0
const SHIFT_SETTLE := 0.45
const REV_UP := 700.0
const REV_DOWN := 500.0
const REV_SHIFT := 1900.0
## How long the starter turns before the engine is heard running.
const STARTER_TIME := 1.5
## Ground → [loop, volume, pitch at speed].
const GROUND := {
	Surface.ROAD: ["surface_gravel", 0.1, 1.35], Surface.WET_ROAD: ["surface_water", 0.22, 1.15],
	Surface.GRAVEL: ["surface_gravel", 0.55, 1.0], Surface.ROCK: ["surface_gravel", 0.42, 0.85],
	Surface.DIRT: ["surface_gravel", 0.3, 0.78], Surface.PACKED: ["surface_gravel", 0.26, 0.82],
	Surface.FOREST: ["surface_gravel", 0.24, 0.68], Surface.GRASS: ["surface_gravel", 0.22, 0.62],
	Surface.SAND: ["surface_gravel", 0.3, 0.6], Surface.CLAY: ["surface_mud", 0.4, 1.0],
	Surface.MUD: ["surface_mud", 0.7, 1.0], Surface.WATER: ["surface_water", 0.8, 1.0],
	Surface.SNOW: ["surface_ice", 0.42, 0.72], Surface.ICE: ["surface_ice", 0.4, 1.0],
}
var game: Node3D
var car: CharacterBody3D
var weather: Node
var loops := {}
var gear := 0
var rpm := IDLE_RPM
var load_share := 0.0
## The right foot as the gearbox feels it (slowly), and the time since the last shift.
var pedal := 0.0
var shift_clock := 10.0
## What spinning tyres add to the engine's speed, slowly (a converter absorbs the rest).
var spin_rev := 0.0
var engine_on := false
var start_clock := 0.0
var splat_clock := 1.0
var thunder_clock := 25.0
var landing_seen := 0
var plunge_seen := 0
var last_speed := 0.0
var random := RandomNumberGenerator.new()
var out_of_fuel := false
## A tyre has let go (one chirp to a slide), and the time to the next chirp.
var sliding := false
var chirp_clock := 0.0

func setup(root: Node3D) -> void:
	game = root
	car = root.lead
	weather = root.test_grounds.weather
	landing_seen = car.landing_count
	random.randomize()
	for name in ["engine_idle", "engine_rev", "wind", "rain", "skid", "brake", "surface_gravel", "surface_mud", "surface_water", "surface_ice"]:
		loops[name] = _loop(name)
	crank()

## The starter: the engine is heard running a moment later.
func crank() -> void:
	shot("engine_start", 0.8)
	start_clock = STARTER_TIME

func _stream(name: String) -> AudioStream:
	return load(DIR + name + ".ogg")

func _loop(name: String) -> AudioStreamPlayer:
	var player := AudioStreamPlayer.new()
	var stream := _stream(name) as AudioStreamOggVorbis
	stream.loop = true
	player.stream = stream
	player.volume_db = -80.0
	add_child(player)
	player.play(random.randf() * minf(stream.get_length(), 2.0))
	return player

## A one-off sound.
func shot(name: String, volume: float = 1.0, pitch: float = 1.0) -> void:
	var player := AudioStreamPlayer.new()
	player.stream = _stream(name)
	player.volume_db = linear_to_db(maxf(volume, 0.0001))
	player.pitch_scale = pitch
	player.finished.connect(player.queue_free)
	add_child(player)
	player.play()

func _mix(name: String, volume: float, pitch: float, delta: float) -> void:
	var player: AudioStreamPlayer = loops[name]
	var now := db_to_linear(player.volume_db)
	player.volume_db = linear_to_db(maxf(lerpf(now, volume, 1.0 - exp(-delta * 8.0)), 0.0001))
	player.pitch_scale = clampf(lerpf(player.pitch_scale, pitch, 1.0 - exp(-delta * 8.0)), 0.3, 3.0)

func _process(delta: float) -> void:
	var quiet: bool = game.paused
	for player: AudioStreamPlayer in loops.values(): player.stream_paused = quiet
	if quiet: return
	_engine(delta)
	_ground(delta)
	_air(delta)
	_knocks(delta)

## Engine speed through the gearbox: the converter slips while pulling away, the box
## changes up later under load; a spinning tyre takes the engine up with it.
func _engine(delta: float) -> void:
	start_clock -= delta
	var dry: bool = game.get_node("Expedition").fuel <= 0.0
	if dry and not out_of_fuel: shot("engine_stop", 0.8)
	out_of_fuel = dry
	engine_on = start_clock <= 0.0 and not dry and car.engine_running
	var speed := absf(car.speed)
	var effort := absf(car.last_throttle)
	load_share = lerpf(load_share, clampf(car.wheel_power / car.engine_power * 1.6, 0.0, 1.0) if effort > 0.02 else 0.0, 1.0 - exp(-delta * 1.2))
	var through := speed / TYRE * 60.0 / TAU * AXLE
	# The gearbox knows the foot at once when it goes down, and forgets it slowly: lifting for a
	# bend does not shift up, and a touch of the pedal does not shift down again. The speeds it
	# shifts up and down at lie far enough apart that it stays in its gear.
	pedal = lerpf(pedal, effort, 1.0 - exp(-delta / (0.35 if effort > pedal else 4.0)))
	shift_clock += delta
	var up_at := lerpf(1500.0, 3200.0, pedal)
	var down_at := lerpf(1200.0, 2000.0, pedal)
	if gear < GEARS.size() - 1 and (through * GEARS[gear] > MAX_RPM or (shift_clock > SHIFT_WAIT and through * GEARS[gear] > up_at)):
		gear += 1
		shift_clock = 0.0
	elif gear > 0 and shift_clock > SHIFT_WAIT and through * GEARS[gear - 1] < down_at:
		gear -= 1
		shift_clock = 0.0
	var coupled: float = through * GEARS[gear]
	var slipping := IDLE_RPM + pedal * (STALL_RPM - IDLE_RPM)
	var target := maxf(coupled, lerpf(slipping, IDLE_RPM, clampf(speed / 7.0, 0.0, 1.0)))
	spin_rev = lerpf(spin_rev, car.spin_share() * effort * 600.0, 1.0 - exp(-delta * 1.5))
	target += spin_rev
	target = clampf(target, IDLE_RPM, MAX_RPM)
	rpm = move_toward(rpm, target, delta * (REV_SHIFT if shift_clock < SHIFT_SETTLE else (REV_UP if target > rpm else REV_DOWN)))
	var up := smoothstep(800.0, 1350.0, rpm)
	var on := 1.0 if engine_on else 0.0
	_mix("engine_idle", on * 0.75 * (1.0 - up), rpm / IDLE_RPM, delta)
	_mix("engine_rev", on * up * (0.34 + 0.5 * load_share), rpm / REV_RPM, delta)

## The ground under the wheels that touch it, each loop by how many are on it.
func _ground(delta: float) -> void:
	var speed := absf(car.speed)
	var loudness := clampf(speed / 16.0, 0.0, 1.0)
	var wanted := {"surface_gravel": [0.0, 1.0], "surface_mud": [0.0, 1.0], "surface_water": [0.0, 1.0], "surface_ice": [0.0, 1.0]}
	var count := 0
	for i in 4:
		if not car.wheel_grounded[i]: continue
		count += 1
		var spec: Array = GROUND.get(car.wheel_surface[i], GROUND[Surface.DIRT])
		var entry: Array = wanted[spec[0]]
		entry[0] += float(spec[1]) * 0.25
		if entry[0] > 0.0: entry[1] = float(spec[2]) * (0.72 + 0.5 * loudness)
	for name: String in wanted:
		var entry: Array = wanted[name]
		_mix(name, float(entry[0]) * (0.15 + 0.85 * loudness), entry[1], delta)
	# Wading: the water takes over, by how deep and how fast.
	if car.water_submersion > 0.02:
		_mix("surface_water", clampf(car.water_submersion * 2.0, 0.2, 1.0) * clampf(0.3 + car.water_relative.length() * 0.14, 0.0, 1.0), 1.0, delta)
	# Clods of mud against the arches.
	splat_clock -= delta * clampf(speed / 6.0 + car.spin_share() * 2.0, 0.0, 2.5)
	if splat_clock <= 0.0:
		splat_clock = random.randf_range(0.5, 1.3)
		for i in 4:
			if car.wheel_grounded[i] and car.wheel_surface[i] in [Surface.MUD, Surface.CLAY]:
				shot("mud_%d" % random.randi_range(1, 5), 0.32 * clampf(speed / 8.0 + 0.3, 0.3, 1.0), random.randf_range(0.9, 1.1))
				break
	# Tyres letting go: a scream on hard ground, gravel flying on loose.
	var slide := clampf(smoothstep(0.35, 0.85, car.slide_amount) * loudness + car.spin_share() * 0.6, 0.0, 1.0) if count > 0 else 0.0
	var hard: bool = count > 0 and car.wheel_surface[1] in [Surface.ROAD, Surface.WET_ROAD, Surface.ROCK, Surface.ICE]
	_mix("skid", slide * 0.7 if hard else 0.0, 0.95, delta)
	# The moment they let go, a chirp.
	chirp_clock -= delta
	if not sliding and slide > 0.3 and hard and chirp_clock <= 0.0:
		shot("chirp_%d" % random.randi_range(1, 3), 0.25 + 0.5 * slide, random.randf_range(0.94, 1.08))
		chirp_clock = 1.2
	sliding = slide > (0.15 if sliding else 0.3)
	# Hard braking: the low scrub of the tyres on the road, nothing that squeaks.
	var braking: bool = car.last_brake > 0.6 and absf(car.speed) > 5.0 and count > 0
	_mix("brake", 0.3 * loudness if braking and hard else 0.0, 1.0, delta)

## Wind by speed and weather, rain as it falls, thunder in a downpour.
func _air(delta: float) -> void:
	var speed := absf(car.speed)
	var wind: float = clampf(speed / 32.0, 0.0, 1.0) * 0.5 + weather.wind * 0.28 + weather.sand * 0.4
	_mix("wind", clampf(wind, 0.0, 1.0), 0.85 + speed / 60.0 + weather.sand * 0.15, delta)
	_mix("rain", clampf(weather.rain, 0.0, 1.0) * 0.85, 1.0, delta)
	if weather.rain > 0.6:
		thunder_clock -= delta
		if thunder_clock <= 0.0:
			thunder_clock = random.randf_range(18.0, 55.0)
			shot("thunder" if random.randf() < 0.5 else "thunder_roll", random.randf_range(0.45, 0.85))
			weather.flash()

## Landings, plunges and running into things.
func _knocks(_delta: float) -> void:
	if car.water_impact_count != plunge_seen:
		plunge_seen = car.water_impact_count
		shot("splash_1", clampf(car.water_plunge_speed * 0.16, 0.35, 1.0))
	if car.landing_count != landing_seen:
		landing_seen = car.landing_count
		if car.landing_impact_speed > 1.5:
			shot("debris", clampf(car.landing_impact_speed * 0.12, 0.12, 0.9), random.randf_range(0.65, 0.85))
	var speed := absf(car.speed)
	if last_speed - speed > 3.5:
		shot("debris", clampf((last_speed - speed) * 0.09, 0.2, 1.0), random.randf_range(0.8, 1.0))
	last_speed = speed
