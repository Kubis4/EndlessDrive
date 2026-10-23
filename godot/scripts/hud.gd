@tool
class_name DriveHUD
extends CanvasLayer
## Responsive native Godot HUD and transactional, explicit loot selection.

signal requested(action: String)
signal touch_changed(action: String, pressed: bool, source: int)

const INK := Color("191d1d")
const PANEL := Color("242928")
const CREAM := Color("f2eddf")
const MUTED := Color("aaa99e")
const GOLD := Color("e5b875")
const GREEN := Color("9cbea1")
const RED := Color("e28a77")
var run: DriveRun
var vehicle: DriveVehicle
var world: DriveWorld
var _root: Control
var _speed: Label
var _distance: Label
var _region: Label
var _road_info: Label
var _junction_info: Label
var _junction_panel: PanelContainer
var _engine: Label
var _cruise_status: Label
var _cruise_button: Button
var _engine_button: Button
var _objective: Label
var _message: Label
var _toast_panel: PanelContainer
var _search: Button
var _near_panel: PanelContainer
var _near_title: Label
var _service: PanelContainer
var _service_tabs: TabContainer
var _pause: PanelContainer
var _loot: PanelContainer
var _shade: ColorRect
var _part_buttons: Dictionary = {}
var _part_bars: Dictionary = {}
var _stats: Dictionary = {}
var _fluid_info: Dictionary = {}
var _cargo_info: Label
var _cargo_list: VBoxContainer
var _loot_list: VBoxContainer
var _loot_title: Label
var _loot_capacity: Label
var _loot_weight: ProgressBar
var _loot_slots: ProgressBar
var _loot_selection: Label
var _loot_take: Button
var _loot_rows: Dictionary = {}
var _loot_building := -1
var _loot_building_title := ""
var _loot_filter := "all"
var _loot_detail: Label
var _loot_diagram: CarDiagram
var _car_diagram: CarDiagram
var _component_info: Label
var _stop_service_info: Label
var _next_stop: Label
var _weather_panel: PanelContainer
var _selected_part := "engine"
var _install_confirmation: ConfirmationDialog
var _pending_install := ""
var _shop_buttons: Dictionary = {}
var _paint_buttons: Dictionary = {}
var weather: DriveWeather
var _pedals: Dictionary = {}
var _active_fingers: Dictionary = {}
var _message_time := 0.0
var _confirmation: ConfirmationDialog
var modal_open := false
var _modal_kind := ""
var _toast_rest: Dictionary = {}
@export_enum("Jazda", "Loot", "Vozidlo", "Pauza") var editor_view: int = 0:
	set(value):
		editor_view = value
		if Engine.is_editor_hint() and is_inside_tree(): _preview_layout.call_deferred()


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	if has_meta("authored_layout"):
		AuthoredLayout.bind_nodes(self, self)
		_remember_toast_layout()
		if Engine.is_editor_hint():
			_preview_layout()
			return
		show_modal("")
		return
	_build_layout()


func _build_layout() -> void:
	_root = Control.new()
	_root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_root)
	_setup_theme()
	_build_driving()
	_build_modals()
	_toast_panel = _panel(INK)
	_place(_toast_panel, Vector2(-350, 236), Vector2(700, 46), Vector2(0.5, 0))
	_message = _label("", 17, CREAM)
	_message.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_message.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_toast_panel.add_child(_message)
	_confirmation = ConfirmationDialog.new()
	_confirmation.title = "Nová cesta"
	_confirmation.dialog_text = "Začať novú cestu a nahradiť uložený postup v Godot verzii?"
	_confirmation.ok_button_text = "Začať cestu"
	_confirmation.cancel_button_text = "Späť"
	_confirmation.confirmed.connect(_dispatch.bind("new"), CONNECT_PERSIST)
	_root.add_child(_confirmation)
	_install_confirmation = ConfirmationDialog.new()
	_install_confirmation.title = "Potvrdiť výmenu dielu"
	_install_confirmation.ok_button_text = "Vymeniť diel"
	_install_confirmation.cancel_button_text = "Ponechať pôvodný"
	_install_confirmation.confirmed.connect(_confirm_install, CONNECT_PERSIST)
	_root.add_child(_install_confirmation)
	_remember_toast_layout()
	show_modal("")


func _setup_theme() -> void:
	var theme := Theme.new()
	theme.default_font_size = 17
	theme.set_color("font_color", "Label", CREAM)
	for state: String in ["normal", "hover", "pressed", "focus", "disabled"]:
		var color := PANEL.lightened(0.08) if state == "hover" else PANEL
		if state == "pressed": color = Color("695538")
		if state == "disabled": color = Color("242726")
		var style := _style(color, 12, 8)
		style.set_border_width_all(1)
		style.border_color = GOLD if state in ["focus", "pressed"] else Color("424943")
		theme.set_stylebox(state, "Button", style)
	theme.set_color("font_color", "Button", CREAM)
	theme.set_color("font_hover_color", "Button", Color.WHITE)
	theme.set_color("font_pressed_color", "Button", GOLD)
	theme.set_color("font_disabled_color", "Button", Color("777d76"))
	theme.set_stylebox("normal", "LineEdit", _style(INK, 8, 7))
	theme.set_color("font_color", "LineEdit", CREAM)
	theme.set_color("font_color", "CheckBox", CREAM)
	theme.set_icon("unchecked", "CheckBox", load("res://assets/ui/unchecked.svg"))
	theme.set_icon("checked", "CheckBox", load("res://assets/ui/checked.svg"))
	theme.set_color("font_color", "TabContainer", CREAM)
	theme.set_stylebox("panel", "TabContainer", _style(INK, 10, 10))
	theme.set_stylebox("tab_selected", "TabContainer", _style(PANEL, 20, 10))
	theme.set_stylebox("tab_unselected", "TabContainer", _style(INK, 20, 10))
	_root.theme = theme


