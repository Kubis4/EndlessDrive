extends RefCounted
## Signed, bounded sand displacement shared by GPU geometry and wheel support.
## Positive values compress the surface; negative values build raised shoulders.
const Surface = preload("res://desert_battlefield/terrain.gd")
const CELL := 0.25
const EXTENT := 128.0
const RESOLUTION := 1025
const TILE_SIZE := 16.0
var geometry_cell := 0.125
var tile_quads := 128
var tile_vertices := 131
const MAX_DEPTH := 0.5
## Per-world rut scale: how much deeper and faster ruts dig than in the desert
## dunes (set in configure), and the depth they may reach.
var depth_scale := 1.0
## How deep the tyre now stamping ruts soft ground (a wide or lugged one rides on
## top of it; vehicle.gd sets it).
var tyre_sink := 1.0
var max_depth := MAX_DEPTH
## The authored Highlands uses shallower soil ruts outside mud and snow.
const MAX_BANK := 0.2
const ROLLING_DEPTH := 0.4
const COMPACTION_PER_METRE := 0.55
## Skid marks on hard ground: slip threshold and the tiny encoded depth.
const MARK_SLIP := 0.3
const MARK_DEPTH := 0.004
## Coarse 4 m occupancy blocks: wheel support skips the 16-texel cubic lookup
## wherever no stamp could have changed the field (almost all of the map).
const BLOCK := 4.0
const BLOCKS := 64
var touched := PackedByteArray()
var live_materials: Array[ShaderMaterial] = []
## GPU copy of the 4 m occupancy blocks: shaders skip the cubic rut lookup on
## untouched ground, which is nearly all vertices and pixels of the map.
var touched_image: Image
var touched_texture: ImageTexture
var cars: Array = []
var field := PackedFloat32Array()
var image: Image
var texture: ImageTexture
var mask_image: Image
var mask_texture: ImageTexture
var tiles: Dictionary = {}
var pending: Dictionary = {}
var dirty := false
var mask_dirty := false
var upload_clock := 0.0
## Ruts reach the GPU at 25 Hz (mobile 13 Hz): close enough that the rendered
## surface keeps up with the wheels without uploading the field every frame.
var upload_interval := 0.04
## Background fine-patch construction (see _build_pending).
var threaded := true
var build_task := -1
var build_tile_id := Vector2i.ZERO
var build_result := {}
var revision := 0
var uploaded_revision := 0
var changed_pixels := 0
var container: Node3D
var fine_material: ShaderMaterial
var phone := false
var shared_indices := PackedInt32Array()
## World position of the field's centre (snapped to whole tiles, so texels stay
## on the world's 25 cm grid). Fixed at the origin in the desert; in larger
## worlds the field moves with the car (`follow`), dropping ruts left far behind.
var center := Vector2.ZERO
var follow := false
const FOLLOW_DISTANCE := 80.0
## Share of a road rut's depth that stays visible under the snow pack.
const ROAD_RUTS_SHOWN := 0.6
## Snow pack on roads (packed by traffic) against open ground.
const ROAD_SNOW := 0.55
## Every material bound to the field (all need the field centre).
var bound: Array[ShaderMaterial] = []
## Ruts left behind when the field moved: world block index -> 17 × 17 texels.
var archive := {}
## Snow layer (worlds with weather): how far tyres have pressed the snow down,
## per texel, next to the soil field. While snow lies the soil beneath is
## buried (frozen): tyres compact only the snow, and old ruts are filled; once
## it melts, the ground is as it was before the snow.
var snow_field := PackedFloat32Array()
var snow_image: Image
var snow_texture: ImageTexture
var snow_dirty := false
var snow_archive := {}
var snow_seen := 0.0
var snow_clock := 0.0
var settle_block := -1
var settle_depth := 0.0
var settle_fallen := 0.0
var settle_bury := 0.0

