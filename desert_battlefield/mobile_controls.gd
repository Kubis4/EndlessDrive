extends CanvasLayer
## Phone controls designed for thumbs that rest away from the screen edges:
## - lower left corner: analog steering zone. Touch anywhere in it; the wheel centres
##   where the thumb lands and turns proportionally as it slides left/right (no
##   hunting for small buttons at the bezel).
## - right: tall gas pedal with brake/reverse beside it and the handbrake above,
##   inset from the edge. A thumb can slide between pedals without lifting.
## - anywhere else: drag to turn the free/orbit camera, pinch with two fingers
##   to zoom (every camera mode).
## The same input actions feed the vehicle integrator (with analog strength).
var game: Node3D
var canvas: Control
## Finger index -> "steer" / pedal action name.
var fingers := {}
var steer_origin := Vector2.ZERO
var steer_finger := -1
var steer_value := 0.0
var steer_rect := Rect2()
var pedal_rects := {}
## Camera fingers (index -> position) for drag and pinch.
var camera_fingers := {}
var drive_button: Button
var diff_button: Button
var label_clock := 0.0
## Thumb travel (in px, scaled with screen) for full steering lock.
const STEER_TRAVEL := 115.0
const DEADZONE := 0.05
const PEDALS := ["throttle", "reverse", "handbrake"]
const LABELS := {"throttle": "PLYN", "reverse": "BRZDA\nCÚVANIE", "handbrake": "RUČNÁ"}
const BUTTON_SIZE := Vector2(150, 56)
const MenuUI = preload("res://offroad/menu_ui.gd")
## The road's buttons: what driving needs is always there, the rest behind VIAC.
const ENDLESS_BUTTONS := [["4X4", KEY_G], ["ZÁMOK", KEY_L], ["SVETLÁ", KEY_F], ["VIAC", KEY_NONE], ["MENU", KEY_ESCAPE]]
const MORE_BUTTONS := [["NA CESTU", KEY_R], ["NÁKLAD", KEY_I], ["DOPLNIŤ", KEY_B]]
const ZOOM_STEP := 1.18
var endless := false
var action_button: Button
var ui_scale := 1.0
var light_button: Button
var more: Array[Button] = []
var zoom: Array[Button] = []

func _ready() -> void:
	game = get_parent()
	layer = 3
	for action in ["battlefield_left", "battlefield_right", "battlefield_throttle", "battlefield_reverse", "battlefield_handbrake"]:
		if not InputMap.has_action(action): InputMap.add_action(action)
	canvas = Control.new()
	canvas.mouse_filter = Control.MOUSE_FILTER_IGNORE
	canvas.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	canvas.draw.connect(_draw_controls)
	add_child(canvas)
	# The endless road needs fewer, larger buttons than the test grounds.
	endless = game.endless_world
	var items := ENDLESS_BUTTONS if endless else [["JAZDA / KAMERA", KEY_TAB], ["POHĽAD", KEY_C], ["TEST", KEY_T], ["POHON", KEY_G], ["RESET AUTA", KEY_R], ["AUTO", KEY_V], ["MENU", KEY_ESCAPE], ["DIFERENCIÁL", KEY_L], ["SVETLÁ", KEY_F], ["POČASIE", KEY_K]]
	for item in items + (MORE_BUTTONS if endless else []):
		var button := Button.new()
		button.text = item[0]
		button.focus_mode = Control.FOCUS_NONE
		if endless:
			MenuUI.style_button(button, "dark", 60.0, 24.0)
			button.clip_text = true
		if item[1] == KEY_G: drive_button = button
		if item[1] == KEY_L: diff_button = button
		if item[1] == KEY_F: light_button = button
		if item in MORE_BUTTONS:
			button.visible = false
			more.append(button)
		button.pressed.connect(func():
			if item[1] == KEY_NONE:
				_show_more(not more[0].visible)
				return
			_show_more(false)
			var key := InputEventKey.new()
			key.keycode = item[1]
			key.pressed = true
			# (Cargo and refilling belong to the expedition.)
			if item[1] in [KEY_I, KEY_B]: game.get_node("Expedition")._unhandled_key_input(key)
			else: game._unhandled_key_input(key))
		add_child(button)
	if endless:
		for step: Array in [["+", ZOOM_STEP], ["−", 1.0 / ZOOM_STEP]]:
			var button := Button.new()
			button.text = step[0]
			button.focus_mode = Control.FOCUS_NONE
			MenuUI.style_button(button, "dark", 60.0, 30.0)
			button.pressed.connect(func(): game.camera_pinch(step[1]))
			add_child(button)
			zoom.append(button)
		# Appears when there is something to do where the car stands.
		action_button = Button.new()
		action_button.focus_mode = Control.FOCUS_NONE
		MenuUI.style_button(action_button, "primary", 76.0, 34.0)
		action_button.pressed.connect(func(): game.get_node("Expedition").interact())
		action_button.visible = false
		var holder := CanvasLayer.new()
		holder.layer = layer
		add_child(holder)
		holder.add_child(action_button)
	get_viewport().size_changed.connect(_layout)
	_layout()