func _build_driving() -> void:
	var header := _panel(INK)
	_root.add_child(header)
	header.anchor_right = 1.0
	header.offset_left = 24
	header.offset_right = -24
	header.offset_top = 20
	header.offset_bottom = 84
	var row := _row(24)
	header.add_child(row)
	var brand := _label("ENDLESS / DRIVE", 22, GOLD)
	brand.custom_minimum_size.x = 260
	row.add_child(brand)
	var route := _column(2)
	route.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(route)
	_region = _label("PRAŠNÁ KOTLINA", 12, MUTED)
	_distance = _label("0.00 km", 20)
	route.add_child(_region)
	route.add_child(_distance)
	for entry: Array in [["L  SVETLÁ", "lights"], ["C  KAMERA", "camera"], ["Ⅱ", "pause"]]:
		var button := _button(entry[0], entry[1])
		button.custom_minimum_size = Vector2(54, 38)
		button.add_theme_font_size_override("font_size", 14)
		row.add_child(button)
	var status := _panel(INK)
	_place(status, Vector2(24, 104), Vector2(236, 228))
	var stats := _column(9)
	status.add_child(stats)
	stats.add_child(_label("VOZIDLO / ZÁSOBY", 12, MUTED))
	for entry: Array in [["fuel", "Palivo"], ["oil", "Olej"], ["coolant", "Chladenie"], ["battery", "Batéria"], ["temperature", "Teplota"]]:
		var item := _column(3)
		var labels := _row(4)
		var label := _label(entry[1], 14, MUTED)
		label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		labels.add_child(label)
		var value := _label("", 14)
		labels.add_child(value)
		item.add_child(labels)
		var bar := _bar()
		item.add_child(bar)
		stats.add_child(item)
		_stats[entry[0]] = {"value": value, "bar": bar}
	_objective = _label("PRVÁ ZASTÁVKA\nNájdi zásoby pri púštnej dielni.", 15, CREAM)
	_place(_objective, Vector2(30, 348), Vector2(260, 60))
	_road_info = _label("PIESOK", 15, CREAM)
	var road_panel := _panel(INK)
	_place(road_panel, Vector2(24, 420), Vector2(236, 76))
	road_panel.add_child(_road_info)
	_road_info.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_junction_panel = _panel(INK)
	_place(_junction_panel, Vector2(-186, 104), Vector2(372, 110), Vector2(1, 0))
	_junction_panel.offset_left = -396
	_junction_panel.offset_right = -24
	_junction_info = _label("", 16, GOLD)
	_junction_info.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_junction_panel.add_child(_junction_info)
	var dash := _panel(INK)
	_place(dash, Vector2(-178, -134), Vector2(356, 108), Vector2(0.5, 1))
	var dashboard := _row(18)
	dash.add_child(dashboard)
	_speed = _label("000", 48)
	_speed.custom_minimum_size.x = 108
	dashboard.add_child(_speed)
	var info := _column(5)
	dashboard.add_child(info)
	info.add_child(_label("KM/H", 11, MUTED))
	_engine = _label("MOTOR VYPNUTÝ", 13)
	info.add_child(_engine)
	_cruise_status = _label("Tempomat vypnutý", 12, MUTED)
	info.add_child(_cruise_status)
	_pedal("◀  A", "left", Vector2(24, -112), Vector2(0, 1), Vector2(96, 88))
	_pedal("D  ▶", "right", Vector2(128, -112), Vector2(0, 1), Vector2(96, 88))
	_pedal("BRZDA\nS / VZAD", "brake", Vector2(-240, -118), Vector2(1, 1), Vector2(100, 94))
	_pedal("PLYN\nW", "throttle", Vector2(-132, -142), Vector2(1, 1), Vector2(108, 118))
	_engine_button = _button("E  NAŠTARTOVAŤ", "engine")
	_place(_engine_button, Vector2(24, -164), Vector2(200, 42), Vector2(0, 1))
	var service_button := _button("TAB  VOZIDLO / KUFOR", "service")
	service_button.add_theme_font_size_override("font_size", 14)
	_place(service_button, Vector2(24, -214), Vector2(200, 42), Vector2(0, 1))
	_cruise_button = _button("R  TEMPOMAT", "cruise")
	_cruise_button.toggle_mode = true
	_cruise_button.tooltip_text = "Zapamätá držaný plyn. Brzda, pauza alebo vypnutie motora ho zruší. Spotreba pri automatickom plyne je vyššia."
	_place(_cruise_button, Vector2(-240, -204), Vector2(216, 44), Vector2(1, 1))
	_near_panel = _panel(INK)
	_place(_near_panel, Vector2(-250, -244), Vector2(500, 84), Vector2(0.5, 1))
	var near_row := _row(16)
	_near_panel.add_child(near_row)
	_near_title = _label("", 16)
	_near_title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	near_row.add_child(_near_title)
	_search = _button("F  PREHĽADAŤ", "search")
	_search.custom_minimum_size.y = 42
	near_row.add_child(_search)
	_near_panel.visible = false
	_next_stop = _label("", 14, MUTED)
	_weather_panel = _panel(INK)
	_place(_weather_panel, Vector2(24, 344), Vector2(236, 68))
	_weather_panel.add_child(_next_stop)
	_next_stop.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART


