@tool
class_name DriveMenu
extends CanvasLayer
## Native overlay: the caller keeps its 3D scene behind this CanvasLayer.
## show_menu distance is in METRES, matching DriveRun.distance.
## The caller owns pausing/input, saving, applying settings and handling actions.
## Nothing here starts a run, writes a save, adjusts audio or quits the process.

signal requested(action: String)
signal setting_changed(key: String, value: float)

const Icons = preload("res://scripts/item_icons.gd")
const Diagram = preload("res://scripts/car_diagram.gd")
const Locale = preload("res://scripts/localization.gd")
const DESIGN := Vector2(1440, 810)
const INK := Color("181e1c")
const PANEL := Color("252c28")
const CREAM := Color("f5ecd7")
const GOLD := Color("e9ba68")
const MUTED := Color("b4b8ac")
const RED := Color("ef947f")
const PART_INFO := {
	"engine": "Srdce auta. Olej a chladenie pomáhajú chrániť motor pred poškodením. Sleduj teplotu počas jazdy.",
	"tires": "Pneumatiky držia auto na ceste. Jazda mimo asfaltu ich opotrebúva rýchlejšie.",
	"brakes": "Brzdy rozhodujú o dĺžke zastavenia. Pred budovou spomaľ včas a úplne zastav.",
	"suspension": "Odpruženie chráni jazdu v nerovnom teréne. Nárazy a terén ho postupne poškodzujú.",
	"radiator": "Chladič pomáha regulovať teplotu motora. Dopĺňaj chladiacu vodu a sleduj prehrievanie.",
	"alternator": "Alternátor dobíja batériu pri bežiacom motore. Slabá batéria môže zabrániť štartu.",
}

var menu_open := false
var _root: Control
var _stage: Control
var _home: Control
var _modal: Control
var _modal_body: VBoxContainer
var _modal_title: Label
var _card_status: Label
var _distance_label: Label
var _journey_label: Label
var _continue_button: Button
var _new_button: Button
var _garage_button: Button
var _close_button: Button
var _volume_slider: HSlider
var _camera_slider: HSlider
var _language_select: OptionButton
var _setting_labels: Dictionary = {}
var _diagram: CarDiagram
var _part_title: Label
var _part_description: Label
var _part_status: Label
var _has_save := false
var _has_ride := false
var _distance := 0.0
var best_distance := 0.0
var _panel_kind := ""
var _volume := 0.8
var _camera := 1.0
var _parts: Dictionary = {}
var _panels: Dictionary = {}
var _upgrade_bank: Label
var _deposit_button: Button
var _upgrade_buttons: Dictionary = {}
var _confirmation_distance: Label
var _vehicle_button: Button
@export_enum("Menu", "Nastavenia", "Ako hrať", "Auto", "Nová jazda", "Ukončenie", "Vylepšenia") var editor_view: int = 0:
	set(value):
		editor_view = value
		if Engine.is_editor_hint() and is_inside_tree(): _preview_layout.call_deferred()


func _ready() -> void:
	_ensure_built()
	preload("res://scripts/hud_skin.gd").polish(_root)
	_setup_language_control()
	_layout()
	if Engine.is_editor_hint(): _preview_layout()


func show_menu(has_save: bool, distance: float) -> void:
	_ensure_built()
	_has_save = has_save
	_distance = maxf(0.0, distance) if is_finite(distance) else 0.0
	_has_ride = has_save or _distance > 0.0
	_continue_button.disabled = not has_save
	_continue_button.tooltip_text = tr("Vráť sa k svojej jazde.") if has_save else tr("Zatiaľ nemáš uloženú jazdu. Vyber Nová jazda.")
	_card_status.text = tr("ROZJAZDENÁ CESTA") if has_save else tr("ĎALŠIA VÝPRAVA")
	_distance_label.text = ("%.1f" % (_distance / 1000.0)).replace(".", ",") + "  km" if _has_ride else tr("Cesta čaká.")
	_journey_label.text = tr("Rekord %.1f km · Prejdi čo najďalej.\nHľadaj zásoby a udrž auto v chode.") % (best_distance / 1000.0)
	menu_open = true
	visible = true
	_root.show()
	_close_panel()
	_layout()


