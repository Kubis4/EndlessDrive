extends RefCounted
## Tyre sets: what each does on each ground, and its model. The car's own wheel
## model is the road tyre; the others keep its rim (the mesh without what lies
## beyond RIM from the hub) and get a tyre built here as real geometry, with the
## hub along local Z: a carcass turned about the axle, and tread blocks, lugs or
## chain links on it, each set with its own pattern.
const RIM := 0.245
const RADIUS := 0.415
const HALF_WIDTH := 0.168

## Grip per ground (Surface order: sand, packed, road, gravel, clay, mud, water,
## rock, snow, ice, grass, wet road, dirt, forest), rolling loss, top speed, and
## a line for the player.
## speed_on: how much of the ground's speed each set gets out of it, in the same
## order; rolling: its loss on hard ground, soft: how much of a soft ground's extra
## drag it feels (a lugged or wide tyre rides on top); sink: how deep it ruts soft
## ground (1: as deep as the road tyre); bite: how fast it digs itself out of a hole.
const TYPES := {
	"road": {"name": "Cestné", "note": "tiché a rýchle na ceste, v teréne kĺžu a zapadajú",
		"grip": [0.7, 0.9, 1.05, 0.9, 0.7, 0.5, 0.7, 0.95, 0.5, 0.4, 0.75, 0.95, 0.8, 0.8],
		"speed_on": [0.85, 1.0, 1.0, 1.0, 0.9, 0.7, 1.0, 1.0, 0.7, 0.6, 0.9, 1.0, 0.9, 0.9], "rolling": 1.0, "soft": 1.0, "speed": 1.0, "sink": 1.0, "bite": 0.25},
	"offroad": {"name": "Terénne", "note": "ťahajú v blate, hline aj v lese, na ceste o niečo pomalšie",
		"grip": [1.05, 1.0, 0.95, 1.05, 1.4, 2.4, 1.0, 1.0, 1.0, 0.6, 1.2, 0.9, 1.3, 1.3],
		"speed_on": [1.1, 1.0, 0.95, 1.2, 1.4, 2.0, 1.3, 1.4, 1.3, 0.8, 1.35, 0.95, 1.4, 1.4], "rolling": 1.1, "soft": 0.5, "speed": 0.95, "sink": 0.55, "bite": 1.0},
	"mud": {"name": "Do bahna", "note": "veľké dezény: v bahne najlepšie, na asfalte zlé a smädné",
		"grip": [1.0, 0.95, 0.8, 1.0, 1.7, 3.2, 1.1, 0.9, 1.1, 0.45, 1.35, 0.7, 1.45, 1.45],
		"speed_on": [1.0, 0.95, 0.82, 1.1, 1.6, 2.8, 1.5, 1.2, 1.4, 0.6, 1.4, 0.8, 1.5, 1.5], "rolling": 1.3, "soft": 0.38, "speed": 0.82, "sink": 0.4, "bite": 1.5},
	"winter": {"name": "Zimné", "note": "sneh, ľad a mokro, v blate slabšie",
		"grip": [0.85, 0.9, 0.95, 0.95, 0.95, 0.95, 0.85, 0.95, 2.2, 3.0, 0.95, 0.95, 1.0, 1.0],
		"speed_on": [0.9, 0.95, 0.95, 1.0, 1.0, 1.0, 1.0, 1.1, 1.9, 1.2, 1.0, 0.95, 1.05, 1.05], "rolling": 1.05, "soft": 0.8, "speed": 0.95, "sink": 0.8, "bite": 0.5},
	"chains": {"name": "S reťazami", "note": "hlboký sneh a ľad, ale pomalé a smädné",
		"grip": [0.9, 0.95, 0.7, 1.15, 1.2, 1.5, 0.9, 1.1, 2.8, 6.0, 1.1, 0.6, 1.2, 1.2],
		"speed_on": [1.0, 1.0, 1.0, 1.1, 1.2, 1.4, 1.1, 1.2, 2.2, 1.5, 1.1, 1.0, 1.2, 1.2], "rolling": 1.35, "soft": 0.7, "speed": 0.55, "sink": 0.7, "bite": 1.0},
	"sand": {"name": "Púštne", "note": "široké: nezaboria sa v piesku, šetria palivo",
		"grip": [1.4, 1.15, 0.9, 0.85, 0.9, 0.9, 0.7, 0.8, 0.7, 0.35, 0.9, 0.8, 0.95, 0.95],
		"speed_on": [1.3, 1.1, 0.9, 0.95, 0.9, 0.8, 0.9, 0.9, 0.8, 0.6, 1.0, 0.85, 0.95, 0.95], "rolling": 0.85, "soft": 0.45, "speed": 0.9, "sink": 0.35, "bite": 0.6},
}