func configure(root: Node3D) -> void:
	follow = root.world_script != null
	if follow:
		# Highland soil: wheels sink well over twice as deep, down to 65 cm in mud.
		depth_scale = 1.6
		max_depth = 0.65
	# At the overview camera 25 cm triangles are only a few pixels wide. The
	# 25 cm physical field and cubic smoothing are identical in both profiles.
	geometry_cell = 0.25 if root.quality_name != "reference" else 0.125
	# (On a phone a stretch of worn road would be millions of triangles: the ruts' look
	# comes from the ground shader, which reads the same field.)
	phone = root.phone
	if phone: geometry_cell = 0.5
	if OS.has_feature("android"):
		# Live tyre segments cover the gap between uploads, so mobile can send
		# the 2 MB field less often without the ruts lagging behind the car.
		upload_interval = 0.15
	tile_quads = roundi(TILE_SIZE / geometry_cell)
	tile_vertices = tile_quads + 3
	field.resize(RESOLUTION * RESOLUTION)
	field.fill(0.0)
	touched.resize(BLOCKS * BLOCKS)
	touched.fill(0)
	touched_image = Image.create(BLOCKS, BLOCKS, false, Image.FORMAT_R8)
	touched_image.fill(Color.BLACK)
	touched_texture = ImageTexture.create_from_image(touched_image)
	# Half-float precision is better than 0.25 mm here, at half the upload size.
	image = Image.create(RESOLUTION, RESOLUTION, false, Image.FORMAT_RH)
	image.fill(Color(0, 0, 0, 1))
	texture = ImageTexture.create_from_image(image)
	snow_field.resize(RESOLUTION * RESOLUTION)
	snow_field.fill(0.0)
	snow_image = Image.create(RESOLUTION, RESOLUTION, false, Image.FORMAT_RH)
	snow_image.fill(Color(0, 0, 0, 1))
	snow_texture = ImageTexture.create_from_image(snow_image)
	mask_image = Image.create(16, 16, false, Image.FORMAT_R8)
	mask_image.fill(Color.BLACK)
	mask_texture = ImageTexture.create_from_image(mask_image)
	container = Node3D.new()
	container.name = "DeformedSandTiles"
	root.add_child(container)
	var base := root.get_node(root.ground_node) as MeshInstance3D
	var material := base.material_override.duplicate() as ShaderMaterial
	base.material_override = material
	bind_material(material)
	material.set_shader_parameter("fine_tile_mask", mask_texture)
	if root.quality_name != "reference" and base.mesh != null:
		preload("res://desert_battlefield/terrain_chunks.gd").partition(base, 48 if OS.has_feature("android") else 32)
	fine_material = material.duplicate() as ShaderMaterial
	fine_material.set_shader_parameter("fine_geometry", true)
	live_materials.append(fine_material)
	# Fine patches sample the field too: they must follow it when it recentres.
	bound.append(fine_material)
	cars = root.vehicles()
	for car in cars: bind_material(car.track_material, false)
	var side := tile_vertices - 1
	shared_indices.resize(side * side * 6)
	for z in side:
		for x in side:
			var a := z * tile_vertices + x
			var offset := (z * side + x) * 6
			shared_indices[offset] = a
			shared_indices[offset + 1] = a + 1
			shared_indices[offset + 2] = a + tile_vertices
			shared_indices[offset + 3] = a + 1
			shared_indices[offset + 4] = a + tile_vertices + 1
			shared_indices[offset + 5] = a + tile_vertices

func bind_material(material: ShaderMaterial, live: bool = true) -> void:
	material.set_shader_parameter("soil_center", center)
	if material not in bound: bound.append(material)
	material.set_shader_parameter("soil_map", texture)
	material.set_shader_parameter("soil_enabled", true)
	material.set_shader_parameter("soil_touched", touched_texture)
	material.set_shader_parameter("snow_map", snow_texture)
	material.set_shader_parameter("snow_layer", follow)
	if live and material not in live_materials: live_materials.append(material)

## Each grounded tyre on deformable ground contributes a segment from where it
## was at the last field upload to where it is now, carrying the rut depth the
## CPU field already has there. Ground materials draw these until the next upload.
func _update_live_segments() -> void:
	if live_materials.is_empty(): return
	var segments: Array[Vector4] = []
	var depths := PackedFloat32Array()
	segments.resize(12)
	depths.resize(12)
	var slot := 0
	for car in cars:
		if slot >= 12: break
		if car.wheel_contacts.size() != 4: continue
		for i in 4:
			var now: Vector3 = car.wheel_contacts[i]
			var start: Vector3 = car.upload_contacts[i] if car.upload_contacts.size() == 4 else now
			if Vector2(start.x - now.x, start.z - now.z).length_squared() > 25.0: start = now
			var depth := depth_at(now.x, now.z) if car.wheel_grounded[i] else 0.0
			segments[slot] = Vector4(start.x, start.z, now.x, now.z)
			depths[slot] = maxf(depth, 0.0)
			slot += 1
	var packed: Array[Vector4] = [Vector4(depths[0], depths[1], depths[2], depths[3]),
		Vector4(depths[4], depths[5], depths[6], depths[7]), Vector4(depths[8], depths[9], depths[10], depths[11])]
	# Only slots that actually carry a rut are looped over per pixel.
	var count := 0
	for i in 12:
		if depths[i] > 0.0: count = i + 1
	for material in live_materials:
		material.set_shader_parameter("live_segments", segments)
		material.set_shader_parameter("live_depths", packed)
		material.set_shader_parameter("live_count", count)

func depth_at(x: float, z: float) -> float:
	x -= center.x
	z -= center.y
	if field.is_empty() or absf(x) >= EXTENT or absf(z) >= EXTENT: return 0.0
	if touched[mini(int((z + EXTENT) / BLOCK), BLOCKS - 1) * BLOCKS + mini(int((x + EXTENT) / BLOCK), BLOCKS - 1)] == 0: return 0.0
	var gx := (x + EXTENT) / CELL
	var gz := (z + EXTENT) / CELL
	var ix := floori(gx)
	var iz := floori(gz)
	var wx := spline_weights(gx - ix)
	var wz := spline_weights(gz - iz)
	var value := 0.0
	var x0 := clampi(ix - 1, 0, RESOLUTION - 1)
	var x1 := clampi(ix, 0, RESOLUTION - 1)
	var x2 := clampi(ix + 1, 0, RESOLUTION - 1)
	var x3 := clampi(ix + 2, 0, RESOLUTION - 1)
	for row in 4:
		var offset := clampi(iz + row - 1, 0, RESOLUTION - 1) * RESOLUTION
		value += wz[row] * (field[offset + x0] * wx.x + field[offset + x1] * wx.y + field[offset + x2] * wx.z + field[offset + x3] * wx.w)
	if not follow: return value
	var pressed := 0.0
	for row in 4:
		var offset := clampi(iz + row - 1, 0, RESOLUTION - 1) * RESOLUTION
		pressed += wz[row] * (snow_field[offset + x0] * wx.x + snow_field[offset + x1] * wx.y + snow_field[offset + x2] * wx.z + snow_field[offset + x3] * wx.w)
	return value * (1.0 - snow_bury()) + pressed

