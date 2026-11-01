extends CanvasLayer
## What a phone shows while driving. Top left the instruments: speed, drive and fuel;
## a tap on them opens the rest (place, ground, weight, fluids). Top centre where the
## road goes next, and below it, only when there is something to say, a message.
const MenuUI = preload("res://offroad/menu_ui.gd")
const Surface = preload("res://desert_battlefield/terrain.gd")
const GameSettings = preload("res://offroad/game_settings.gd")
const SHEET := Color(0.07, 0.065, 0.05, 0.62)
## What the row of buttons in the top right corner takes (mobile_controls.gd).
const BUTTONS_WIDTH := 860.0
var game: Node3D
var card: PanelContainer
var speed: Label
var drive: Label
var fuel_bar: ProgressBar
var fuel_text: Label
var details: Label
var rate: Label
var road: PanelContainer
var road_text: Label
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
	fuel_bar = ProgressBar.new()
	fuel_bar.show_percentage = false
	fuel_bar.custom_minimum_size = Vector2(280.0, 12.0)
	fuel_bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	fuel_bar.add_theme_stylebox_override("background", _bar(Color(MenuUI.IVORY, 0.18)))
	fuel_bar.add_theme_stylebox_override("fill", _bar(MenuUI.BUTTON_AMBER))
	column.add_child(fuel_bar)
	fuel_text = _text(30, MenuUI.IVORY)
	column.add_child(fuel_text)
	details = _text(30, Color(MenuUI.IVORY, 0.86))
	details.visible = false
	column.add_child(details)
	rate = _text(24, Color(MenuUI.IVORY, 0.55))
	column.add_child(rate)
	road_text = _text(40, MenuUI.IVORY)
	road = _sheet(road_text)
	add_child(road)
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

func _lay_out() -> void:
	var size := get_viewport().get_visible_rect().size
	var inset: Vector4 = game.safe_insets()
	card.position = Vector2(24.0 + inset.x, 16.0 + inset.y)
	# Between the instruments and the buttons.
	var from := card.get_rect().end.x + 16.0
	var to: float = size.x - inset.z - BUTTONS_WIDTH
	road.position = Vector2(maxf(from, (from + to - road.size.x) * 0.5), 16.0 + inset.y)
	notice.position = Vector2(size.x * 0.5 - notice.size.x * 0.5, road.get_rect().end.y + 10.0)

## From the expedition, a few times a second. Text is only set when it changes.
func refresh(state: Dictionary) -> void:
	var car = game.lead
	speed.text = str(roundi(car.speed_kmh))
	drive.text = ("4X2 P" if car.front_drive else ("4X4" if car.four_wheel_drive else "4X2")) + (" · ZÁMOK" if car.diff_locked else "")
	fuel_bar.max_value = state.tank
	fuel_bar.value = state.fuel
	fuel_text.text = "%.0f L · %s" % [state.fuel, state.burn]
	# (With the mountains if there is room for them.)
	var whole: String = (state.next + " · " + state.land).to_upper() if state.next != "" else state.land.to_upper()
	var room: float = get_viewport().get_visible_rect().size.x - game.safe_insets().z - BUTTONS_WIDTH - card.get_rect().end.x - 16.0
	var font: Font = road_text.get_theme_font("font")
	var fits := font.get_string_size(whole, HORIZONTAL_ALIGNMENT_LEFT, -1, road_text.get_theme_font_size("font_size")).x + 60.0 <= room
	road_text.text = whole if fits or state.next == "" else state.next.to_upper()
	rate.text = "%d FPS · %s" % [roundi(Engine.get_frames_per_second()), GameSettings.QUALITY_NAMES[GameSettings.effective_quality()]]
	var ground: String = Surface.surface_name(car.wheel_surface[0])
	if details.visible:
		details.text = "\n".join(PackedStringArray([state.place, state.weather, "Povrch: " + ground, game.drivetrain_text(), "Hmotnosť %d kg · Stav %.0f %%" % [roundi(car.mass_kg), state.condition], state.fluids, "Scrap %d" % state.scraps]))
	var say: String = state.warning
	if car.overturned and car.grounded: say = "Prevrátené · NA CESTU ťa postaví späť"
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