func _build_modals() -> void:
	_shade = ColorRect.new()
	_shade.color = Color(0.025, 0.035, 0.03, 0.80)
	_shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_root.add_child(_shade)
	_build_loot()
	_build_service()
	_pause = _panel(INK)
	_place(_pause, Vector2(-230, -210), Vector2(460, 420), Vector2(0.5, 0.5))
	var menu := _column(16)
	_pause.add_child(menu)
	menu.add_child(_label("CESTA POČKÁ.", 30, GOLD))
	menu.add_child(_label("Postup sa pri pauze automaticky uloží.", 16, MUTED))
	for entry: Array in [["POKRAČOVAŤ", "resume"], ["ULOŽIŤ CESTU", "save"], ["HLAVNÉ MENU", "menu"], ["ULOŽIŤ A UKONČIŤ", "quit"]]:
		var button := _button(entry[0], entry[1])
		button.custom_minimum_size.y = 48
		menu.add_child(button)


func _build_loot() -> void:
	_loot = _panel(INK)
	_place(_loot, Vector2(-620, -345), Vector2(1240, 690), Vector2(0.5, 0.5))
	var contents := _column(12)
	_loot.add_child(contents)
	var title_row := _row(20)
	var titles := _column(3)
	titles.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	titles.add_child(_label("PREHĽADÁVANIE / ZÁSOBY", 12, GOLD))
	_loot_title = _label("Púštna dielňa", 27)
	titles.add_child(_loot_title)
	title_row.add_child(titles)
	title_row.add_child(_button("ZAVRIEŤ  ESC", "resume"))
	contents.add_child(title_row)
	var categories := _row(8)
	for entry: Array in [["VŠETKO", "all"], ["KVAPALINY", "fluids"], ["DIELY", "parts"], ["VÝBAVA", "tools"]]:
		var category := Button.new()
		category.text = entry[0]
		category.custom_minimum_size = Vector2(130, 36)
		category.pressed.connect(_filter_loot.bind(entry[1]), CONNECT_PERSIST)
		categories.add_child(category)
	contents.add_child(categories)
	var body := _row(20)
	body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	contents.add_child(body)
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.custom_minimum_size = Vector2(730, 340)
	scroll.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	body.add_child(scroll)
	_loot_list = _column(8)
	_loot_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(_loot_list)
	var summary := _panel(PANEL)
	summary.custom_minimum_size.x = 380
	body.add_child(summary)
	var aside := _column(7)
	var summary_scroll := ScrollContainer.new()
	summary_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	summary.add_child(summary_scroll)
	summary_scroll.add_child(aside)
	aside.add_child(_label("MIESTO V KUFRI", 13, GOLD))
	_loot_capacity = _label("", 17)
	aside.add_child(_loot_capacity)
	aside.add_child(_label("HMOTNOSŤ", 11, MUTED))
	_loot_weight = _bar()
	aside.add_child(_loot_weight)
	aside.add_child(_label("ÚLOŽNÉ MIESTA", 11, MUTED))
	_loot_slots = _bar()
	aside.add_child(_loot_slots)
	_loot_selection = _label("", 17)
	_loot_selection.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_loot_selection.custom_minimum_size = Vector2(310, 42)
	aside.add_child(_loot_selection)
	_loot_diagram = CarDiagram.new()
	_loot_diagram.compact_mode = true
	_loot_diagram.custom_minimum_size = Vector2(310, 185)
	aside.add_child(_loot_diagram)
	_loot_detail = _label("Klikni na ikonu nálezu. Zobrazí sa jeho význam a porovnanie s autom.\n\nNič sa nezoberie ani nenamontuje automaticky.", 14, MUTED)
	_loot_detail.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_loot_detail.custom_minimum_size = Vector2(310, 100)
	aside.add_child(_loot_detail)
	var footer := _row(12)
	contents.add_child(footer)
	var select_all := _button("OZNAČIŤ VŠETKO", "select_loot")
	select_all.pressed.connect(_select_all_loot, CONNECT_PERSIST)
	footer.add_child(select_all)
	footer.add_child(_button("AUTO / POUŽIŤ ZÁSOBY", "service"))
	var spacer := Control.new()
	spacer.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	footer.add_child(spacer)
	_loot_take = _button("VZIAŤ VYBRANÉ", "take_selected")
	_loot_take.custom_minimum_size = Vector2(250, 46)
	footer.add_child(_loot_take)