static func spline_weights(f: float) -> Vector4:
	var f2 := f * f
	var f3 := f2 * f
	var a := 1.0 - f
	return Vector4(a * a * a, 3.0 * f3 - 6.0 * f2 + 4.0, -3.0 * f3 + 3.0 * f2 + 3.0 * f + 1.0, f3) / 6.0

func height_at(x: float, z: float) -> float:
	var ground := Surface.height_at(x, z)
	return ground + snow_lift(x, z, ground) - depth_at(x, z)

## How much the snow buries the soil field (0 bare .. 1 fully covered).
func snow_bury() -> float:
	return smoothstep(0.03, 0.1, Surface.snow_depth * Surface.snow_cover) if follow else 0.0

## Height of the snow pack at a point: thinner on roads (packed by traffic),
## none on water (the ground shader lifts the surface the same way).
func snow_lift(x: float, z: float, ground: float) -> float:
	if not follow or Surface.snow_depth <= 0.005 or Surface.snow_cover <= 0.0: return 0.0
	var kind := Surface.surface_at(x, z)
	if kind == Surface.WATER: return 0.0
	var open := ROAD_SNOW if kind in [Surface.DIRT, Surface.GRAVEL, Surface.MUD] else 1.0
	return Surface.snow_depth * Surface.snow_cover * open * smoothstep(Surface.water_level(), Surface.water_level() + 0.4, ground)

func normal_at(x: float, z: float) -> Vector3:
	return Vector3(height_at(x - CELL, z) - height_at(x + CELL, z), CELL * 2,
		height_at(x, z - CELL) - height_at(x, z + CELL)).normalized()

## Limit the eventual depth without suppressing the first visible tyre pass.
## Grip, resistance and the snow pack remain controlled by the surface system.
func rut_depth_scale_at(x: float, z: float) -> float:
	return 1.0

func stamp_segment(a: Vector3, b: Vector3, forward: Vector3, slip: float) -> void:
	var distance := Vector2(b.x - a.x, b.z - a.z).length()
	# Resets/teleports must never excavate a line across the map.
	if distance < 0.025 or distance > 4.0: return
	var steps := maxi(1, ceili(distance / 0.22))
	for i in steps:
		stamp(a.lerp(b, float(i + 1) / steps), forward, distance / steps, slip)

func stamp(point: Vector3, forward: Vector3, travel: float, slip: float) -> void:
	# Field-local position (index maths); ground queries use the world position.
	var world := point
	point -= Vector3(center.x, 0.0, center.y)
	if field.is_empty() or absf(point.x) > EXTENT - 1 or absf(point.z) > EXTENT - 1: return
	if travel <= 0.0: return
	slip = clampf(slip, 0.0, 1.0)
	# The compacted concrete-station foundation does not excavate like loose dunes.
	var softness := Surface.rut_softness(world.x, world.z)
	if softness < 0.01: return
	if follow and snow_bury() > 0.5:
		_stamp_snow(point, world, forward, travel)
		return
	# Test grounds vary per texel: asphalt never ruts, mud compacts readily.
	var mixed_ground := Surface.in_test_ground(world.x, world.z, 1.5)
	var uniform_deform := 1.0
	var uniform_scale := 1.0
	if mixed_ground:
		# Sample the footprint corners; only a footprint straddling two grounds
		# needs the per-texel lookup below.
		uniform_deform = Surface.ruts_at(world.x, world.z)
		uniform_scale = rut_depth_scale_at(world.x, world.z)
		mixed_ground = false
		for corner: Vector2 in [Vector2(-1.1, -1.1), Vector2(1.1, -1.1), Vector2(-1.1, 1.1), Vector2(1.1, 1.1)]:
			if Surface.ruts_at(world.x + corner.x, world.z + corner.y) != uniform_deform or rut_depth_scale_at(world.x + corner.x, world.z + corner.y) != uniform_scale: mixed_ground = true
		if not mixed_ground and uniform_deform <= 0.0 and slip < MARK_SLIP: return
	# Hard ground (asphalt, rock) cannot rut, but a skidding tyre leaves rubber:
	# a 4 mm "depth" that the ground shaders draw as a dark mark.
	var marking := slip >= MARK_SLIP
	var mark_strength := clampf((slip - MARK_SLIP) / 0.45, 0.25, 1.0)
	var direction := Vector2(forward.x, forward.z).normalized()
	var side := Vector2(-direction.y, direction.x)
	var foot := Vector2(point.x, point.z)
	# Accumulate wheel travel against a deeper bearing layer. A first passage is
	# shallow; subsequent front/rear axle passages keep compacting the same rut.
	# The exponential response depends on distance, not frame rate/stamp count,
	# and slows naturally near the bearing limit instead of sinking forever.
	var wear_step := COMPACTION_PER_METRE * travel * (1.0 + slip * 1.3) * sqrt(depth_scale)
	var base_limit := lerpf(ROLLING_DEPTH, MAX_DEPTH, slip) * softness * depth_scale
	var limit := minf(base_limit * uniform_deform, max_depth) * uniform_scale * tyre_sink
	var radius := 0.95 + slip * 0.10
	var stamp_changed := false
	for iz in range(floori((point.z - radius + EXTENT) / CELL), ceili((point.z + radius + EXTENT) / CELL) + 1):
		for ix in range(floori((point.x - radius + EXTENT) / CELL), ceili((point.x + radius + EXTENT) / CELL) + 1):
			var delta := Vector2(ix * CELL - EXTENT, iz * CELL - EXTENT) - foot
			var along := 1.0 - smoothstep(0.10, 0.46, absf(delta.dot(direction)))
			if along < 0.001: continue
			var deform := uniform_deform
			var depth_scale_at_point := uniform_scale
			if mixed_ground:
				var x := ix * CELL - EXTENT + center.x
				var z := iz * CELL - EXTENT + center.y
				deform = Surface.ruts_at(x, z)
				depth_scale_at_point = rut_depth_scale_at(x, z)
				limit = minf(base_limit * deform, max_depth) * depth_scale_at_point
			var lateral := absf(delta.dot(side))
			if deform <= 0.0:
				if not marking: continue
				var rubber := MARK_DEPTH * mark_strength * along * exp(-lateral * lateral / 0.045)
				var old := field[iz * RESOLUTION + ix]
				if rubber <= old + 0.00005: continue
				field[iz * RESOLUTION + ix] = rubber
				image.set_pixel(ix, iz, Color(rubber, 0, 0, 1))
				dirty = true
				stamp_changed = true
				changed_pixels += 1
				continue
			var tread_distance := lateral / (0.30 + slip * 0.065)
			var shoulder_distance := (lateral - 0.61 - slip * 0.09) / 0.22
			var tread := exp(-tread_distance * tread_distance)
			var shoulder := exp(-shoulder_distance * shoulder_distance)
			var target := limit * tread - minf(MAX_BANK * depth_scale, limit * 0.3) * shoulder
			var index := iz * RESOLUTION + ix
			var previous := field[index]
			# A wheel flattens an old bank before digging; shoulders never fill ruts.
			var difference := maxf(0.0, target - previous) if target > 0 else (minf(0.0, target - previous) if previous <= 0 else 0.0)
			# Footprint weighting belongs inside the exponent, so splitting equal
			# travel into more updates does not change the accumulated compaction.
			# A smaller bearing limit is reached sooner; initial impressions stay
			# visible even though repeated wheelspin cannot dig as deep.
			var response := 1.0 - exp(-wear_step * along * maxf(deform, 0.35) / depth_scale_at_point)
			var value := clampf(previous + difference * response, -MAX_BANK * depth_scale, max_depth)
			if absf(value - previous) < 0.00002: continue
			field[index] = value
			image.set_pixel(ix, iz, Color(value, 0, 0, 1))
			dirty = true
			stamp_changed = true
			changed_pixels += 1
	if stamp_changed: _mark_changed(point, radius)

