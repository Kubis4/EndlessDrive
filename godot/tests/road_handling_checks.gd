extends SceneTree
## Real impacts with tumbling crates, plus graded roads and physical offroad ruts.
var failures := 0
var checks := 0
class UnruttedTerrain extends DriveTerrain:
	func offroad_rut_depth(_x: float, _z: float) -> float: return 0.0
class FlatTerrain extends DriveTerrain:
	func center_at(_x: float) -> float: return 0.0
	func road_centers(_x: float) -> PackedFloat32Array: return PackedFloat32Array([0.0])
	func dry_height(_x: float, _z: float) -> float: return 0.0
	func pools_near(_x: float) -> Array[Dictionary]: return []
	func feature_at(_x: float) -> Dictionary: return {}
	func fork_at(_x: float) -> Dictionary: return {}
func _initialize() -> void: call_deferred("_run")
func frames(n: int) -> void:
	for tick in n:
		await physics_frame
		await process_frame
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", title])
func _run() -> void:
	var terrain := DriveTerrain.new()
	var unrutted := UnruttedTerrain.new()
	var grade := 0.0
	var road_error := 0.0
	var width_case := ""
	var depth := 0.0
	var actual_cut := 0.0
	var safe_ruts := true
	for seed in [24601, 1000001, 1000002, 1000003, 1000004, 1000005, 1000006]:
		terrain.seed_value = seed
		terrain.clear_caches()
		unrutted.seed_value = seed
		unrutted.clear_caches()
		for x in range(150, 4000, 7):
			grade = maxf(grade, absf(terrain.base_height(x + 0.5) - terrain.base_height(x - 0.5)))
			if terrain.mountain_at(x).is_empty() and terrain.fork_at(x - 8).is_empty() and terrain.fork_at(x + 8).is_empty() and terrain.road_centers(x - 8).size() == 1 and terrain.road_centers(x + 8).size() == 1:
				var frame := terrain.road_frame(x, terrain.center_at(x))
				var right := Vector2(-float(frame.slope), 1.0).normalized()
				var point := Vector2(x, float(frame.center)) + right * 4.0
				var error := absf(absf(terrain.road_distance(point.x, point.y)) - 4.0)
				if error > road_error: width_case = "seed=%d x=%.1f point=%s frame=%s" % [seed, x, point, frame]
				road_error = maxf(road_error, error)
			for track: Vector3 in terrain.offroad_tracks_at(x):
				depth = maxf(depth, track.y)
				var center := terrain.center_at(x)
				var scale := sqrt(1.0 + pow(float(terrain.road_frame(x, center).slope), 2))
				var z := center + track.x * scale
				actual_cut = maxf(actual_cut, unrutted.dry_height(x, z) - terrain.dry_height(x, z))
				safe_ruts = safe_ruts and terrain.feature_at(x).is_empty() and terrain.fork_at(x).is_empty() and absf(track.x) > 10
	print("ROAD WIDTH CASE ", width_case)
	print("ROAD geometry max_grade=%.3f width_error=%.3f offroad_depth=%.3f physical_cut=%.3f" % [grade, road_error, depth, actual_cut])
	check(grade < 0.25, "Graded road elevation avoids steep alpine steps across seven starting biomes")
	check(road_error < 0.35, "Bends retain a readable physical road width perpendicular to their direction")
	check(depth > 0.5 and depth <= 0.65 and actual_cut > 0.35, "Deep offroad tyre ruts cut actual terrain by over 35 cm")
	check(safe_ruts and terrain.offroad_tracks_at(80).is_empty(), "Deep ruts spare the start, main road, junctions and structures")
	terrain.seed_value = 1000003
	unrutted.seed_value = terrain.seed_value
	terrain.clear_caches()
	unrutted.clear_caches()
	var baked := DriveTerrainBake.bake(terrain, 3)
	var physical_cut := 0.0
	for band: PackedVector3Array in baked.collision_bands:
		for point: Vector3 in band:
			if absf(terrain.road_distance(point.x, point.z)) > 10:
				physical_cut = maxf(physical_cut, unrutted.ground_height_at(point.x, point.z) - point.y)
	check(physical_cut > 0.45, "Streamed collision triangles preserve over 45 cm of offroad rut relief")
	terrain.seed_value = 1000005
	terrain.clear_caches()
	var gravel := 0
	var snow := 0
	var mountain_relief := 0.0
	for x in range(30, 350, 5):
		var z := terrain.center_at(x)
		var sample := terrain.sample(x, z)
		if int(sample.material) == DriveTerrain.SurfaceKind.GRAVEL: gravel += 1
		if int(terrain.sample(x, z + 40).material) == DriveTerrain.SurfaceKind.SNOW: snow += 1
		mountain_relief = maxf(mountain_relief, terrain.height_at(x, z + 40) - terrain.height_at(x, z))
	print("ALPINE gravel_samples=%d snowy_slopes=%d shoulder_relief=%.2f" % [gravel, snow, mountain_relief])
	check(gravel > 40 and snow > 40 and mountain_relief > 5, "Alpine pass has a gravel road, snowy slopes and real mountain relief")
	# A simple collider avoids terrain or recovery logic masking a crate launch.
	var world := DriveWorld.new()
	root.add_child(world)
	world.terrain = FlatTerrain.new()
	world.configure(1000003)
	var floor_body := StaticBody3D.new()
	floor_body.add_to_group("drive_terrain")
	var floor_shape := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(180, 1, 50)
	floor_shape.shape = box
	floor_body.position = Vector3(40, -0.5, 0)
	floor_body.add_child(floor_shape)
	world.add_child(floor_body)
	var car := DriveVehicle.new()
	car.world = world
	car.run = DriveRun.new()
	car.controls_enabled = false
	car.boundary_recovery_enabled = false
	root.add_child(car)
	for variant in 3:
		car.park()
		car.position = Vector3(0, 0.5, 0)
		car.heading = 0
		car.body_roll = 0
		car.body_pitch = 0
		await frames(25)
		var debris: RigidBody3D = load("res://scripts/road_debris.gd").new() if variant < 2 else RigidBody3D.new()
		debris.mass = 22
		debris.position = Vector3(8, 0.75, 0)
		var shape := CollisionShape3D.new()
		var crate_shape := BoxShape3D.new()
		crate_shape.size = Vector3(1.2, 1.2, 1.2)
		shape.shape = crate_shape
		debris.add_child(shape)
		world.add_child(debris)
		debris.angular_velocity = [Vector3(0, 0, 9), Vector3(8, 5, 7), Vector3(0, 0, 12)][variant]
		car.speed = 22
		var impacts := car.obstacle_impacts
		var max_y := car.position.y
		var max_up := 0.0
		var max_box_speed := 0.0
		for tick in 240:
			await frames(1)
			max_y = maxf(max_y, car.position.y)
			max_up = maxf(max_up, car.velocity.y)
			max_box_speed = maxf(max_box_speed, Vector2(debris.linear_velocity.x, debris.linear_velocity.z).length())
		print("CRATE variant=%d x=%.2f max_y=%.2f max_up=%.2f box_speed=%.2f impacts=%d" % [variant, car.position.x, max_y, max_up, max_box_speed, car.obstacle_impacts - impacts])
		check(car.obstacle_impacts > impacts, "Car physically hits tumbling crate %d" % variant)
		check(max_y < 3.0 and max_up < 6.0 and car.position.is_finite() and car.boundary_recoveries == 0, "Tumbling crate %d cannot launch the car or invoke recovery" % variant)
		if variant < 2: check(max_box_speed < 14, "Crate %d receives bounded, horizontal contact impulses" % variant)
		debris.queue_free()
		await frames(2)
	# Directly exercise the original moving-floor case: a rotating box beneath
	# the tyres, on default layer 1, with no debris script or velocity limiter.
	car.park()
	car.position = Vector3(0, 1.95, 0)
	car.heading = 0
	var platform := RigidBody3D.new()
	platform.mass = 22
	platform.position = Vector3(0, 0.5, 0)
	var platform_shape := CollisionShape3D.new()
	var platform_box := BoxShape3D.new()
	platform_box.size = Vector3(3.5, 1.0, 2.4)
	platform_shape.shape = platform_box
	platform.add_child(platform_shape)
	world.add_child(platform)
	await frames(40)
	check(car.position.y > 1.2 and car.is_on_floor(), "Tyres physically rest on the crate before it tumbles")
	platform.angular_velocity = Vector3(0, 0, 12)
	car.speed = 6
	var platform_max_y := car.position.y
	var platform_max_up := 0.0
	for tick in 240:
		await frames(1)
		platform_max_y = maxf(platform_max_y, car.position.y)
		platform_max_up = maxf(platform_max_up, car.velocity.y)
	print("ROTATING FLOOR max_y=%.2f max_up=%.2f" % [platform_max_y, platform_max_up])
	check(platform_max_y < 4 and platform_max_up < 6 and car.boundary_recoveries == 0, "Leaving a tumbling crate cannot transfer its platform velocity to the car")
	car.queue_free()
	world.queue_free()
	await frames(2)
	print("ROAD HANDLING %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
