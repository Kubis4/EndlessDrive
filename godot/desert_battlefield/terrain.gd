extends RefCounted
## Shared analytic surface: the visible mesh, suspension and tracks use one height field.
## The dune function stays unchanged; two flattened test grounds (a surface test
## course and an asphalt skid pad) are blended into it for physics and rendering.

enum { SAND, PACKED, ROAD, GRAVEL, CLAY, MUD, WATER, ROCK, SNOW, ICE, GRASS, WET_ROAD }
const SURFACE_NAMES := ["Piesok", "Udupaný piesok", "Asfalt", "Štrk", "Hlina", "Bahno", "Brod", "Kamenná cesta",
	"Sneh", "Ľad", "Tráva", "Mokrý asfalt"]
## Gameplay approximations of tyre friction, rolling resistance (fraction of load)
## and how readily the ground is compacted into ruts (1 = loose dune sand).
const GRIP := [0.85, 0.80, 1.0, 0.66, 0.76, 0.38, 0.5, 0.9, 0.34, 0.1, 0.55, 0.6]
const ROLLING := [0.085, 0.05, 0.015, 0.032, 0.045, 0.16, 0.10, 0.03, 0.07, 0.01, 0.05, 0.02]
const DEFORM := [1.0, 0.6, 0.0, 0.4, 0.8, 1.6, 1.3, 0.0, 1.1, 0.0, 0.35, 0.0]
## Particle response: dust clouds, ballistic grains/clods/spray and their tint.
const DUST := [1.0, 0.75, 0.1, 0.65, 0.8, 0.0, 0.0, 0.3, 0.45, 0.0, 0.15, 0.0]
const SPRAY := [1.0, 0.7, 0.0, 1.0, 0.8, 1.25, 1.7, 0.35, 1.3, 0.25, 0.6, 1.1]
const TINT := [Color(1, 1, 1), Color(0.96, 0.94, 0.9), Color(0.7, 0.7, 0.7), Color(1.04, 1.1, 1.28),
	Color(1.06, 0.8, 0.68), Color(0.5, 0.42, 0.34), Color(1.55, 1.68, 1.8), Color(1.0, 1.06, 1.2),
	Color(1.75, 1.85, 2.0), Color(1.7, 1.8, 1.95), Color(0.6, 0.75, 0.4), Color(1.45, 1.55, 1.65)]
## Ruts and tread ribbons belong to soft ground only.
## Painted tread ribbons are off everywhere: the deformed ground itself shows
## where the tyres went (and asphalt, gravel, rock and water show nothing).
const TRACKED := [false, false, false, false, false, false, false, false, false, false, false, false]
## Fraction of the vehicle's top speed reachable on each ground: the engine
## stops pushing beyond it, and soft ground (factor < 0.7) actively bogs an
## overspeeding car down towards it.
## Mud is slow and treacherous: a car arriving fast is dragged down hard and
## can dig itself in (see vehicle.gd sinkage).
const SPEED := [0.85, 0.92, 1.0, 0.8, 0.72, 0.22, 0.28, 0.5, 0.6, 1.0, 0.75, 1.0]

## Test course east of the dunes: Vector4(x0, z0, x1, z1). Lanes run along X;
## each starts at the east end, so the car drives west (yaw = PI / 2).
const COURSE := Vector4(62.0, -112.0, 120.0, -6.0)
const COURSE_LEVEL := 0.7
## Asphalt skid pad south-west of the station.
const PAD := Vector4(-102.0, -99.0, -54.0, -51.0)
const PAD_LEVEL := 0.25
const PAD_CENTER := Vector2(-78.0, -75.0)
const PAD_RADIUS := 19.0
const MARGIN := 5.0
const LANE_X0 := 68.0
const LANE_X1 := 115.0
const LANE_HALF := 4.3
const LANE_SPACING := 9.5
## Lanes from north to south (the first four are the winter/grip lanes).
const LANE_Z := [-15.5, -25.0, -34.5, -44.0, -53.5, -63.0, -72.5, -82.0, -91.5, -101.0]
const LANE_TYPE := [SNOW, ICE, GRASS, WET_ROAD, ROAD, GRAVEL, CLAY, MUD, WATER, CLAY]
const LANE_NAMES := ["Sneh", "Ľad", "Tráva", "Mokrý asfalt", "Cesta · slalom", "Štrk", "Hlina", "Bahno", "Brod", "Hrboľe a skok"]
const LANE_ROAD := 4
const LANE_WATER := 8
const LANE_MOGULS := 9
## Order of the lane tests in the teleport list (keys 1–9, 0).
const LANE_ORDER := [4, 5, 6, 7, 8, 9, 0, 1, 2, 3]
## Water ford: water over the whole lane, a basin with 6 m entry/exit ramps
## and a 0.85 m deep centre; even the lane margins hold a few centimetres.
const WATER_X0 := LANE_X0
const WATER_X1 := LANE_X1
const WATER_DEPTH := 0.7
const WATER_LEVEL := COURSE_LEVEL - 0.02
const SNOW_DEPTH := 0.14
const JUMP_HEIGHT := 1.2
## Rocky off-road trail north-east of the station: a 4.6 m wide track that winds
## twice over a lowered dune ridge, with embedded rocks, alternating rock steps
## and off-camber slopes. Boulder fields flank it.
const ROCKS := Vector4(40.0, 68.0, 118.0, 120.0)
const TRAIL := [Vector2(110, 111), Vector2(89, 113), Vector2(68, 106), Vector2(52, 96), Vector2(56, 84),
	Vector2(74, 80), Vector2(92, 89), Vector2(107, 84), Vector2(100, 77.5), Vector2(80, 77)]
