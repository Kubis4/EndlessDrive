extends RefCounted
## Interactive ford surface: a damped 2D wave equation on a coarse grid, driven by
## the cars' wheels and hulls and carried along by the same current they feel. What
## makes waves is a car's motion through the water (vehicle.gd works it out in its
## physics step), so a parked car in a fast river parts it and a drifting one barely
## does. Heights go to the water shader as a small texture. The simulation sleeps
## when the last waves have run out and no car is in the water.
const Surface = preload("res://desert_battlefield/terrain.gd")
const STEP := 1.0 / 30.0
## Waves run at sqrt(g * depth), the speed of real shallow water: a car at walking
## pace overtakes them in a ford and draws a spreading V behind it.
const GRAVITY := 9.81
const DEPTH_LIMITS := Vector2(0.15, 1.3)
const DAMPING := 0.985
const VISCOSITY := 0.035
const SETTLING := 0.99
const HEIGHT_LIMIT := 0.22
## How high a part of the car moving through the water piles it up (m per m/s): a crest
## that goes along with the car, whether or not the waves it sends out can outrun it.
const WHEEL_CREST := 0.012
const HULL_CREST := 0.03
## Below this it is not worth disturbing what is already there (m).
const SMALLEST_CREST := 0.004
## Points on the hull's outline: along and across (share of the half length and
## width, outside the body by OUTLINE_REACH) and the way each faces.
const OUTLINE_REACH := 0.3
const OUTLINE: Array[Vector4] = [
	Vector4(1, -1, 0.7, -0.7), Vector4(1, -0.5, 1, 0), Vector4(1, 0, 1, 0), Vector4(1, 0.5, 1, 0), Vector4(1, 1, 0.7, 0.7),
	Vector4(0.45, 1, 0, 1), Vector4(-0.1, 1, 0, 1), Vector4(-0.6, 1, 0, 1),
	Vector4(-1, 1, -0.7, 0.7), Vector4(-1, 0, -1, 0), Vector4(-1, -1, -0.7, -0.7),
	Vector4(-0.6, -1, 0, -1), Vector4(-0.1, -1, 0, -1), Vector4(0.45, -1, 0, -1),
]
## Inside the hull the surface is held down and calm.
const INSIDE: Array[Vector2] = [Vector2(0.6, -0.5), Vector2(0.6, 0.5), Vector2(0, -0.5), Vector2(0, 0.5), Vector2(-0.6, -0.5), Vector2(-0.6, 0.5)]
## New cells are looked up a few at a time (every second cell, about the terrain's own
## resolution), so moving the grid costs no single frame much.
const LOOKUPS_PER_FRAME := 40
var cell := 0.35
var origin := Vector2.ZERO
var nx := 0
var nz := 0
var height := PackedFloat32Array()
var speed := PackedFloat32Array()
var smooth := PackedFloat32Array()
var wet := PackedByteArray()
var shore := PackedFloat32Array()
## Wave speed term per cell (from the depth), and the current in cells per step.
var pace := PackedFloat32Array()
var drift_x := PackedFloat32Array()
var drift_z := PackedFloat32Array()
var image: Image
var texture: ImageTexture
## The field goes to the texture through two buffers in turn: the image holds one while
## the other is written, so neither is ever copied or allocated again.
var pixels: Array[PackedByteArray] = [PackedByteArray(), PackedByteArray()]
var filling := 0
var clock := 0.0
var calm_time := 0.0
var active := false
var moved := false
var rewrites := 0
## Largest wave after the last step (the worker writes it, the main thread reads it
## between tasks).
var peak := 0.0
## Wave steps run on a worker thread; the main thread only applies the cars'
## forcing and uploads the finished field one frame later.
var task := -1
## Where the grid is to go, in cells, once the running task lets it (worker applies it).
var shift_wanted := Vector2i.ZERO
var shift_running := Vector2i.ZERO
## Cells still to look up (whole grid coordinates, a ring).
var todo_x := PackedInt32Array()
var todo_z := PackedInt32Array()
var todo_head := 0
var todo_count := 0
## Timing for the tests: worker per step, main thread per stamp (microseconds).
var step_usec := 0
var step_count := 0
var step_worst := 0
var force_usec := 0
var force_count := 0