func _build_service() -> void:
	_service = _panel(INK)
	_place(_service, Vector2(-620, -345), Vector2(1240, 690), Vector2(0.5, 0.5))
	var contents := _column(12)
	_service.add_child(contents)
	var heading := _row(16)
	var title := _label("VOZIDLO / PRÍPRAVA NA CESTU", 26, GOLD)
	title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	heading.add_child(title)
	heading.add_child(_button("ZAVRIEŤ  ESC", "resume"))
	contents.add_child(heading)
	_cargo_info = _label("", 15, MUTED)
	contents.add_child(_cargo_info)
	var tabs := TabContainer.new()
	_service_tabs = tabs
	tabs.size_flags_vertical = Control.SIZE_EXPAND_FILL
	contents.add_child(tabs)
	var service_tab := _row(24)
	service_tab.name = "Auto · údržba"
	tabs.add_child(service_tab)
	var visual := _column(9)
	visual.custom_minimum_size.x = 300
	service_tab.add_child(visual)
	visual.add_child(_label("VYBER DIEL NA AUTE", 13, GOLD))
	_car_diagram = CarDiagram.new()
	_car_diagram.custom_minimum_size = Vector2(300, 290)
	_car_diagram.selected.connect(_select_part, CONNECT_PERSIST)
	visual.add_child(_car_diagram)
	_component_info = _label("", 14, CREAM)
	_component_info.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_component_info.custom_minimum_size = Vector2(290, 64)
	visual.add_child(_component_info)
	visual.add_child(_button("SADA → VYBRANÝ DIEL", "usekit"))
	visual.add_child(_button("ZDROJ → BATÉRIA", "use:batterycell"))
	var installed := _column(9)
	installed.custom_minimum_size.x = 430
	installed.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	service_tab.add_child(installed)
	installed.add_child(_label("NAMONTOVANÉ DIELY", 13, MUTED))
	for part: String in DriveRun.PART_NAMES:
		var card := _panel(PANEL, 12, 9)
		var column := _column(5)
		card.add_child(column)
		var row := _row(10)
		row.add_child(_icon(part, Vector2(28, 28)))
		var label := _label(DriveRun.PART_NAMES[part], 15)
		label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(label)
		var button := _button("", "repair:" + part)
		button.add_theme_font_size_override("font_size", 12)
		row.add_child(button)
		column.add_child(row)
		var bar := _bar()
		column.add_child(bar)
		installed.add_child(card)
		_part_buttons[part] = button
		_part_bars[part] = bar
	var fluids := _column(12)
	fluids.custom_minimum_size.x = 330
	service_tab.add_child(fluids)
	fluids.add_child(_label("KVAPALINY A ZÁSOBY", 13, MUTED))
	for kind: String in ["fuel", "oil", "coolant"]:
		var card := _panel(PANEL, 14, 12)
		var column := _column(9)
		card.add_child(column)
		var name_row := _row(8)
		name_row.add_child(_icon(kind, Vector2(28, 28)))
		name_row.add_child(_label(DriveRun.RESOURCE_NAMES[kind], 18))
		column.add_child(name_row)
		var info := _label("", 14, MUTED)
		column.add_child(info)
		column.add_child(_button("DOPLNIŤ Z KUFRA", "fluid:" + kind))
		fluids.add_child(card)
		_fluid_info[kind] = info
	var cargo_scroll := ScrollContainer.new()
	cargo_scroll.name = "Kufor a náhradné diely"
	cargo_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	tabs.add_child(cargo_scroll)
	_cargo_list = _column(10)
	_cargo_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cargo_scroll.add_child(_cargo_list)
	var shop_tab := _column(16)
	shop_tab.name = "Prevádzka · lak"
	tabs.add_child(shop_tab)
	_stop_service_info = _label("", 18, GOLD)
	_stop_service_info.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	shop_tab.add_child(_stop_service_info)
	var shop_row := _row(12)
	for entry: Array in [["gas", "NATANKOVAŤ 5 L · 5 ŠROTU", "pump"], ["repair", "SERVIS VYBRANÉHO DIELU", "shop_repair"]]:
		var button := _button(entry[1], entry[2])
		button.custom_minimum_size = Vector2(340, 55)
		_shop_buttons[entry[0]] = button
		shop_row.add_child(button)
	shop_tab.add_child(shop_row)
	shop_tab.add_child(_label("LAK KAROSÉRIE / 1 AUTOLAK V KUFRI ALEBO 4 ŠROTU V LAKOVNI", 14, MUTED))
	var colors := _row(12)
	for paint_title: String in DriveRun.PAINTS:
		var color: String = DriveRun.PAINTS[paint_title]
		var button := _button(paint_title, "paint:" + color)
		button.custom_minimum_size = Vector2(150, 80)
		button.add_theme_color_override("font_color", Color(color).lightened(0.30))
		_paint_buttons[color] = button
		colors.add_child(button)
	shop_tab.add_child(colors)
	shop_tab.add_child(_label("Zásoby zo skriniek v budove a platené služby sú oddelené.\nStojany majú konečnú zásobu; opravy ani lakovanie nie sú zadarmo.", 16, MUTED))


