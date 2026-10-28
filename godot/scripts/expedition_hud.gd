@tool
extends Control
## Variant C: safe-area aware touch controls and a collapsible equipment fan.
signal action_requested(action: String)
signal controls_changed
const FanButton = preload("res://scripts/fan_button.gd")
const DashboardSkin = preload("res://scripts/hud_skin.gd")
const GOLD := Color("e9ba68")
@export var preview_equipment := false:
	set(value):
		preview_equipment = value
		if Engine.is_editor_hint() and is_node_ready(): set_equipment_open(value)
var equipment_open := false
var touch_mode := 0 # Auto, touch, desktop. Explicit preference survives sessions.
var touch_seen := false
var touch_enabled := true
var pedals: Dictionary = {}
var actions: Dictionary = {}
var _last_size := Vector2.ZERO
var _last_touch := false
var _last_safe := Rect2i()
var _light_icon_mode := -1

func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	if not has_node("Trip"): _build()
	if not has_node("Status"): _label("Status", "", 17).horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	for entry: Array in [["lights", "headlights"], ["camera", "camera"], ["brake", "brake"], ["handbrake", "handbrake"], ["throttle", "accelerator"], ["left", "chevron_left"], ["right", "chevron_right"], ["pause", "pause"], ["Tools", "tools"], ["engine", "power"], ["service", "tools"], ["cruise", "cruise"], ["InputMode", "input_mode"]]:
		var button := get_node(entry[0]) as Button
		button.icon = load("res://assets/ui/" + entry[1] + ".svg")
		button.text = ""
		button.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
		button.expand_icon = true
		button.add_theme_constant_override("icon_max_width", 56)
	_center_icon($Tools, 44)
	_center_icon($service, 28)
	for key: String in ["drive_mode", "differential", "winch"]:
		var parent := get_node(key)
		if not parent.has_node("Icon"):
			var icon := TextureRect.new()
			icon.name = "Icon"
			icon.texture = load("res://assets/ui/" + ("drivetrain" if key == "drive_mode" else key) + ".svg")
			icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
			icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
			parent.add_child(icon)
	for key: String in ["left", "right", "brake", "throttle", "handbrake"]: pedals[key] = get_node(key)
	for key: String in ["lights", "camera", "pause", "drive_mode", "differential", "winch", "winch_pull", "service", "engine", "cruise", "search"]:
		actions[key] = get_node(key)
		var button: Button = actions[key]
		button.pressed.connect(func() -> void: action_requested.emit(key))
	$Tools.pressed.connect(func() -> void: set_equipment_open(not equipment_open))
	var hub_style := _style(Color("191d18f5"), 42)
	hub_style.border_color = GOLD
	$Tools.add_theme_stylebox_override("normal", hub_style)
	$AlertIcon.z_index = 1
	$InputMode.pressed.connect(_cycle_input_mode)
	_apply_skin()
	if not Engine.is_editor_hint() and not OS.get_cmdline_user_args().has("--test-session"):
		var config := ConfigFile.new()
		if config.load("user://hud-controls.cfg") == OK: touch_mode = clampi(int(config.get_value("controls", "mode", 0)), 0, 2)
	set_equipment_open(preview_equipment if Engine.is_editor_hint() else false)
	resized.connect(_layout)
	_layout()

func _center_icon(button: Button, width: float) -> void:
	# A separate centered rect avoids Button text/icon spacing and skin margins.
	button.icon = null
	var glyph := button.get_node_or_null("Glyph") as TextureRect
	if glyph == null:
		glyph = TextureRect.new()
		glyph.name = "Glyph"
		button.add_child(glyph)
	glyph.texture = load("res://assets/ui/tools.svg")
	glyph.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	glyph.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	glyph.mouse_filter = Control.MOUSE_FILTER_IGNORE
	glyph.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	glyph.offset_left = -width * 0.5
	glyph.offset_top = -width * 0.5
	glyph.offset_right = width * 0.5
	glyph.offset_bottom = width * 0.5