## Simulated area (x0, z0, size x, size z); the desert ford lane by default.
func configure(area: Vector4 = Vector4.ZERO, cell_size: float = 0.35) -> void:
	if area == Vector4.ZERO:
		area = Vector4(Surface.WATER_X0, Surface.LANE_Z[Surface.LANE_WATER] - Surface.LANE_HALF,
			Surface.WATER_X1 - Surface.WATER_X0, Surface.LANE_HALF * 2.0)
	cell = cell_size
	origin = (Vector2(area.x, area.y) / cell).floor() * cell
	nx = ceili(area.z / cell) + 1
	nz = ceili(area.w / cell) + 1
	for field: PackedFloat32Array in [height, speed, smooth, shore, pace, drift_x, drift_z]:
		field.resize(nx * nz)
		field.fill(0.0)
	wet.resize(nx * nz)
	wet.fill(0)
	todo_x.resize(nx * nz / 2)
	todo_z.resize(nx * nz / 2)
	pixels[0].resize(nx * nz * 2)
	pixels[1].resize(nx * nz * 2)
	pixels[0].fill(0)
	pixels[1].fill(0)
	image = Image.create_from_data(nx, nz, false, Image.FORMAT_RH, pixels[0])
	filling = 1
	texture = ImageTexture.create_from_image(image)
	look_up_all()

## World-space rectangle covered by the texture (x0, z0, size x, size z).
func bounds() -> Vector4:
	return Vector4(origin.x, origin.y, (nx - 1) * cell, (nz - 1) * cell)

## Keeps `centre` well inside the simulated rectangle by moving it in whole
## cells: running waves keep their world position, only the strip that comes
## into view is set up (and starts calm). The worker moves the fields; bounds()
## changes when it has (see `moved`).
func follow(centre: Vector2) -> void:
	var half := Vector2((nx - 1) * cell, (nz - 1) * cell) * 0.5
	var offset := centre - (origin + half)
	if absf(offset.x) < half.x * 0.3 and absf(offset.y) < half.y * 0.3: return
	if shift_wanted != Vector2i.ZERO or shift_running != Vector2i.ZERO: return
	shift_wanted = Vector2i(roundi(offset.x / cell), roundi(offset.y / cell))

func _shift_done() -> void:
	var shift := shift_running
	shift_running = Vector2i.ZERO
	if shift == Vector2i.ZERO: return
	var old_x := roundi(origin.x / cell)
	var old_z := roundi(origin.y / cell)
	origin += Vector2(shift) * cell
	moved = true
	rewrites = 2
	if absi(shift.x) >= nx or absi(shift.y) >= nz:
		look_up_all()
		return
	var x0 := old_x + shift.x
	var z0 := old_z + shift.y
	# The rows and columns that came into view.
	if shift.y != 0:
		var from := old_z + nz if shift.y > 0 else z0
		var to := z0 + nz if shift.y > 0 else old_z
		_queue(x0, x0 + nx, from, to)
	if shift.x != 0:
		var from := old_x + nx if shift.x > 0 else x0
		var to := x0 + nx if shift.x > 0 else old_x
		_queue(from, to, maxi(z0, old_z), mini(z0, old_z) + nz)

func look_up_all() -> void:
	todo_head = 0
	todo_count = 0
	var x0 := roundi(origin.x / cell)
	var z0 := roundi(origin.y / cell)
	_queue(x0, x0 + nx, z0, z0 + nz)

## Queues every second cell of a block of whole grid coordinates (each covers its pair).
func _queue(x0: int, x1: int, z0: int, z1: int) -> void:
	var size := todo_x.size()
	for z in range(z0 - posmod(z0, 2), z1, 2):
		for x in range(x0 - posmod(x0, 2), x1, 2):
			if todo_count >= size: return
			var at := (todo_head + todo_count) % size
			todo_x[at] = x
			todo_z[at] = z
			todo_count += 1