func _process(dt: float) -> void:
	if Engine.is_editor_hint(): return
	if run == null or vehicle == null:
		return
	_speed.text = "%03d" % roundi(absf(vehicle.speed) * 3.6)
	_distance.text = "%.2f km" % (run.distance / 1000.0)
	_region.text = DriveWorld.BIOMES[world.biome_at(vehicle.position.x)].to_upper()
	var surface := world.terrain.sample(vehicle.position.x, vehicle.position.z)
	_road_info.text = "POVRCH / %s\nPriľnavosť %d %%" % [str(surface.label).to_upper(), roundi(float(surface.grip) * 100.0)]
	_road_info.modulate = GOLD if float(surface.water) > 0.03 else CREAM
	if vehicle.sink_depth > 0.08 or vehicle.soil_slip > 0.15:
		_road_info.text += "\nZaborenie %d cm\nPrešmyk %d %%" % [roundi(vehicle.sink_depth * 100), roundi(vehicle.soil_slip * 100)]
	if vehicle.stop_reason in ["bogged", "high_centered"]:
		_road_info.text += "\n" + ("NA PODVOZKU" if vehicle.stop_reason == "high_centered" else "ZAPADNUTÉ")
		_road_info.modulate = GOLD
	_junction_panel.visible = false
	_road_info.visible = not modal_open
	_engine.text = "MOTOR BEŽÍ" if run.engine_running else "MOTOR VYPNUTÝ"
	_engine.modulate = GREEN if run.engine_running else MUTED
	_engine_button.text = "E  VYPNÚŤ MOTOR" if run.engine_running else "E  NAŠTARTOVAŤ"
	_cruise_button.set_pressed_no_signal(vehicle.cruise_enabled)
	_cruise_status.text = "TEMPOMAT · PLYN %d %%" % roundi(vehicle.cruise_amount * 100) if vehicle.cruise_enabled else "Tempomat vypnutý"
	_cruise_status.modulate = GOLD if vehicle.cruise_enabled else MUTED
	_update_stat("fuel", run.fuel / DriveRun.FUEL_CAPACITY, "%.1f l" % run.fuel, run.fuel < 3.0)
	_update_stat("oil", run.oil / DriveRun.OIL_CAPACITY, "%.1f l" % run.oil, run.oil < 0.5)
	_update_stat("coolant", run.coolant / DriveRun.COOLANT_CAPACITY, "%.1f l" % run.coolant, run.coolant < 1.0)
	_update_stat("battery", run.battery, "%d %%" % roundi(run.battery * 100), run.battery < 0.12)
	_update_stat("temperature", run.temperature / 130.0, "%d °C" % roundi(run.temperature), run.temperature > 110.0)
	var building := world.nearest_building(vehicle.position)
	_near_panel.visible = not building.is_empty() and not modal_open
	if not building.is_empty():
		var pending: Array = run.loot_piles.get(str(building.id), [])
		var status := "%d položiek" % pending.size() if run.searched.has(str(building.id)) else "Neprehľadané zásoby"
		_near_title.text = str(building.title) + "\n" + status
		_search.text = "F  PREHĽADAŤ" if absf(vehicle.speed) < 0.5 else "NAJPRV ZASTAV"
		_search.disabled = absf(vehicle.speed) >= 0.5
	_objective.visible = false
	_next_stop.visible = false
	if weather != null:
		_region.text += " · " + weather.mode.to_upper()
		if weather.storm_active:
			_next_stop.visible = not modal_open
			_next_stop.text = "V GARÁŽI SI V BEZPEČÍ" if weather.sheltered else "PIESOČNÁ BÚRKA\nHľadaj krytú garáž. Prach poškodzuje auto."
	_weather_panel.visible = _next_stop.visible
	_message_time = maxf(0.0, _message_time - dt)
	_toast_panel.visible = _message_time > 0.0


func _update_stat(key: String, amount: float, text: String, warning: bool) -> void:
	var label: Label = _stats[key].value
	var bar: ProgressBar = _stats[key].bar
	label.text = text
	label.modulate = RED if warning else CREAM
	bar.value = clampf(amount, 0, 1)
	bar.modulate = RED if warning else GOLD


func toast(text: String) -> void:
	_message.text = text
	_message_time = 5.0


func show_modal(kind: String) -> void:
	_modal_kind = kind
	modal_open = not kind.is_empty()
	_service.visible = kind == "service"
	_pause.visible = kind == "pause"
	_loot.visible = kind == "loot"
	_shade.visible = modal_open
	_active_fingers.clear()
	for pedal: Button in _pedals.values():
		if pedal.is_inside_tree(): pedal.release_focus()
	if kind == "service": refresh_service()
	if kind == "loot": refresh_loot()
	if _toast_panel != null:
		for property: String in _toast_rest: _toast_panel.set(property, _toast_rest[property])
		if modal_open:
			_toast_panel.anchor_top = 0.0
			_toast_panel.anchor_bottom = 0.0
			_toast_panel.offset_top = 8
			_toast_panel.offset_bottom = 54


func _remember_toast_layout() -> void:
	for property: String in ["anchor_top", "anchor_bottom", "offset_top", "offset_bottom"]:
		_toast_rest[property] = _toast_panel.get(property)


func set_loot_building(building_id: int, title: String) -> void:
	_loot_building = building_id
	_loot_building_title = title


func refresh_loot(preserve_selection: bool = false) -> void:
	var previous: Dictionary = {}
	if preserve_selection:
		for entry: Dictionary in selected_loot(): previous[entry.uid] = entry.amount
	_clear(_loot_list)
	_loot_rows.clear()
	_loot_title.text = _loot_building_title
	var contents: Array = run.loot_piles.get(str(_loot_building), [])
	for item: Dictionary in contents:
		var card := _panel(PANEL, 10, 10)
		card.custom_minimum_size.y = 92
		card.visible = _loot_filter == "all" or (_loot_filter == "fluids" and item.kind in DriveRun.FLUIDS) or (_loot_filter == "parts" and item.kind == "part") or (_loot_filter == "tools" and item.kind not in DriveRun.FLUIDS and item.kind != "part")
		var row := _row(10)
		card.add_child(row)
		var check := CheckBox.new()
		check.custom_minimum_size = Vector2(30, 42)
		check.focus_mode = Control.FOCUS_NONE
		row.add_child(check)
		var badge := Button.new()
		badge.icon = DriveIcons.texture(str(item.slot) if item.kind == "part" else str(item.kind))
		badge.expand_icon = true
		badge.custom_minimum_size = Vector2(54, 58)
		badge.tooltip_text = "Zobraziť význam a porovnanie"
		badge.pressed.connect(func() -> void: _inspect_loot(item))
		row.add_child(badge)
		var description := _column(4)
		description.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(description)
		description.add_child(_label(DriveRun.item_name(item), 17))
		var detail: String
		if item.kind == "part":
			var delta := roundi((float(item.condition) - float(run.parts[item.slot])) * 100)
			detail = "%s · stav %d %% · v aute %d %% · %+d bodov" % [DriveRun.quality_label(item.condition), roundi(float(item.condition) * 100), roundi(float(run.parts[item.slot]) * 100), delta]
		else:
			detail = "%.1f l · čistota %d %% · %s" % [item.amount, roundi(float(item.get("quality", 1.0)) * 100), DriveRun.quality_label(item.get("quality", 1.0))] if item.kind in DriveRun.FLUIDS else "%d ks · %.1f kg" % [int(item.amount), DriveRun.item_weight(item)]
		var quality := _label(detail, 13, MUTED)
		quality.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		description.add_child(quality)
		if item.kind == "part":
			var health := _bar()
			health.value = float(item.condition)
			health.modulate = GREEN if float(item.condition) >= float(run.parts[item.slot]) else RED
			description.add_child(health)
		var amount := SpinBox.new()
		amount.custom_minimum_size = Vector2(100, 38)
		amount.min_value = 0.1 if item.kind in DriveRun.FLUIDS else 1.0
		amount.max_value = float(item.amount)
		amount.step = 0.1 if item.kind in DriveRun.FLUIDS else 1.0
		amount.value = float(item.amount)
		amount.suffix = "l" if item.kind in DriveRun.FLUIDS else "ks"
		row.add_child(amount)
		_loot_list.add_child(card)
		_loot_rows[item.uid] = {"check": check, "amount": amount, "item": item}
		if previous.has(item.uid):
			check.set_pressed_no_signal(true)
			amount.value = float(previous[item.uid])
		check.toggled.connect(func(on: bool) -> void:
			if on: _inspect_loot(item)
			_update_selection())
		amount.value_changed.connect(func(_value: float) -> void: _update_selection())
	if contents.is_empty():
		_loot_list.add_child(_label("Tu už nič nezostalo.\nMôžeš sa vrátiť na cestu.", 22, MUTED))
	_update_selection()
	_loot_diagram.update_parts(run.parts, _selected_part)