## A tyre working against the wall of its hole digs its way out: the ground ahead is
## cut down to a ramp of this slope from the bottom it stands in, so it can climb.
func dig_out(point: Vector3, forward: Vector3, slope: float, amount: float) -> void:
	var world := point
	point -= Vector3(center.x, 0.0, center.y)
	if field.is_empty() or absf(point.x) > EXTENT - 5.0 or absf(point.z) > EXTENT - 5.0: return
	if follow and snow_bury() > 0.5: return
	var direction := Vector2(forward.x, forward.z).normalized()
	var side := Vector2(-direction.y, direction.x)
	var foot := Vector2(point.x, point.z)
	# From the bottom of the hole: the tyre may already stand partway up its wall.
	var bottom := depth_at(world.x, world.z)
	for back: float in [0.15, 0.3, 0.45, 0.6, 0.75]:
		var there := depth_at(world.x - direction.x * back, world.z - direction.y * back)
		if there > bottom:
			bottom = there
			foot = Vector2(point.x, point.z) - direction * back
	if bottom < 0.06: return
	point = Vector3(foot.x, point.y, foot.y)
	var length := minf(bottom / maxf(slope, 0.05), 3.5)
	var response := 1.0 - exp(-amount)
	var changed := false
	for iz in range(floori((point.z - length - 0.5 + EXTENT) / CELL), ceili((point.z + length + 0.5 + EXTENT) / CELL) + 1):
		for ix in range(floori((point.x - length - 0.5 + EXTENT) / CELL), ceili((point.x + length + 0.5 + EXTENT) / CELL) + 1):
			var delta := Vector2(ix * CELL - EXTENT, iz * CELL - EXTENT) - foot
			var ahead := delta.dot(direction)
			var lateral := absf(delta.dot(side))
			if ahead <= 0.0 or ahead > length or lateral > 0.45: continue
			var target := (bottom - ahead * slope) * (1.0 - smoothstep(0.25, 0.45, lateral))
			var index := iz * RESOLUTION + ix
			var previous := field[index]
			if target <= previous: continue
			if Surface.ruts_at(ix * CELL - EXTENT + center.x, iz * CELL - EXTENT + center.y) <= 0.0: continue
			var value := minf(previous + (target - previous) * response, max_depth)
			field[index] = value
			image.set_pixel(ix, iz, Color(value, 0, 0, 1))
			changed = true
			changed_pixels += 1
	if changed:
		dirty = true
		_mark_changed(point, length + 0.5)