const TRAIL_HALF := 2.3

static func dune_height(x: float, z: float) -> float:
	var wind := x * 0.075 + z * 0.029 + 0.66 * sin(z * 0.046)
	var crest := maxf(0.0, sin(wind))
	var ridge := crest * crest * crest
	var h := 1.1 * sin(x * 0.031 + z * 0.022) + 0.7 * cos(z * 0.047)
	h += ridge * (6.8 + 2.5 * sin(z * 0.021 + 1.0))
	h += 0.21 * sin(x * 0.19 + z * 0.09) * sin(z * 0.13)
	var pad := 1.0 - smoothstep(10.0, 24.0, Vector2(x + 19.0, z + 13.0).length())
	return lerpf(h, 0.6, pad)

static func height_at(x: float, z: float) -> float:
	if x > COURSE.x and x < COURSE.z and z > COURSE.y and z < COURSE.w:
		var w := smoothstep(0.0, MARGIN, minf(minf(x - COURSE.x, COURSE.z - x), minf(z - COURSE.y, COURSE.w - z)))
		return lerpf(dune_height(x, z), COURSE_LEVEL + course_relief(x, z), w)
	if x > PAD.x and x < PAD.z and z > PAD.y and z < PAD.w:
		var w := smoothstep(0.0, MARGIN, minf(minf(x - PAD.x, PAD.z - x), minf(z - PAD.y, PAD.w - z)))
		return lerpf(dune_height(x, z), PAD_LEVEL, w)
	if x > ROCKS.x and x < ROCKS.z and z > ROCKS.y and z < ROCKS.w:
		var w := smoothstep(0.0, MARGIN, minf(minf(x - ROCKS.x, ROCKS.z - x), minf(z - ROCKS.y, ROCKS.w - z)))
		var dune := dune_height(x, z)
		return lerpf(dune, rocky_height(x, z, dune), w)
	return dune_height(x, z)

## Distance to the trail centreline (x) and arc length along it (y).
static func trail_coords(x: float, z: float) -> Vector2:
	var p := Vector2(x, z)
	var best := INF
	var along := 0.0
	var travelled := 0.0
	for i in TRAIL.size() - 1:
		var a: Vector2 = TRAIL[i]
		var b: Vector2 = TRAIL[i + 1]
		var ab := b - a
		var length := ab.length()
		var t := clampf((p - a).dot(ab) / (length * length), 0.0, 1.0)
		var d := p.distance_squared_to(a + ab * t)
		if d < best:
			best = d
			along = travelled + t * length
		travelled += length
	return Vector2(sqrt(best), along)

static func _hash(cx: int, cz: int, salt: float) -> float:
	var s := sin(float(cx) * 127.1 + float(cz) * 311.7 + salt * 74.7) * 43758.5453
	return s - floorf(s)

## One rounded rock per cell (kept inside its cell, so one lookup suffices).
static func _rock(x: float, z: float, cell: float, chance: float, max_height: float, salt: float) -> float:
	var cx := floori(x / cell)
	var cz := floori(z / cell)
	# One sine per cell; the other rock parameters are derived from it.
	var h := _hash(cx, cz, salt)
	if h > chance: return 0.0
	var h1 := fposmod(h * 37.17, 1.0)
	var h2 := fposmod(h * 91.31, 1.0)
	var h3 := fposmod(h * 53.93, 1.0)
	var centre := (Vector2(cx, cz) + Vector2(0.25 + 0.5 * h1, 0.25 + 0.5 * h2)) * cell
	var radius := cell * (0.13 + 0.12 * h3)
	var d := Vector2(x, z).distance_to(centre) / radius
	if d >= 1.0: return 0.0
	# Flat-topped block with steep (but suspension-friendly) sides.
	var block := smoothstep(1.0, 0.5, d) * (0.85 + 0.15 * (1.0 - d * d))
	return max_height * (0.45 + 0.55 * fposmod(h * 17.77, 1.0)) * block