func show_panel(kind: String) -> void:
	_ensure_built()
	if kind in ["", "menu", "main"]:
		show_menu(_has_save, _distance)
		return
	if kind not in ["settings", "howto", "garage", "new", "quit", "upgrades"]:
		return
	if kind == "new" and not _has_ride:
		requested.emit("new")
		return
	menu_open = true
	visible = true
	_root.show()
	_panel_kind = kind
	# Hiding the home controls also removes them from keyboard focus traversal.
	_home.hide()
	_modal.show()
	if has_meta("authored_layout"):
		for key: String in _panels: _panels[key].visible = key == kind
		_modal_title.text = {"settings": tr("NASTAVENIA"), "howto": tr("AKO HRAŤ"), "garage": tr("GARÁŽ / TVOJE AUTO"), "new": tr("ZAČAŤ NOVÚ JAZDU?"), "quit": tr("UKONČIŤ HRU?"), "upgrades": tr("GARÁŽ / AUTO PRE NOVÚ VÝPRAVU")}[kind]
		set_settings(_volume, _camera)
		if kind == "garage":
			_select_part("engine")
			_vehicle_button.disabled = not _has_ride
		if kind == "new": _confirmation_distance.text = tr("Prejdená vzdialenosť: ") + ("%.1f km" % (_distance / 1000.0)).replace(".", ",")
		_close_button.grab_focus()
		_layout()
		return
	_volume_slider = null
	_camera_slider = null
	_setting_labels.clear()
	_diagram = null
	_part_title = null
	for child: Node in _modal_body.get_children():
		_modal_body.remove_child(child)
		child.queue_free()
	match kind:
		"settings": _build_settings()
		"howto": _build_howto()
		"garage": _build_garage()
		"new": _build_confirmation(false)
		"quit": _build_confirmation(true)
		"upgrades": _build_upgrades()
	_close_button.grab_focus()
	_layout()


func hide_menu() -> void:
	menu_open = false
	_panel_kind = ""
	if _root != null:
		var focus := _root.get_viewport().gui_get_focus_owner() if is_inside_tree() else null
		if focus != null and _root.is_ancestor_of(focus): focus.release_focus()
		_root.hide()
	visible = false


## Restore persisted values without emitting setting_changed or applying them.
func set_settings(volume: float, camera: float) -> void:
	_volume = clampf(volume, 0.0, 1.0) if is_finite(volume) else 0.8
	_camera = clampf(camera, 0.76, 1.3) if is_finite(camera) else 1.0
	if is_instance_valid(_volume_slider):
		_volume_slider.set_value_no_signal(_volume)
		(_setting_labels["volume"] as Label).text = _setting_text("volume", _volume)
	if is_instance_valid(_camera_slider):
		_camera_slider.set_value_no_signal(_camera)
		(_setting_labels["camera"] as Label).text = _setting_text("camera", _camera)
	set_language(Locale.language_index())


func set_language(index: int) -> void:
	if is_instance_valid(_language_select): _language_select.select(clampi(index, 0, 1))
	if _root != null and _has_ride:
		_journey_label.text = tr("Rekord %.1f km · Prejdi čo najďalej.\nHľadaj zásoby a udrž auto v chode.") % (best_distance / 1000.0)
	if _modal_title != null and not _panel_kind.is_empty():
		_modal_title.text = tr({"settings": tr("NASTAVENIA"), "howto": tr("AKO HRAŤ"), "garage": tr("GARÁŽ / TVOJE AUTO"), "new": tr("ZAČAŤ NOVÚ JAZDU?"), "quit": tr("UKONČIŤ HRU?"), "upgrades": tr("GARÁŽ / AUTO PRE NOVÚ VÝPRAVU")}.get(_panel_kind, ""))
	if _diagram != null and _panel_kind == "garage": _select_part(_diagram.selected_slot)