## After a stamp of this radius: the field's blocks and fine tiles it touched.
func _mark_changed(point: Vector3, radius: float) -> void:
	if true:
		revision += 1
		var reach := radius + CELL * 3.0
		for bz in range(maxi(0, floori((point.z - reach + EXTENT) / BLOCK)), mini(BLOCKS - 1, floori((point.z + reach + EXTENT) / BLOCK)) + 1):
			for bx in range(maxi(0, floori((point.x - reach + EXTENT) / BLOCK)), mini(BLOCKS - 1, floori((point.x + reach + EXTENT) / BLOCK)) + 1):
				if touched[bz * BLOCKS + bx] == 0:
					touched[bz * BLOCKS + bx] = 1
					touched_image.set_pixel(bx, bz, Color.WHITE)
		# Queue touched tiles once per footprint, not once per changed texel.
		var margin := radius + CELL * 2.0
		for tz in range(maxi(0, floori((point.z - margin + EXTENT) / TILE_SIZE)), mini(15, floori((point.z + margin + EXTENT) / TILE_SIZE)) + 1):
			for tx in range(maxi(0, floori((point.x - margin + EXTENT) / TILE_SIZE)), mini(15, floori((point.x + margin + EXTENT) / TILE_SIZE)) + 1):
				var tile := Vector2i(tx, tz)
				# Test grounds render their own deformable surface; a fine sand
				# patch hidden entirely beneath one would only cost geometry.
				if not tiles.has(tile) and not _tile_hidden(tile): pending[tile] = true

## A tyre in lying snow: presses the snow down (most of its depth, at once)
## and pushes a little up at the sides; the buried soil is left as it was.
func _stamp_snow(point: Vector3, world: Vector3, forward: Vector3, travel: float) -> void:
	var local := snow_lift(world.x, world.z, Surface.height_at(world.x, world.z))
	if local <= 0.005: return
	var direction := Vector2(forward.x, forward.z).normalized()
	var side := Vector2(-direction.y, direction.x)
	var foot := Vector2(point.x, point.z)
	const RADIUS := 0.95
	var changed := false
	for iz in range(floori((point.z - RADIUS + EXTENT) / CELL), ceili((point.z + RADIUS + EXTENT) / CELL) + 1):
		for ix in range(floori((point.x - RADIUS + EXTENT) / CELL), ceili((point.x + RADIUS + EXTENT) / CELL) + 1):
			var delta := Vector2(ix * CELL - EXTENT, iz * CELL - EXTENT) - foot
			var along := 1.0 - smoothstep(0.10, 0.46, absf(delta.dot(direction)))
			if along < 0.001: continue
			var lateral := absf(delta.dot(side))
			# A flat-bottomed track with steep walls, the pushed-aside snow
			# heaped in a ridge along each side.
			var tread := 1.0 - smoothstep(0.2, 0.36, lateral)
			var shoulder := exp(-pow((lateral - 0.5) / 0.14, 2.0))
			var target := local * 0.85 * tread - local * 0.22 * shoulder
			var index := iz * RESOLUTION + ix
			var previous := snow_field[index]
			var difference := maxf(0.0, target - previous) if target > 0.0 else minf(0.0, target - previous) if previous <= 0.0 else 0.0
			var value := previous + difference * (1.0 - exp(-travel * along * 8.0))
			if absf(value - previous) < 0.0005: continue
			snow_field[index] = value
			snow_image.set_pixel(ix, iz, Color(value, 0, 0, 1))
			changed = true
	if not changed: return
	snow_dirty = true
	revision += 1
	_mark_touched(point, RADIUS)

## Marks the 4 m blocks and queues the fine patches around a stamp.
func _mark_touched(point: Vector3, radius: float) -> void:
	var reach := radius + CELL * 3.0
	for bz in range(maxi(0, floori((point.z - reach + EXTENT) / BLOCK)), mini(BLOCKS - 1, floori((point.z + reach + EXTENT) / BLOCK)) + 1):
		for bx in range(maxi(0, floori((point.x - reach + EXTENT) / BLOCK)), mini(BLOCKS - 1, floori((point.x + reach + EXTENT) / BLOCK)) + 1):
			if touched[bz * BLOCKS + bx] == 0:
				touched[bz * BLOCKS + bx] = 1
				touched_image.set_pixel(bx, bz, Color.WHITE)
				dirty = true
	var margin := radius + CELL * 2.0
	for tz in range(maxi(0, floori((point.z - margin + EXTENT) / TILE_SIZE)), mini(15, floori((point.z + margin + EXTENT) / TILE_SIZE)) + 1):
		for tx in range(maxi(0, floori((point.x - margin + EXTENT) / TILE_SIZE)), mini(15, floori((point.x + margin + EXTENT) / TILE_SIZE)) + 1):
			var tile := Vector2i(tx, tz)
			if not tiles.has(tile) and not _tile_hidden(tile): pending[tile] = true

