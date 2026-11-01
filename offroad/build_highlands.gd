extends SceneTree
## Bakes Lesná vrchovina: height and ground maps from the layout in highlands.gd,
## the coarse ground mesh, and the scene (the desert scene's vehicles, cameras,
## sun and atmosphere with this world plugged in).
## godot --headless --path . --script res://offroad/build_highlands.gd
const H = preload("res://offroad/highlands.gd")
const Surface = preload("res://desert_battlefield/terrain.gd")
const N := H.N
const SHOULDER := 4.0
const SCENE_PATH := "res://offroad/highlands.tscn"
var h := PackedFloat32Array()
var kind := PackedByteArray()
## Nearest trail per texel: distance, trail index, levelled centre height, arc length.
var trail_dist := PackedFloat32Array()
var trail_id := PackedInt32Array()
var trail_h := PackedFloat32Array()
var trail_along := PackedFloat32Array()
var trail_length := PackedFloat32Array()
## Levelled centre heights per trail (already built ones), for junctions.
var centres: Array[PackedFloat32Array] = []
## Per trail and polyline sample: is the trail straight there (ice lies on
## straight stretches, never in the steeper bends)?
var straight: Array = []

func _initialize() -> void: call_deferred("run")

static func coord(i: int) -> float:
	return -H.HALF + i * H.STEP

## Rolling hills, the summit and east forest hills, the serpentine mountain and
## the range closing the map.
static func hills(x: float, z: float) -> float:
	var b := 4.0 * sin(x * 0.018 + 0.6) * cos(z * 0.015 - 0.4) + 2.4 * sin(x * 0.041 + z * 0.023 + 1.3)
	b += 1.2 * sin(x * 0.083 - z * 0.061) * sin(z * 0.072 + 0.5) + 0.45 * sin(x * 0.17 + 1.0) * sin(z * 0.19)
	# Calmer rolling on the mountain slope, so the serpentine's grade stays even.
	b *= 1.0 - 0.6 * smoothstep(130.0, 170.0, z) * (1.0 - smoothstep(70.0, 115.0, absf(x - H.MOUNTAIN_X.x)))
	b += 6.0
	# Off-road bumps: 0.3–0.5 m rolls every few metres (smoothed by the 0.5 m grid).
	b += 0.32 * sin(x * 0.29 + z * 0.13) * sin(z * 0.31 - x * 0.07) + 0.16 * sin(x * 0.61 + 1.3) * cos(z * 0.53 + 0.4)
	for hill: Vector4 in [H.SUMMIT, H.EAST_HILLS]:
		b += hill.z * exp(-((x - hill.x) * (x - hill.x) + (z - hill.y) * (z - hill.y)) / (2.0 * hill.w * hill.w))
	b += massif(x, z)
	# Craggy boundary range (the river valley still cuts through it).
	var edge := smoothstep(296.0, 356.0, maxf(absf(x), absf(z)))
	b += edge * edge * (34.0 + 12.0 * sin(x * 0.045 + 1.1) * sin(z * 0.038 - 0.6) + 5.0 * sin(x * 0.13 - z * 0.11))
	return b

## The mountain: a slope rising north from the foot (softened over ±20 m) under
## the serpentine, ridges on both sides of its cirque.
static func massif(x: float, z: float) -> float:
	var dx := absf(x - H.MOUNTAIN_X.x)
	var t := z - H.MOUNTAIN_FOOT
	var ramp := t if t > 20.0 else ((t + 20.0) * (t + 20.0) / 80.0 if t > -20.0 else 0.0)
	var m := ramp * H.MOUNTAIN_SLOPE * (1.0 - smoothstep(H.MOUNTAIN_X.y, H.MOUNTAIN_X.y + 45.0, dx))
	# The east ridge is lower and narrower: the long road passes it into the valley.
	var east := x > H.MOUNTAIN_X.x
	var ridge := smoothstep(60.0, 92.0, dx) * (1.0 - (smoothstep(95.0, 120.0, dx) if east else smoothstep(105.0, 150.0, dx))) * smoothstep(150.0, 240.0, z)
	m += ridge * (26.0 + 6.0 * sin(z * 0.07 + x * 0.05)) * (0.5 if east else 1.0)
	return m