func _setup_language_control() -> void:
	var settings: Node = _panels.get("settings", _modal_body)
	_language_select = settings.get_node_or_null("LanguageRow/Language")
	if _language_select == null:
		var row := _row(16)
		row.name = "LanguageRow"
		var label := _label(tr("JAZYK / LANGUAGE"), 16, GOLD)
		label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(label)
		_language_select = OptionButton.new()
		_language_select.name = "Language"
		_language_select.custom_minimum_size = Vector2(240, 42)
		_language_select.add_item("Slovenčina", 0)
		_language_select.add_item("English", 1)
		row.add_child(_language_select)
		settings.add_child(row)
		settings.move_child(row, mini(1, settings.get_child_count() - 1))
	_language_select.select(Locale.language_index())
	if not _language_select.item_selected.is_connected(_language_changed):
		_language_select.item_selected.connect(_language_changed)


func _language_changed(index: int) -> void:
	setting_changed.emit("language", float(index))


## Optional live data for the garage preview. Missing values are never invented.
func set_parts(parts: Dictionary) -> void:
	_parts = parts.duplicate(true)
	if is_instance_valid(_diagram):
		_diagram.update_parts(_parts, _diagram.selected_slot)
		_select_part(_diagram.selected_slot)


func _ensure_built() -> void:
	if _root != null: return
	process_mode = Node.PROCESS_MODE_ALWAYS
	layer = 20
	if has_meta("authored_layout"):
		AuthoredLayout.bind_nodes(self, self)
		_root.resized.connect(_layout)
		return
	_root = Control.new()
	_root.name = "DriveMenuRoot"
	_root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_root.mouse_filter = Control.MOUSE_FILTER_STOP
	_root.theme = _theme()
	add_child(_root)
	_root.resized.connect(_layout)
	var dim := ColorRect.new()
	dim.color = Color(0.035, 0.045, 0.038, 0.18)
	_full_decor(dim)
	_add_veil(PackedFloat32Array([0.0, 0.36, 0.57, 1.0]), PackedColorArray([Color(0.04, 0.06, 0.05, 0.88), Color(0.04, 0.06, 0.05, 0.42), Color(0.04, 0.06, 0.05, 0.02), Color(0.04, 0.06, 0.05, 0.48)]), false)
	_add_veil(PackedFloat32Array([0.0, 0.35, 0.68, 1.0]), PackedColorArray([Color(0.04, 0.06, 0.05, 0.28), Color.TRANSPARENT, Color.TRANSPARENT, Color(0.04, 0.06, 0.05, 0.83)]), true)
	_stage = Control.new()
	_stage.name = "LandscapeLayout"
	_stage.size = DESIGN
	_stage.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(_stage)
	_build_home()
	_build_modal_shell()
	_root.hide()
	visible = false


func _layout() -> void:
	if _stage == null or not is_inside_tree(): return
	var bounds := _root.size
	if bounds.x <= 1.0 or bounds.y <= 1.0:
		bounds = get_viewport().get_visible_rect().size
	var factor := minf(bounds.x / DESIGN.x, bounds.y / DESIGN.y)
	_stage.scale = Vector2.ONE * maxf(factor, 0.01)
	_stage.position = (bounds - DESIGN * factor) * 0.5


func _full_decor(control: Control) -> void:
	_root.add_child(control)
	control.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	control.mouse_filter = Control.MOUSE_FILTER_IGNORE


func _add_veil(offsets: PackedFloat32Array, colors: PackedColorArray, vertical: bool) -> void:
	var gradient := Gradient.new()
	gradient.offsets = offsets
	gradient.colors = colors
	var texture := GradientTexture2D.new()
	texture.gradient = gradient
	texture.width = 16 if vertical else 512
	texture.height = 512 if vertical else 16
	texture.fill_from = Vector2.ZERO
	texture.fill_to = Vector2(0, 1) if vertical else Vector2(1, 0)
	var rect := TextureRect.new()
	rect.texture = texture
	rect.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_full_decor(rect)


