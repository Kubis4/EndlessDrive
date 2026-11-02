extends CanvasLayer
## What a phone shows while driving. Top left the instruments: speed and drive, and gauges for
## what the run depends on (fuel, engine heat, the air outside, oil, coolant, the car's state),
## turning red when it needs seeing to; a tap on them opens the rest (place, ground, weight).
## Top centre, only when there is something to say, a message.
const MenuUI = preload("res://offroad/menu_ui.gd")
const Surface = preload("res://desert_battlefield/terrain.gd")
const GameSettings = preload("res://offroad/game_settings.gd")
const SHEET := Color(0.07, 0.065, 0.05, 0.62)
var game: Node3D
var card: PanelContainer
var speed: Label
var drive: Label
## Each gauge: its bar and its figure.
var gauges := {}
var fills := {}
var details: Label
var rate: Label
var notice: PanelContainer
var notice_text: Label

func setup(root: Node3D) -> void:
	game = root
	name = "PhoneHud"
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 2)
	card = _sheet(column)
	card.mouse_filter = Control.MOUSE_FILTER_STOP
	card.gui_input.connect(_on_card_input)
	add_child(card)
	var top := HBoxContainer.new()
	top.add_theme_constant_override("separation", 12)
	column.add_child(top)
	speed = _text(92, MenuUI.IVORY)
	speed.custom_minimum_size.x = 110.0
	speed.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	top.add_child(speed)
	var side := VBoxContainer.new()
	side.alignment = BoxContainer.ALIGNMENT_CENTER
	side.add_theme_constant_override("separation", -6)
	top.add_child(side)
	var unit := _text(30, Color(MenuUI.IVORY, 0.7))
	unit.text = "KM/H"
	side.add_child(unit)
	drive = _text(34, MenuUI.AMBER)
	side.add_child(drive)
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", 22)
	grid.add_theme_constant_override("v_separation", 4)
	grid.mouse_filter = Control.MOUSE_FILTER_IGNORE
	column.add_child(grid)
	for entry: Array in GAUGES: _gauge(grid, entry[0], entry[1], entry[2], entry[3])
	details = _text(30, Color(MenuUI.IVORY, 0.86))
	details.visible = false
	column.add_child(details)
	rate = _text(24, Color(MenuUI.IVORY, 0.55))
	column.add_child(rate)
	notice_text = _text(38, MenuUI.INK)
	notice_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	notice_text.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	notice_text.custom_minimum_size.x = 720.0
	notice = _sheet(notice_text)
	notice.add_theme_stylebox_override("panel", _style(Color(MenuUI.BUTTON_AMBER, 0.92)))
	notice.visible = false
	add_child(notice)
	get_viewport().size_changed.connect(_lay_out)
	_lay_out.call_deferred()

## Name, symbol, lowest and highest of the bar.
const GAUGES := [["fuel", "fuel", 0.0, 100.0], ["engine", "engine_block", 0.0, 130.0], ["oil", "oil", 0.0, 5.0], ["outside", "thermometer", -30.0, 45.0], ["coolant", "coolant", 0.0, 8.0], ["state", "repairkit", 0.0, 100.0]]
const RED := Color("e0533d")
const COLD := Color("7fb6e6")

func _gauge(grid: Control, key: String, symbol: String, low: float, high: float) -> void:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 8)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	grid.add_child(row)
	var picture := TextureRect.new()
	picture.texture = load("res://assets/ui/%s.svg" % symbol)
	picture.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	picture.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	picture.custom_minimum_size = Vector2.ONE * 34.0
	picture.mouse_filter = Control.MOUSE_FILTER_IGNORE
	row.add_child(picture)
	var bar := ProgressBar.new()
	bar.show_percentage = false
	bar.min_value = low
	bar.max_value = high
	bar.custom_minimum_size = Vector2(96.0, 12.0)
	bar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bar.add_theme_stylebox_override("background", _bar(Color(MenuUI.IVORY, 0.18)))
	var fill := _bar(MenuUI.BUTTON_AMBER)
	bar.add_theme_stylebox_override("fill", fill)
	row.add_child(bar)
	var figure := _text(28, MenuUI.IVORY)
	figure.custom_minimum_size.x = 66.0
	figure.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	row.add_child(figure)
	gauges[key] = [bar, figure]
	fills[key] = fill