func _apply_skin() -> void:
	for child in get_children():
		if child is Button and not child is FanButton:
			var shape := "_left" if child.name == "left" else ("_right" if child.name == "right" else ("_hub" if child.name == "Tools" else ""))
			for state: String in ["normal", "hover", "pressed", "disabled", "focus"]:
				var kind: String = {"normal": "idle", "hover": "hover", "pressed": "active", "disabled": "disabled", "focus": "hover"}[state]
				if child.name == "throttle" and state == "normal": kind = "primary"
				if child.name == "Tools" and state == "normal": kind = "active"
				child.add_theme_stylebox_override(state, DashboardSkin.style(kind, shape))
			child.add_theme_color_override("icon_normal_color", Color.WHITE)
			child.add_theme_color_override("icon_hover_color", Color.WHITE)
			child.add_theme_color_override("icon_pressed_color", GOLD)
			child.add_theme_color_override("icon_disabled_color", Color("7d856f"))
	$Trip.add_theme_stylebox_override("panel", DashboardSkin.style())
	$AlertPlate.add_theme_stylebox_override("panel", DashboardSkin.style("danger"))

func _style(color: Color, radius := 14) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = color
	style.set_corner_radius_all(radius)
	style.set_border_width_all(2)
	style.border_color = Color("70735e")
	style.shadow_color = Color(0, 0, 0, 0.28)
	style.shadow_size = 5
	style.shadow_offset = Vector2(0, 3)
	return style

func _label(key: String, text: String, font_size: int) -> Label:
	var node := Label.new()
	node.name = key
	node.text = text
	node.add_theme_font_size_override("font_size", font_size)
	node.add_theme_color_override("font_color", Color("f5ecd7"))
	node.add_theme_color_override("font_shadow_color", Color("10150e"))
	node.add_theme_constant_override("shadow_outline_size", 4)
	node.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(node)
	return node

func _button(key: String, text: String, toggle := false) -> Button:
	var node := Button.new()
	node.name = key
	node.text = text
	node.toggle_mode = toggle
	node.focus_mode = Control.FOCUS_NONE
	node.add_theme_font_size_override("font_size", 20)
	node.add_theme_stylebox_override("normal", _style(Color("191d18e8")))
	node.add_theme_stylebox_override("hover", _style(Color("424b37")))
	node.add_theme_stylebox_override("pressed", _style(Color("695538")))
	node.add_theme_stylebox_override("disabled", _style(Color("191d18aa")))
	add_child(node)
	return node