## Trail weight (1 on the track, 0 among the boulders).
static func trail_weight(x: float, z: float) -> float:
	return 1.0 - smoothstep(TRAIL_HALF - 0.5, TRAIL_HALF + 0.9, trail_coords(x, z).x)

static func rocky_height(x: float, z: float, dune: float) -> float:
	var coords := trail_coords(x, z)
	var on_trail := 1.0 - smoothstep(TRAIL_HALF - 0.5, TRAIL_HALF + 0.9, coords.x)
	# The dune ridge is lowered to a climbable hill; gentle rolls add off-camber.
	var base := 0.45 * dune + 0.3 + 0.55 * sin(x * 0.11 + 0.5) * cos(z * 0.13)
	# Alternating 22 cm rock steps every 16 m along the track.
	var phase := fposmod(coords.y, 32.0)
	var steps := 0.22 * (smoothstep(8.0, 8.35, phase) - smoothstep(24.0, 24.35, phase))
	var track := steps + _rock(x, z, 1.9, 0.55, 0.28, 0.0) + _rock(x + 0.95, z + 0.95, 1.9, 0.35, 0.18, 9.0) - 0.06
	var field := _rock(x, z, 4.2, 0.5, 0.8, 20.0) + _rock(x + 2.1, z + 1.3, 2.3, 0.45, 0.35, 30.0)
	return base + lerpf(field, track, on_trail)

## Vertex paint for the rocky ground: r = rock relief, g = trail, b = rocky ground.
static func rock_paint(x: float, z: float) -> Color:
	if not (x > ROCKS.x and x < ROCKS.z and z > ROCKS.y and z < ROCKS.w): return Color(0, 0, 0, 0)
	var on_trail := trail_weight(x, z)
	var relief := lerpf(_rock(x, z, 4.2, 0.5, 0.8, 20.0) + _rock(x + 2.1, z + 1.3, 2.3, 0.45, 0.35, 30.0),
		_rock(x, z, 1.9, 0.55, 0.28, 0.0) + _rock(x + 0.95, z + 0.95, 1.9, 0.35, 0.18, 9.0), on_trail)
	return Color(clampf(relief * 4.0, 0.0, 1.0), on_trail, 1.0, 0.0)

## Index of the course lane containing the point, or -1.
static func lane_at(x: float, z: float) -> int:
	if x <= LANE_X0 or x >= LANE_X1: return -1
	var lane := roundi((LANE_Z[0] - z) / LANE_SPACING)
	if lane < 0 or lane >= LANE_Z.size(): return -1
	return lane if absf(z - LANE_Z[lane]) < LANE_HALF else -1

## Height of course features above the flattened course level.
static func course_relief(x: float, z: float) -> float:
	var lane := lane_at(x, z)
	if lane < 0: return 0.0
	var across: float = z - LANE_Z[lane]
	var inside := minf(LANE_HALF - absf(across), minf(x - LANE_X0, LANE_X1 - x))
	match LANE_TYPE[lane]:
		MUD:
			# A slightly sunken, uneven bog that holds the wet surface.
			return -smoothstep(0.0, 1.4, inside) * (0.08 + 0.035 * sin(x * 0.9) * sin(z * 1.3))
		WATER:
			var along := smoothstep(0.6, 6.5, minf(x - WATER_X0, WATER_X1 - x))
			var side := smoothstep(0.4, 2.4, LANE_HALF - absf(across))
			return -0.07 * smoothstep(0.0, 0.35, inside) - WATER_DEPTH * along * side
		SNOW:
			# A raised, gently drifted snow layer the tyres sink into.
			return smoothstep(0.0, 0.8, inside) * (SNOW_DEPTH + 0.04 * sin(x * 0.7 + z * 0.3))
		ICE:
			return -0.01 * smoothstep(0.0, 0.3, inside)
		GRASS:
			return smoothstep(0.0, 1.0, inside) * 0.05 * sin(x * 0.45) * sin(z * 0.8 + 1.0)
	if lane == LANE_MOGULS:
		var taper := smoothstep(0.0, 1.5, inside)
		# Staggered moguls articulate the suspension (left/right out of phase).
		var moguls := smoothstep(93.0, 96.0, x) * (1.0 - smoothstep(110.0, 113.0, x))
		var twist := 0.27 * sin((x - 93.0) * TAU / 5.0) * sin(across / LANE_HALF * PI * 0.5)
		# Tabletop jump approached from the east: kicker, flat table, landing slope.
		var jump := 0.0
		if x > 84.0 and x < 90.0: jump = JUMP_HEIGHT * pow((90.0 - x) / 6.0, 1.6)
		elif x > 78.0 and x <= 84.0: jump = JUMP_HEIGHT
		elif x > 70.0 and x <= 78.0: jump = JUMP_HEIGHT * smoothstep(70.0, 78.0, x)
		return (twist * moguls + jump) * taper
	return 0.0