## Looks a few queued cells up (main thread: the terrain is not safe to read elsewhere).
func look_up_some() -> void:
	if todo_count == 0: return
	if task != -1 or shift_running != Vector2i.ZERO: return
	var size := todo_x.size()
	var ox := roundi(origin.x / cell)
	var oz := roundi(origin.y / cell)
	for n in mini(LOOKUPS_PER_FRAME, todo_count):
		var wx := todo_x[todo_head]
		var wz := todo_z[todo_head]
		todo_head = (todo_head + 1) % size
		todo_count -= 1
		_set_cells(wx, wz, ox, oz)

## What the water is like around a cell pair: wet or not, how fast waves run, the current.
func _set_cells(wx: int, wz: int, ox: int, oz: int) -> void:
	if wx + 1 < ox or wz + 1 < oz or wx >= ox + nx or wz >= oz + nz: return
	var x := wx * cell + cell * 0.5
	var z := wz * cell + cell * 0.5
	var depth := Surface.water_depth_at(x, z)
	var flow := Surface.water_flow_at(x, z)
	var wave := clampf(depth, DEPTH_LIMITS.x, DEPTH_LIMITS.y)
	var term := GRAVITY * wave * (STEP / cell) * (STEP / cell)
	# Shallow margins absorb energy like a sloping beach.
	var absorb := lerpf(0.9, DAMPING, smoothstep(0.03, 0.35, depth))
	for cz in range(wz, wz + 2):
		for cx in range(wx, wx + 2):
			var ix := cx - ox
			var iz := cz - oz
			if ix < 0 or iz < 0 or ix >= nx or iz >= nz: continue
			var i := iz * nx + ix
			wet[i] = 1 if depth > 0.03 else 0
			shore[i] = absorb
			pace[i] = term
			drift_x[i] = flow.x * STEP / cell
			drift_z[i] = flow.y * STEP / cell

func update(delta: float, cars: Array) -> void:
	var driving := false
	for car in cars:
		if car.water_contact > 0.0: driving = true
	if driving:
		active = true
		calm_time = 0.0
	if task != -1:
		if not WorkerThreadPool.is_task_completed(task): return
		WorkerThreadPool.wait_for_task_completion(task)
		task = -1
		_shift_done()
		_upload()
	if not active and shift_wanted != Vector2i.ZERO:
		_start(0)
		return
	look_up_some()
	if not active: return
	clock = minf(clock + delta, STEP * 2.0)
	var steps := 0
	while clock >= STEP:
		clock -= STEP
		steps += 1
	if steps == 0: return
	if driving:
		var began := Time.get_ticks_usec()
		for car in cars: _force(car, float(steps))
		force_usec += Time.get_ticks_usec() - began
		force_count += 1
	else:
		# Nothing left to make waves: asleep when the last of them have run out.
		calm_time = calm_time + delta if peak < 0.004 else 0.0
		if calm_time > 0.5:
			_go_to_sleep()
			return
	_start(steps)

func _start(steps: int) -> void:
	shift_running = shift_wanted
	shift_wanted = Vector2i.ZERO
	task = WorkerThreadPool.add_task(_run_steps.bind(steps), false, "Ford waves")

func _upload() -> void:
	image.set_data(nx, nz, false, Image.FORMAT_RH, pixels[filling])
	filling = 1 - filling
	texture.update(image)

func _go_to_sleep() -> void:
	active = false
	calm_time = 0.0
	height.fill(0.0)
	speed.fill(0.0)
	peak = 0.0
	rewrites = 2
	_write_pixels()
	_upload()

func _run_steps(steps: int) -> void:
	var began := Time.get_ticks_usec()
	if shift_running != Vector2i.ZERO: _move_fields()
	for i in steps: _step()
	_write_pixels()
	var spent := Time.get_ticks_usec() - began
	step_usec += spent
	step_count += steps
	step_worst = maxi(step_worst, spent)