## Hills settling into the flat river valley.
static func valley(x: float, z: float) -> float:
	var dv := absf(x - H.river_x(z))
	var b := lerpf(hills(x, z), 1.0 + 0.06 * maxf(0.0, dv - 6.0), 1.0 - smoothstep(12.0, 58.0, dv))
	# Keep everything outside the river above the water.
	if b < 1.2: b = 1.2 - (1.2 - b) * 0.25
	return b

func sample(x: float, z: float) -> float:
	var gx := clampf((x + H.HALF) / H.STEP, 0.0, N - 1.001)
	var gz := clampf((z + H.HALF) / H.STEP, 0.0, N - 1.001)
	var ix := int(gx)
	var iz := int(gz)
	var fx := gx - ix
	var fz := gz - iz
	var a := lerpf(h[iz * N + ix], h[iz * N + ix + 1], fx)
	var b := lerpf(h[(iz + 1) * N + ix], h[(iz + 1) * N + ix + 1], fx)
	return lerpf(a, b, fz)

static func forest(x: float, z: float) -> float:
	var f := 0.5 + 0.3 * sin(x * 0.031 + 1.0) * cos(z * 0.027 - 0.5) + 0.2 * sin(x * 0.083 + z * 0.061)
	f += 0.25 * smoothstep(30.0, 80.0, x)
	f -= 0.35 * (1.0 - smoothstep(10.0, 30.0, absf(x - H.river_x(z))))
	return f