func _build_home() -> void:
	_home = Control.new()
	_home.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_place(_stage, _home, Vector2.ZERO, DESIGN)
	_place(_home, _label(tr("OTVORENÁ CESTA  /  TVOJ PRÍBEH"), 15, GOLD), Vector2(64, 90), Vector2(540, 26))
	var line := ColorRect.new()
	line.color = GOLD
	line.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_place(_home, line, Vector2(66, 138), Vector2(62, 3))
	var title := _label("ENDLESS\nDRIVE", 112)
	var display_font := SystemFont.new()
	display_font.font_names = PackedStringArray(["Bahnschrift", "Arial", "Noto Sans"])
	display_font.font_weight = 800
	title.add_theme_font_override("font", display_font)
	title.add_theme_constant_override("line_spacing", -17)
	title.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.35))
	title.add_theme_constant_override("shadow_offset_x", 3)
	title.add_theme_constant_override("shadow_offset_y", 5)
	_place(_home, title, Vector2(58, 162), Vector2(630, 280))
	_place(_home, _label(tr("UDRŽ MOTOR PRI ŽIVOTE."), 19, GOLD), Vector2(66, 456), Vector2(540, 30))
	_place(_home, _label(tr("Jedno auto. Nekonečno možností.\nKaždý kilometer si musíš zaslúžiť."), 19, CREAM), Vector2(66, 512), Vector2(500, 66))
	var stops := _row(12)
	for entry: Array in [["gas", tr("ZÁSOBY")], ["repair", tr("OPRAVY")], ["home", tr("OBJAVOVANIE")]]:
		var item := _row(7)
		item.add_child(_icon(entry[0], 24))
		item.add_child(_label(entry[1], 12, MUTED))
		stops.add_child(item)
	_place(_home, stops, Vector2(66, 669), Vector2(560, 38))
	_place(_home, _label(tr("CESTA NEMÁ KONIEC. LEN ĎALŠIU ZASTÁVKU."), 12, MUTED), Vector2(66, 741), Vector2(700, 24))
	var card := Panel.new()
	card.add_theme_stylebox_override("panel", _style(Color(0.085, 0.11, 0.09, 0.94), Color("626457"), 16))
	_place(_home, card, Vector2(948, 90), Vector2(436, 630))
	var content := Control.new()
	_place(card, content, Vector2(28, 26), Vector2(380, 580))
	_card_status = _label(tr("ĎALŠIA VÝPRAVA"), 13, GOLD)
	_place(content, _card_status, Vector2.ZERO, Vector2(380, 22))
	_place(content, _label(tr("Cesta ťa volá."), 29), Vector2(0, 30), Vector2(380, 42))
	_distance_label = _label(tr("Cesta čaká."), 46)
	_place(content, _distance_label, Vector2(0, 80), Vector2(380, 65))
	_journey_label = _label(tr("Nájdi zásoby. Udrž motor pri živote."), 16, MUTED)
	_place(content, _journey_label, Vector2(0, 149), Vector2(380, 30))
	_continue_button = _button(tr("POKRAČOVAŤ"), _request.bind("continue"), true)
	_continue_button.name = "Continue"
	_continue_button.disabled = true
	_place(content, _continue_button, Vector2(0, 200), Vector2(380, 60))
	_new_button = _button(tr("NOVÁ JAZDA"), show_panel.bind("new"))
	_place(content, _new_button, Vector2(0, 272), Vector2(380, 52))
	_garage_button = _button(tr("GARÁŽ / VYLEPŠENIA"), _request.bind("upgrades"))
	_garage_button.icon = Icons.texture("workshop")
	_place(content, _garage_button, Vector2(0, 336), Vector2(380, 52))
	_place(content, _button(tr("NASTAVENIA"), show_panel.bind("settings")), Vector2(0, 400), Vector2(184, 48))
	_place(content, _button(tr("AKO HRAŤ"), show_panel.bind("howto")), Vector2(196, 400), Vector2(184, 48))
	_place(content, _button(tr("UKONČIŤ HRU"), show_panel.bind("quit")), Vector2(0, 460), Vector2(380, 42))
	var hint := _label(tr("Pomalá jazda. Pozorné rozhodnutia.\nZásoby nikdy nie sú samozrejmosť."), 13, MUTED)
	hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_place(content, hint, Vector2(0, 524), Vector2(380, 48))


