extends SceneTree
## Attach to actual streamed tree, pole and rock collision geometry.
var failures := 0
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	game.world.configure(1000003)
	game.world.update_stream(170)
	game.weather.forced_mode = "Jasno"
	game.vehicle.controls_enabled = false
	for tick in 3: await physics_frame; await process_frame
	var anchors := get_nodes_in_group("winch_anchor")
	for node: Node in anchors: node.remove_from_group("winch_anchor")
	for kind: String in ["tree", "pole", "rock"]:
		var attached := false
		for node: Node in anchors:
			if str(node.get_meta("anchor_kind", "")) != kind: continue
			var target := node as Node3D
			var point: Vector3 = game.world.to_local(target.global_position)
			if point.x < 0 or point.x > 450: continue
			for angle in 8:
				var offset := Vector3(cos(angle * PI / 4.0), 0, sin(angle * PI / 4.0)) * 8.0
				var position: Vector3 = game.world.safe_position(point + offset)
				position.y = game.world.height_at(position.x, position.z) + 0.6
				game.vehicle.park()
				game.vehicle.position = position
				game.vehicle.heading = atan2(point.z - position.z, point.x - position.x)
				game.vehicle.rotation = Vector3(0, -game.vehicle.heading, 0)
				target.add_to_group("winch_anchor")
				game.winch.toggle_attachment()
				attached = game.winch.anchor_node() == target
				game.winch.disconnect_cable()
				target.remove_from_group("winch_anchor")
				if attached: break
			if attached: break
		print("%s Cable can attach to an actual streamed %s from an unobstructed approach" % ["PASS" if attached else "FAIL", kind])
		if not attached: failures += 1
	game.queue_free()
	await process_frame
	quit(1 if failures else 0)
