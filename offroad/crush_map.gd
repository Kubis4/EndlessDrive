extends RefCounted
## Where the car has flattened plants: a map around the car that repeats every
## 256 m, half a metre to a texel (how flat, and the way it went). Crops and ground
## cover read it in their shaders; stamps far behind are wiped before the map comes
## round to them again.
const T = preload("res://offroad/endless_terrain.gd")
const Surface = preload("res://desert_battlefield/terrain.gd")
const SIZE := 512
const TEXEL := 0.5
## The flattened patch under the car: half its length and half its width (m).
const HALF_LENGTH := 2.4
const HALF_WIDTH := 1.1
## Moved this far since the last stamp (m), and how long a stamp lasts by distance.
const STEP := 0.5
const FORGET := 180.0
const UPLOAD_EVERY := 3
var data := PackedByteArray()
var texture: ImageTexture
var last := Vector2(INF, INF)
var stamps: Array = []
var dirty := false
var frames := 0

func _init() -> void:
	data.resize(SIZE * SIZE * 2)
	texture = ImageTexture.create_from_image(Image.create_from_data(SIZE, SIZE, false, Image.FORMAT_RG8, data))

func update(car: Node3D) -> void:
	var at := Vector2(car.position.x, car.position.z)
	frames += 1
	if at.distance_squared_to(last) >= STEP * STEP:
		var run := at - last
		if last.x != INF and run.length() < 20.0 and _over_plants(at, run): _press(at, run.normalized())
		last = at
	_forget(at)
	if dirty and frames % UPLOAD_EVERY == 0:
		dirty = false
		texture.update(Image.create_from_data(SIZE, SIZE, false, Image.FORMAT_RG8, data))

## Is there anything to flatten here or just ahead (roads have nothing)?
func _over_plants(at: Vector2, run: Vector2) -> bool:
	var ahead := at + run.normalized() * HALF_LENGTH
	for p: Vector2 in [at, ahead]:
		if T.crop_at(p.x, p.y) >= 0 or T.surface_at(p.x, p.y) in [Surface.GRASS, Surface.FOREST, Surface.MUD]: return true
	return false

func _press(at: Vector2, way: Vector2) -> void:
	var across := way.orthogonal()
	var angle := byte_of_angle(way)
	var reach := Vector2(HALF_LENGTH, HALF_WIDTH).length()
	var low := Vector2i(floori((at.x - reach) / TEXEL), floori((at.y - reach) / TEXEL))
	var high := Vector2i(ceili((at.x + reach) / TEXEL), ceili((at.y + reach) / TEXEL))
	var pressed := PackedInt32Array()
	for y in range(low.y, high.y + 1):
		for x in range(low.x, high.x + 1):
			var from := Vector2((x + 0.5) * TEXEL, (y + 0.5) * TEXEL) - at
			if absf(from.dot(way)) > HALF_LENGTH or absf(from.dot(across)) > HALF_WIDTH: continue
			var index := (posmod(y, SIZE) * SIZE + posmod(x, SIZE)) * 2
			if data[index] == 255: continue
			data[index] = 255
			data[index + 1] = angle
			pressed.append(index)
	if pressed.is_empty(): return
	stamps.append([at, pressed])
	dirty = true

static func byte_of_angle(way: Vector2) -> int:
	return clampi(roundi(fposmod(atan2(way.y, way.x), TAU) / TAU * 255.0), 0, 255)

func _forget(at: Vector2) -> void:
	while not stamps.is_empty() and (stamps[0][0] as Vector2).distance_to(at) > FORGET:
		for index: int in stamps[0][1]: data[index] = 0
		stamps.pop_front()
		dirty = true
