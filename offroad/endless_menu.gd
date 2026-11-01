extends Control
## The main menu: a simple list on a torn sheet of parchment on the left, and on the
## right the chosen car turning (menu_car.gd). The garage, on the same sheet, buys cars
## and what a run starts with (garage.gd). A new run asks where to start; it replaces a
## saved one only after asking. The start and the car go to the game through Engine
## metadata (endless_terrain.gd, garage.gd).
const Settings = preload("res://offroad/game_settings.gd")
const UI = preload("res://offroad/menu_ui.gd")
const Garage = preload("res://offroad/garage.gd")
const Catalog = preload("res://scripts/vehicle_catalog.gd")
const SAVE := "user://endless_expedition.json"
## The sheet's width on screen.
const SHEET := 0.38
const STARTS := [["POLIA", "Kukurica, jačmeň, repka a slnečnice, potom lúky, step, močiare, lesy a hory.", 5],
	["PÚŠŤ", "Horúčava a piesočné búrky, potom step, polia, močiare, lesy a hory.", 2]]
const HELP_KEYS := "W A S D alebo šípky: plyn, brzda, zatáčanie\nMedzerník: ručná brzda      R: späť na cestu\nE: vziať z budovy, keď stojíš pri plávajúcej kocke\nI: náklad a pneumatiky      B: doplniť kvapaliny\nG: pohon 4x4 alebo 4x2      L: zámok diferenciálu\nF: svetlá      Koliesko alebo + −: priblíženie      ESC: pauza"
const HELP_TOUCH := "Ľavý palec: zatáčanie v ľavom dolnom rohu\nPravý palec: plyn, brzda a cúvanie, ručná brzda\nVZIAŤ: objaví sa pri plávajúcej kocke, keď zastavíš\nVIAC: náklad, doplnenie kvapalín, návrat na cestu\n4X4 a ZÁMOK: keď uviazneš\nŤahaním po obrazovke otáčaš kameru, dvoma prstami alebo + − ju približuješ\nŤuknutím na rýchlosť zobrazíš podrobnosti"
var saved := {}
var sheet: ColorRect
var sheet_material: ShaderMaterial
var car_view: SubViewportContainer
var turntable: Node3D
var car_plate: Label
var list: VBoxContainer
## The car shown in the garage.
var viewing := ""

func _ready() -> void:
	Settings.load_settings()
	Settings.apply_settings()
	get_tree().quit_on_go_back = true
	set_anchors_preset(Control.PRESET_FULL_RECT)
	_bring_old_files()
	Garage.load_garage()
	_read_save()
	var back := ColorRect.new()
	back.color = Color(0.86, 0.76, 0.6)
	back.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(back)
	car_view = SubViewportContainer.new()
	car_view.stretch = true
	car_view.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(car_view)
	var view := SubViewport.new()
	view.own_world_3d = true
	view.world_3d = World3D.new()
	view.msaa_3d = Viewport.MSAA_4X
	view.handle_input_locally = false
	car_view.add_child(view)
	turntable = preload("res://offroad/menu_car.gd").new()
	view.add_child(turntable)
	turntable.show_car(Garage.car)
	car_plate = UI.label("", 30, UI.PEN)
	car_plate.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	car_plate.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(car_plate)
	_name_car(Garage.car)
	sheet = ColorRect.new()
	sheet_material = ShaderMaterial.new()
	sheet_material.shader = preload("res://offroad/parchment.gdshader")
	sheet.material = sheet_material
	sheet.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(sheet)
	var margin := MarginContainer.new()
	margin.name = "Page"
	add_child(margin)
	list = VBoxContainer.new()
	list.alignment = BoxContainer.ALIGNMENT_CENTER
	list.add_theme_constant_override("separation", roundi(4.0 * UI.k()))
	margin.add_child(list)
	_fill_page()
	resized.connect(_lay_out)
	_lay_out.call_deferred()

func _lay_out() -> void:
	var inset := _safe_insets()
	var whole := size
	var width := whole.x * SHEET + inset.x
	# The sheet reaches a little past its torn edge, for the shadow.
	var outer := width + 70.0
	sheet.position = Vector2.ZERO
	sheet.size = Vector2(outer, whole.y)
	sheet_material.set_shader_parameter("area", sheet.size)
	sheet_material.set_shader_parameter("edge", width / outer)
	car_view.position = Vector2(width - 40.0, 0.0)
	car_view.size = Vector2(whole.x - width + 40.0, whole.y)
	car_plate.position = Vector2(width, whole.y - inset.w - 100.0 * UI.k())
	car_plate.size = Vector2(whole.x - width - inset.z, 90.0 * UI.k())
	var margin: MarginContainer = get_node("Page")
	margin.position = Vector2(inset.x + 56.0, inset.y + 20.0)
	margin.size = Vector2(width - inset.x - 56.0 - 70.0, whole.y - inset.y - inset.w - 40.0)

