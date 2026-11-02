extends CanvasLayer
## Phone controls designed for thumbs that rest away from the screen edges:
## - lower left corner: analog steering zone. Touch anywhere in it; the wheel centres
##   where the thumb lands and turns proportionally as it slides left/right (no
##   hunting for small buttons at the bezel).
## - right: tall gas pedal with brake/reverse beside it and the handbrake above,
##   inset from the edge. A thumb can slide between pedals without lifting.
## - on the road, by the left thumb, the AUTO button: it opens a fan of the car's switches,
##   each a symbol as on a dashboard (engine, lights, wipers, 4x4, diff lock, winch). In the
##   top right corner the inventory and a menu for what has no symbol. A tap elsewhere closes
##   what is open.
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
## The fan: symbol, key, distance from AUTO and angle (degrees, 0 to the right).
const FAN := [["engine", KEY_M, 185.0, 5.0], ["headlights", KEY_F, 185.0, 45.0], ["wipers", KEY_U, 185.0, 85.0],
	["drivetrain", KEY_G, 320.0, 8.0], ["differential", KEY_L, 320.0, 36.0], ["winch", KEY_Y, 320.0, 64.0]]
const MENU_ITEMS := [["NA CESTU", KEY_R], ["SKOK · TEST", KEY_N], ["PAUZA", KEY_ESCAPE]]
const GAP := 12.0
## Seconds an open AUTO stays with nothing touched.
const AUTO_OPEN_TIME := 5.0
var endless := false
var action_button: Button
var ui_scale := 1.0
var light_button: Button
var engine_button: Button
var wiper_button: Button
var winch_button: Button
var auto_items: Array[Button] = []
var auto_button: Button
var auto_open := false
var auto_clock := 0.0
var auto_centre := Vector2.ZERO
var inventory_button: Button
var menu_button: Button
var menu_items: Array[Button] = []
var menu_open := false

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
	var items := [] if endless else [["JAZDA / KAMERA", KEY_TAB], ["POHĽAD", KEY_C], ["TEST", KEY_T], ["POHON", KEY_G], ["RESET AUTA", KEY_R], ["AUTO", KEY_V], ["MENU", KEY_ESCAPE], ["DIFERENCIÁL", KEY_L], ["SVETLÁ", KEY_F], ["POČASIE", KEY_K]]
	for item in items:
		var button := Button.new()
		button.text = item[0]
		button.focus_mode = Control.FOCUS_NONE
		if item[1] == KEY_G: drive_button = button
		if item[1] == KEY_L: diff_button = button
		if item[1] == KEY_F: light_button = button
		button.pressed.connect(_send_key.bind(item[1]))
		add_child(button)
	if endless:
		_build_road_buttons()
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

## The AUTO button with its fan, the inventory and the menu in the corner.
func _build_road_buttons() -> void:
	auto_button = _road_button("AUTO", func(): show_auto(not auto_open), true)
	for entry: Array in FAN:
		var button := _road_button("", _send_key.bind(entry[1]), true)
		button.icon = load("res://assets/ui/%s.svg" % entry[0])
		button.expand_icon = true
		button.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
		button.visible = false
		auto_items.append(button)
		match entry[1]:
			KEY_G: drive_button = button
			KEY_L: diff_button = button
			KEY_F: light_button = button
			KEY_M: engine_button = button
			KEY_U: wiper_button = button
			KEY_Y: winch_button = button
	inventory_button = _road_button("INVENTÁR", _send_key.bind(KEY_I))
	menu_button = _road_button("MENU", func(): show_menu(not menu_open))
	for entry: Array in MENU_ITEMS:
		var button := _road_button(entry[0], func():
			show_menu(false)
			_send_key(entry[1]))
		button.visible = false
		menu_items.append(button)

func _road_button(text: String, action: Callable, round_one := false) -> Button:
	var button := Button.new()
	button.text = text
	button.focus_mode = Control.FOCUS_NONE
	if round_one: button.set_meta("round", true)
	_style(button, false)
	button.clip_text = true
	button.pressed.connect(action)
	add_child(button)
	return button