func _set_gauge(key: String, value: float, text: String, colour: Color) -> void:
	var gauge: Array = gauges[key]
	gauge[0].value = value
	if gauge[1].text != text: gauge[1].text = text
	if fills[key].bg_color != colour: fills[key].bg_color = colour
	gauge[1].add_theme_color_override("font_color", RED if colour == RED else MenuUI.IVORY)

func _lay_out() -> void:
	var size := get_viewport().get_visible_rect().size
	var inset: Vector4 = game.safe_insets()
	card.position = Vector2(24.0 + inset.x, 16.0 + inset.y)
	notice.position = Vector2(size.x * 0.5 - notice.size.x * 0.5, 16.0 + inset.y)

## From the expedition, a few times a second. Text is only set when it changes.
func refresh(state: Dictionary) -> void:
	var car = game.lead
	speed.text = str(roundi(car.speed_kmh))
	drive.text = ("4X2 P" if car.front_drive else ("4X4" if car.four_wheel_drive else "4X2")) + (" · ZÁMOK" if car.diff_locked else "")
	var amber := MenuUI.BUTTON_AMBER
	(gauges.fuel[0] as ProgressBar).max_value = state.tank
	_set_gauge("fuel", state.fuel, "%d L" % roundi(state.fuel), RED if state.fuel < state.tank * 0.12 else amber)
	_set_gauge("engine", state.engine, "%d°" % roundi(state.engine), RED if state.engine > 105.0 else (COLD if state.engine < 40.0 else amber))
	_set_gauge("oil", state.oil, "%.1f L" % state.oil, RED if state.oil < 1.0 else amber)
	_set_gauge("outside", state.outside, "%d°" % roundi(state.outside), COLD if state.outside < 0.0 else (RED if state.outside > 36.0 else amber))
	_set_gauge("coolant", state.coolant, "%.1f L" % state.coolant, RED if state.coolant < 1.5 else amber)
	_set_gauge("state", state.condition, "%d %%" % roundi(state.condition), RED if state.condition < 35.0 else amber)
	rate.text = "%d M · %s · %d FPS" % [roundi(car.position.y), state.burn, roundi(Engine.get_frames_per_second())]
	var ground: String = Surface.surface_name(car.wheel_surface[0])
	if details.visible:
		details.text = "\n".join(PackedStringArray([state.place, state.weather, "Povrch: " + ground, game.drivetrain_text(), "Hmotnosť %d kg" % roundi(car.mass_kg), state.fluids, "Scrap %d" % state.scraps]))
	var say: String = state.warning
	if not car.engine_running: say = "Motor je vypnutý · naštartuješ ho tlačidlom MOTOR"
	elif car.overturned and car.grounded: say = "Prevrátené · NA CESTU ťa postaví späť"
	elif say == "" and car.water_submersion > 0.45: say = "Brod · ponor %d cm" % roundi(car.water_submersion * 100.0)
	elif say == "": say = state.message
	notice.visible = say != ""
	if notice_text.text != say:
		notice_text.text = say
	_lay_out()

func _on_card_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		details.visible = not details.visible
		# (The card shrinks back when they are closed.)
		card.reset_size()

func _sheet(content: Control) -> PanelContainer:
	var sheet := PanelContainer.new()
	sheet.mouse_filter = Control.MOUSE_FILTER_IGNORE
	sheet.add_theme_stylebox_override("panel", _style(SHEET))
	sheet.add_child(content)
	return sheet

func _style(colour: Color) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = colour
	style.set_corner_radius_all(14)
	style.content_margin_left = 20.0
	style.content_margin_right = 20.0
	style.content_margin_top = 8.0
	style.content_margin_bottom = 8.0
	return style

func _bar(colour: Color) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = colour
	style.set_corner_radius_all(5)
	return style

func _text(size: int, colour: Color, font: Font = MenuUI.FONT) -> Label:
	var node := Label.new()
	node.mouse_filter = Control.MOUSE_FILTER_IGNORE
	node.add_theme_font_override("font", font)
	node.add_theme_font_size_override("font_size", size)
	node.add_theme_color_override("font_color", colour)
	return node