## Moves the fields inside their arrays: running waves stay where they are in the world,
## the cells that came into view start calm and dry until they are looked up.
func _move_fields() -> void:
	var sx := shift_running.x
	var sz := shift_running.y
	if absi(sx) >= nx or absi(sz) >= nz:
		height.fill(0.0)
		speed.fill(0.0)
		wet.fill(0)
		return
	# (In the order that reads each cell before it is overwritten.)
	for row_at in nz:
		var z := row_at if sz >= 0 else nz - 1 - row_at
		var fz := z + sz
		var row := z * nx
		var from_row := fz * nx
		var inside_z := fz >= 0 and fz < nz
		for column_at in nx:
			var x := column_at if sx >= 0 else nx - 1 - column_at
			var i := row + x
			var fx := x + sx
			if inside_z and fx >= 0 and fx < nx:
				var j := from_row + fx
				height[i] = height[j]
				speed[i] = speed[j]
				wet[i] = wet[j]
				shore[i] = shore[j]
				pace[i] = pace[j]
				drift_x[i] = drift_x[j]
				drift_z[i] = drift_z[j]
			else:
				height[i] = 0.0
				speed[i] = 0.0
				wet[i] = 0
	# (The rim never steps: what the move brought there must not stay and grow.)
	for x in nx:
		_clear_cell(x)
		_clear_cell((nz - 1) * nx + x)
	for z in nz:
		_clear_cell(z * nx)
		_clear_cell(z * nx + nx - 1)

func _clear_cell(i: int) -> void:
	height[i] = 0.0
	speed[i] = 0.0
	smooth[i] = 0.0

## The car in the water: its tyres, then its hull once that is under, all by its motion
## through the water and how deep each part is.
func _force(car, steps: float) -> void:
	if car.water_contact <= 0.0: return
	var basis: Basis = car.attitude
	var forward := Vector2(-basis.z.x, -basis.z.z).normalized()
	var right := Vector2(-forward.y, forward.x)
	var centre := Vector2(car.position.x, car.position.z)
	var through: Vector2 = car.water_relative
	var moving := through.length()
	var half_length: float = car.hull_half_length()
	var half_width: float = car.hull_half_width()
	var level: float = car.water_surface
	var push := minf(moving, 8.0)
	var along_water := through / moving if moving > 0.01 else forward
	# A plunge goes out once, by how much of the car went in how fast.
	if car.water_impact > 0.0:
		_ring(centre, car.water_impact * 0.006, half_length + 0.6)
		car.water_impact = 0.0
	# Leaving: what the last wet wheel sends out.
	if car.water_exit > 0.0:
		var from: Vector3 = car.water_exit_point
		_ring(Vector2(from.x, from.z), minf(car.water_exit, 3.0) * 0.006, 1.2)
		car.water_exit = 0.0
	for w in 4:
		var depth: float = car.wheel_wet[w]
		if depth <= 0.02: continue
		var contact: Vector3 = car.wheel_contacts[w]
		var at := Vector2(contact.x, contact.z)
		# The front wheels break the water, the rear ones run in what they left.
		var crest := minf(depth / 0.35, 1.0) * (1.0 if w % 2 == 0 else 0.5) * push * WHEEL_CREST
		if crest > SMALLEST_CREST:
			_press(at + along_water * (0.35 + push * 0.05), crest)
			_press(at - along_water * 0.5, -crest * 0.6)
		# A spinning tyre stirs what is around it.
		if car.drive_slip[w] > 0.1: _bump(at, -car.drive_slip[w] * 0.01 * steps)
	# Each point of the outline under water: pushes water up where it faces the motion, leaves
	# a hollow where it faces away. The bumper going under joins the two wheel waves into one.
	for point: Vector4 in OUTLINE:
		var across := point.y * (half_width + OUTLINE_REACH)
		var along := point.x * (half_length + OUTLINE_REACH)
		var under: float = level - (car.position.y + basis.z.y * -along + basis.x.y * across - car.floor_height())
		if under <= 0.0: continue
		var facing := point.z * forward + point.w * right
		var into := facing.dot(through)
		var at := centre + forward * along + right * across
		var crest := minf(under / 0.2, 1.0) * clampf(into, -6.0, 6.0) * HULL_CREST
		if crest > SMALLEST_CREST: _press(at + facing * (push * 0.05), crest)
		elif crest < -SMALLEST_CREST: _press(at, crest * 0.5)
	# (A hull going along with the water leaves it as it is.)
	if car.water_submersion > 0.02 and moving > 0.2:
		var hold := (-0.03 - minf(car.water_submersion, 0.6) * 0.12) * minf(moving * 0.5, 1.0)
		for point: Vector2 in INSIDE:
			_press(centre + forward * (point.x * half_length) + right * (point.y * half_width), hold)