## As the keyboard would (cargo, refilling and test jumps belong to the expedition).
func _send_key(keycode: int) -> void:
	auto_clock = 0.0
	var key := InputEventKey.new()
	key.keycode = keycode
	key.pressed = true
	if keycode in [KEY_I, KEY_B, KEY_N]: game.get_node("Expedition")._unhandled_key_input(key)
	else: game._unhandled_key_input(key)

func show_menu(on: bool) -> void:
	menu_open = on
	for button in menu_items: button.visible = on

func show_auto(on: bool) -> void:
	auto_open = on
	auto_clock = 0.0
	auto_button.text = "ZAVRIEŤ" if on else "AUTO"
	for button in auto_items: button.visible = on
	_layout()
	# The fan opens out of AUTO, the nearest first.
	if on:
		for i in auto_items.size():
			var button := auto_items[i]
			var target := button.position
			button.position = auto_centre - button.size * 0.5
			var opening := create_tween()
			opening.tween_property(button, "position", target, 0.14).set_delay(i * 0.012).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)

func _process(delta: float) -> void:
	if auto_open:
		auto_clock += delta
		if auto_clock > AUTO_OPEN_TIME or game.paused: show_auto(false)
	if menu_open and game.paused: show_menu(false)
	if action_button != null:
		var expedition = game.get_node_or_null("Expedition")
		var text: String = expedition.action_text() if expedition != null and expedition.window != null else ""
		action_button.visible = text != "" and not game.paused and not expedition.window.visible and not auto_open
		action_button.text = text
	label_clock += delta
	if label_clock < 0.2: return
	label_clock = 0.0
	var car = game.lead
	drive_button.disabled = car.front_drive
	if endless:
		diff_button.disabled = car.front_drive
		wiper_button.disabled = car.wipers.size() != 2
		_mark(drive_button, car.four_wheel_drive and not car.front_drive)
		_mark(diff_button, car.diff_locked)
		_mark(light_button, car.lights_on)
		_mark(engine_button, car.engine_running)
		_mark(wiper_button, car.wipers_on)
		var expedition = game.get_node_or_null("Expedition")
		if expedition != null and expedition.winch != null: _mark(winch_button, expedition.winch.attached)
		return
	drive_button.text = "POHON " + ("4x2 P" if car.front_drive else ("4x4" if car.four_wheel_drive else "4x2"))
	diff_button.text = "DIF " + ("ZAMKNUTÝ" if car.diff_locked else "OTVORENÝ")

## A button that is on is amber (restyled only when that changes).
func _mark(button: Button, on: bool) -> void:
	if button.has_meta("on") and button.get_meta("on") == on: return
	button.set_meta("on", on)
	var font := button.get_theme_font_size("font_size")
	var keep := button.size
	_style(button, on)
	button.add_theme_font_size_override("font_size", font)
	button.custom_minimum_size = keep
	button.size = keep

## The menu's button look with narrow margins (words fill these small buttons), round where
## wanted, with its symbol in the writing's colour.
func _style(button: Button, on: bool) -> void:
	MenuUI.style_button(button, "chosen" if on else "dark", 60.0, 24.0)
	for state in ["normal", "hover", "pressed", "focus", "disabled"]:
		var style := button.get_theme_stylebox(state) as StyleBoxFlat
		style.content_margin_left = 4.0
		style.content_margin_right = 4.0
		if button.has_meta("round"):
			style.set_corner_radius_all(100)
			style.content_margin_top = 22.0
			style.content_margin_bottom = 22.0
			style.content_margin_left = 22.0
			style.content_margin_right = 22.0
	var ink := MenuUI.INK if on else MenuUI.IVORY
	for state in ["icon_normal_color", "icon_hover_color", "icon_pressed_color", "icon_hover_pressed_color", "icon_focus_color"]:
		button.add_theme_color_override(state, ink)
	button.add_theme_color_override("icon_disabled_color", Color(ink, 0.3))

