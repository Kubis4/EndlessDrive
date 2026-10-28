extends SceneTree
## Check both choices against real tunnel mountain collision after the junction.
var failures := 0
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var world := DriveWorld.new()
	root.add_child(world)
	world.hazards_enabled = true
	for choice in [-1, 1]:
		world.configure(24602)
		world.update_stream(680)
		await physics_frame
		await physics_frame
		# The future terrain and tunnel were generated before the player chose.
		var lanes := world.terrain.road_centers(560.0)
		var selected_z := float(lanes[0] if choice < 0 else lanes[-1])
		var selection := world.choose_route(Vector3(560.0, 0, selected_z))
		if selection.is_empty() or int(selection.side) != choice:
			print("FAIL Could not commit junction choice ", choice)
			failures += 1
		var open := true
		for x in range(650, 709, 2):
			var a_x := float(x)
			var b_x := a_x + 2.0
			var a_z := world.terrain.center_at(a_x)
			var b_z := world.terrain.center_at(b_x)
			var a := world.to_global(Vector3(a_x, world.height_at(a_x, a_z) + 1.3, a_z))
			var b := world.to_global(Vector3(b_x, world.height_at(b_x, b_z) + 1.3, b_z))
			var hit := world.get_world_3d().direct_space_state.intersect_ray(PhysicsRayQueryParameters3D.create(a, b))
			if not hit.is_empty() and str(hit.collider.name).begins_with("TunnelMountainCollision"):
				open = false
				print("BLOCKED side=", choice, " x=", x, " collider=", hit.collider.name)
		print("%s Junction choice %d reaches tunnel without mountain blocking the road" % ["PASS" if open else "FAIL", choice])
		if not open: failures += 1
	world.queue_free()
	await process_frame
	quit(1 if failures else 0)