func run() -> void:
	var start := Time.get_ticks_msec()
	h.resize(N * N)
	kind.resize(N * N)
	# 1. Hills, valley and the quarry pit.
	var pit_floor := valley(H.QUARRY.x, H.QUARRY.y) - H.QUARRY_DEPTH
	# The camp clearing is levelled, so the start stands on flat ground.
	var camp_level := valley(H.CAMP.x, H.CAMP.y)
	for iz in N:
		var z := coord(iz)
		for ix in N:
			var x := coord(ix)
			var b := valley(x, z)
			var d := Vector2(x - H.QUARRY.x, z - H.QUARRY.y).length()
			b = lerpf(b, pit_floor, 1.0 - smoothstep(H.QUARRY.z - 8.0, H.QUARRY.z, d))
			b = lerpf(b, camp_level, 1.0 - smoothstep(H.CAMP.z, H.CAMP.z + 8.0, Vector2(x - H.CAMP.x, z - H.CAMP.y).length()))
			# Terrain stamps from the layout scene: smooth hills and hollows.
			for stamp: Vector4 in H.stamps:
				var r := Vector2(x - stamp.x, z - stamp.y).length()
				if r < stamp.z: b += stamp.w * (0.5 + 0.5 * cos(PI * r / stamp.z))
			h[iz * N + ix] = b
	print("BAKE terrain %d ms" % (Time.get_ticks_msec() - start))
	# 2. Trails: level a track along each centreline (heights smoothed over ±10 m
	# so it cuts through bumps), keeping the nearest trail per texel.
	trail_dist.resize(N * N)
	trail_dist.fill(INF)
	trail_id.resize(N * N)
	trail_h.resize(N * N)
	trail_along.resize(N * N)
	for t in H.trails.size():
		var trail: Dictionary = H.trails[t]
		var line: PackedVector2Array = H.paths[t]
		var count := line.size()
		var raw := PackedFloat32Array()
		for p in line: raw.append(sample(p.x, p.y))
		var centre := PackedFloat32Array()
		for k in count:
			var total := 0.0
			var weight := 0.0
			var smooth: int = trail.get("smooth", 5)
			for o in range(-smooth, smooth + 1):
				var j := k + o
				if trail.closed: j = posmod(j, count)
				elif j < 0 or j >= count: continue
				total += raw[j]
				weight += 1.0
			centre.append(total / weight)
		# Fords: where the road crosses the river it comes down to the valley
		# floor well before the water (at most 15 % from 8 m off the crossing),
		# so the bank ramp stays drivable.
		for c in count - 1:
			var side_a := line[c].x - H.river_x(line[c].y)
			var side_b := line[c + 1].x - H.river_x(line[c + 1].y)
			if signf(side_a) == signf(side_b): continue
			for k in count:
				var off := absf(float(k - c)) * 2.0
				if off < 160.0: centre[k] = minf(centre[k], 0.9 + maxf(0.0, off - 8.0) * 0.15)
		# Ponds: roads come down to the shore the same way.
		for pond: Vector4 in H.ponds:
			for k in count:
				var d := line[k].distance_to(Vector2(pond.x, pond.y))
				if d < pond.z + 120.0: centre[k] = minf(centre[k], pond.w + 0.25 + maxf(0.0, d - pond.z) * 0.15)
		# Junctions: an open trail's ends meet the road they branch off at that
		# road's level, easing in over ~24 m (else each road levels itself and
		# the junction becomes a step).
		if not trail.closed:
			for end in [0, count - 1]:
				var joint := _joined_level(line[end], t)
				if is_nan(joint): continue
				# (A third of a short trail at most, so its own grade stays.)
				var span := mini(12, count / 3)
				for k in span:
					var j: int = k if end == 0 else count - 1 - k
					centre[j] = lerpf(joint, centre[j], smoothstep(0.0, float(span), float(k)))
		centres.append(centre)
		var flat := []
		for k in count:
			var a := line[maxi(k - 3, 0)]
			var b := line[k]
			var c := line[mini(k + 3, count - 1)]
			flat.append(a == b or b == c or absf((b - a).angle_to(c - b)) < 0.2)
		straight.append(flat)
		var reach: float = trail.half + SHOULDER
		var along := 0.0
		var segments := count if trail.closed else count - 1
		for k in segments:
			var a := line[k]
			var b := line[(k + 1) % count]
			var length := a.distance_to(b)
			var ix0 := maxi(0, floori((minf(a.x, b.x) - reach + H.HALF) / H.STEP))
			var ix1 := mini(N - 1, ceili((maxf(a.x, b.x) + reach + H.HALF) / H.STEP))
			var iz0 := maxi(0, floori((minf(a.y, b.y) - reach + H.HALF) / H.STEP))
			var iz1 := mini(N - 1, ceili((maxf(a.y, b.y) + reach + H.HALF) / H.STEP))
			for iz in range(iz0, iz1 + 1):
				for ix in range(ix0, ix1 + 1):
					var p := Vector2(coord(ix), coord(iz))
					var s := clampf((p - a).dot(b - a) / maxf(length * length, 0.0001), 0.0, 1.0)
					var dist := p.distance_to(a.lerp(b, s))
					var i := iz * N + ix
					if dist < reach and dist < trail_dist[i]:
						trail_dist[i] = dist
						trail_id[i] = t
						trail_h[i] = lerpf(centre[k], centre[(k + 1) % count], s)
						trail_along[i] = along + s * length
			along += length
		trail_length.append(along)
		# Steepest grade of the levelled track over 6 m.
		var steepest := 0.0
		for k in count - 3: steepest = maxf(steepest, absf(centre[k + 3] - centre[k]) / maxf(line[k].distance_to(line[k + 3]), 0.1))
		print("TRAIL %s length=%d m steepest=%d %%" % [trail.name, roundi(along), roundi(steepest * 100.0)])
	for i in N * N:
		if trail_dist[i] == INF: continue
		var half: float = H.trails[trail_id[i]].half
		var dist := trail_dist[i]
		var w := 1.0 - smoothstep(half, half + SHOULDER, dist)
		# A slight crown inside the track, shallow ditches at its edges.
		var crown := 0.05 * (1.0 - (dist / half) * (dist / half)) if dist < half else -0.06 * (1.0 - smoothstep(half, half + 1.5, dist))
		h[i] = lerpf(h[i], trail_h[i] + crown, w)
	print("BAKE trails %d ms" % (Time.get_ticks_msec() - start))
	# 3. The river channel last, shallow where a trail fords it.
	for iz in N:
		var z := coord(iz)
		var rx := H.river_x(z)
		for ix in N:
			var dv := absf(coord(ix) - rx)
			if dv >= 24.0: continue
			var i := iz * N + ix
			# Fords: the bank ramps over 20 m instead of 5 m.
			var ford := 0.0
			var depth := 1.2
			if trail_dist[i] != INF:
				var trail: Dictionary = H.trails[trail_id[i]]
				ford = 1.0 - smoothstep(trail.half, trail.half + 3.0, trail_dist[i])
				depth = lerpf(depth, trail.ford, ford)
			# A 9 m flat-bottomed channel with gentle banks (about 25 % on each side).
			var c := 1.0 - smoothstep(4.5, lerpf(9.5, 24.0, ford), dv)
			if c <= 0.0: continue
			h[i] = lerpf(h[i], H.WATER_LEVEL - depth, c)
	# 3b. Ponds: a round bowl POND_DEPTH under the level, with low banks.
	for pond: Vector4 in H.ponds:
		var reach := pond.z + 14.0
		for iz in range(maxi(0, floori((pond.y - reach + H.HALF) / H.STEP)), mini(N, ceili((pond.y + reach + H.HALF) / H.STEP))):
			for ix in range(maxi(0, floori((pond.x - reach + H.HALF) / H.STEP)), mini(N, ceili((pond.x + reach + H.HALF) / H.STEP))):
				var d := Vector2(coord(ix) - pond.x, coord(iz) - pond.y).length()
				var i := iz * N + ix
				if d < pond.z:
					var f := d / pond.z
					h[i] = pond.w - H.POND_DEPTH * (1.0 - f * f)
				else:
					h[i] = lerpf(pond.w + 0.25 + (d - pond.z) * 0.08, h[i], smoothstep(pond.z + 4.0, reach, d))
	# 4. Ground kinds.
	for iz in N:
		var z := coord(iz)
		var rx := H.river_x(z)
		for ix in N:
			var x := coord(ix)
			var i := iz * N + ix
			var slope := Vector2(h[iz * N + mini(ix + 1, N - 1)] - h[iz * N + maxi(ix - 1, 0)],
				h[mini(iz + 1, N - 1) * N + ix] - h[maxi(iz - 1, 0) * N + ix]).length() / (2.0 * H.STEP)
			var dv := absf(x - rx)
			var k := Surface.FOREST if forest(x, z) > 0.55 else Surface.GRASS
			var noise := sin(x * 0.37 + sin(z * 0.23) * 2.0) * sin(z * 0.31 - x * 0.11)
			if Vector2(x - H.BOG.x, z - H.BOG.y).length() < H.BOG.z: k = Surface.MUD if noise > -0.2 else Surface.GRASS
			if Vector2(x - H.CAMP.x, z - H.CAMP.y).length() < H.CAMP.z: k = Surface.DIRT
			if Vector2(x - H.QUARRY.x, z - H.QUARRY.y).length() < H.QUARRY.z: k = Surface.GRAVEL
			# Above the forest: alpine meadow among rock outcrops; snow higher up.
			if h[i] > 24.0: k = Surface.ROCK if noise > 0.25 or slope > 0.5 else Surface.GRASS
			if slope > 0.75: k = Surface.ROCK
			var snowline := H.SNOWLINE + 3.0 * sin(x * 0.05 + z * 0.03) + 2.0 * noise
			var snowy := h[i] > snowline
			if snowy and slope < 0.95: k = Surface.SNOW
			# Look variant for the shader (upper 4 bits; physics reads the kind only).
			var look := 0
			if dv < 13.0 and h[i] < 1.6:
				k = Surface.MUD
				look = H.LOOK_BANK
			if trail_dist[i] != INF:
				var trail: Dictionary = H.trails[trail_id[i]]
				var dist := trail_dist[i]
				if dist < trail.half:
					k = trail.kind
					if trail.get("tracks", false):
						# Dirt road; gravel shows only in the two worn wheel tracks.
						k = Surface.GRAVEL if dist > 0.45 and dist < 1.15 else Surface.DIRT
					look = 0
					# Puddles on the forest dirt: low spots along the track.
					# (none within 15 m of a trail end, where the test spots start, nor in camp).
					var s := trail_along[i]
					var in_camp := Vector2(x - H.CAMP.x, z - H.CAMP.y).length() < H.CAMP.z
					if k == Surface.DIRT and not in_camp and s > 15.0 and s < trail_length[trail_id[i]] - 15.0 and sin(s * 0.11) * sin(s * 0.047 + 1.0) > 0.45: k = Surface.MUD
					if trail.get("bog", false) and Vector2(x - H.BOG.x, z - H.BOG.y).length() < H.BOG.z: k = Surface.MUD
					# Packed snow above the snowline; glazed ice patches on the upper
					# serpentine legs (never on the hairpins, which are steeper).
					if snowy: k = Surface.SNOW
					if trail.get("ice", false) and h[i] > H.ICELINE and straight[trail_id[i]][mini(int(s / 2.0), straight[trail_id[i]].size() - 1)] and sin(s * 0.075) > -0.15: k = Surface.ICE
				elif dist < trail.half + 1.0:
					k = Surface.GRAVEL if trail.kind in [Surface.GRAVEL, Surface.ROCK] and not trail.get("tracks", false) else Surface.DIRT
					if snowy: k = Surface.SNOW
					look = 0
			var pond := H.pond_at(x, z)
			if pond >= 0:
				var shore: Vector4 = H.ponds[pond]
				if shore.w - h[i] > 0.03:
					k = Surface.WATER
					look = 0
				elif trail_dist[i] == INF and Vector2(x - shore.x, z - shore.y).length() < shore.z + 2.5:
					k = Surface.MUD
					look = 0
			if dv < 10.0 and H.WATER_LEVEL - h[i] > 0.03:
				k = Surface.WATER
				look = 0
			kind[i] = k | (look << 4)
	print("BAKE ground %d ms" % (Time.get_ticks_msec() - start))
	# 5. Worn relief, baked so physics and the ground agree: old ruts and lumps
	# in the mud (a pair of grooves where it lies on a track, pits and humps in
	# the bog) and the gravel wheel tracks of the dirt roads sunk a little.
	for iz in N:
		var z := coord(iz)
		for ix in N:
			var x := coord(ix)
			var i := iz * N + ix
			var k := kind[i] & 15
			var on_track: bool = trail_dist[i] != INF and trail_dist[i] < H.trails[trail_id[i]].half
			var lumps := sin(x * 1.3 + sin(z * 0.9) * 1.5) * sin(z * 1.1 - x * 0.4) * 0.5 + sin(x * 2.9 + z * 2.3) * 0.25
			if k == Surface.MUD and (kind[i] >> 4) == 0:
				if on_track:
					var lateral := trail_dist[i]
					var groove := exp(-pow((lateral - 0.8) / 0.28, 2.0))
					h[i] -= 0.12 * groove + 0.04 * (1.0 + lumps) * (1.0 - groove)
				else:
					h[i] -= 0.06 + 0.07 * lumps
			elif on_track and k == Surface.GRAVEL and H.trails[trail_id[i]].get("tracks", false):
				# Two worn grooves, 11 cm deep (wide enough for the 0.5 m height grid), with a low crown of loose gravel between.
				h[i] -= 0.11 * exp(-pow((trail_dist[i] - 0.8) / 0.4, 2.0)) - 0.025 * exp(-pow(trail_dist[i] / 0.3, 2.0))
	print("BAKE relief %d ms" % (Time.get_ticks_msec() - start))
	ResourceSaver.save(Image.create_from_data(N, N, false, Image.FORMAT_RF, h.to_byte_array()), H.HEIGHTS_PATH)
	ResourceSaver.save(Image.create_from_data(N, N, false, Image.FORMAT_R8, kind), H.GROUND_PATH)
	# The runtime sampler now serves the freshly baked maps.
	H.heights = h
	H.ground = kind
	_build_mesh()
	print("BAKE mesh %d ms" % (Time.get_ticks_msec() - start))
	_build_scene()
	print("BAKE done %d ms" % (Time.get_ticks_msec() - start))
	quit()