static var meshes := {}
static var rims := {}

## The rim alone of a wheel model (cached per model).
static func rim_of(wheel: Mesh) -> Mesh:
	if rims.has(wheel): return rims[wheel]
	var arrays := wheel.surface_get_arrays(0)
	var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX] if arrays[Mesh.ARRAY_INDEX] != null else PackedInt32Array(range(vertices.size()))
	var kept := PackedInt32Array()
	for t in range(0, indices.size(), 3):
		var inside := true
		for k in 3:
			var v := vertices[indices[t + k]]
			if Vector2(v.x, v.y).length() > RIM: inside = false
		if inside: kept.append_array([indices[t], indices[t + 1], indices[t + 2]])
	arrays[Mesh.ARRAY_INDEX] = kept
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	mesh.surface_set_material(0, wheel.surface_get_material(0))
	rims[wheel] = mesh
	return mesh

## One tyre set's model: surface 0 rubber, surface 1 (chains) steel.
static func mesh_of(kind: String) -> Mesh:
	if meshes.has(kind): return meshes[kind]
	var rubber := SurfaceTool.new()
	rubber.begin(Mesh.PRIMITIVE_TRIANGLES)
	var steel: SurfaceTool = null
	match kind:
		"offroad":
			_carcass(rubber, RADIUS - 0.022, HALF_WIDTH)
			for row: Array in [[-0.125, 0.04, 0.0], [-0.045, 0.036, 0.5], [0.045, 0.036, 0.0], [0.125, 0.04, 0.5]]:
				_lugs(rubber, 24, row[2], row[0], row[1], 0.72, RADIUS - 0.022, RADIUS, 0.0)
		"mud":
			var big := RADIUS + 0.012
			_carcass(rubber, big - 0.042, HALF_WIDTH + 0.006)
			# Big chevrons, wide apart, running down over the shoulders.
			_lugs(rubber, 14, 0.0, -0.09, 0.075, 0.62, big - 0.042, big, 0.9)
			_lugs(rubber, 14, 0.5, 0.09, 0.075, 0.62, big - 0.042, big, -0.9)
			_lugs(rubber, 14, 0.0, -0.172, 0.012, 0.5, big - 0.075, big - 0.012, 0.0)
			_lugs(rubber, 14, 0.5, 0.172, 0.012, 0.5, big - 0.075, big - 0.012, 0.0)
		"winter":
			_carcass(rubber, RADIUS - 0.012, HALF_WIDTH)
			for row: Array in [[-0.135, 0.0], [-0.08, 0.5], [-0.027, 0.0], [0.027, 0.5], [0.08, 0.0], [0.135, 0.5]]:
				_lugs(rubber, 42, row[1], row[0], 0.022, 0.68, RADIUS - 0.012, RADIUS, 0.25)
		"sand":
			_carcass(rubber, RADIUS - 0.014, HALF_WIDTH + 0.022)
			# Low paddles across a wide, smooth tread.
			_lugs(rubber, 16, 0.0, 0.0, 0.16, 0.14, RADIUS - 0.014, RADIUS, 0.0)
		"chains":
			_carcass(rubber, RADIUS - 0.012, HALF_WIDTH)
			for row: Array in [[-0.11, 0.0], [-0.04, 0.5], [0.04, 0.0], [0.11, 0.5]]:
				_lugs(rubber, 34, row[1], row[0], 0.03, 0.7, RADIUS - 0.012, RADIUS, 0.0)
			steel = SurfaceTool.new()
			steel.begin(Mesh.PRIMITIVE_TRIANGLES)
			_chains(steel)
	var mesh := rubber.commit()
	if steel != null: steel.commit(mesh)
	meshes[kind] = mesh
	return mesh

## Adds a triangle facing `out` (Godot's front faces wind clockwise).
static func _tri(st: SurfaceTool, a: Vector3, b: Vector3, c: Vector3, out: Vector3) -> void:
	var normal := (b - a).cross(c - a).normalized()
	if normal.dot(out) > 0.0:
		var swap := b
		b = c
		c = swap
	else:
		normal = -normal
	st.set_normal(normal)
	st.add_vertex(a)
	st.add_vertex(b)
	st.add_vertex(c)