func _build() -> void:
	var trip := Panel.new()
	trip.name = "Trip"
	trip.add_theme_stylebox_override("panel", _style(Color("191d18e8")))
	trip.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(trip)
	_label("Distance", "8.43 km", 34)
	_label("Region", "PRAŠNÁ KOTLINA", 13)
	for key: String in ["Fuel", "Temperature"]:
		var chip := _button(key, "", false)
		chip.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_label("FuelText", "1.7 l", 20)
	_label("TempText", "112 °C", 20)
	for entry: Array in [["FuelIcon", "fuel"], ["TempIcon", "thermometer"], ["TripIcon", "route_pin"], ["AlertIcon", "thermometer"]]:
		var icon := TextureRect.new()
		icon.name = entry[0]
		icon.texture = load("res://assets/ui/" + entry[1] + ".svg")
		icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
		add_child(icon)
	_label("Speed", "0", 46)
	_label("SpeedUnit", "km/h", 22)
	_label("Gear", "D", 28).modulate = GOLD
	_label("Alert", "", 24).horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_label("Weather", "", 22).horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	for entry: Array in [["handbrake", "P"], ["lights", "☼"], ["camera", "▣"], ["pause", "Ⅱ"], ["left", "❮"], ["right", "❯"], ["brake", "●"], ["throttle", "↑"], ["Tools", "⚒"], ["winch_pull", "ŤAHAŤ"], ["service", "VOZIDLO"], ["engine", "MOTOR"], ["cruise", "TEMPOMAT"], ["search", "PREHĽADAŤ"], ["InputMode", "DOTYK · AUTO"]]:
		_button(entry[0], entry[1], entry[0] in ["lights", "engine", "cruise", "winch_pull"])
	for key: String in ["left", "right", "throttle", "brake", "pause"]: get_node(key).add_theme_font_size_override("font_size", 42)
	for key: String in ["engine", "service", "cruise", "winch_pull", "search"]: get_node(key).add_theme_font_size_override("font_size", 26)
	var gas_style := _style(Color("755421ed"), 18)
	gas_style.border_color = GOLD
	$throttle.add_theme_stylebox_override("normal", gas_style)
	var gas_pressed := _style(Color("bd7924"), 18)
	gas_pressed.border_color = GOLD
	$throttle.add_theme_stylebox_override("pressed", gas_pressed)
	for key: String in ["left", "right"]:
		for state: String in ["normal", "hover", "pressed"]:
			var steering := _style(Color("191d18ee") if state == "normal" else Color("49503d"), 46)
			if key == "left":
				steering.corner_radius_top_right = 0
				steering.corner_radius_bottom_right = 0
				steering.border_width_right = 1
			else:
				steering.corner_radius_top_left = 0
				steering.corner_radius_bottom_left = 0
				steering.border_width_left = 1
			get_node(key).add_theme_stylebox_override(state, steering)
	var alert_plate := Panel.new()
	alert_plate.name = "AlertPlate"
	alert_plate.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var alert_style := _style(Color("240e0fe8"), 15)
	alert_style.border_color = Color("c74749")
	alert_plate.add_theme_stylebox_override("panel", alert_style)
	add_child(alert_plate)
	move_child(alert_plate, $Alert.get_index())
	$AlertIcon.modulate = Color("ff5959")
	$FuelIcon.modulate = GOLD

	for index in 3:
		var key: String = ["drive_mode", "differential", "winch"][index]
		var node := FanButton.new()
		node.name = key
		node.toggle_mode = true
		node.focus_mode = Control.FOCUS_NONE
		node.start_degrees = 190 + index * 53
		node.end_degrees = 240 + index * 53
		for state: String in ["normal", "hover", "pressed", "focus", "disabled"]: node.add_theme_stylebox_override(state, StyleBoxEmpty.new())
		add_child(node)
		var label := Label.new()
		label.name = "Caption"
		label.text = ["4×4", "UZÁVIERKA", "NAVIJAK"][index]
		label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		label.add_theme_font_size_override("font_size", 18)
		label.mouse_filter = Control.MOUSE_FILTER_IGNORE
		node.add_child(label)
	_label("VehicleSummary", "", 22).horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER

func _rect(key: String, pos: Vector2, dimensions: Vector2) -> void:
	var node := get_node(key) as Control
	node.position = pos
	node.size = dimensions