func _process(delta: float) -> void:
	if action_button != null:
		var expedition = game.get_node_or_null("Expedition")
		var text: String = expedition.action_text() if expedition != null and expedition.window != null else ""
		action_button.visible = text != "" and not game.paused and not expedition.window.visible
		action_button.text = text
	label_clock += delta
	if label_clock < 0.2: return
	label_clock = 0.0
	var car = game.lead
	drive_button.disabled = car.front_drive
	if endless:
		drive_button.text = "4X2 P" if car.front_drive else ("4X4" if car.four_wheel_drive else "4X2")
		_mark(drive_button, car.four_wheel_drive and not car.front_drive)
		_mark(diff_button, car.diff_locked)
		_mark(light_button, car.lights_on)
		return
	drive_button.text = "POHON " + ("4x2 P" if car.front_drive else ("4x4" if car.four_wheel_drive else "4x2"))
	diff_button.text = "DIF " + ("ZAMKNUTÝ" if car.diff_locked else "OTVORENÝ")

## A button that is on is amber (restyled only when that changes).
func _mark(button: Button, on: bool) -> void:
	if button.has_meta("on") and button.get_meta("on") == on: return
	button.set_meta("on", on)
	var font := button.get_theme_font_size("font_size")
	var keep := button.size
	MenuUI.style_button(button, "chosen" if on else "dark", 60.0, 24.0)
	button.add_theme_font_size_override("font_size", font)
	button.custom_minimum_size = keep
	button.size = keep

func _show_more(on: bool) -> void:
	for button in more: button.visible = on

func _layout() -> void:
	var size := get_viewport().get_visible_rect().size
	# Controls are laid out for a 1280×720-ish reference and scale with the
	# shorter side, so they keep their physical size across phones.
	ui_scale = clampf(size.y / 720.0, 0.75, 2.0)
	var s := ui_scale
	# Left thumb zone: starts ~5 % in from the edge, covers the lower-left area.
	var margin_x := maxf(size.x * 0.05, 48.0 * s)
	# Notches, punch-hole cameras and gesture bars keep their edges free.
	var inset: Vector4 = game.safe_insets()
	var margin_left := margin_x + inset.x
	var margin_right := margin_x + inset.z
	var bottom := size.y - 36.0 * s - inset.w
	steer_rect = Rect2(margin_left, size.y * 0.42, size.x * 0.36, bottom - size.y * 0.42)
	if endless:
		# In the corner, from the screen's edge up to where the thumb still reaches.
		var left := 28.0 * s + inset.x
		steer_rect = Rect2(left, size.y * 0.5, size.x * 0.34, size.y * 0.5 - 20.0 * s - inset.w)
	# Right cluster, inset from the edge: gas (tall), brake left of it,
	# handbrake above the brake where the thumb pivots naturally.
	var pedal := Vector2(128, 196) * s
	var gas_x := size.x - margin_right - pedal.x
	pedal_rects["throttle"] = Rect2(gas_x, bottom - pedal.y, pedal.x, pedal.y)
	pedal_rects["reverse"] = Rect2(gas_x - 20.0 * s - pedal.x, bottom - pedal.y * 0.72, pedal.x, pedal.y * 0.72)
	pedal_rects["handbrake"] = Rect2(gas_x - 20.0 * s - pedal.x, bottom - pedal.y * 0.72 - 16.0 * s - 92.0 * s, pedal.x, 92.0 * s)
	var index := 0
	var button_size := BUTTON_SIZE * clampf(s, 0.75, 1.3)
	var per_row := 6
	if endless:
		button_size = Vector2(150.0, 84.0) * clampf(s / 1.5, 0.75, 1.4)
		if action_button != null:
			var wide := Vector2(520.0, 104.0) * clampf(s / 1.5, 0.75, 1.4)
			action_button.size = wide
			# Below the car, between the steering and the pedals.
			action_button.position = Vector2(size.x * 0.5 - wide.x * 0.5, bottom - wide.y)
		# The top row against the right edge, what is behind VIAC under it, and the zoom
		# above the gas pedal.
		var right := size.x - 24.0 * s - inset.z
		var top := 16.0 + inset.y
		var row_items := ENDLESS_BUTTONS.size()
		for i in row_items + more.size():
			var button: Button = get_child(i + 1) as Button
			var row := 1 if i >= row_items else 0
			var column := i - row_items if row == 1 else i
			var count: int = more.size() if row == 1 else row_items
			button.custom_minimum_size = button_size
			button.size = button_size
			button.add_theme_font_size_override("font_size", roundi(button_size.y * 0.42))
			button.position = Vector2(right - (button_size.x + 10.0) * (count - column) + 10.0, top + row * (button_size.y + 10.0))
		var key := Vector2.ONE * button_size.y * 1.05
		for i in zoom.size():
			zoom[i].custom_minimum_size = key
			zoom[i].size = key
			zoom[i].add_theme_font_size_override("font_size", roundi(key.y * 0.6))
			zoom[i].position = Vector2(pedal_rects["throttle"].get_center().x - key.x * 0.5, pedal_rects["throttle"].position.y - (key.y + 12.0) * (2 - i))
		canvas.queue_redraw()
		return
	for child in get_children():
		if child is Button:
			# Two rows of six in the top-right corner, clear of the speed
			# readout on the left and of the pedals below.
			var column := index % per_row
			var row := index / per_row
			child.custom_minimum_size = button_size
			child.size = button_size
			# Text grows with the button, legible on 1440p phones and tablets.
			child.add_theme_font_size_override("font_size", roundi(button_size.y * 0.36))
			child.custom_minimum_size.y = button_size.y
			child.position = Vector2(size.x - margin_x * 0.5 - inset.z - (button_size.x + 10.0) * (per_row - column), 14.0 * s + inset.y + row * (button_size.y + 10.0))
			index += 1
	canvas.queue_redraw()