static func surface_at(x: float, z: float) -> int:
	if x > COURSE.x and x < COURSE.z and z > COURSE.y and z < COURSE.w:
		var lane := lane_at(x, z)
		if lane >= 0:
			var kind: int = LANE_TYPE[lane]
			if kind == WATER: return WATER if water_depth_at(x, z) > 0.03 else MUD
			return kind
		var edge := minf(minf(x - COURSE.x, COURSE.z - x), minf(z - COURSE.y, COURSE.w - z))
		return PACKED if edge > MARGIN * 0.5 else SAND
	if x > PAD.x and x < PAD.z and z > PAD.y and z < PAD.w:
		if Vector2(x, z).distance_squared_to(PAD_CENTER) < PAD_RADIUS * PAD_RADIUS: return ROAD
		var edge := minf(minf(x - PAD.x, PAD.z - x), minf(z - PAD.y, PAD.w - z))
		return PACKED if edge > MARGIN * 0.5 else SAND
	if x > ROCKS.x and x < ROCKS.z and z > ROCKS.y and z < ROCKS.w:
		var edge := minf(minf(x - ROCKS.x, ROCKS.z - x), minf(z - ROCKS.y, ROCKS.w - z))
		if edge < MARGIN * 0.5: return SAND
		return ROCK if trail_coords(x, z).x < TRAIL_HALF + 0.2 else GRAVEL
	return SAND

## Rut compaction scale at a point; loose dunes are 1.
static func deform_at(x: float, z: float) -> float:
	return DEFORM[surface_at(x, z)]

static func in_test_ground(x: float, z: float, margin: float = 0.0) -> bool:
	return (x > COURSE.x - margin and x < COURSE.z + margin and z > COURSE.y - margin and z < COURSE.w + margin) \
		or (x > PAD.x - margin and x < PAD.z + margin and z > PAD.y - margin and z < PAD.w + margin) \
		or (x > ROCKS.x - margin and x < ROCKS.z + margin and z > ROCKS.y - margin and z < ROCKS.w + margin)

## Water above the ford bed; zero everywhere else.
static func water_depth_at(x: float, z: float) -> float:
	if x <= WATER_X0 or x >= WATER_X1 or absf(z - LANE_Z[LANE_WATER]) >= LANE_HALF: return 0.0
	return maxf(0.0, WATER_LEVEL - (COURSE_LEVEL + course_relief(x, z)))

## Named driving tests: [label, x, z, yaw]. Yaw PI/2 faces west (-X).
static func test_spots() -> Array:
	var spots := []
	for lane: int in LANE_ORDER:
		spots.append([LANE_NAMES[lane], 114.0, LANE_Z[lane], PI * 0.5])
	spots.append(["Kruh · asfalt", PAD_CENTER.x + PAD_RADIUS - 3.0, PAD_CENTER.y, 0.0])
	spots.append(["Skok z duny", -25.0, 45.0, -PI * 0.5])
	var start: Vector2 = TRAIL[0]
	var heading: Vector2 = (TRAIL[1] - TRAIL[0]).normalized()
	spots.append(["Kamenná trať", start.x, start.y, atan2(-heading.x, -heading.y)])
	return spots

static func normal_at(x: float, z: float) -> Vector3:
	return Vector3(height_at(x - 0.3, z) - height_at(x + 0.3, z), 0.6,
		height_at(x, z - 0.3) - height_at(x, z + 0.3)).normalized()

static func dune_normal_at(x: float, z: float) -> Vector3:
	return Vector3(dune_height(x - 0.3, z) - dune_height(x + 0.3, z), 0.6,
		dune_height(x, z - 0.3) - dune_height(x, z + 0.3)).normalized()

static func route(t: float, lane: int = 0) -> Vector3:
	var p := route_xz(t, lane)
	return Vector3(p.x, height_at(p.x, p.y), p.y)

static func route_xz(t: float, lane: int = 0) -> Vector2:
	# Patrol the valley north of the station. The old ellipse crossed steep
	# dune faces beyond the van's traction; manual driving can still go anywhere.
	var z := (25.0 + float(lane) * 0.8) * sin(t) + 30.0
	var wind := -1.2 + (1.2 + float(lane) * 0.035) * cos(t)
	var x := (wind - z * 0.029 - 0.66 * sin(z * 0.046)) / 0.075
	return Vector2(x, z)