func _layout() -> void:
	touch_enabled = touch_mode == 1 or (touch_mode == 0 and (touch_seen or DisplayServer.is_touchscreen_available() or Engine.is_editor_hint()))
	var safe := _last_safe
	if size != _last_size or touch_enabled != _last_touch:
		safe = DisplayServer.get_display_safe_area() if OS.has_feature("mobile") else Rect2i()
	if size == _last_size and touch_enabled == _last_touch and safe == _last_safe: return
	_last_size = size
	_last_touch = touch_enabled
	_last_safe = safe
	var w := size.x
	var h := size.y
	var margin := 28.0
	var top := 0.0
	if OS.has_feature("mobile"):
		var window := Vector2(DisplayServer.window_get_size())
		if window.x > 0: margin = maxf(margin, maxf(safe.position.x, window.x - safe.end.x) * w / window.x + 12)
		if window.y > 0 and safe.size.y > 0:
			top = safe.position.y * size.y / window.y
			h -= maxf(0, window.y - safe.end.y) * size.y / window.y
	_rect("Trip", Vector2(margin, 20), Vector2(232, 76))
	_rect("TripIcon", Vector2(margin + 15, 30), Vector2(37, 54))
	_rect("Distance", Vector2(margin + 64, 22), Vector2(164, 44))
	_rect("Region", Vector2(margin + 66, 67), Vector2(160, 24))
	for index in 2:
		var x := margin + index * 120
		_rect(["Fuel", "Temperature"][index], Vector2(x, 104), Vector2(112, 48))
		_rect(["FuelIcon", "TempIcon"][index], Vector2(x + 12, 114), Vector2(25, 28))
		_rect(["FuelText", "TempText"][index], Vector2(x + 45, 111), Vector2(68, 32))
	for index in 3: _rect(["lights", "camera", "pause"][index], Vector2(w - margin - 280 + index * 98, 20), Vector2(84, 76))
	_rect("AlertPlate", Vector2(w * 0.5 - 182, 20), Vector2(364, 54))
	_rect("AlertIcon", Vector2(w * 0.5 - 166, 31), Vector2(26, 32))
	_rect("Alert", Vector2(w * 0.5 - 130, 24), Vector2(294, 44))
	$Alert.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	$Alert.clip_text = true
	$Alert.add_theme_font_size_override("font_size", 16)
	_rect("Status", Vector2(w * 0.5 - 250, 78), Vector2(500, 28))
	_rect("Weather", Vector2(w * 0.5 - 250, 78), Vector2(500, 28))
	$Weather.add_theme_font_size_override("font_size", 17)
	_rect("left", Vector2(margin, h - 124), Vector2(172, 94))
	_rect("right", Vector2(margin + 172, h - 124), Vector2(172, 94))
	_rect("brake", Vector2(w - margin - 246, h - 146), Vector2(120, 116))
	_rect("throttle", Vector2(w - margin - 100, h - 202), Vector2(100, 172))
	_rect("handbrake", Vector2(w - margin - 234, h - 252), Vector2(96, 92))
	_rect("Speed", Vector2(margin + 18, h - (185 if touch_enabled else 90)), Vector2(100, 58))
	_rect("SpeedUnit", Vector2(margin + 100, h - (164 if touch_enabled else 69)), Vector2(72, 30))
	_rect("Gear", Vector2(margin + 188, h - (171 if touch_enabled else 76)), Vector2(60, 36))
	for key: String in pedals: pedals[key].visible = touch_enabled
	var hub := Vector2(w * 0.5, h - 68)
	_rect("Tools", hub - Vector2(42, 42), Vector2(84, 84))
	for index in 3:
		var key: String = ["drive_mode", "differential", "winch"][index]
		_rect(key, hub - Vector2(200, 200), Vector2(400, 400))
		var angle := deg_to_rad(215 + index * 53)
		var caption := get_node(key + "/Caption") as Label
		var midpoint := Vector2(200, 200) + Vector2(cos(angle), sin(angle)) * 134
		caption.position = midpoint - Vector2(42, -19)
		caption.size = Vector2(84, 24)
		caption.add_theme_font_size_override("font_size", 14)
		caption.clip_text = true
		var icon := get_node(key + "/Icon") as TextureRect
		icon.position = midpoint - Vector2(26, 37)
		icon.size = Vector2(52, 52)
		icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	_rect("winch_pull", Vector2(w * 0.5 - 110, h - 478), Vector2(220, 62))
	for index in 4:
		_rect(["engine", "service", "cruise", "InputMode"][index], hub + Vector2([-204.0, -124.0, 60.0, 140.0][index], -6), Vector2(64, 56))
		get_node(["engine", "service", "cruise", "InputMode"][index]).add_theme_constant_override("icon_max_width", 28)
	_rect("search", Vector2(w * 0.5 - 130, 123), Vector2(260, 64))
	_rect("VehicleSummary", Vector2(w * 0.5 - 260, h - 418), Vector2(520, 32))
	$VehicleSummary.add_theme_font_size_override("font_size", 16)
	for key: String in ["Trip", "TripIcon", "Distance", "Region", "Fuel", "Temperature", "FuelIcon", "TempIcon", "FuelText", "TempText", "lights", "camera", "pause", "AlertPlate", "AlertIcon", "Alert", "Status", "Weather", "search"]:
		get_node(key).position.y += top

