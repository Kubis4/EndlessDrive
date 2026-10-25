extends SceneTree
var failures := 0
func _initialize() -> void: call_deferred("_run")
func frames(n: int) -> void:
	for i in n:
		await physics_frame
		await process_frame
func check(ok: bool, title: String) -> void:
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func hit(world: DriveWorld, a: Vector3, b: Vector3) -> Dictionary:
	return world.get_world_3d().direct_space_state.intersect_ray(PhysicsRayQueryParameters3D.create(world.to_global(a), world.to_global(b)))
func _run() -> void:
	var game := (load("res://scenes/main.tscn") as PackedScene).instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	game.hud.hide()
	game.vehicle.controls_enabled = false
	var world: DriveWorld = game.world
	world.hazards_enabled = true
	world.configure(24601)
	world.update_stream(744)
	world.clock = 8.0 - float(posmod(world.world_seed, 20))
	game.vehicle.position = Vector3(724, world.height_at(724, world.center_at(724)) + 0.6, world.center_at(724))
	await frames(10)
	var rails := 0
	for chunk: Node3D in world.chunks.values():
		for node: Node in chunk.get_children():
			if node is RouteHazard and node.kind == "rail":
				rails += 1
				check(node.has_node("RailPortalNorth") and node.has_node("RailPortalSouth"), "Railway has a rock portal at both ends of the crossing")
	var center := Vector3(744, world.height_at(744, world.center_at(744)), world.center_at(744))
	for side: float in [-1, 1]:
		var a := center + Vector3(0, 3.5, side * 27)
		var b := center + Vector3(0, 3.5, side * 35)
		check(hit(world, a, b).is_empty(), "Rail portal %d has an actual opening in the boundary rock/collider" % int(side))
	check(rails == 1, "One rail event is installed in its owning streamed chunk")
	if DisplayServer.get_name() != "headless":
		game.overview_size = 91
		game._update_camera(1, true)
		await process_frame
		await RenderingServer.frame_post_draw
		root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/rail-portals.png"))
	world.configure(24601)
	world.update_stream(1704)
	game.vehicle.position = Vector3(1670, world.height_at(1670, world.center_at(1670)) + 0.6, world.center_at(1670))
	await frames(10)
	for side: float in [-1, 1]:
		var a := Vector3(1665, world.height_at(1668, world.center_at(1668)) + 1.5, world.center_at(1668) + side * 15)
		a.y = maxf(world.height_at(1665, a.z), world.height_at(1671, a.z)) + 2.0
		var b := Vector3(1671, a.y, a.z)
		var blocked := hit(world, a, b)
		print("MOUNTAIN RAY side=", side, " hit=", blocked.get("collider"), " position=", blocked.get("position"))
		check(not blocked.is_empty() and str(blocked.collider.name).begins_with("TunnelMountainCollision"), "Mountain fill blocks driving around tunnel side %d" % int(side))
	var a := Vector3(1698, world.height_at(1698, world.center_at(1698)) + 1.5, world.center_at(1698))
	var b := Vector3(1710, world.height_at(1710, world.center_at(1710)) + 1.5, world.center_at(1710))
	check(hit(world, a, b).is_empty(), "The intended road through the arch remains open")
	if DisplayServer.get_name() != "headless":
		game.overview_size = 110
		game._update_camera(1, true)
		await process_frame
		await RenderingServer.frame_post_draw
		root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../build/tunnel-mountain.png"))
	game.queue_free()
	await frames(2)
	print("STRUCTURES: %d failures" % failures)
	quit(0 if failures == 0 else 1)
