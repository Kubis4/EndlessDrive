@tool
extends RefCounted
## A menu-specific skin. Driving controls keep their existing sliced shapes,
## margins, positions, touch regions and sizing in hud_skin.gd.
const INK := Color("191d18")
const CREAM := Color("f5ecd7")
const GOLD := Color("e9ba68")
const MUTED := Color("b9baa9")

static func surface(state: String, primary := false, margin_source: StyleBox = null) -> StyleBoxFlat:
	var result := StyleBoxFlat.new()
	result.bg_color = Color("30372c")
	result.border_color = Color("70735e")
	if state == "hover":
		result.bg_color = Color("424b37")
		result.border_color = Color("b3ae87")
	elif state == "pressed":
		result.bg_color = Color("252b21")
		result.border_color = GOLD
	elif state == "disabled":
		result.bg_color = Color("252b23")
		result.border_color = Color("454c3e")
	if primary and state != "disabled":
		result.bg_color = {"normal": GOLD, "hover": Color("f4cf8a"), "pressed": Color("c99f59")}.get(state, GOLD)
		result.border_color = Color("f6d697")
	result.set_corner_radius_all(11)
	result.set_border_width_all(1)
	result.shadow_color = Color(0, 0, 0, 0.20)
	result.shadow_size = 5
	result.shadow_offset = Vector2(0, 3)
	if state == "focus":
		result.bg_color = Color.TRANSPARENT
		result.border_color = Color("ffe0a1")
		result.set_border_width_all(2)
		result.shadow_size = 0
	for side in [SIDE_LEFT, SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM]:
		result.set_content_margin(side, margin_source.get_content_margin(side) if margin_source != null else 12)
	return result

static func set_primary(button: Button, enabled: bool) -> void:
	button.set_meta("menu_primary", enabled)
	if button.get_meta("paper_menu", false):
		_paper_button(button, enabled)
		return
	for state: String in ["normal", "hover", "pressed", "disabled", "focus"]:
		var old := button.get_theme_stylebox(state)
		button.add_theme_stylebox_override(state, surface(state, enabled, old))
	for state: String in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color"]:
		button.add_theme_color_override(state, INK if enabled else CREAM)
	button.add_theme_color_override("font_disabled_color", Color("878e79"))
	button.add_theme_color_override("icon_disabled_color", Color("878e79"))
	button.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND

## Native paper actions; modal controls keep their olive skin.
static func _paper_button(button: Button, primary: bool) -> void:
	button.alignment = HORIZONTAL_ALIGNMENT_LEFT
	button.icon = preload("res://assets/ui/menu/next.svg") if primary else null
	button.icon_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	button.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	button.add_theme_font_size_override("font_size", 23)
	for state: String in ["normal", "hover", "pressed", "disabled", "focus"]:
		var style := StyleBoxFlat.new()
		style.bg_color = Color.TRANSPARENT
		style.border_color = Color("aaa08a")
		style.border_width_bottom = 1
		if primary and state != "disabled":
			style.bg_color = Color("e6ae4e")
			style.border_width_bottom = 0
		if state == "hover": style.bg_color = Color("f2c46c") if primary else Color("d7c6a36b")
		if state == "pressed": style.bg_color = Color("ce9740") if primary else Color("c1ac806b")
		if state == "focus":
			style.bg_color = Color.TRANSPARENT
			style.border_color = INK
			style.set_border_width_all(2)
		for side in [SIDE_LEFT, SIDE_RIGHT]: style.set_content_margin(side, 22)
		for side in [SIDE_TOP, SIDE_BOTTOM]: style.set_content_margin(side, 10)
		button.add_theme_stylebox_override(state, style)
	for state: String in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color"]:
		button.add_theme_color_override(state, INK)
	button.add_theme_color_override("font_disabled_color", Color("7b7568"))


static func polish(node: Node) -> void:
	if node is Button:
		var old := node.get_theme_stylebox("normal") as StyleBoxFlat
		var primary := bool(node.get_meta("menu_primary", old != null and old.bg_color.r > 0.55 and old.bg_color.g > 0.35))
		set_primary(node, primary)
	elif node is Panel or node is PanelContainer:
		var old := node.get_theme_stylebox("panel") as StyleBoxFlat
		if old != null and old.bg_color.a > 0.4:
			var panel := surface("normal", false, old)
			panel.bg_color = Color("20271ff5")
			panel.border_color = Color("626952")
			panel.set_corner_radius_all(16)
			node.add_theme_stylebox_override("panel", panel)
	if node is Control and node.theme != null:
		var theme: Theme = node.theme.duplicate()
		for type: String in ["Button", "OptionButton"]:
			for state: String in ["normal", "hover", "pressed", "disabled", "focus"]:
				theme.set_stylebox(state, type, surface(state, false, theme.get_stylebox(state, type)))
			theme.set_color("font_color", type, CREAM)
			theme.set_color("font_hover_color", type, CREAM)
			theme.set_color("font_disabled_color", type, Color("878e79"))
		var popup := surface("normal")
		popup.bg_color = Color("20271f")
		theme.set_stylebox("panel", "PopupMenu", popup)
		theme.set_stylebox("hover", "PopupMenu", surface("hover"))
		theme.set_color("font_color", "PopupMenu", CREAM)
		node.theme = theme
	for child: Node in node.get_children(): polish(child)
