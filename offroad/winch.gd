extends Node3D
## The winch: one tap hooks the cable to the nearest tree, pole or rock ahead (with none in
## reach, to a ground anchor) and winds it in; another lets go. It pulls with a real force at
## the front bumper, weaker wheels and slopes included, slower the harder it has to pull, and
## only with the engine running.
const REACH := 30.0
const MOST_CABLE := 42.0
const WIND_SPEED := 1.7
const MOST_PULL := 38000.0
const STIFF := 60000.0
const DAMP := 9000.0
## How much cable stays out at the end.
const SHORTEST := 3.0
const STAKE_AHEAD := 24.0
var game: Node3D
var car: CharacterBody3D
var attached := false
var anchor := Vector3.ZERO
var cable_length := 0.0
var tension := 0.0
var last_outlet := Vector3.ZERO
## What it has to say to the expedition ("" nothing).
var news := ""
var cable: MeshInstance3D
var cable_mesh: CylinderMesh
var stake: MeshInstance3D
## The bumper, relative to the car's centre.
var front := -2.5

func setup(root: Node3D, vehicle: CharacterBody3D) -> void:
	name = "Winch"
	game = root
	car = vehicle
	for point: Vector3 in car.hull_points: front = minf(front, point.z)
	var steel := StandardMaterial3D.new()
	steel.albedo_color = Color(0.7, 0.74, 0.76)
	steel.metallic = 0.7
	steel.roughness = 0.45
	cable_mesh = CylinderMesh.new()
	cable_mesh.top_radius = 0.022
	cable_mesh.bottom_radius = 0.022
	cable_mesh.radial_segments = 6
	cable_mesh.rings = 1
	cable_mesh.material = steel
	cable = MeshInstance3D.new()
	cable.mesh = cable_mesh
	cable.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	cable.visible = false
	add_child(cable)
	var post := CylinderMesh.new()
	post.top_radius = 0.09
	post.bottom_radius = 0.05
	post.height = 1.0
	post.material = steel
	stake = MeshInstance3D.new()
	stake.mesh = post
	stake.visible = false
	add_child(stake)

func outlet() -> Vector3:
	return car.position + car.attitude * Vector3(0.0, 0.1, front)

## From a tap: hooks on or lets go. Says what happened.
func toggle() -> String:
	if attached:
		release()
		return "Navijak odpojený."
	if absf(car.speed) > 3.0: return "Pred pripojením navijaka spomaľ."
	if not car.engine_running: return "Navijak potrebuje bežiaci motor."
	var from := outlet()
	var forward: Vector3 = -car.attitude.z
	forward = Vector3(forward.x, 0.0, forward.z).normalized()
	var best := Vector3.INF
	var best_score := INF
	for spot: Vector3 in game.test_grounds.anchors_near(Vector2(from.x, from.z), REACH):
		var flat := Vector3(spot.x - from.x, 0.0, spot.z - from.z)
		var far := flat.length()
		if far < 4.0 or far > REACH: continue
		var ahead := flat.normalized().dot(forward)
		if ahead < -0.25: continue
		var score := far * (1.4 - ahead * 0.4)
		if score < best_score:
			best_score = score
			best = spot
	var staked := best == Vector3.INF
	if staked:
		var spot: Vector3 = from + forward * STAKE_AHEAD
		best = Vector3(spot.x, game.soil.height_at(spot.x, spot.z) + 0.5, spot.z)
		stake.position = best
		stake.visible = true
	anchor = best
	attached = true
	cable_length = from.distance_to(anchor)
	last_outlet = from
	cable.visible = true
	return "Navijak ťahá · zemná kotva." if staked else "Navijak ťahá."

func release() -> void:
	attached = false
	tension = 0.0
	car.winch_force = Vector3.ZERO
	cable.visible = false
	stake.visible = false

func _physics_process(dt: float) -> void:
	if not attached or game.paused: return
	var from := outlet()
	# Moved by more than driving can (a recovery, a jump): the cable is off.
	if from.distance_to(last_outlet) > 3.0:
		release()
		return
	last_outlet = from
	var line := anchor - from
	var far := line.length()
	if far > MOST_CABLE:
		release()
		news = "Lano je pridlhé, navijak sa odpojil."
		return
	var direction := line / maxf(far, 0.01)
	# Winding slows as the load comes on; slack is taken up at once.
	var running: bool = car.engine_running and not car.get_meta("expedition_no_fuel", false)
	if running and cable_length > SHORTEST: cable_length = maxf(SHORTEST, cable_length - WIND_SPEED * (1.0 - tension / MOST_PULL) * dt)
	cable_length = minf(cable_length, far + 0.3)
	var stretch := far - cable_length
	var closing := car.velocity.dot(direction)
	tension = clampf(STIFF * stretch - DAMP * closing, 0.0, MOST_PULL) if stretch > 0.0 else 0.0
	car.winch_force = direction * tension
	car.winch_arm = car.attitude * Vector3(0.0, 0.1, front)

func _process(_delta: float) -> void:
	if not attached: return
	var from := outlet()
	var line := anchor - from
	var far := line.length()
	cable_mesh.height = far
	var up := line / maxf(far, 0.01)
	var side := up.cross(Vector3.FORWARD if absf(up.z) < 0.9 else Vector3.RIGHT).normalized()
	cable.global_transform = Transform3D(Basis(side, up, side.cross(up)), from + line * 0.5)