## A button at its place and size, with its words to fit.
func _put(button: Button, at: Vector2, size: Vector2, font_share: float) -> void:
	button.custom_minimum_size = size
	button.size = size
	button.position = at
	button.add_theme_font_size_override("font_size", roundi(size.y * font_share))

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
		steer_rect = Rect2(left, size.y * 0.6, size.x * 0.34, size.y * 0.4 - 20.0 * s - inset.w)
	# Right cluster, inset from the edge: gas (tall), brake left of it,
	# handbrake above the brake where the thumb pivots naturally.
	var pedal := Vector2(128, 196) * s
	var gas_x := size.x - margin_right - pedal.x
	pedal_rects["throttle"] = Rect2(gas_x, bottom - pedal.y, pedal.x, pedal.y)
	pedal_rects["reverse"] = Rect2(gas_x - 20.0 * s - pedal.x, bottom - pedal.y * 0.72, pedal.x, pedal.y * 0.72)
	pedal_rects["handbrake"] = Rect2(gas_x - 20.0 * s - pedal.x, bottom - pedal.y * 0.72 - 16.0 * s - 92.0 * s, pedal.x, 92.0 * s)
	# On the road the gas pedal stands as tall as the two beside it.
	if endless: pedal_rects["throttle"] = Rect2(gas_x, pedal_rects["handbrake"].position.y, pedal.x, bottom - pedal_rects["handbrake"].position.y)
	var index := 0
	var button_size := BUTTON_SIZE * clampf(s, 0.75, 1.3)
	var per_row := 6
	if endless:
		var unit := clampf(s / 1.5, 0.75, 1.4)
		if action_button != null:
			var wide := Vector2(520.0, 104.0) * unit
			action_button.size = wide
			# Below the car, between the steering and the pedals.
			action_button.position = Vector2(size.x * 0.5 - wide.x * 0.5, bottom - wide.y)
		_layout_auto(size, inset, unit)
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

## AUTO and its fan on the left, the inventory and the menu in the top right corner.
func _layout_auto(size: Vector2, inset: Vector4, unit: float) -> void:
	var gap := GAP * unit
	var key := Vector2.ONE * 132.0 * unit
	var at := Vector2(steer_rect.position.x, steer_rect.position.y - gap * 0.5 - key.y)
	_put(auto_button, at, key, 0.24)
	auto_centre = at + key * 0.5
	var round_key := Vector2.ONE * 124.0 * unit
	for i in auto_items.size():
		var turn := deg_to_rad(float(FAN[i][3]))
		_put(auto_items[i], auto_centre + Vector2(cos(turn), -sin(turn)) * float(FAN[i][2]) * unit - round_key * 0.5, round_key, 0.25)
	var right := size.x - 24.0 * ui_scale - inset.z
	var top := 16.0 + inset.y
	var small := Vector2(170.0, 84.0) * unit
	_put(menu_button, Vector2(right - small.x, top), small, 0.38)
	_put(inventory_button, Vector2(right - small.x * 2.0 - gap, top), small, 0.38)
	var row := Vector2(small.x * 2.0 + gap, small.y)
	for i in menu_items.size():
		_put(menu_items[i], Vector2(right - row.x, top + (i + 1) * (small.y + gap)), row, 0.38)

func _input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.pressed:
			# Over a menu or window, a touch is for its buttons.
			var expedition = game.get_node_or_null("Expedition")
			if game.paused or (expedition != null and expedition.get("window") != null and expedition.window.visible): return
			# (The switches stand close to the steering: a touch on one is for it.)
			if _on_button(event.position): return
			# A touch anywhere else closes AUTO, and still does what it does.
			if auto_open: show_auto(false)
			if menu_open: show_menu(false)
			if steer_rect.grow(24.0 * ui_scale).has_point(event.position) and steer_finger == -1:
				steer_finger = event.index
				steer_origin = event.position
				fingers[event.index] = "steer"
				_set_steer(0.0)
			else:
				var pedal := _pedal_at(event.position)
				if pedal != "": fingers[event.index] = pedal
				else:
					camera_fingers[event.index] = event.position
					return
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