## Ground mesh heights on the 0.75 m render grid (961 × 961): highlands_world.gd
## builds the ground chunks from them at load. The grid and triangle split are
## the desert dune mesh's, so soil.gd's coarse_height matches the chunks.
func _build_mesh() -> void:
	const SPACING := 0.75
	var side := roundi(2.0 * H.HALF / SPACING) + 1
	var grid := PackedFloat32Array()
	grid.resize(side * side)
	for z in side:
		for x in side:
			grid[z * side + x] = H.height_at(-H.HALF + x * SPACING, -H.HALF + z * SPACING)
	ResourceSaver.save(Image.create_from_data(side, side, false, Image.FORMAT_RF, grid.to_byte_array()), H.MESH_HEIGHTS_PATH)

## The desert scene with this world: same vehicles, cameras and controls, a new
## ground mesh and material, greener light and air.
func _build_scene() -> void:
	var root: Node3D = (load("res://desert_battlefield/desert_battlefield.tscn") as PackedScene).instantiate()
	root.name = "LesnaVrchovina"
	root.world_script = load("res://offroad/highlands.gd")
	root.grounds_script = load("res://offroad/highlands_world.gd")
	root.ground_node = NodePath("Ground")
	root.other_world = "res://desert_battlefield/desert_battlefield.tscn"
	root.sky_colors = PackedColorArray([Color("7fa3c4"), Color("c9d3cf"), Color("6f7a5a")])
	var dunes := root.get_node("SculptedDunes") as MeshInstance3D
	var ground := MeshInstance3D.new()
	ground.name = "Ground"
	var material := ShaderMaterial.new()
	material.shader = load("res://offroad/highlands_ground.gdshader")
	ground.material_override = material
	# Trees, rocks and the car still cast shadows; the terrain does not (it would
	# be drawn into every shadow cascade).
	ground.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	root.add_child(ground)
	root.move_child(ground, dunes.get_index())
	ground.owner = root
	root.remove_child(dunes)
	dunes.free()
	var atmosphere := root.get_node("DesertAtmosphere") as WorldEnvironment
	var environment := atmosphere.environment.duplicate() as Environment
	environment.background_color = Color(0.63, 0.7, 0.72)
	environment.ambient_light_color = Color(0.66, 0.74, 0.8)
	environment.ambient_light_energy = 0.45
	environment.fog_light_color = Color(0.68, 0.74, 0.74)
	environment.fog_density = 0.0005
	environment.volumetric_fog_albedo = Color(0.74, 0.78, 0.78)
	environment.volumetric_fog_density = 0.0008
	atmosphere.environment = environment
	var sun := root.get_node("LateAfternoonSun") as DirectionalLight3D
	sun.light_color = Color(1.0, 0.95, 0.86)
	sun.light_energy = 1.3
	var scene := PackedScene.new()
	scene.pack(root)
	ResourceSaver.save(scene, SCENE_PATH)
	root.free()

## Levelled height of an earlier trail's centreline within 5 m of a point, or NAN.
func _joined_level(p: Vector2, before: int) -> float:
	var best := 5.0
	var level := NAN
	for t in before:
		var line: PackedVector2Array = H.paths[t]
		for k in line.size():
			var d := line[k].distance_to(p)
			if d < best:
				best = d
				level = centres[t][k]
	return level
