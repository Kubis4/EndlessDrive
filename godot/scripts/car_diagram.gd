@tool
class_name CarDiagram
extends Control
## Independent top-down schematic; parts are normalized condition values (0..1).
## At 300 x 300 every component has a labelled 96 x 48 mouse/touch target.
## Arrow keys cycle components; Enter/Space select. Missing data stays unknown.

signal selected(slot: String)

const Icons = preload("res://scripts/item_icons.gd")
const VEHICLE_IMAGES := {"izh": preload("res://assets/ui/vehicles/izh_top.png"), "jeep": preload("res://assets/ui/vehicles/jeep_top.png")}
const SLOTS: Array[String] = ["radiator", "engine", "alternator", "tires", "brakes", "suspension"]
const NAMES := {"engine": "Motor", "tires": "Pneumatiky", "brakes": "Brzdy", "suspension": "Odpruženie", "radiator": "Chladič", "alternator": "Alternátor"}
const CREAM := Color("f5ecd7")
const GOLD := Color("e9ba68")
const MUTED := Color("aeb3ad")
const GOOD := Color("98c6ac")
const BAD := Color("ef947f")
const CARDS := {
	"radiator": Rect2(3, 47, 96, 48), "engine": Rect2(3, 111, 96, 48),
	"alternator": Rect2(3, 175, 96, 48), "tires": Rect2(201, 47, 96, 48),
	"brakes": Rect2(201, 111, 96, 48), "suspension": Rect2(201, 175, 96, 48),
}
const ZONES := {
	"radiator": Rect2(125, 42, 50, 14), "engine": Rect2(129, 64, 42, 32),
	"alternator": Rect2(119, 100, 23, 19), "tires": Rect2(179, 69, 13, 34),
	"brakes": Rect2(168, 125, 16, 23), "suspension": Rect2(119, 189, 62, 12),
}

## Set before add_child for a noninteractive loot thumbnail with no side labels.
@export var compact_mode: bool = false
@export var selected_slot: String = "engine":
	set(value):
		selected_slot = value if NAMES.has(value) else "engine"
		queue_redraw()
@export var conditions: Dictionary = {}:
	set(value):
		conditions = value
		queue_redraw()
var _hovered := ""
var vehicle_id := "izh":
	set(value):
		vehicle_id = value if VEHICLE_IMAGES.has(value) else "izh"
		queue_redraw()


func _ready() -> void:
	# The scene owns its dimensions; do not replace hand-authored sizes.
	focus_mode = Control.FOCUS_NONE if compact_mode else Control.FOCUS_ALL
	mouse_filter = Control.MOUSE_FILTER_IGNORE if compact_mode else Control.MOUSE_FILTER_STOP
	mouse_default_cursor_shape = Control.CURSOR_ARROW if compact_mode else Control.CURSOR_POINTING_HAND
	resized.connect(queue_redraw)
	focus_entered.connect(queue_redraw)
	focus_exited.connect(queue_redraw)
	mouse_exited.connect(func() -> void:
		_hovered = ""
		queue_redraw())


func update_parts(parts: Dictionary, slot: String) -> void:
	conditions = parts.duplicate(true)
	selected_slot = slot


func _condition(slot: String) -> float:
	var value: Variant = conditions.get(slot)
	if not (value is float or value is int):
		return -1.0
	return clampf(float(value), 0.0, 1.0) if is_finite(float(value)) else -1.0


func _color(slot: String) -> Color:
	var value := _condition(slot)
	if value < 0.0: return MUTED
	if value < 0.35: return BAD
	if value < 0.7: return GOLD
	return GOOD


func _status(slot: String) -> String:
	var value := _condition(slot)
	if value < 0.0: return "— / neznámy"
	var state := "kritický" if value < 0.35 else ("opotrebovaný" if value < 0.7 else "dobrý")
	return "%d %% / %s" % [roundi(value * 100.0), state]


func _fit() -> float:
	if compact_mode:
		return maxf(0.001, minf(size.x / 150.0, size.y / 250.0))
	return maxf(0.001, minf(size.x, size.y) / 300.0)


func _origin() -> Vector2:
	if compact_mode:
		return (size - Vector2(150, 250) * _fit()) * 0.5 - Vector2(75, 0) * _fit()
	return (size - Vector2(300, 300) * _fit()) * 0.5


func _local_point(point: Vector2) -> Vector2:
	return (point - _origin()) / _fit()


func _hit(point: Vector2) -> String:
	for slot: String in SLOTS:
		if CARDS[slot].has_point(point) or ZONES[slot].grow(3).has_point(point):
			return slot
	# All four tyres share one component slot.
	for wheel: Rect2 in [Rect2(108, 69, 13, 34), Rect2(108, 179, 13, 34), Rect2(179, 179, 13, 34)]:
		if wheel.has_point(point): return "tires"
	return ""