func _build_modal_shell() -> void:
	_modal = Control.new()
	_place(_stage, _modal, Vector2.ZERO, DESIGN)
	var shade := ColorRect.new()
	shade.color = Color(0.025, 0.035, 0.028, 0.58)
	_place(_modal, shade, Vector2.ZERO, DESIGN)
	var panel := PanelContainer.new()
	panel.add_theme_stylebox_override("panel", _style(Color("1d2521"), Color("696b59"), 18, 30))
	_place(_modal, panel, Vector2(310, 89), Vector2(820, 632))
	var stack := _column(18)
	panel.add_child(stack)
	var header := _row(12)
	stack.add_child(header)
	_modal_title = _label("", 30)
	_modal_title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(_modal_title)
	_close_button = _button(tr("SPÄŤ"), _close_panel)
	_close_button.custom_minimum_size = Vector2(112, 44)
	header.add_child(_close_button)
	var rule := HSeparator.new()
	rule.add_theme_stylebox_override("separator", _style(Color("535b4f"), Color.TRANSPARENT, 0))
	rule.custom_minimum_size.y = 1
	stack.add_child(rule)
	_modal_body = _column(16)
	_modal_body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	stack.add_child(_modal_body)
	_modal.hide()


func _build_settings() -> void:
	_modal_title.text = tr("NASTAVENIA")
	_modal_body.add_child(_paragraph(tr("Nalaď si cestu podľa seba. Zmeny sa prejavia počas jazdy."), 18))
	_volume_slider = _setting_row(tr("HLASITOSŤ"), tr("Zvuk motora a okolia"), "volume", 0.0, 1.0, _volume)
	_camera_slider = _setting_row(tr("ODSTUP KAMERY"), tr("Bližšie k autu alebo širší výhľad na cestu"), "camera", 0.76, 1.3, _camera)
	var note := _paragraph(tr("OVLÁDANIE\nWASD / šípky: jazda   ·   E: motor   ·   Esc: ponuka\nNa dotykovej obrazovke použi pedále a tlačidlá."), 17)
	_modal_body.add_child(note)
	_modal_body.add_child(_spacer())
	var reset := _button(tr("OBNOVIŤ PREDVOLENÉ"), _reset_settings)
	_modal_body.add_child(reset)
	_setup_language_control()


func _setting_row(title: String, description: String, key: String, minimum: float, maximum: float, value: float) -> HSlider:
	var box := _column(6)
	_modal_body.add_child(box)
	var header := _row(10)
	box.add_child(header)
	var label := _label(title, 16, GOLD)
	label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(label)
	var number := _label("", 19)
	_setting_labels[key] = number
	header.add_child(number)
	box.add_child(_label(description, 15, MUTED))
	var slider := HSlider.new()
	slider.name = "Volume" if key == "volume" else "Camera"
	slider.min_value = minimum
	slider.max_value = maximum
	slider.step = 0.01
	slider.value = value
	slider.custom_minimum_size.y = 38
	slider.focus_mode = Control.FOCUS_ALL
	slider.tooltip_text = title + ": posuň jazdec alebo použi šípky."
	number.text = _setting_text(key, value)
	slider.value_changed.connect(_setting_changed.bind(key), CONNECT_PERSIST)
	box.add_child(slider)
	return slider


func _setting_text(key: String, value: float) -> String:
	if key == "volume": return "%d %%" % roundi(value * 100.0)
	var formatted := "%.2f ×" % value
	return formatted if Locale.language_index() == 1 else formatted.replace(".", ",")