func set_equipment_open(value: bool) -> void:
	equipment_open = value
	for key: String in ["drive_mode", "differential", "winch", "service", "engine", "cruise", "InputMode"]: get_node(key).visible = value
	$VehicleSummary.hide()
	$Tools.text = ""
	$Tools.icon = null
	$Tools/Glyph.modulate = GOLD if value else Color.WHITE
	$Tools.add_theme_font_size_override("font_size", 38)

func touch_button_at(point: Vector2) -> Button:
	for key: String in ["Tools", "InputMode", "lights", "camera", "pause", "drive_mode", "differential", "winch", "winch_pull", "service", "engine", "cruise", "search"]:
		var button := get_node(key) as Button
		if not button.is_visible_in_tree() or button.disabled: continue
		var local := point - button.global_position
		if button is FanButton:
			if button._has_point(local): return button
		elif Rect2(Vector2.ZERO, button.size).has_point(local): return button
	return null

func _cycle_input_mode() -> void:
	touch_mode = (touch_mode + 1) % 3
	controls_changed.emit()
	var config := ConfigFile.new()
	config.set_value("controls", "mode", touch_mode)
	if not Engine.is_editor_hint() and not OS.get_cmdline_user_args().has("--test-session"): config.save("user://hud-controls.cfg")
	_last_size = Vector2.ZERO
	_layout()