## A run saved when the game had its old name is carried over (on a computer: the
## folder goes by the name).
func _bring_old_files() -> void:
	var old := OS.get_user_data_dir().get_base_dir().path_join("Sirocco")
	var brought := false
	for file: String in [SAVE.get_file(), Settings.FILE.get_file()]:
		if not FileAccess.file_exists("user://" + file) and FileAccess.file_exists(old.path_join(file)):
			DirAccess.copy_absolute(old.path_join(file), OS.get_user_data_dir().path_join(file))
			brought = true
	if not brought: return
	Settings.loaded = false
	Settings.load_settings()
	Settings.apply_settings()

func _name_car(id: String) -> void:
	var spec: Dictionary = Garage.CARS[id]
	car_plate.text = "%s\n%d KG · %d KW · %d KM/H · NÁDRŽ %d L · NÁKLAD %d KG" % [str(Catalog.DATA[id].name).to_upper(), spec.mass, spec.power / 1000.0, spec.top * 3.6, spec.tank, spec.cargo]

func _read_save() -> void:
	if not FileAccess.file_exists(SAVE): return
	var data = JSON.parse_string(FileAccess.get_file_as_string(SAVE))
	if data is Dictionary: saved = data

func _safe_insets() -> Vector4:
	if not OS.has_feature("mobile"): return Vector4.ZERO
	var screen := Vector2(DisplayServer.screen_get_size())
	var safe := DisplayServer.get_display_safe_area()
	if safe.size.x <= 0 or screen.x <= 0.0: return Vector4.ZERO
	var units := get_viewport().get_visible_rect().size / screen
	return Vector4(safe.position.x * units.x, safe.position.y * units.y, (screen.x - safe.end.x) * units.x, (screen.y - safe.end.y) * units.y)

func _fill_page() -> void:
	var title := FontVariation.new()
	title.base_font = UI.TITLE_FONT
	title.variation_transform = Transform2D(Vector2(0.9, 0.0), Vector2(0.0, 1.0), Vector2.ZERO)
	list.add_child(UI.label("ENDLESS DRIVE", 74, UI.PEN, title))
	list.add_child(UI.label("CESTA DO HÔR", 30, UI.RUST))
	var rule := ColorRect.new()
	rule.color = Color(UI.PEN, 0.55)
	rule.custom_minimum_size = Vector2(0.0, 2.0)
	var gap := Control.new()
	gap.custom_minimum_size.y = 16.0 * UI.k()
	list.add_child(gap)
	list.add_child(rule)
	var gap2 := Control.new()
	gap2.custom_minimum_size.y = 14.0 * UI.k()
	list.add_child(gap2)
	var can_continue := not saved.is_empty()
	var first: Button
	if can_continue:
		first = _entry("POKRAČOVAŤ  ·  %.1f KM" % (float(saved.get("distance", 0.0)) / 1000.0), _go.bind(-1))
		_entry("NOVÁ VÝPRAVA", _choose_start)
	else:
		first = _entry("NOVÁ VÝPRAVA", _choose_start)
	_entry("GARÁŽ", _open_garage)
	_entry("NASTAVENIA", func() -> void: UI.open_settings(self, func() -> void: pass))
	_entry("AKO HRAŤ", _show_help)
	if not Settings.is_mobile() and not OS.has_feature("web"):
		_entry("UKONČIŤ", func() -> void: get_tree().quit())
	if can_continue and float(saved.get("record", 0.0)) > 0.0:
		var gap3 := Control.new()
		gap3.custom_minimum_size.y = 14.0 * UI.k()
		list.add_child(gap3)
		list.add_child(UI.label("REKORD %.1f KM" % (float(saved.record) / 1000.0), 26, UI.RUST))
	if not Settings.is_mobile(): first.grab_focus.call_deferred()

func _entry(text: String, action: Callable) -> Button:
	var button := UI.button(text, action, "list", 62.0, 44.0)
	button.alignment = HORIZONTAL_ALIGNMENT_LEFT
	list.add_child(button)
	return button

func _clear_page() -> void:
	for child in list.get_children(): child.free()

## The garage: on the sheet, the cars and upgrades; on the stand, the car being looked at.
func _open_garage() -> void:
	viewing = Garage.car
	_fill_garage()

func _close_garage() -> void:
	turntable.show_car(Garage.car)
	_name_car(Garage.car)
	_clear_page()
	_fill_page()