func _build_howto() -> void:
	_modal_title.text = tr("AKO HRAŤ")
	_modal_body.add_child(_paragraph(tr("Tvoj cieľ je jednoduchý: dostať sa ďalej a udržať auto v chode."), 19))
	for entry: Array in [
		["engine", tr("01  VYRAZ NA CESTU"), tr("E naštartuje motor. W / ↑ pridáva plyn, S / ↓ brzdí a po zastavení cúva. A / D alebo ← / → zatáčajú.")],
		["gas", tr("02  ZASTAV PRI ZÁSOBÁCH"), tr("Pri budove zastav a stlač F. Označ zásoby, nastav množstvo a vezmi iba to, čo odvezieš. Kufor má obmedzenú kapacitu.")],
		["repair", tr("03  STARAJ SA O AUTO"), tr("Tab otvorí vozidlo a kufor. Dopĺňaj palivo, olej a vodu; opravuj diely za šrot alebo ich vymeň za diely z kufra.")],
	]:
		var row := _row(18)
		_modal_body.add_child(row)
		row.add_child(_icon(entry[0], 42))
		var text := _column(5)
		text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(text)
		text.add_child(_label(entry[1], 16, GOLD))
		text.add_child(_paragraph(entry[2], 17))
	_modal_body.add_child(_spacer())
	_modal_body.add_child(_paragraph(tr("POHON A NAVIJAK\nV: 4×2 / 4×4   ·   B: uzávierka   ·   G: pripojiť lano   ·   H: navíjať\nL: vypnuté / stretávacie / diaľkové   ·   K: diaľkové\nMedzerník: ručná brzda   ·   C: kamera   ·   R: tempomat"), 16))


func _build_garage() -> void:
	_modal_title.text = tr("GARÁŽ / TVOJE AUTO")
	_modal_body.add_child(_paragraph(tr("Vyber diel na schéme a spoznaj, čo drží tvoje auto v pohybe."), 18))
	var row := _row(28)
	_modal_body.add_child(row)
	_diagram = Diagram.new()
	_diagram.custom_minimum_size = Vector2(320, 320)
	_diagram.update_parts(_parts, "engine")
	_diagram.selected.connect(_select_part, CONNECT_PERSIST)
	row.add_child(_diagram)
	var details := _column(13)
	details.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(details)
	details.add_child(_label(tr("VYBRANÝ DIEL"), 13, GOLD))
	_part_title = _label("", 27)
	details.add_child(_part_title)
	_part_status = _label("", 17, GOLD)
	details.add_child(_part_status)
	_part_description = _paragraph("", 18)
	details.add_child(_part_description)
	details.add_child(_spacer())
	details.add_child(_paragraph(tr("Náhradné diely nájdeš v budovách. Pôvodný diel po výmene zostáva v kufri."), 15))
	_select_part("engine")
	_modal_body.add_child(_spacer())
	var open_vehicle := _button(tr("OTVORIŤ VOZIDLO A KUFOR"), _request.bind("garage"), true)
	_vehicle_button = open_vehicle
	open_vehicle.disabled = not _has_ride
	open_vehicle.tooltip_text = tr("Začni novú jazdu a potom otvor vozidlo.") if not _has_ride else tr("Zobraz opravy, zásoby a náhradné diely aktuálnej jazdy.")
	_modal_body.add_child(open_vehicle)


func _select_part(slot: String) -> void:
	if _part_title == null: return
	_part_title.text = Diagram.NAMES.get(slot, tr("Diel"))
	_part_description.text = PART_INFO.get(slot, "")
	var value: Variant = _parts.get(slot)
	if (value is float or value is int) and is_finite(float(value)):
		var condition := clampf(float(value), 0.0, 1.0)
		var state := tr("kritický") if condition < 0.35 else (tr("opotrebovaný") if condition < 0.7 else tr("dobrý"))
		_part_status.text = tr("Stav: %d %% · %s") % [roundi(condition * 100.0), state]
		_part_status.add_theme_color_override("font_color", RED if condition < 0.35 else (GOLD if condition < 0.7 else Diagram.GOOD))
	else:
		_part_status.text = tr("Stav: bez údajov")
		_part_status.add_theme_color_override("font_color", MUTED)