## Follows the snow pack: fresh snow fills the tracks pressed into the old
## (by as much as fell since), melting caps them at what is left, and with the
## snow gone the layer clears. Runs every couple of seconds over touched blocks.
func _settle_snow() -> void:
	if settle_block < 0:
		settle_depth = Surface.snow_depth * Surface.snow_cover
		if settle_depth == snow_seen: return
		settle_fallen = maxf(0.0, settle_depth - snow_seen)
		snow_seen = settle_depth
		settle_bury = snow_bury()
		settle_block = 0
	var deadline := Time.get_ticks_usec() + 900
	while settle_block < BLOCKS * BLOCKS:
		if Time.get_ticks_usec() >= deadline: return
		var b := settle_block
		settle_block += 1
		if touched[b] == 0: continue
		var bx := b % BLOCKS
		var bz := b / BLOCKS
		for row in 17:
			var start := mini(bz * 16 + row, RESOLUTION - 1) * RESOLUTION + bx * 16
			for col in 17:
				# Adjacent touched blocks share border samples. Settle each once.
				if col == 0 and bx > 0 and touched[b - 1]: continue
				if row == 0 and bz > 0 and touched[b - BLOCKS]: continue
				if row == 0 and col == 0 and bx > 0 and bz > 0 and touched[b - BLOCKS - 1]: continue
				var i := start + mini(col, RESOLUTION - 1 - bx * 16)
				var v := snow_field[i]
				# Ruts in a road (thin, packed snow) stay partly visible: the
				# layer gives back that share of the buried soil depth.
				var shown := 0.0
				if settle_depth > 0.001 and field[i] > 0.002:
					var x := center.x + (i % RESOLUTION) * CELL - EXTENT
					var z := center.y + (i / RESOLUTION) * CELL - EXTENT
					if Surface.surface_at(x, z) in [Surface.DIRT, Surface.GRAVEL, Surface.MUD]: shown = field[i] * settle_bury * ROAD_RUTS_SHOWN
				if v == 0.0 and shown == 0.0: continue
				# Tracks fill up (never above the surface); side ridges stay.
				var settled := clampf(v - settle_fallen, 0.0, settle_depth) if v > 0.0 else maxf(v, -settle_depth * 0.15)
				settled = maxf(settled, shown)
				if absf(settled) < 0.002: settled = 0.0
				if settled != v:
					snow_field[i] = settled
					snow_image.set_pixel(i % RESOLUTION, i / RESOLUTION, Color(settled, 0, 0))
					snow_dirty = true
	settle_block = -1

func _upload_snow() -> void:
	# One bulk conversion (no per-texel writes).
	snow_image = Image.create_from_data(RESOLUTION, RESOLUTION, false, Image.FORMAT_RF, snow_field.to_byte_array())
	snow_image.convert(Image.FORMAT_RH)
	snow_texture.update(snow_image)
	snow_dirty = false

## Queue the fine patch under a point the car is about to reach, so its ruts
## render on refined geometry from the first contact.
func prefetch(point: Vector3) -> void:
	var world := point
	point -= Vector3(center.x, 0.0, center.y)
	if container == null or absf(point.x) >= EXTENT or absf(point.z) >= EXTENT: return
	var tile := Vector2i(clampi(floori((point.x + EXTENT) / TILE_SIZE), 0, 15), clampi(floori((point.z + EXTENT) / TILE_SIZE), 0, 15))
	if not tiles.has(tile) and not pending.has(tile) and not _tile_hidden(tile) \
		and Surface.ruts_at(world.x, world.z) > 0.0: pending[tile] = true

func _tile_hidden(tile: Vector2i) -> bool:
	var x0 := tile.x * TILE_SIZE - EXTENT - CELL + center.x
	var z0 := tile.y * TILE_SIZE - EXTENT - CELL + center.y
	var x1 := x0 + TILE_SIZE + CELL * 2.0
	var z1 := z0 + TILE_SIZE + CELL * 2.0
	for hole: Vector4 in Surface.hidden_rects():
		if x0 >= hole.x and z0 >= hole.y and x1 <= hole.z and z1 <= hole.w: return true
	return false

func update(delta: float, force: bool = false) -> void:
	if container == null: return
	if follow and not cars.is_empty():
		var p: Vector3 = cars[0].position
		if maxf(absf(p.x - center.x), absf(p.z - center.y)) > FOLLOW_DISTANCE:
			if build_task == -1 or WorkerThreadPool.is_task_completed(build_task): recenter(Vector2(p.x, p.z))
		_lay_worn_ruts()
	_build_pending(force)
	upload_clock += delta
	if follow:
		snow_clock += delta
		if settle_block >= 0 or snow_clock > 2.0:
			snow_clock = 0.0
			_settle_snow()
		if snow_dirty and (upload_clock >= upload_interval or force):
			snow_texture.update(snow_image)
			snow_dirty = false
			if not dirty:
				upload_clock = 0.0
				for car in cars: car.upload_contacts.assign(car.wheel_contacts)
	if (upload_clock >= upload_interval or force) and dirty:
		texture.update(image)
		# Sent with the field so the GPU skip mask never hides uploaded ruts.
		touched_texture.update(touched_image)
		dirty = false
		upload_clock = 0.0
		uploaded_revision = revision
		for car in cars: car.upload_contacts.assign(car.wheel_contacts)
	if mask_dirty:
		mask_texture.update(mask_image)
		mask_dirty = false
	# Nothing waiting for upload: the GPU field is current, so segments restart
	# at the wheels (never a straight chord across an earlier turn).
	if not dirty:
		for car in cars: car.upload_contacts.assign(car.wheel_contacts)
	_update_live_segments()

## Fine patches are computed on a worker thread (pure surface maths) and only
## turned into a mesh on the main thread, so driving into new sand costs no
## frame time. `force` (tests, captures) builds synchronously.
func _build_pending(force: bool) -> void:
	if build_task != -1:
		if not force and not WorkerThreadPool.is_task_completed(build_task): return
		WorkerThreadPool.wait_for_task_completion(build_task)
		build_task = -1
		_finish_tile(build_tile_id, build_result)
		pending.erase(build_tile_id)
	if pending.is_empty(): return
	var tile := Vector2i.ZERO
	for key: Vector2i in pending:
		tile = key
		break
	if force or not threaded:
		_finish_tile(tile, _compute_tile(tile))
		pending.erase(tile)
		return
	build_tile_id = tile
	build_task = WorkerThreadPool.add_task(func(): build_result = _compute_tile(tile), false, "Sand patch")

