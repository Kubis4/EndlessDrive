@tool
extends Control
## Native editable instrument, drawn at any viewport resolution.
@export_range(0.0, 100.0, 0.1) var speed_kmh := 0.0:
	set(value):
		if absf(speed_kmh - value) < 0.05: return
		speed_kmh = value
		queue_redraw()
func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	resized.connect(queue_redraw)
func _draw() -> void:
	var center := Vector2(size.x * 0.5, size.y * 0.52)
	var radius := minf(size.x * 0.46, size.y * 0.48)
	var start := deg_to_rad(135.0)
	var end := deg_to_rad(405.0)
	var orange := Color("ffc66b")
	var active := lerpf(start, end, clampf(speed_kmh / 100.0, 0, 1))
	draw_circle(center, radius, Color("0c141d"))
	draw_arc(center, radius - 2, start, end, 96, Color("344552"), 5, true)
	if speed_kmh > 0.1: draw_arc(center, radius - 2, start, active, 96, orange, 5, true)
	for tick in 21:
		var angle := lerpf(start, end, tick / 20.0)
		var direction := Vector2(cos(angle), sin(angle))
		var major := tick % 4 == 0
		var ink := Color("becbd1") if major else Color("526774")
		draw_line(center + direction * (radius - (15 if major else 10)), center + direction * (radius - 6), ink, 1.5 if major else 1.0, true)
		if major:
			var text := str(tick * 5)
			var font := ThemeDB.fallback_font
			var extent := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, 10)
			draw_string(font, center + direction * (radius - 25) + Vector2(-extent.x * 0.5, extent.y * 0.25), text, HORIZONTAL_ALIGNMENT_LEFT, -1, 10, Color("96a9b4"))
	var pointer := center + Vector2(cos(active), sin(active)) * (radius - 2)
	draw_circle(pointer, 6, Color(1, 0.63, 0.21, 0.15))
	draw_circle(pointer, 3.5, orange)
