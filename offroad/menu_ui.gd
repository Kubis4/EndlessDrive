extends RefCounted
## The look of the menus and in-game panels, after Endless Drive's menu: ivory and
## amber on dark translucent cards, Anton for the title and Bebas Neue for the rest.
## On a phone everything is larger, as the same 1080 units are a much smaller screen.
const Settings = preload("res://offroad/game_settings.gd")
const IVORY := Color("f5ecd7")
const AMBER := Color("e9ba68")
const BUTTON_AMBER := Color("ecaf39")
const INK := Color("211b10")
## Writing on parchment.
const PAPER := Color("ecdcb8")
const PEN := Color("33261a")
const RUST := Color("8a4b1e")
const CARD := Color(0.11, 0.115, 0.09, 0.9)
const TITLE_FONT := preload("res://assets/fonts/anton_regular.ttf")
const FONT := preload("res://assets/fonts/bebas_neue_regular.ttf")

static func k() -> float:
	return 1.5 if Settings.is_mobile() else 1.0

static func paper_style() -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = PAPER
	style.set_corner_radius_all(6)
	style.set_border_width_all(2)
	style.border_color = Color(PEN, 0.5)
	style.shadow_color = Color(0.03, 0.02, 0.01, 0.45)
	style.shadow_size = 22
	style.set_content_margin_all(34.0 * k())
	return style

static func card_style(alpha: float = 0.9) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(CARD, alpha)
	style.set_corner_radius_all(16)
	style.set_border_width_all(2)
	style.border_color = Color(IVORY, 0.23)
	style.set_content_margin_all(30.0 * k())
	return style

static func label(text: String, size: float, colour: Color = IVORY, font: Font = FONT) -> Label:
	var node := Label.new()
	node.text = text
	node.add_theme_font_override("font", font)
	node.add_theme_font_size_override("font_size", roundi(size * k()))
	node.add_theme_color_override("font_color", colour)
	return node

## "primary" (amber), "ghost" (outlined), "dark" (outlined, over the game) or "chosen"
## (a selected option); on parchment "list" (just the word), "box" (outlined in ink) and
## "pen" (filled with ink).
static func style_button(button: Button, kind: String = "ghost", height: float = 64.0, size: float = 34.0) -> void:
	if kind in ["list", "box", "pen"]:
		_paper_button(button, kind, height, size)
		return
	var fill := Color(BUTTON_AMBER) if kind in ["primary", "chosen"] else Color(0.05, 0.05, 0.04, 0.62 if kind == "dark" else 0.28)
	var border := Color("ffd383") if kind in ["primary", "chosen"] else Color(IVORY, 0.32)
	var ink := INK if kind in ["primary", "chosen"] else IVORY
	for state: Array in [["normal", fill], ["hover", fill.lightened(0.1)], ["pressed", fill.darkened(0.15)], ["focus", Color(0, 0, 0, 0)], ["disabled", Color(fill, fill.a * 0.4)]]:
		var style := StyleBoxFlat.new()
		style.bg_color = state[1]
		style.set_corner_radius_all(10)
		style.set_border_width_all(2)
		style.border_color = border if state[0] != "focus" else Color(IVORY, 0.8)
		style.content_margin_left = 16.0 * k()
		style.content_margin_right = 16.0 * k()
		button.add_theme_stylebox_override(state[0], style)
	button.add_theme_font_override("font", FONT)
	button.add_theme_font_size_override("font_size", roundi(size * k()))
	for colour in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color", "font_hover_pressed_color"]:
		button.add_theme_color_override(colour, ink)
	button.add_theme_color_override("font_disabled_color", Color(ink, 0.5))
	button.custom_minimum_size.y = height * k()