static func _quad(st: SurfaceTool, a: Vector3, b: Vector3, c: Vector3, d: Vector3, out: Vector3) -> void:
	_tri(st, a, b, c, out)
	_tri(st, a, c, d, out)

static func _on(radius: float, angle: float, z: float) -> Vector3:
	return Vector3(cos(angle) * radius, sin(angle) * radius, z)

## The tyre's body: bead, sidewall, rounded shoulder and tread band, turned
## about the axle.
static func _carcass(st: SurfaceTool, tread: float, half: float) -> void:
	var profile := [Vector2(0.235, half * 0.72), Vector2(0.3, half * 0.97), Vector2(tread - 0.05, half), Vector2(tread - 0.012, half * 0.9), Vector2(tread, half * 0.72), Vector2(tread, -half * 0.72), Vector2(tread - 0.012, -half * 0.9), Vector2(tread - 0.05, -half), Vector2(0.3, -half * 0.97), Vector2(0.235, -half * 0.72)]
	const SIDES := 48
	for s in SIDES:
		var a0 := TAU * s / SIDES
		var a1 := TAU * (s + 1) / SIDES
		for p in profile.size() - 1:
			var u: Vector2 = profile[p]
			var v: Vector2 = profile[p + 1]
			var mid := (a0 + a1) * 0.5
			var along := v - u
			var out := Vector3(-cos(mid) * along.y, -sin(mid) * along.y, along.x).normalized()
			_quad(st, _on(u.x, a0, u.y), _on(v.x, a0, v.y), _on(v.x, a1, v.y), _on(u.x, a1, u.y), out)

## A ring of `count` tread blocks at z, half width dz, covering `fill` of each
## pitch, from radius low to high; `skew` slants them (chevrons).
static func _lugs(st: SurfaceTool, count: int, offset: float, z: float, dz: float, fill: float, low: float, high: float, skew: float) -> void:
	var pitch := TAU / count
	for n in count:
		var centre := (n + offset) * pitch
		var half := pitch * fill * 0.5
		var a := [centre - half - skew * dz, centre + half - skew * dz, centre + half + skew * dz, centre - half + skew * dz]
		var zs := [z - dz, z - dz, z + dz, z + dz]
		var top := []
		var bottom := []
		for k in 4:
			top.append(_on(high, a[k], zs[k]))
			bottom.append(_on(low, a[k], zs[k]))
		var radial := _on(1.0, centre, 0.0)
		_quad(st, top[0], top[1], top[2], top[3], radial)
		_quad(st, bottom[0], bottom[1], top[1], top[0], Vector3(0, 0, -1))
		_quad(st, bottom[1], bottom[2], top[2], top[1], _on(1.0, centre + PI * 0.5, 0.0))
		_quad(st, bottom[2], bottom[3], top[3], top[2], Vector3(0, 0, 1))
		_quad(st, bottom[3], bottom[0], top[0], top[3], _on(1.0, centre - PI * 0.5, 0.0))

## Chains: a side chain round each shoulder and cross chains over the tread,
## each a row of small flat links.
static func _chains(st: SurfaceTool) -> void:
	const CROSSES := 10
	var r := RADIUS + 0.008
	for c in CROSSES:
		var angle := TAU * c / CROSSES
		for l in 7:
			var z := -HALF_WIDTH * 0.8 + HALF_WIDTH * 1.6 * (l + 0.5) / 7.0
			_link(st, angle + (0.03 if l % 2 == 0 else -0.03), z, r, 0.018, 0.024)
	for side in [-1.0, 1.0]:
		for l in 60:
			var angle := TAU * l / 60.0
			_link(st, angle, side * HALF_WIDTH * 0.86, r - 0.012, 0.024, 0.008)

static func _link(st: SurfaceTool, angle: float, z: float, r: float, half_along: float, half_z: float) -> void:
	var dt := half_along / r
	var t := 0.008
	var a := [angle - dt, angle + dt, angle + dt, angle - dt]
	var zs := [z - half_z, z - half_z, z + half_z, z + half_z]
	var top := []
	var low := []
	for k in 4:
		top.append(_on(r + t, a[k], zs[k]))
		low.append(_on(r - t, a[k], zs[k]))
	_quad(st, top[0], top[1], top[2], top[3], _on(1.0, angle, 0.0))
	for k in 4:
		var n := (k + 1) % 4
		var mid: Vector3 = (top[k] + top[n]) * 0.5
		_quad(st, low[k], low[n], top[n], top[k], mid - _on(r, angle, z))
