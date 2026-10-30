extends RefCounted
## Interactive ford surface: a damped 2D wave equation on a coarse grid, driven by
## the cars' submerged hulls and wheels. Cars displace water, push a bow wave,
## leave a wake; waves reflect off the basin walls and are absorbed in shallows.
## Heights (R) and vertical speeds (G) go to the water shader as a small float
## texture. The simulation sleeps when the water is calm and no car is in it.
const Surface = preload("res://desert_battlefield/terrain.gd")
const CELL := 0.35
const STEP := 1.0 / 30.0
## Wave coupling per step (stable below 0.5) and per-step damping.
const COUPLING := 0.3
const DAMPING := 0.985
const VISCOSITY := 0.06
var origin := Vector2.ZERO
var nx := 0
var nz := 0
var height := PackedFloat32Array()
var speed := PackedFloat32Array()
var smooth := PackedFloat32Array()
var wet := PackedByteArray()
var shore := PackedFloat32Array()
var image: Image
var texture: ImageTexture
var clock := 0.0
var calm_time := 0.0
var active := false
## Wave steps run on a worker thread; the main thread only applies the cars'
## forcing and uploads the finished field one frame later.
var task := -1

func configure() -> void:
	origin = Vector2(Surface.WATER_X0, Surface.LANE_Z[Surface.LANE_WATER] - Surface.LANE_HALF)
	nx = ceili((Surface.WATER_X1 - Surface.WATER_X0) / CELL) + 1
	nz = ceili(Surface.LANE_HALF * 2.0 / CELL) + 1
	height.resize(nx * nz)
	speed.resize(nx * nz)
	smooth.resize(nx * nz)
	wet.resize(nx * nz)
	shore.resize(nx * nz)
	for z in nz:
		for x in nx:
			var depth := Surface.water_depth_at(origin.x + x * CELL, origin.y + z * CELL)
			wet[z * nx + x] = 1 if depth > 0.03 else 0
			# Shallow margins absorb energy like a sloping beach.
			shore[z * nx + x] = lerpf(0.9, DAMPING, smoothstep(0.03, 0.35, depth))
	image = Image.create(nx, nz, false, Image.FORMAT_RGF)
	image.fill(Color(0, 0, 0, 1))
	texture = ImageTexture.create_from_image(image)

## World-space rectangle covered by the texture (x0, z0, size x, size z).
func bounds() -> Vector4:
	return Vector4(origin.x, origin.y, (nx - 1) * CELL, (nz - 1) * CELL)

func update(delta: float, cars: Array) -> void:
	var driving := false
	for car in cars:
		if car.water_submersion > 0.0 or Surface.water_depth_at(car.position.x, car.position.z) > 0.0:
			driving = true
	if driving:
		active = true
		calm_time = 0.0
	if task != -1:
		if not WorkerThreadPool.is_task_completed(task): return
		WorkerThreadPool.wait_for_task_completion(task)
		task = -1
		_upload()
	if not active: return
	clock = minf(clock + delta, STEP * 2.0)
	var steps := 0
	while clock >= STEP:
		clock -= STEP
		steps += 1
	if steps == 0: return
	for car in cars: _force(car)
	task = WorkerThreadPool.add_task(func():
		for i in steps: _step(), false, "Ford waves")
	if not driving:
		calm_time += delta
		if calm_time > 8.0: active = false

## Displace the water under a car's floor and drive a bow wave/wake from motion.
func _force(car) -> void:
	if Surface.water_depth_at(car.position.x, car.position.z) <= 0.0 and car.water_submersion <= 0.0: return
	var basis: Basis = car.attitude
	var forward := Vector2(-basis.z.x, -basis.z.z).normalized()
	var right := Vector2(-forward.y, forward.x)
	var centre := Vector2(car.position.x, car.position.z)
	var velocity := Vector2(car.velocity.x, car.velocity.z)
	var moving := velocity.length()
	var half_length: float = car.half_wheelbase + 0.8
	var half_width: float = car.half_track + 0.15
	var floor_depth := clampf(car.water_submersion, 0.0, 0.6)
	var reach := half_length + 1.2
	var x0 := clampi(floori((centre.x - reach - origin.x) / CELL), 1, nx - 2)
	var x1 := clampi(ceili((centre.x + reach - origin.x) / CELL), 1, nx - 2)
	var z0 := clampi(floori((centre.y - reach - origin.y) / CELL), 1, nz - 2)
	var z1 := clampi(ceili((centre.y + reach - origin.y) / CELL), 1, nz - 2)
	for z in range(z0, z1 + 1):
		for x in range(x0, x1 + 1):
			var i := z * nx + x
			if wet[i] == 0: continue
			var local := Vector2(origin.x + x * CELL, origin.y + z * CELL) - centre
			var along := local.dot(forward)
			var across := absf(local.dot(right))
			if across > half_width + 0.6 or absf(along) > half_length + 1.0: continue
			if across < half_width and absf(along) < half_length:
				# The hull pushes the surface down and calms it inside the footprint.
				height[i] = lerpf(height[i], -0.04 - floor_depth * 0.12, 0.25)
				speed[i] *= 0.6
			else:
				# Water piles up ahead and along the sides in proportion to speed;
				# the moving footprint itself leaves the trough that becomes the wake.
				var ahead := smoothstep(-0.5, 0.8, along - half_length + 0.6) * (1.0 - smoothstep(0.0, 1.0, along - half_length))
				var side := (1.0 - smoothstep(0.0, 0.6, across - half_width)) * 0.35
				speed[i] += minf(moving, 9.0) * 0.0009 * (ahead + side) * (0.5 + floor_depth * 2.0)
	# Tyres churn the surface where they turn in the water.
	for w in 4:
		var contact: Vector3 = car.wheel_contacts[w] if car.wheel_contacts.size() == 4 else car.position
		var cx := roundi((contact.x - origin.x) / CELL)
		var cz := roundi((contact.z - origin.y) / CELL)
		if cx < 1 or cz < 1 or cx > nx - 2 or cz > nz - 2: continue
		var i := cz * nx + cx
		if wet[i] == 1: speed[i] -= minf(moving, 8.0) * 0.0012

func _step() -> void:
	for z in range(1, nz - 1):
		var row := z * nx
		for x in range(1, nx - 1):
			var i := row + x
			if wet[i] == 0: continue
			var h := height[i]
			# Dry neighbours act as walls (reflect by mirroring the cell itself).
			var l := height[i - 1] if wet[i - 1] == 1 else h
			var r := height[i + 1] if wet[i + 1] == 1 else h
			var d := height[i - nx] if wet[i - nx] == 1 else h
			var u := height[i + nx] if wet[i + nx] == 1 else h
			var laplacian := l + r + d + u - 4.0 * h
			# Wave term plus a little viscosity that removes grid-scale ripples.
			speed[i] = (speed[i] + laplacian * COUPLING) * shore[i]
			smooth[i] = laplacian * VISCOSITY
	for i in height.size():
		if wet[i] == 1: height[i] = clampf(height[i] + speed[i] + smooth[i], -0.15, 0.15) * 0.999

func _upload() -> void:
	var interleaved := PackedFloat32Array()
	interleaved.resize(height.size() * 2)
	for i in height.size():
		interleaved[i * 2] = height[i]
		interleaved[i * 2 + 1] = speed[i]
	image.set_data(nx, nz, false, Image.FORMAT_RGF, interleaved.to_byte_array())
	texture.update(image)