func build_tile(tile: Vector2i) -> void:
	_finish_tile(tile, _compute_tile(tile))

func _compute_tile(tile: Vector2i) -> Dictionary:
	var vertices := PackedVector3Array()
	var normals := PackedVector3Array()
	# A one-cell halo makes both sides of a tile seam use fragment clipping,
	# avoiding MSAA coverage cracks between clipped coarse and fine triangles.
	vertices.resize(tile_vertices * tile_vertices)
	normals.resize(tile_vertices * tile_vertices)
	var origin := Vector2(tile) * TILE_SIZE - Vector2.ONE * EXTENT + center
	for z in tile_vertices:
		for x in tile_vertices:
			var px := origin.x + (x - 1) * geometry_cell
			var pz := origin.y + (z - 1) * geometry_cell
			var i := z * tile_vertices + x
			var border := maxf(0, minf(minf(x - 1, tile_quads + 1 - x), minf(z - 1, tile_quads + 1 - z))) * geometry_cell
			# Match the exact coarse triangles at the replacement boundary. Without
			# this, tiny analytic/interpolated height differences reveal hairline gaps.
			var height := Surface.height_at(px, pz)
			if border < 0.75: height = lerpf(coarse_height(px, pz), height, smoothstep(0.0, 0.75, border))
			vertices[i] = Vector3(px, height, pz)
			# Near seams use the coarse mesh's analytic normals; interiors are
			# derived from the height grid (no extra surface samples).
			if border < 1.0: normals[i] = Surface.normal_at(px, pz)
	var span := 2.0 * geometry_cell
	for z in range(1, tile_vertices - 1):
		for x in range(1, tile_vertices - 1):
			var border := minf(minf(x - 1, tile_quads + 1 - x), minf(z - 1, tile_quads + 1 - z)) * geometry_cell
			if border < 1.0: continue
			var i := z * tile_vertices + x
			normals[i] = Vector3(vertices[i - 1].y - vertices[i + 1].y, span,
				vertices[i - tile_vertices].y - vertices[i + tile_vertices].y).normalized()
	return {"vertices": vertices, "normals": normals}

func _finish_tile(tile: Vector2i, data: Dictionary) -> void:
	if tiles.has(tile): return
	var origin := Vector2(tile) * TILE_SIZE - Vector2.ONE * EXTENT + center
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = data.vertices
	arrays[Mesh.ARRAY_NORMAL] = data.normals
	arrays[Mesh.ARRAY_INDEX] = shared_indices
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	var patch := MeshInstance3D.new()
	patch.name = "Sand_%d_%d" % [tile.x, tile.y]
	patch.mesh = mesh
	patch.material_override = fine_material
	patch.set_instance_shader_parameter("patch_bounds", Vector4(origin.x, origin.y, origin.x + TILE_SIZE, origin.y + TILE_SIZE))
	patch.extra_cull_margin = max_depth + MAX_BANK * depth_scale
	if phone: patch.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	container.add_child(patch)
	tiles[tile] = patch
	mask_image.set_pixel(tile.x, tile.y, Color.WHITE)
	mask_dirty = true

## Moves the field to a new centre (snapped to whole tiles). Ruts are kept for
## the whole world: every touched 4 m block is archived (17 × 17 texels, its
## edges included) under its world block index before the move, and every
## archived block that falls inside the new field is copied back, with its fine
## patches queued again. Untouched ground costs nothing; a 1.6 km lap of ruts
## is about 2 MB.
func recenter(world: Vector2) -> void:
	if build_task != -1:
		WorkerThreadPool.wait_for_task_completion(build_task)
		build_task = -1
	_archive_blocks()
	var previous := center
	center = (world / TILE_SIZE).round() * TILE_SIZE
	field.fill(0.0)
	snow_field.fill(0.0)
	touched.fill(0)
	touched_image.fill(Color.BLACK)
	mask_image.fill(Color.BLACK)
	# Fine patches are world geometry: those still inside the field are kept
	# (under their new tile index), so ruts never blink out while the patches
	# would otherwise be rebuilt one by one.
	var shift := Vector2i(roundi((center.x - previous.x) / TILE_SIZE), roundi((center.y - previous.y) / TILE_SIZE))
	var kept := {}
	for tile: Vector2i in tiles:
		var moved := tile - shift
		if moved.x >= 0 and moved.y >= 0 and moved.x < 16 and moved.y < 16:
			kept[moved] = tiles[tile]
			mask_image.set_pixel(moved.x, moved.y, Color.WHITE)
		else:
			tiles[tile].queue_free()
	tiles = kept
	pending.clear()
	_restore_blocks()
	# (The endless world lays its old ruts on the new ground: see update.)
	worn_due = true
	worn_known = Rect2(previous - Vector2(EXTENT, EXTENT) * 0.94, Vector2(EXTENT, EXTENT) * 1.88)
	# One bulk conversion instead of a per-texel write (no hitch on the move).
	image = Image.create_from_data(RESOLUTION, RESOLUTION, false, Image.FORMAT_RF, field.to_byte_array())
	image.convert(Image.FORMAT_RH)
	texture.update(image)
	touched_texture.update(touched_image)
	mask_texture.update(mask_image)
	dirty = false
	if follow: _upload_snow()
	mask_dirty = false
	revision += 1
	for material in bound: material.set_shader_parameter("soil_center", center)
	for car in cars: car.upload_contacts.assign(car.wheel_contacts)