func _input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.pressed:
			# Over a menu or window, a touch is for its buttons.
			var expedition = game.get_node_or_null("Expedition")
			if game.paused or (expedition != null and expedition.get("window") != null and expedition.window.visible): return
			if steer_rect.grow(24.0 * ui_scale).has_point(event.position) and steer_finger == -1:
				steer_finger = event.index
				steer_origin = event.position
				fingers[event.index] = "steer"
				_set_steer(0.0)
			else:
				var pedal := _pedal_at(event.position)
				if pedal != "": fingers[event.index] = pedal
				elif not _on_button(event.position):
					camera_fingers[event.index] = event.position
					return
				else: return
			get_viewport().set_input_as_handled()
		elif camera_fingers.has(event.index):
			camera_fingers.erase(event.index)
		elif fingers.has(event.index):
			if event.index == steer_finger:
				steer_finger = -1
				_set_steer(0.0)
			fingers.erase(event.index)
			get_viewport().set_input_as_handled()
		_apply_pedals()
	elif event is InputEventScreenDrag and camera_fingers.has(event.index):
		var previous: Vector2 = camera_fingers[event.index]
		if camera_fingers.size() >= 2:
			# Pinch: compare the finger spread before and after this move.
			var other := -1
			for key in camera_fingers.keys():
				if key != event.index: other = key
			var anchor: Vector2 = camera_fingers[other]
			var before := previous.distance_to(anchor)
			var after: float = event.position.distance_to(anchor)
			if before > 20.0: game.camera_pinch(after / before)
		elif game.camera_mode in game.FREE_CAMERAS:
			game.camera_drag(event.position - previous)
		camera_fingers[event.index] = event.position
		get_viewport().set_input_as_handled()
	elif event is InputEventScreenDrag and fingers.has(event.index):
		if event.index == steer_finger:
			var value: float = (event.position.x - steer_origin.x) / (STEER_TRAVEL * ui_scale)
			# Pulling past full lock drags the centre along, so reversing the
			# thumb direction responds immediately instead of after dead travel.
			if absf(value) > 1.0:
				steer_origin.x = event.position.x - signf(value) * STEER_TRAVEL * ui_scale
				value = signf(value)
			_set_steer(value)
		else:
			# Slide between pedals without lifting the thumb.
			var pedal := _pedal_at(event.position)
			if pedal != "" and pedal != fingers[event.index]:
				fingers[event.index] = pedal
				_apply_pedals()
		get_viewport().set_input_as_handled()

