@tool
extends Button
## Native button semantics with a curved, non-overlapping touch sector.
@export var start_degrees := 205.0
@export var end_degrees := 245.0
var polygon := PackedVector2Array()
static var _surfaces: Dictionary = {}
func _surface(active: bool) -> Texture2D:
	if not _surfaces.has(active):
		var gradient := Gradient.new()
		gradient.colors = PackedColorArray([Color("6b542e"), Color("272419")]) if active else PackedColorArray([Color("343d2e"), Color("1b2118")])
		var texture := GradientTexture2D.new()
		texture.gradient = gradient
		texture.width = 64
		texture.height = 64
		texture.fill_to = Vector2(0.25, 1)
		_surfaces[active] = texture
	return _surfaces[active]
func _ready() -> void:
	resized.connect(queue_redraw)
	mouse_entered.connect(queue_redraw)
	mouse_exited.connect(queue_redraw)
	toggled.connect(func(_pressed: bool) -> void: queue_redraw())
func _draw() -> void:
	polygon.clear()
	var center := size * 0.5
	for step in 17:
		var angle := deg_to_rad(lerpf(start_degrees, end_degrees, step / 16.0))
		polygon.append(center + Vector2(cos(angle), sin(angle)) * (size.x * 0.478))
	for step in 17:
		var angle := deg_to_rad(lerpf(end_degrees, start_degrees, step / 16.0))
		polygon.append(center + Vector2(cos(angle), sin(angle)) * (size.x * 0.2083))
	var active := button_pressed or is_hovered()
	var uv := PackedVector2Array()
	for point in polygon: uv.append(point / size)
	draw_polygon(polygon, PackedColorArray([Color.WHITE]), uv, _surface(active))
	var outline := polygon.duplicate()
	outline.append(polygon[0])
	draw_polyline(outline, Color("e9ba68") if active else Color("70735e"), 2, true)
func _has_point(point: Vector2) -> bool:
	var delta := point - size * 0.5
	var degrees := fposmod(rad_to_deg(delta.angle()), 360.0)
	return delta.length() >= size.x * 0.2083 and delta.length() <= size.x * 0.478 and degrees >= start_degrees and degrees <= end_degrees