## Old ruts for worlds that have them (endless_terrain.gd stamp_worn): laid on the
## part of the road the field has just reached, when the planner is not busy
## with the roads. Archived blocks far behind are forgotten then.
var worn_due := true
var worn_known := Rect2()

func _lay_worn_ruts() -> void:
	if not worn_due or Surface.world == null or Surface.world.resource_path != "res://offroad/endless_terrain.gd": return
	if not Surface.world.plan_lock.try_lock(): return
	worn_due = false
	Surface.world.stamp_worn(self, Rect2(center - Vector2(EXTENT, EXTENT) + Vector2(4, 4), Vector2(EXTENT, EXTENT) * 2.0 - Vector2(8, 8)), worn_known)
	Surface.world.plan_lock.unlock()
	var origin := _block_origin()
	for key: Vector2i in archive.keys():
		if (key - origin).length_squared() > 90000:
			archive.erase(key)
			snow_archive.erase(key)
	image = Image.create_from_data(RESOLUTION, RESOLUTION, false, Image.FORMAT_RF, field.to_byte_array())
	image.convert(Image.FORMAT_RH)
	texture.update(image)
	touched_texture.update(touched_image)
	dirty = false
	revision += 1

## One texel of an old rut (world position): the deeper of it and what is there;
## a low ridge only where nothing was.
func lay_worn(world: Vector2, depth: float) -> void:
	var ix := roundi((world.x - center.x + EXTENT) / CELL)
	var iz := roundi((world.y - center.y + EXTENT) / CELL)
	if ix < 0 or iz < 0 or ix >= RESOLUTION or iz >= RESOLUTION: return
	var i := iz * RESOLUTION + ix
	if depth > field[i] or (depth < 0.0 and field[i] == 0.0): field[i] = depth

## Marks the blocks and fine patches around a worn stretch of road.
func touch_worn(world: Vector2, radius: float) -> void:
	_mark_touched(Vector3(world.x - center.x, 0.0, world.y - center.y), radius)

## World block index of the field's block (0, 0).
func _block_origin() -> Vector2i:
	return Vector2i(roundi((center.x - EXTENT) / BLOCK), roundi((center.y - EXTENT) / BLOCK))

func _archive_blocks() -> void:
	const SIDE := 17
	var origin := _block_origin()
	for bz in BLOCKS:
		for bx in BLOCKS:
			if touched[bz * BLOCKS + bx] == 0: continue
			var cells := PackedFloat32Array()
			for row in SIDE:
				var start := (bz * 16 + row) * RESOLUTION + bx * 16
				cells.append_array(field.slice(start, start + SIDE))
			archive[origin + Vector2i(bx, bz)] = cells
			if not follow: continue
			var pressed := PackedFloat32Array()
			for row in SIDE:
				var start := (bz * 16 + row) * RESOLUTION + bx * 16
				pressed.append_array(snow_field.slice(start, start + SIDE))
			snow_archive[origin + Vector2i(bx, bz)] = pressed

func _restore_blocks() -> void:
	const SIDE := 17
	var origin := _block_origin()
	for key: Vector2i in archive:
		var b := key - origin
		if b.x < 0 or b.y < 0 or b.x >= BLOCKS or b.y >= BLOCKS: continue
		var cells: PackedFloat32Array = archive[key]
		for row in SIDE:
			var start := (b.y * 16 + row) * RESOLUTION + b.x * 16
			for col in SIDE:
				# Shared edge texels: keep the deeper of two neighbouring blocks.
				var v := cells[row * SIDE + col]
				if absf(v) > absf(field[start + col]): field[start + col] = v
		if snow_archive.has(key):
			var pressed: PackedFloat32Array = snow_archive[key]
			for row in SIDE:
				var start := (b.y * 16 + row) * RESOLUTION + b.x * 16
				for col in SIDE:
					var v := pressed[row * SIDE + col]
					if absf(v) > absf(snow_field[start + col]): snow_field[start + col] = v
		touched[b.y * BLOCKS + b.x] = 1
		touched_image.set_pixel(b.x, b.y, Color.WHITE)
		var tile := Vector2i(b.x / 4, b.y / 4)
		if not tiles.has(tile) and not _tile_hidden(tile): pending[tile] = true

func coarse_height(x: float, z: float) -> float:
	const STEP := 0.75
	var a := Vector2(floorf(x / STEP), floorf(z / STEP)) * STEP
	var f := (Vector2(x, z) - a) / STEP
	# The authored coarse mesh is the pure dune function (test grounds are separate).
	var h00 := Surface.dune_height(a.x, a.y)
	var h10 := Surface.dune_height(a.x + STEP, a.y)
	var h01 := Surface.dune_height(a.x, a.y + STEP)
	if f.x + f.y <= 1.0: return h00 + (h10 - h00) * f.x + (h01 - h00) * f.y
	var h11 := Surface.dune_height(a.x + STEP, a.y + STEP)
	return h11 + (h01 - h11) * (1.0 - f.x) + (h10 - h11) * (1.0 - f.y)