func _build_confirmation(quitting: bool) -> void:
	_modal_title.text = tr("UKONČIŤ HRU?") if quitting else tr("ZAČAŤ NOVÚ JAZDU?")
	_modal_body.add_child(_spacer())
	_modal_body.add_child(_icon("home" if quitting else "engine", 64))
	_modal_body.add_child(_paragraph(tr("Chceš sa rozlúčiť s cestou a ukončiť hru?") if quitting else tr("Nová jazda nahradí aktuálnu aj uloženú cestu. Doterajší postup tejto jazdy stratíš."), 24))
	if not quitting:
		_confirmation_distance = _paragraph(tr("Prejdená vzdialenosť: ") + ("%.1f km" % (_distance / 1000.0)).replace(".", ","), 18)
		_modal_body.add_child(_confirmation_distance)
	_modal_body.add_child(_spacer())
	var row := _row(14)
	_modal_body.add_child(row)
	var cancel := _button(tr("ZOSTAŤ") if quitting else tr("PONECHAŤ JAZDU"), _close_panel)
	cancel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(cancel)
	var confirm := _button(tr("UKONČIŤ HRU") if quitting else tr("NAHRADIŤ JAZDU"), _request.bind("quit" if quitting else "new"), true)
	confirm.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	if not quitting:
		confirm.add_theme_stylebox_override("normal", _style(RED, RED, 8, 12))
	row.add_child(confirm)


func _close_panel() -> void:
	_panel_kind = ""
	_modal.hide()
	_home.show()
	if menu_open and is_inside_tree():
		if _has_save: _continue_button.grab_focus()
		else: _new_button.grab_focus()


func _input(event: InputEvent) -> void:
	if Engine.is_editor_hint(): return
	if not menu_open: return
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
		if not _panel_kind.is_empty(): _close_panel()
		elif _has_save: requested.emit("continue")
		else: show_panel("quit")
		get_viewport().set_input_as_handled()


func _theme() -> Theme:
	var result := Theme.new()
	result.default_font_size = 18
	result.set_color("font_color", "Label", CREAM)
	for state: String in ["normal", "hover", "pressed", "disabled", "focus"]:
		var fill := PANEL
		var border := Color("596151")
		if state == "hover": fill = Color("394236")
		if state in ["pressed", "focus"]: border = GOLD
		if state == "pressed": fill = Color("4d4935")
		if state == "disabled": fill = Color("202720")
		var style := _style(fill, border, 8, 12)
		if state == "focus":
			style.bg_color = Color.TRANSPARENT
			style.set_border_width_all(2)
		result.set_stylebox(state, "Button", style)
	result.set_color("font_color", "Button", CREAM)
	result.set_color("font_hover_color", "Button", Color.WHITE)
	result.set_color("font_pressed_color", "Button", GOLD)
	result.set_color("font_disabled_color", "Button", Color("818b7e"))
	result.set_constant("icon_max_width", "Button", 26)
	result.set_constant("h_separation", "Button", 12)
	for entry: Array in [["slider", Color("555f51")], ["grabber_area", GOLD], ["grabber_area_highlight", CREAM]]:
		var track := _style(entry[1], Color.TRANSPARENT, 3)
		track.content_margin_top = 3
		track.content_margin_bottom = 3
		result.set_stylebox(entry[0], "HSlider", track)
	var thumb_image := Image.new()
	thumb_image.load_svg_from_string('<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><circle cx="12" cy="12" r="9" fill="#e9ba68" stroke="#f5ecd7" stroke-width="2"/></svg>', 2.0)
	# Rasterize at double size, then antialias into a 24 logical pixel grabber.
	thumb_image.resize(24, 24, Image.INTERPOLATE_LANCZOS)
	var thumb := ImageTexture.create_from_image(thumb_image)
	result.set_icon("grabber", "HSlider", thumb)
	result.set_icon("grabber_highlight", "HSlider", thumb)
	result.set_stylebox("focus", "HSlider", _style(Color.TRANSPARENT, GOLD, 6))
	result.set_stylebox("panel", "TooltipPanel", _style(INK, GOLD, 6, 10))
	result.set_color("font_color", "TooltipLabel", CREAM)
	return result


func _style(fill: Color, border: Color, radius: int, margin: float = 0) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = fill
	style.border_color = border
	style.set_border_width_all(1)
	style.set_corner_radius_all(radius)
	style.content_margin_left = margin
	style.content_margin_right = margin
	style.content_margin_top = margin
	style.content_margin_bottom = margin
	return style