func _fill_garage() -> void:
	_clear_page()
	list.add_child(UI.label("GARÁŽ · SCRAP %d" % Garage.scraps, 52, UI.PEN))
	for id: String in Garage.ORDER:
		var marks := " · S NÍM VYRAZÍŠ" if id == Garage.car else ("" if id in Garage.owned else " · %d SCRAP" % Garage.CARS[id].price)
		list.add_child(UI.button(str(Catalog.DATA[id].name).to_upper() + marks, _view_car.bind(id), "pen" if id == viewing else "box", 46.0, 26.0))
	var note := UI.label(str(Garage.CARS[viewing].note), 22, UI.PEN)
	note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	list.add_child(note)
	if viewing != Garage.car:
		if viewing in Garage.owned:
			list.add_child(UI.button("VYRAZIŤ S TÝMTO AUTOM", func() -> void:
				Garage.choose_car(viewing)
				_fill_garage(), "pen", 46.0, 26.0))
		else:
			var price: int = Garage.CARS[viewing].price
			var buy := UI.button("KÚPIŤ ZA %d SCRAP" % price if Garage.scraps >= price else "CHÝBA %d SCRAP" % (price - Garage.scraps), func() -> void:
				Garage.buy_car(viewing)
				_fill_garage(), "pen", 46.0, 26.0)
			buy.disabled = Garage.scraps < price
			list.add_child(buy)
	list.add_child(UI.label("S ČÍM ZAČNEŠ NOVÚ VÝPRAVU", 26, UI.RUST))
	for key: String in Garage.UPGRADES:
		var spec: Dictionary = Garage.UPGRADES[key]
		var at := Garage.level(key)
		var price := Garage.next_price(key)
		var text: String = "%s %s" % [spec.name, spec.steps[at]] if price < 0 else "%s %s → %s · %d" % [spec.name, spec.steps[at], spec.steps[at + 1], price]
		var row := UI.button(text, func() -> void:
			Garage.buy_upgrade(key)
			_fill_garage(), "box", 46.0, 26.0)
		row.disabled = price < 0 or price > Garage.scraps
		list.add_child(row)
	list.add_child(UI.button("SPÄŤ", _close_garage, "box", 46.0, 26.0))
	turntable.show_car(viewing)
	_name_car(viewing)

func _view_car(id: String) -> void:
	viewing = id
	_fill_garage()

func _choose_start() -> void:
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", roundi(12.0 * UI.k()))
	column.add_child(UI.label("KDE ZAČNEME?", 54, UI.PEN))
	if not saved.is_empty():
		column.add_child(UI.label("Uložená výprava (%.1f km) sa nahradí novou." % (float(saved.get("distance", 0.0)) / 1000.0), 28, UI.RUST))
	var scrim := UI.modal(self, column, 980.0)
	for start: Array in STARTS:
		var biome: int = start[2]
		column.add_child(UI.button(start[0], func() -> void: _go(biome), "pen", 72.0, 38.0))
		var note := UI.label(start[1], 24, UI.PEN)
		note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		column.add_child(note)
	column.add_child(UI.button("NÁHODNE", func() -> void: _go(STARTS[randi() % STARTS.size()][2]), "box"))
	column.add_child(UI.button("SPÄŤ", func() -> void: scrim.queue_free(), "box"))

func _show_help() -> void:
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", roundi(14.0 * UI.k()))
	column.add_child(UI.label("AKO HRAŤ", 54, UI.PEN))
	var text := UI.label("Asi 56 km cez polia, močiare a lesy až do hôr, a potom stále ďalej. Palivo berieš z benzíniek a domov pri ceste.\n\n" + (HELP_TOUCH if Settings.is_mobile() else HELP_KEYS), 28, UI.PEN)
	text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(text)
	var scrim := UI.modal(self, column, 1100.0)
	column.add_child(UI.button("ROZUMIEM", func() -> void: scrim.queue_free(), "pen"))

## biome -1 carries on the saved run; otherwise a new run starts there.
func _go(biome: int) -> void:
	if biome >= 0:
		if FileAccess.file_exists(SAVE): DirAccess.remove_absolute(ProjectSettings.globalize_path(SAVE))
		Engine.set_meta("endless_start", biome)
		Engine.set_meta("endless_car", Garage.car)
	else: Engine.set_meta("endless_car", str(saved.get("car", "m1009")))
	# The load takes seconds: first say so.
	var cover := ColorRect.new()
	cover.color = Color(0.04, 0.035, 0.025, 0.8)
	cover.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(cover)
	var centre := CenterContainer.new()
	centre.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	cover.add_child(centre)
	centre.add_child(UI.label("CESTA SA CHYSTÁ…", 60, UI.PAPER))
	await get_tree().process_frame
	await get_tree().process_frame
	get_tree().change_scene_to_file("res://offroad/endless.tscn")