static func _paper_button(button: Button, kind: String, height: float, size: float) -> void:
	var ink := PAPER if kind == "pen" else PEN
	var fill := PEN if kind == "pen" else Color(PEN, 0.0)
	for state: Array in [["normal", fill], ["hover", fill.lightened(0.14) if kind == "pen" else Color(PEN, 0.11)], ["pressed", fill.darkened(0.2) if kind == "pen" else Color(PEN, 0.2)], ["focus", Color(0, 0, 0, 0)], ["disabled", Color(fill, fill.a * 0.4)]]:
		var style := StyleBoxFlat.new()
		style.bg_color = state[1]
		style.set_corner_radius_all(5)
		if kind == "box": style.set_border_width_all(2)
		style.border_color = Color(PEN, 0.45)
		if state[0] == "focus":
			style.set_border_width_all(0)
			style.border_width_bottom = 3
			style.border_color = Color(RUST, 0.9)
		style.content_margin_left = (8.0 if kind == "list" else 16.0) * k()
		style.content_margin_right = 12.0 * k()
		button.add_theme_stylebox_override(state[0], style)
	button.add_theme_font_override("font", FONT)
	button.add_theme_font_size_override("font_size", roundi(size * k()))
	for colour in ["font_color", "font_focus_color", "font_hover_pressed_color", "font_pressed_color"]:
		button.add_theme_color_override(colour, ink)
	button.add_theme_color_override("font_hover_color", ink if kind == "pen" else RUST)
	button.add_theme_color_override("font_disabled_color", Color(PEN, 0.62))
	button.custom_minimum_size.y = height * k()

static func button(text: String, action: Callable, kind: String = "ghost", height: float = 64.0, size: float = 34.0) -> Button:
	var node := Button.new()
	node.text = text
	style_button(node, kind, height, size)
	node.pressed.connect(action)
	return node

## A card over a dimmed screen; freeing what this returns closes it.
static func modal(parent: Node, content: Control, width: float = 860.0) -> Control:
	var scrim := ColorRect.new()
	scrim.color = Color(0.03, 0.03, 0.02, 0.72)
	scrim.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	parent.add_child(scrim)
	var centre := CenterContainer.new()
	centre.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	scrim.add_child(centre)
	var card := PanelContainer.new()
	card.add_theme_stylebox_override("panel", paper_style())
	card.custom_minimum_size.x = width * k()
	centre.add_child(card)
	card.add_child(content)
	return scrim

## The settings page, opened over `parent`. `changed` runs after each choice.
static func open_settings(parent: Node, changed: Callable) -> Control:
	Settings.load_settings()
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", roundi(14.0 * k()))
	column.add_child(label("NASTAVENIA", 54, PEN))
	var scrim := modal(parent, column, 980.0)
	column.add_child(label("KVALITA GRAFIKY", 26, RUST))
	var quality_row := HBoxContainer.new()
	quality_row.add_theme_constant_override("separation", roundi(10.0 * k()))
	column.add_child(quality_row)
	column.add_child(label("SNÍMKY ZA SEKUNDU", 26, RUST))
	var rate_row := HBoxContainer.new()
	rate_row.add_theme_constant_override("separation", roundi(10.0 * k()))
	column.add_child(rate_row)
	column.add_child(label("ZVUK", 26, RUST))
	var sound_row := HBoxContainer.new()
	sound_row.add_theme_constant_override("separation", roundi(10.0 * k()))
	column.add_child(sound_row)
	var info := label("", 24, PEN)
	info.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(info)
	var buttons := {}
	var refresh := func() -> void:
		for key: Variant in buttons:
			var chosen: bool = (key is String and key == Settings.quality) or (key is int and key == Settings.rate) or (key is float and is_equal_approx(key, Settings.volume))
			style_button(buttons[key], "pen" if chosen else "box", 68.0, 32.0)
		info.text = "Teraz: " + Settings.auto_text()
	for key: String in Settings.QUALITIES:
		var option := button(Settings.QUALITY_NAMES[key], func() -> void:
			# (Asking for automatic again gives the device another chance.)
			if key == "auto": Settings.auto_cap = ""
			Settings.quality = key
			Settings.save_settings()
			Settings.apply_settings()
			changed.call()
			refresh.call())
		option.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		quality_row.add_child(option)
		buttons[key] = option
	for key: int in Settings.RATES:
		var option := button("Auto" if key == 0 else str(key), func() -> void:
			Settings.rate = key
			Settings.save_settings()
			Settings.apply_settings()
			changed.call()
			refresh.call())
		option.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		rate_row.add_child(option)
		buttons[key] = option
	for pair: Array in [["VYP.", 0.0], ["TICHO", 0.4], ["NAPLNO", 1.0]]:
		var value: float = pair[1]
		var option := button(pair[0], func() -> void:
			Settings.volume = value
			Settings.save_settings()
			Settings.apply_settings()
			refresh.call())
		option.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		sound_row.add_child(option)
		buttons[value] = option
	refresh.call()
	column.add_child(button("HOTOVO", func() -> void: scrim.queue_free(), "pen"))
	return scrim