func _inspect_loot(item: Dictionary) -> void:
	var detail := DriveRun.item_name(item) + "\n" + DriveRun.item_effect(item)
	if item.kind == "part":
		_selected_part = str(item.slot)
		detail += "\nNález %d %% → v aute %d %%. Výmena je samostatný krok v kufri." % [roundi(float(item.condition) * 100), roundi(float(run.parts[item.slot]) * 100)]
	elif item.kind in DriveRun.FLUIDS:
		detail += "\nV kufri: %d %% · nález: %d %%." % [roundi(float(run.inventory_quality[item.kind]) * 100), roundi(float(item.get("quality", 1.0)) * 100)]
	_loot_detail.text = detail
	_loot_diagram.update_parts(run.parts, _selected_part)


func selected_loot() -> Array[Dictionary]:
	var result: Array[Dictionary] = []
	for uid: String in _loot_rows:
		var entry: Dictionary = _loot_rows[uid]
		if entry.check.button_pressed:
			result.append({"uid": uid, "amount": float(entry.amount.value)})
	return result


func _select_all_loot() -> void:
	var select := selected_loot().size() != _loot_rows.size()
	for entry: Dictionary in _loot_rows.values():
		entry.check.set_pressed_no_signal(select)
	_update_selection()


func _update_selection() -> void:
	var selected := selected_loot()
	var weight := 0.0
	var slots := 0
	var resource_slots: Dictionary = {}
	var only_scrap := not selected.is_empty()
	for choice: Dictionary in selected:
		var item: Dictionary = _loot_rows[choice.uid].item
		only_scrap = only_scrap and item.kind == "scrap"
		weight += DriveRun.item_weight(item, choice.amount)
		if item.kind == "part":
			slots += 1
		elif item.kind != "scrap" and float(run.inventory[item.kind]) <= 0.001 and not resource_slots.has(item.kind):
			slots += 1
			resource_slots[item.kind] = true
	var fits := only_scrap or (run.cargo_weight() + weight <= DriveRun.CARGO_MAX_WEIGHT + 0.0001 and run.cargo_used_slots() + slots <= DriveRun.CARGO_SLOTS)
	_loot_capacity.text = "%.1f / %.0f kg\n%d / %d miest" % [run.cargo_weight(), DriveRun.CARGO_MAX_WEIGHT, run.cargo_used_slots(), DriveRun.CARGO_SLOTS]
	_loot_weight.value = (run.cargo_weight() + weight) / DriveRun.CARGO_MAX_WEIGHT
	_loot_slots.value = float(run.cargo_used_slots() + slots) / DriveRun.CARGO_SLOTS
	_loot_selection.text = "Vybrané: %d\nHmotnosť: %.1f kg" % [selected.size(), weight]
	if not fits: _loot_selection.text += "\nVýber sa nezmestí."
	_loot_selection.modulate = CREAM if fits else RED
	_loot_take.disabled = selected.is_empty() or not fits
	_loot_take.tooltip_text = "Najprv označ zásoby." if selected.is_empty() else ("Uprav množstvo alebo výber; kufor je plný." if not fits else "Presunie iba označené množstvá do kufra.")