func _choose(slot: String) -> void:
	if slot.is_empty(): return
	selected_slot = slot
	grab_focus()
	selected.emit(slot)


func _gui_input(event: InputEvent) -> void:
	if compact_mode: return
	if event is InputEventMouseMotion:
		_hovered = _hit(_local_point(event.position))
		queue_redraw()
	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
		_choose(_hit(_local_point(event.position)))
		accept_event()
	elif event is InputEventScreenTouch and event.pressed:
		_choose(_hit(_local_point(event.position)))
		accept_event()
	elif event is InputEventKey and event.pressed:
		var index := SLOTS.find(selected_slot)
		match event.keycode:
			KEY_LEFT, KEY_UP:
				_choose(SLOTS[posmod(index - 1, SLOTS.size())])
				accept_event()
			KEY_RIGHT, KEY_DOWN:
				_choose(SLOTS[(index + 1) % SLOTS.size()])
				accept_event()
			KEY_ENTER, KEY_KP_ENTER, KEY_SPACE:
				_choose(selected_slot)
				accept_event()


func _get_tooltip(at_position: Vector2) -> String:
	if compact_mode:
		return "%s: %s" % [NAMES[selected_slot], _status(selected_slot)]
	var slot := _hit(_local_point(at_position))
	return "%s: %s. Kliknutím vyber diel." % [NAMES[slot], _status(slot)] if not slot.is_empty() else "Vyber diel na schéme alebo šípkami."


func _text(point: Vector2, text: String, font_size: int, color: Color) -> void:
	draw_string(ThemeDB.fallback_font, point, text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, color)


func _draw() -> void:
	var factor := _fit()
	draw_set_transform(_origin(), 0.0, Vector2.ONE * factor)
	_text(Vector2(114, 17), "↑ PREDOK", 12, MUTED)
	# The underlying image is rendered from the selected playable model.
	draw_texture_rect(VEHICLE_IMAGES[vehicle_id], Rect2(94, 28, 112, 205), false)
	for slot: String in SLOTS:
		var rect: Rect2 = ZONES[slot]
		draw_style_box(_box(_color(slot).darkened(0.72), _color(slot)), rect)
		if slot == selected_slot:
			draw_rect(rect.grow(3), GOLD, false, 2.0)
	if compact_mode:
		for slot: String in SLOTS:
			var rect: Rect2 = ZONES[slot]
			var pixels := minf(20.0, minf(rect.size.x, rect.size.y))
			draw_texture_rect(Icons.texture(slot), Rect2(rect.get_center() - Vector2.ONE * pixels * 0.5, Vector2.ONE * pixels), false)
		_text(Vector2(105, 247), NAMES[selected_slot], 12, GOLD)
		return
	for slot: String in SLOTS:
		var card: Rect2 = CARDS[slot]
		var zone: Rect2 = ZONES[slot]
		var left := card.position.x < 100
		var start := Vector2(card.end.x if left else card.position.x, card.get_center().y)
		var end := zone.get_center()
		var elbow := Vector2(104 if left else 196, start.y)
		draw_polyline(PackedVector2Array([start, elbow, Vector2(elbow.x, end.y), end]), _color(slot).darkened(0.3), 1.0, true)
		var active := slot == selected_slot
		var border := GOLD if active else (_color(slot) if slot == _hovered else Color("4d5753"))
		draw_style_box(_box(Color("303932") if active else Color("202725"), border), card)
		draw_texture_rect(Icons.texture(slot), Rect2(card.position + Vector2(6, 5), Vector2(17, 17)), false)
		_text(card.position + Vector2(26, 18), NAMES[slot], 11, CREAM)
		var value := _condition(slot)
		_text(card.position + Vector2(7, 37), "%d %%" % roundi(value * 100) if value >= 0 else "— neznámy", 13, _color(slot))
		if active:
			draw_circle(card.position + Vector2(85, 34), 3, GOLD)
	_text(Vector2(4, 251), "STAV DIELOV  /  VYBER KLIKNUTÍM", 11, CREAM)
	for entry: Array in [[4, BAD, "<35 %"], [103, GOLD, "35–69 %"], [211, GOOD, "≥70 %"]]:
		draw_circle(Vector2(entry[0] + 4, 268), 3, entry[1])
		_text(Vector2(entry[0] + 13, 272), entry[2], 12, entry[1])
	_text(Vector2(4, 292), "— bez údajov    ·    šípky / Enter: výber", 11, MUTED)
	if has_focus():
		draw_rect(Rect2(1, 1, 298, 298), GOLD, false, 1.5)


func _box(fill: Color, border: Color) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = fill
	style.border_color = border
	style.set_border_width_all(1)
	style.set_corner_radius_all(4)
	return style