func _on_button(point: Vector2) -> bool:
	for child in get_children():
		if child is Button and child.visible and child.get_global_rect().has_point(point): return true
	if action_button != null and action_button.visible and action_button.get_global_rect().has_point(point): return true
	return false

func _pedal_at(point: Vector2) -> String:
	for pedal in PEDALS:
		if (pedal_rects[pedal] as Rect2).grow(10.0 * ui_scale).has_point(point): return pedal
	return ""

func _set_steer(value: float) -> void:
	steer_value = clampf(value, -1.0, 1.0)
	var amount := clampf((absf(steer_value) - DEADZONE) / (1.0 - DEADZONE), 0.0, 1.0)
	# Slightly progressive response: fine corrections near the centre.
	amount = lerpf(amount, amount * amount, 0.35)
	_press("battlefield_left", amount if steer_value < 0.0 else 0.0)
	_press("battlefield_right", amount if steer_value > 0.0 else 0.0)
	canvas.queue_redraw()

func _apply_pedals() -> void:
	var held := fingers.values()
	for pedal in PEDALS:
		_press("battlefield_" + pedal, 1.0 if pedal in held else 0.0)
	canvas.queue_redraw()

func _press(action: String, strength: float) -> void:
	if strength > 0.0: Input.action_press(action, strength)
	else: Input.action_release(action)

func _draw_controls() -> void:
	var s := ui_scale
	var font: Font = MenuUI.FONT
	var fill := Color(0.09, 0.08, 0.06, 0.55)
	var line := Color(0.95, 0.89, 0.78, 0.35)
	var active := Color(MenuUI.BUTTON_AMBER, 0.75)
	# Steering zone: faint track plus the wheel where the thumb is.
	var centre := steer_rect.get_center()
	if endless:
		# Resting in the corner; under the thumb while it steers.
		centre = Vector2(steer_rect.position.x + (STEER_TRAVEL + 22.0) * s, steer_rect.end.y - 32.0 * s)
		if steer_finger != -1: centre = steer_origin
	elif steer_finger != -1: centre = Vector2(steer_origin.x, steer_rect.get_center().y)
	# The touch zone itself is invisible; only the slim track and knob are drawn.
	var track := Rect2(centre.x - STEER_TRAVEL * s - 22.0 * s, centre.y - 23.0 * s, (STEER_TRAVEL * s + 22.0 * s) * 2.0, 46.0 * s)
	_rounded(track, fill, line, 23.0 * s)
	canvas.draw_line(Vector2(centre.x, track.position.y + 7.0 * s), Vector2(centre.x, track.end.y - 7.0 * s), line, 2.0 * s)
	var knob := Vector2(centre.x + steer_value * STEER_TRAVEL * s, centre.y)
	canvas.draw_circle(knob, 32.0 * s, active if steer_finger != -1 else Color(0.2, 0.18, 0.14, 0.8))
	canvas.draw_arc(knob, 32.0 * s, 0.0, TAU, 40, Color(0.95, 0.89, 0.78, 0.6), 2.5 * s)
	canvas.draw_string(font, Vector2(knob.x - 30.0 * s, knob.y + 8.0 * s), "◀ ▶", HORIZONTAL_ALIGNMENT_CENTER, 60.0 * s, int(22 * s), Color(0.97, 0.93, 0.85))
	var held := fingers.values()
	for pedal in PEDALS:
		var rect: Rect2 = pedal_rects[pedal]
		_rounded(rect, active if pedal in held else Color(0.09, 0.08, 0.06, 0.72), line, 22.0 * s)
		var lines: PackedStringArray = LABELS[pedal].split("\n")
		var size := int((26 if pedal != "reverse" else 22) * s)
		for i in lines.size():
			var y := rect.get_center().y + (i - (lines.size() - 1) * 0.5) * size * 1.15 + size * 0.35
			canvas.draw_string(font, Vector2(rect.position.x, y), lines[i], HORIZONTAL_ALIGNMENT_CENTER, rect.size.x, size, Color(0.97, 0.93, 0.85))

func _rounded(rect: Rect2, fill: Color, border: Color, radius: float) -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = fill
	style.border_color = border
	style.set_border_width_all(2)
	style.set_corner_radius_all(int(radius))
	canvas.draw_style_box(style, rect)

func _exit_tree() -> void:
	for action in ["battlefield_left", "battlefield_right", "battlefield_throttle", "battlefield_reverse", "battlefield_handbrake"]:
		Input.action_release(action)