func refresh_service() -> void:
	if _cargo_list == null:
		return
	_cargo_info.text = "KUFOR  %.1f / %.0f kg   ·   MIESTO  %d / %d   ·   ŠROT  %d" % [run.cargo_weight(), DriveRun.CARGO_MAX_WEIGHT, run.cargo_used_slots(), DriveRun.CARGO_SLOTS, int(run.inventory.scrap)]
	for part: String in _part_buttons:
		var button: Button = _part_buttons[part]
		button.text = "%d %% · OPRAVIŤ / 2 ŠROTU" % roundi(float(run.parts[part]) * 100)
		button.disabled = float(run.inventory.scrap) < 2 or float(run.parts[part]) >= 0.99
		var bar: ProgressBar = _part_bars[part]
		bar.value = float(run.parts[part])
		bar.modulate = GREEN if float(run.parts[part]) >= 0.7 else GOLD
	for kind: String in _fluid_info:
		var capacity: float = {"fuel": DriveRun.FUEL_CAPACITY, "oil": DriveRun.OIL_CAPACITY, "coolant": DriveRun.COOLANT_CAPACITY}[kind]
		var label: Label = _fluid_info[kind]
		label.text = "Auto %.1f / %.0f l · kvalita %d %%\nKufor %.1f l · kvalita %d %%" % [run.get(kind), capacity, roundi(float(run.fluid_quality[kind]) * 100), run.inventory[kind], roundi(float(run.inventory_quality[kind]) * 100)]
	_car_diagram.update_parts(run.parts, _selected_part)
	_component_info.text = "%s · %s\nStav %d %% · sady v kufri: %d" % [DriveRun.PART_NAMES[_selected_part], DriveRun.VARIANT_NAMES[run.variants[_selected_part]], roundi(float(run.parts[_selected_part]) * 100), int(run.inventory.repairkit)]
	if _selected_part == "brakes":
		_component_info.text += "\nSuchý asfalt: približne %.1f m/s²" % run.brake_deceleration(0.9, 20)
	var nearby := world.nearest_building(vehicle.position)
	var stop_kind := str(nearby.get("kind", ""))
	_stop_service_info.text = "Zastav pri pumpe, servisnej dielni alebo lakovni.\nNa ceste môžeš používať zásoby z kufra."
	if not nearby.is_empty():
		_stop_service_info.text = str(nearby.title) + "\nŠrot: %d · vybraný diel: %s" % [int(run.inventory.scrap), DriveRun.PART_NAMES[_selected_part]]
		if stop_kind == "gas": _stop_service_info.text += " · stojan %.0f l" % run.pump_remaining(int(nearby.id))
	var pump_amount := 0
	if stop_kind == "gas": pump_amount = floori(minf(5.0, minf(DriveRun.FUEL_CAPACITY - run.fuel, minf(run.pump_remaining(int(nearby.id)), float(run.inventory.scrap)))))
	_shop_buttons.gas.disabled = pump_amount < 1
	_shop_buttons.gas.text = "NATANKOVAŤ %d L · %d ŠROTU" % [maxi(0, pump_amount), maxi(0, pump_amount)] if stop_kind == "gas" else "TANKOVANIE · IBA NA PUMPE"
	var repair_cost := maxi(2, ceili((0.98 - float(run.parts[_selected_part])) * 12.0))
	_shop_buttons.repair.disabled = stop_kind not in ["repair", "workshop"] or float(run.parts[_selected_part]) >= 0.98 or float(run.inventory.scrap) < repair_cost
	_shop_buttons.repair.text = "SERVIS %s · %d ŠROTU" % [str(DriveRun.PART_NAMES[_selected_part]).to_upper(), repair_cost]
	for color: String in _paint_buttons:
		var button: Button = _paint_buttons[color]
		var available := float(run.inventory.scrap) >= 4.0 if stop_kind == "paint" else float(run.inventory.paintcan) >= 1.0
		button.disabled = not available or run.paint_color == color
		button.tooltip_text = "Cena: 4 šrotu v lakovni." if stop_kind == "paint" else "Použije 1 autolak z kufra."
	_clear(_cargo_list)
	_cargo_list.add_child(_label("ZÁSOBY", 13, GOLD))
	var resource_row := GridContainer.new()
	resource_row.columns = 4
	resource_row.add_theme_constant_override("h_separation", 12)
	resource_row.add_theme_constant_override("v_separation", 12)
	_cargo_list.add_child(resource_row)
	for kind: String in DriveRun.RESOURCE_NAMES:
		if float(run.inventory[kind]) <= 0.0: continue
		var card := _panel(PANEL)
		card.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var box := _column(5)
		box.add_child(_icon(kind, Vector2(32, 32)))
		box.add_child(_label("%s\n%.1f %s" % [DriveRun.RESOURCE_NAMES[kind], run.inventory[kind], "l" if kind in DriveRun.FLUIDS else "ks"], 15))
		card.add_child(box)
		resource_row.add_child(card)
	_cargo_list.add_child(_label("NÁHRADNÉ DIELY / VÝMENA ZACHOVÁ PÔVODNÝ DIEL V KUFRI", 13, MUTED))
	for item: Dictionary in run.cargo_parts:
		var card := _panel(PANEL, 14, 12)
		var row := _row(14)
		card.add_child(row)
		row.add_child(_icon(str(item.slot), Vector2(46, 46)))
		var description := _column(5)
		description.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(description)
		description.add_child(_label(DriveRun.item_name(item), 19))
		description.add_child(_label("Stav %d %% · v aute %d %% · %.1f kg" % [roundi(float(item.condition) * 100), roundi(float(run.parts[item.slot]) * 100), DriveRun.item_weight(item)], 14, MUTED))
		var use := Button.new()
		use.text = "VYMENIŤ " + str(DriveRun.PART_NAMES[item.slot]).to_upper()
		use.custom_minimum_size.y = 42
		use.pressed.connect(func() -> void: _request_install(item))
		row.add_child(use)
		var salvage := _button("NA ŠROT", "salvage:" + str(item.uid))
		salvage.tooltip_text = "Rozoberie diel na materiál pre opravy."
		row.add_child(salvage)
		_cargo_list.add_child(card)
	if run.cargo_parts.is_empty():
		_cargo_list.add_child(_label("Žiadne náhradné diely. Nájdeš ich pri prehľadávaní budov.", 17, MUTED))


