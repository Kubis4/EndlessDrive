extends SceneTree
## Drives a lap of the mixed-surface circuit on autopilot (pure pursuit on the
## centreline) and saves views of it, including the sand → snow crossing where
## the earlier dust must keep its colour.
## godot --path . --script res://desert_battlefield/capture_circuit.gd [-- --touch-controls]
const Surface = preload("res://desert_battlefield/terrain.gd")
var game

func _initialize() -> void: call_deferred("run")

func shot(name: String) -> void:
	for i in 3: await process_frame
	await RenderingServer.frame_post_draw
	var path := ProjectSettings.globalize_path("res://build/circuit-" + name + ".png")
	root.get_texture().get_image().save_png(path)
	print("CAPTURE ", path)

## One autopilot tick: steer at a centreline point a few metres ahead and hold
## a target speed (slower before tight corners and on low-grip ground).
func pilot() -> Vector4:
	var car = game.lead
	var c := Surface.circuit_coords(car.position.x, car.position.z)
	var length := Surface.circuit_length()
	var ahead: Array = Surface.circuit_frame(c.y + 6.0)
	var forward := Vector2(-sin(car.heading), -cos(car.heading))
	var target: Vector2 = ahead[0] - Vector2(car.position.x, car.position.z)
	var turn := clampf(-forward.angle_to(target) * 2.2, -1.0, 1.0)
	var kind: int = Surface.CIRCUIT_SECTIONS[Surface.circuit_section(c.y)][2]
	var corner: Array = Surface.circuit_frame(c.y + 12.0)
	var bend := absf(forward.angle_to(corner[1]))
	var speed := lerpf(11.0, 6.0, clampf(bend / 1.2, 0.0, 1.0)) * (0.6 if kind in [Surface.ICE, Surface.SNOW, Surface.MUD] else 1.0)
	var throttle := clampf((speed - car.speed) * 0.45, -1.0, 1.0)
	car.simulate(1.0 / 60.0, maxf(throttle, 0.0), maxf(-throttle, 0.0), turn, false)
	return Vector4(c.x, fposmod(c.y, length), kind, car.speed)

func run() -> void:
	root.size = Vector2i(1600, 900)
	game = (load("res://desert_battlefield/desert_battlefield.tscn") as PackedScene).instantiate()
	root.add_child(game)
	await process_frame
	var spots: Array = game.test_spots
	var index := {}
	for i in spots.size(): index[spots[i][0]] = i
	game.lead.set_physics_process(false)
	# Whole circuit from above.
	game.teleport_to_test(index[Surface.CIRCUIT_NAMES[0]])
	game._set_camera_mode(0)
	game.camera_size = 78.0
	game.lead.place_on_ground(Vector3(-75, 0, 94), 0.0)
	game.focus = game.lead.position
	game.camera_snap = true
	await shot("overview")
	# A full lap from the start grid, logging every section and any trouble.
	game.teleport_to_test(index[Surface.CIRCUIT_NAMES[0]])
	game._set_camera_mode(2)
	var section := -1
	var worst_across := 0.0
	var travelled := 0.0
	var previous: Vector3 = game.lead.position
	var frames := 0
	var shots := {"Štrk": "gravel-drums", "Kamenná cesta": "rock-climb", "Bahno": "mud"}
	while travelled < Surface.circuit_length() + 5.0 and frames < 60 * 150:
		await physics_frame
		var state := pilot()
		frames += 1
		travelled += Vector2(game.lead.position.x - previous.x, game.lead.position.z - previous.z).length()
		previous = game.lead.position
		worst_across = maxf(worst_across, absf(state.x))
		if frames % 120 == 0:
			print("  t=%.0fs along=%.1f speed=%.1f across=%.2f obstacle=%s" % [frames / 60.0, state.y, state.w, state.x, game.lead.last_static_obstacle])
		var now := Surface.circuit_section(state.y)
		if now != section:
			section = now
			var title: String = Surface.SURFACE_NAMES[Surface.CIRCUIT_SECTIONS[now][2]]
			print("SECTION %d %s t=%.1fs speed=%.1f across=%.2f y=%.2f grounded=%d" % [now, title, frames / 60.0, state.w, state.x, game.lead.position.y, game.lead.grounded_wheels])
			if shots.has(title):
				for i in 40:
					await physics_frame
					pilot()
				await shot(shots[title])
	print("LAP %s t=%.1fs travelled=%.0f worst_across=%.2f" % ["DONE" if travelled >= Surface.circuit_length() else "INCOMPLETE", frames / 60.0, travelled, worst_across])
	# Dust across a ground change: kick up sand, cross onto snow, look back at
	# the cloud. The sand cloud must stay sand-coloured next to the white one.
	game.teleport_to_test(index[Surface.CIRCUIT_NAMES[2]])
	game._set_camera_mode(6)
	for i in 150:
		await physics_frame
		pilot()
	game.orbit_yaw = game.lead.heading + PI * 0.8
	game.orbit_pitch = 0.32
	game.follow_distance[6] = 13.0
	print("DUST emitters on car: ", game.lead.find_children("*", "GPUParticles3D", false, false).size(), " surface=", Surface.SURFACE_NAMES[game.lead.surface_kind])
	# Every copy keeps the tint of its own ground; only the current one emits.
	var copies: Dictionary = game.lead.surface_emitters[game.lead.dust]
	for ground in copies:
		var emitter: GPUParticles3D = copies[ground]
		var tint: Color = (emitter.process_material as ParticleProcessMaterial).color
		var expected: Color = Surface.TINT[ground]
		print("DUST %s %s emitting=%s tint_ok=%s" % [emitter.name, Surface.SURFACE_NAMES[ground], emitter.emitting, tint.is_equal_approx(expected)])
	await shot("dust-sand-to-snow")
	quit()