func refresh(hud: DriveHUD) -> void:
	_layout()
	visible = not hud.modal_open
	if hud.modal_open: set_equipment_open(false)
	var run := hud.run
	var vehicle := hud.vehicle
	$Distance.text = "%.2f km" % (run.odometer / 1000)
	$Distance.tooltip_text = tr("Prejdená vzdialenosť") + " · " + tr("Dosah trasy") + ": %.2f km" % (run.distance / 1000)
	$Region.text = tr(DriveWorld.BIOMES[hud.world.biome_at(vehicle.position.x)]).to_upper()
	$FuelText.text = "%.1f l" % run.fuel
	$TempText.text = "%d °C" % roundi(run.temperature)
	$FuelText.modulate = GOLD if run.fuel < 3 else Color.WHITE
	$TempText.modulate = Color("fa846e") if run.temperature > 110 else Color.WHITE
	$Speed.text = "%d" % roundi(absf(vehicle.speed) * 3.6)
	$Gear.text = "R" if vehicle.speed < -0.1 or vehicle._reverse_engaged else ("D" if run.engine_running else "N")
	$drive_mode/Caption.text = "4×4" if run.four_wheel_drive else "4×2"
	$differential/Caption.text = tr("UZÁVIERKA")
	$winch/Caption.text = tr("NAVIJAK")
	$drive_mode.set_pressed_no_signal(run.four_wheel_drive)
	$differential.set_pressed_no_signal(run.differential_locked)
	$drive_mode.visible = equipment_open and not vehicle.is_front_drive()
	$differential.visible = equipment_open and not vehicle.is_front_drive()
	var state := str(vehicle.get_meta("winch_state", "idle"))
	$winch.set_pressed_no_signal(state != "idle")
	$winch_pull.visible = state != "idle"
	$winch_pull.text = tr("ZASTAVIŤ") if state == "reeling" else tr("ŤAHAŤ")
	$engine.tooltip_text = tr("VYPNÚŤ") if run.engine_running else tr("ŠTART")
	$engine.set_pressed_no_signal(run.engine_running)
	$cruise.tooltip_text = tr("TEMPOMAT")
	$cruise.set_pressed_no_signal(vehicle.cruise_enabled)
	$service.tooltip_text = tr("VOZIDLO")
	$InputMode.tooltip_text = [tr("DOTYK · AUTO"), tr("DOTYK · ZAP"), tr("DOTYK · VYP")][touch_mode]
	$lights.set_pressed_no_signal(run.headlights)
	var light_mode := (2 if run.high_beams else 1) if run.headlights else 0
	if light_mode != _light_icon_mode:
		_light_icon_mode = light_mode
		$lights.icon = load("res://assets/ui/headlights_high.svg" if light_mode == 2 else "res://assets/ui/headlights.svg")
		var indicator := Color("83bfff") if light_mode == 2 else (Color("83dba4") if light_mode == 1 else Color.WHITE)
		for theme_key in ["icon_normal_color", "icon_hover_color", "icon_pressed_color"]: $lights.add_theme_color_override(theme_key, indicator)
	$handbrake.modulate = GOLD if vehicle.handbrake_input > 0.0 else Color.WHITE
	$Alert.text = tr("Motor sa prehrieva") if run.temperature > 110 else (tr("Málo paliva") if run.fuel < 3 else (tr("Slabá batéria") if run.battery < 0.12 else (tr("Málo oleja") if run.oil < 0.5 else (tr("Málo chladiacej kvapaliny") if run.coolant < 1 else ""))))
	$Alert.modulate = Color("ff5959")
	if run.water_ingress >= 1.0: $Alert.text = tr("Motor nasal vodu")
	elif run.water_ingress > 0.25: $Alert.text = tr("Voda v nasávaní")
	var diagnostic := vehicle.motion_diagnostic()
	var informational := diagnostic.begins_with("Motor je vypnutý") or diagnostic.begins_with("Brzdíš") or diagnostic.begins_with("Ručná brzda")
	if not informational and not diagnostic.is_empty() and run.temperature <= 110 and run.water_ingress < 1.0:
		$Alert.text = tr(diagnostic)
	$Alert.tooltip_text = tr(diagnostic)
	var transient := hud._message.text if hud._message_time > 0 else ""
	var is_error := int(hud._message.get_meta("priority", 0)) > 0
	if $Alert.text.is_empty() and is_error: $Alert.text = transient
	$Status.text = transient if not is_error and not transient.is_empty() else (tr(diagnostic) if informational else tr("Motor beží" if run.engine_running else "Motor vypnutý"))
	$Status.modulate = Color("ddd4bd")
	hud._toast_panel.hide()
	$AlertPlate.visible = not $Alert.text.is_empty()
	$AlertIcon.visible = $AlertPlate.visible
	$Weather.text = tr("V úkryte") if hud.weather != null and hud.weather.sheltered and hud.weather.storm_active else (tr("Piesočná búrka") if hud.weather != null and hud.weather.storm_visibility > 0.20 else "")
	if hud.weather != null and hud.weather.storm_active and hud.weather.storm_warning > 0 and not hud.weather.sheltered:
		$Weather.text = tr("Búrka za %d s · vyhľadaj prístrešok") % ceili(hud.weather.storm_warning)
	# Weather takes the informational row while relevant; keep the existing
	# Search button position and never stack two labels over its hit area.
	$Status.visible = $Weather.text.is_empty()
	$VehicleSummary.text = tr("Olej %.1f l · Chladenie %.1f l · Batéria %d %%") % [run.oil, run.coolant, roundi(run.battery * 100)]
	$search.visible = not hud.world.nearest_building(vehicle.position).is_empty()
	$search.disabled = absf(vehicle.speed) > 0.5
	$search.text = tr("PREHĽADAŤ") if not $search.disabled else tr("NAJPRV ZASTAV")