func _request_install(item: Dictionary) -> void:
	_selected_part = str(item.slot)
	if float(item.condition) < float(run.parts[item.slot]):
		_pending_install = str(item.uid)
		_install_confirmation.dialog_text = "%s\nNamontovaný: %s · %d %%\nNáhrada: %s · %d %%\nNáhrada je v horšom stave. Pôvodný diel zostane v kufri." % [DriveRun.PART_NAMES[item.slot], DriveRun.VARIANT_NAMES[run.variants[item.slot]], roundi(float(run.parts[item.slot]) * 100), DriveRun.VARIANT_NAMES[item.get("variant", "standard")], roundi(float(item.condition) * 100)]
		_install_confirmation.popup_centered(Vector2i(600, 240))
	else:
		requested.emit("install:" + str(item.uid))


func _icon(key: String, dimensions: Vector2) -> TextureRect:
	var icon := TextureRect.new()
	icon.texture = DriveIcons.texture(key)
	icon.custom_minimum_size = dimensions
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	return icon


func _input(event: InputEvent) -> void:
	if Engine.is_editor_hint(): return
	if not visible: return
	if event is InputEventScreenTouch:
		if not event.pressed:
			if _active_fingers.has(event.index):
				touch_changed.emit(_active_fingers[event.index], false, event.index)
				_active_fingers.erase(event.index)
				get_viewport().set_input_as_handled()
		elif not modal_open:
			for action: String in _pedals:
				var button: Button = _pedals[action]
				if button.get_global_rect().has_point(event.position):
					_active_fingers[event.index] = action
					touch_changed.emit(action, true, event.index)
					get_viewport().set_input_as_handled()
					break


func _unhandled_input(event: InputEvent) -> void:
	if Engine.is_editor_hint(): return
	if not visible: return
	if event is InputEventKey and event.pressed and not event.echo:
		if event.is_action_pressed("pause"):
			requested.emit("resume" if modal_open else "pause")
			get_viewport().set_input_as_handled()
		elif not modal_open:
			for action: String in ["engine", "search", "service", "lights", "camera", "cruise"]:
				if event.is_action_pressed(action):
					requested.emit(action)
					get_viewport().set_input_as_handled()


func _pedal(text: String, action: String, pos: Vector2, anchor: Vector2, dimensions: Vector2) -> void:
	var button := Button.new()
	button.text = text
	button.focus_mode = Control.FOCUS_NONE
	button.add_theme_font_size_override("font_size", 16)
	button.button_down.connect(_pedal_changed.bind(action, true), CONNECT_PERSIST)
	button.button_up.connect(_pedal_changed.bind(action, false), CONNECT_PERSIST)
	_place(button, pos, dimensions, anchor)
	_pedals[action] = button


func _button(text: String, action: String) -> Button:
	var button := Button.new()
	button.text = text
	button.focus_mode = Control.FOCUS_NONE
	button.pressed.connect(_dispatch.bind(action), CONNECT_PERSIST)
	return button


func _dispatch(action: String) -> void:
	if action == "confirm_new": _confirmation.popup_centered()
	elif action != "select_loot": requested.emit(action)


func _confirm_install() -> void:
	requested.emit("install:" + _pending_install)


func _filter_loot(kind: String) -> void:
	_loot_filter = kind
	refresh_loot(true)


func _select_part(slot: String) -> void:
	_selected_part = slot
	refresh_service()


func _pedal_changed(action: String, pressed: bool) -> void:
	touch_changed.emit(action, pressed, -(_pedals[action] as Button).get_instance_id())


func _preview_layout() -> void:
	if _loot == null: return
	_loot.visible = editor_view == 1
	_service.visible = editor_view == 2
	_pause.visible = editor_view == 3
	_shade.visible = editor_view != 0
	_objective.hide()
	_junction_panel.hide()
	_weather_panel.hide()
	_toast_panel.hide()


func _label(text: String, size: int, color: Color = CREAM) -> Label:
	var label := Label.new()
	label.text = text
	label.add_theme_font_size_override("font_size", size)
	label.add_theme_color_override("font_color", color)
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return label


func _bar() -> ProgressBar:
	var bar := ProgressBar.new()
	bar.max_value = 1.0
	bar.show_percentage = false
	bar.custom_minimum_size.y = 5
	bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bar.add_theme_stylebox_override("background", _style(Color("44473f"), 0, 0))
	bar.add_theme_stylebox_override("fill", _style(Color.WHITE, 0, 0))
	bar.modulate = GOLD
	return bar


func _panel(color: Color = INK, horizontal: float = 18, vertical: float = 14) -> PanelContainer:
	var panel := PanelContainer.new()
	panel.add_theme_stylebox_override("panel", _style(color, horizontal, vertical))
	return panel


func _style(color: Color, horizontal: float, vertical: float) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = color
	style.set_corner_radius_all(10)
	style.content_margin_left = horizontal
	style.content_margin_right = horizontal
	style.content_margin_top = vertical
	style.content_margin_bottom = vertical
	return style


func _column(spacing: int = 8) -> VBoxContainer:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", spacing)
	return box


func _row(spacing: int = 8) -> HBoxContainer:
	var box := HBoxContainer.new()
	box.add_theme_constant_override("separation", spacing)
	return box


func _clear(parent: Node) -> void:
	for child: Node in parent.get_children():
		parent.remove_child(child)
		child.queue_free()


func _place(control: Control, pos: Vector2, dimensions: Vector2, anchor := Vector2.ZERO) -> void:
	_root.add_child(control)
	control.anchor_left = anchor.x
	control.anchor_right = anchor.x
	control.anchor_top = anchor.y
	control.anchor_bottom = anchor.y
	control.offset_left = pos.x
	control.offset_top = pos.y
	control.offset_right = pos.x + dimensions.x
	control.offset_bottom = pos.y + dimensions.y
