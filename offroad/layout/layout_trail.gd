@tool
extends Path3D
## A trail of Lesná vrchovina in the layout scene: drag its curve points (only
## X and Z count; the bake levels the track into the terrain).
## Ground of the track (values of terrain.gd's surface enum).
enum Ground { GRAVEL = 3, MUD = 5, ROCK = 7, DIRT = 12 }
@export var ground: Ground = Ground.DIRT
## Half width (m).
@export_range(1.0, 6.0, 0.1) var half := 2.5
## Closed loop (the last point joins the first).
@export var closed := false
## Water depth (m) where it fords the river.
@export_range(0.1, 1.2, 0.05) var ford := 0.5
## Grade smoothing: samples (2 m each) averaged on either side.
@export_range(2, 30) var smooth := 5
## Dirt road with gravel only in the two worn wheel tracks.
@export var tracks := false
## Glazed ice patches on straight stretches above the ice line.
@export var ice := false
## Deep mud where it crosses the bog.
@export var bog := false

func to_trail() -> Dictionary:
	var points := []
	for i in curve.point_count:
		var p := curve.get_point_position(i) + position
		points.append(Vector2(p.x, p.z))
	var trail := {"name": String(name), "kind": int(ground), "half": half, "closed": closed, "ford": ford, "points": points}
	if smooth != 5: trail.smooth = smooth
	if tracks: trail.tracks = true
	if ice: trail.ice = true
	if bog: trail.bog = true
	return trail

func from_trail(trail: Dictionary, height: Callable) -> void:
	name = trail.name
	ground = trail.kind
	half = trail.half
	closed = trail.closed
	ford = trail.ford
	smooth = trail.get("smooth", 5)
	tracks = trail.get("tracks", false)
	ice = trail.get("ice", false)
	bog = trail.get("bog", false)
	curve = Curve3D.new()
	for p: Vector2 in trail.points: curve.add_point(Vector3(p.x, height.call(p.x, p.y) + 0.5, p.y))