## Adds to the water's upward speed at a point, softly over the neighbouring cells.
func _bump(at: Vector2, amount: float) -> void:
	var x := roundi((at.x - origin.x) / cell)
	var z := roundi((at.y - origin.y) / cell)
	if x < 2 or z < 2 or x > nx - 3 or z > nz - 3: return
	var i := z * nx + x
	if wet[i] == 1: speed[i] += amount * 0.4
	if wet[i - 1] == 1: speed[i - 1] += amount * 0.15
	if wet[i + 1] == 1: speed[i + 1] += amount * 0.15
	if wet[i - nx] == 1: speed[i - nx] += amount * 0.15
	if wet[i + nx] == 1: speed[i + nx] += amount * 0.15

## Draws the surface towards a height at a point and over its neighbours: crests against the
## car, the hollow behind it, the calm under it.
func _press(at: Vector2, level: float) -> void:
	var x := roundi((at.x - origin.x) / cell)
	var z := roundi((at.y - origin.y) / cell)
	if x < 2 or z < 2 or x > nx - 3 or z > nz - 3: return
	var at_cell := z * nx + x
	_hold_cell(at_cell, level)
	_hold_cell(at_cell - 1, level)
	_hold_cell(at_cell + 1, level)
	_hold_cell(at_cell - nx, level)
	_hold_cell(at_cell + nx, level)

func _hold_cell(i: int, level: float) -> void:
	if wet[i] == 0: return
	height[i] = lerpf(height[i], level, 0.25)
	speed[i] *= 0.6

## One strong push: the water thrown out of a circle, rising in a ring around it.
func _ring(at: Vector2, amount: float, radius: float) -> void:
	var reach := ceili(radius * 1.5 / cell)
	var cx := roundi((at.x - origin.x) / cell)
	var cz := roundi((at.y - origin.y) / cell)
	for z in range(maxi(cz - reach, 1), mini(cz + reach, nz - 2) + 1):
		for x in range(maxi(cx - reach, 1), mini(cx + reach, nx - 2) + 1):
			var i := z * nx + x
			if wet[i] == 0: continue
			var r := Vector2(x - cx, z - cz).length() * cell / radius
			if r < 1.5: speed[i] += amount * cos(r * PI * 0.67) * (1.0 - r / 1.5)

func _step() -> void:
	var top := 0.0
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
			# The current carries height and motion along with it (from upstream).
			var cx := drift_x[i]
			var cz := drift_z[i]
			var carried := absf(cx) * ((l if cx > 0.0 else r) - h) + absf(cz) * ((d if cz > 0.0 else u) - h)
			var s := speed[i]
			var carried_speed := absf(cx) * (speed[i - 1 if cx > 0.0 else i + 1] - s) + absf(cz) * (speed[i - nx if cz > 0.0 else i + nx] - s)
			# Wave term plus a little viscosity that removes grid-scale ripples.
			speed[i] = (s + carried_speed + laplacian * pace[i]) * shore[i]
			smooth[i] = laplacian * VISCOSITY + carried
	for i in height.size():
		if wet[i] == 1:
			# (Water does not stay piled up: an offset sinks back within seconds.)
			var h := clampf(height[i] + speed[i] + smooth[i], -HEIGHT_LIMIT, HEIGHT_LIMIT) * SETTLING
			height[i] = h
			if h > top: top = h
			elif -h > top: top = -h
	peak = top

## The heights as half floats, a format every phone can filter. Dry cells stay zero, and only
## need writing when the grid has moved or gone to sleep (in both buffers).
func _write_pixels() -> void:
	var all := rewrites > 0
	if all: rewrites -= 1
	var bytes := pixels[filling]
	for i in height.size():
		if all or wet[i] == 1: bytes.encode_half(i * 2, height[i])