func _button(text: String, callback: Callable, primary: bool = false) -> Button:
	var button := Button.new()
	button.text = tr(text)
	button.custom_minimum_size.y = 48
	button.focus_mode = Control.FOCUS_ALL
	button.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	button.expand_icon = true
	button.pressed.connect(callback, CONNECT_PERSIST)
	if primary:
		button.add_theme_stylebox_override("normal", _style(GOLD, Color("ffdc99"), 8, 12))
		button.add_theme_stylebox_override("hover", _style(Color("f7ce87"), CREAM, 8, 12))
		button.add_theme_stylebox_override("pressed", _style(Color("c69a55"), GOLD, 8, 12))
		for state: String in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color"]:
			button.add_theme_color_override(state, INK)
	return button


func _request(action: String) -> void:
	requested.emit(action)


func _reset_settings() -> void:
	_volume_slider.value = 0.8
	_camera_slider.value = 1.0


func _setting_changed(current: float, key: String) -> void:
	(_setting_labels[key] as Label).text = _setting_text(key, current)
	if key == "volume": _volume = current
	else: _camera = current
	setting_changed.emit(key, current)


func _build_upgrades() -> void:
	_upgrade_bank = _paragraph(tr("ŠROT V GARÁŽI: 0\nVylepšenia sa použijú pri NOVEJ jazde. Aktuálne auto si zachová svoj stav."), 18)
	_modal_body.add_child(_upgrade_bank)
	_deposit_button = _button(tr("ODLOŽIŤ NAJDENÝ ŠROT Z JAZDY"), _request.bind("deposit"))
	_modal_body.add_child(_deposit_button)
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", 12)
	grid.add_theme_constant_override("v_separation", 12)
	_modal_body.add_child(grid)
	for slot: String in DriveRun.PART_NAMES:
		var button := _button(DriveRun.PART_NAMES[slot] + tr(" · ÚROVEŇ 0/3\n10 ŠROTU · LEPŠÍ ŠTART"), _request.bind("upgrade:" + slot))
		button.icon = Icons.texture(slot)
		button.custom_minimum_size = Vector2(340, 74)
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		grid.add_child(button)
		_upgrade_buttons[slot] = button


func _preview_layout() -> void:
	if _root == null or _panels.is_empty(): return
	_root.show()
	_home.visible = editor_view == 0
	_modal.visible = editor_view != 0
	var kind: String = ["", "settings", "howto", "garage", "new", "quit", "upgrades"][editor_view]
	for key: String in _panels: _panels[key].visible = key == kind
	_modal_title.text = {"": "", "settings": tr("NASTAVENIA"), "howto": tr("AKO HRAŤ"), "garage": tr("GARÁŽ / TVOJE AUTO"), "new": tr("ZAČAŤ NOVÚ JAZDU?"), "quit": tr("UKONČIŤ HRU?"), "upgrades": tr("GARÁŽ / AUTO PRE NOVÚ VÝPRAVU")}[kind]


func _label(text: String, font_size: int, color: Color = CREAM) -> Label:
	var label := Label.new()
	label.text = tr(text)
	label.add_theme_font_size_override("font_size", font_size)
	label.add_theme_color_override("font_color", color)
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return label


func _paragraph(text: String, font_size: int) -> Label:
	var label := _label(text, font_size, MUTED)
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return label


func _icon(key: String, pixels: float) -> TextureRect:
	var icon := TextureRect.new()
	icon.texture = Icons.texture(key)
	icon.custom_minimum_size = Vector2.ONE * pixels
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	icon.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	icon.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return icon


func _row(spacing: int) -> HBoxContainer:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", spacing)
	return row


func _column(spacing: int) -> VBoxContainer:
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", spacing)
	return column


func _spacer() -> Control:
	var spacer := Control.new()
	spacer.size_flags_vertical = Control.SIZE_EXPAND_FILL
	spacer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return spacer


func _place(parent: Node, control: Control, point: Vector2, dimensions: Vector2) -> void:
	# Give wrapped labels their width before entering the tree, avoiding a stale
	# one-character-wide minimum height on the very first layout pass.
	control.position = point
	control.size = dimensions
	parent.add_child(control)
