extends "res://offroad/generated_expedition.gd"
## The expedition on the endless road (endless_terrain.gd): fuel, oil, coolant,
## repairs and cargo as before (generated_expedition.gd), but the sites come
## along the road as it is planned, and the aim is to get as far as you can.
## Near a store E opens the loot window; I opens the inventory anywhere: fuel, oil, coolant,
## repair kits and tyre sets are used from there (tyres.gd).
const T = preload("res://offroad/endless_terrain.gd")
const Tyres = preload("res://offroad/tyres.gd")
const Buildings = preload("res://offroad/buildings.gd")
const Roadside = preload("res://offroad/roadside.gd")
const UI = preload("res://offroad/menu_ui.gd")
const Garage = preload("res://offroad/garage.gd")
const SummitTower = preload("res://offroad/summit_tower.gd")
## Sites are put up within this distance of the car, and taken down a little further.
const SHOW_NEAR := 300.0
const SHOW_MARGIN := 60.0
const TYRE_SET_KG := 40.0
const ITEM_NAMES := {"fuel": "Palivo (l)", "repair": "Opravné sady", "oil": "Olej (l)", "coolant": "Chladiaca kvapalina (l)"}

## Distance along the road reached (m), this run and the best; the road
## sample the car is nearest (searched near the last one).
var distance := 0.0
var record := 0.0
var road_sample := 0
var road_search := 0.0
## Tyre sets carried, the set on the car, and what was taken from each site
## (site index → item indices).
var tyres: Array = []
var tyre_kind := "road"
var chain_sets := 0
var chains_on := false
const CHAIN_KG := 12.0
var taken := {}
## The loot window: which site it shows (-1: the cargo only).
var window: PanelContainer
var window_site := -1
var loot_list: VBoxContainer
var cargo_list: VBoxContainer
var window_title: Label
var fitted_label: Label
var audio: Node
## Fuel flow, smoothed for the display (l/h).
var burn_rate := 1.5
var take_all_button: Button
## What the window has to say; its two scrolling lists; a finger scrolling one is not a tap.
var window_note: Label
var scrolls: Array[ScrollContainer] = []
var drag_travel := 0.0
var dragged := false
## How far from the road the run was saved, and whether its world has changed since.
var saved_offset := -1.0
var world_changed := false
## The last message and how long it stays up.
var last_message := ""
var message_clock := 0.0
const MESSAGE_TIME := 7.0
## Whether this run was saved before, and the kilometres scrap has been paid for.
var resumed := false
var paid_km := 0
## Scrap for a whole site, and for a new kilometre of road.
const SCRAP_SITE := 10
## What one repair kit gives back (condition, %).
const REPAIR_KIT := 20.0
const SCRAP_KM := 5
## Scrap for reaching the summit, how near the transmitter counts, and from how far it
## is seen.
const SCRAP_SUMMIT := 150
const SUMMIT_REACH := 48.0
const SUMMIT_SEEN := 2600.0
## The window over the game (checkpoints, the summit) and its layer. A test jump earns
## no more scrap for what it skipped; whether the run has made one.
var over_layer: CanvasLayer
var over_window: Control
var jumped := false
## Places to jump to while testing: [name, z]. The game itself has no checkpoints: a run goes
## from the start to the end and is saved as it goes.
var checkpoints: Array = []
var starting := false
var winch: Node3D
## The engine's temperature (°C), and from what it gets into trouble.
var engine_temp := 15.0
const HOT := 105.0
const DAMAGING := 112.0
const SEIZING := 120.0
## Below this a cold diesel may not catch at the first try.
const COLD_START := -8.0
## A phone's display (phone_hud.gd), and the time to its next refresh.
var phone_hud: CanvasLayer
var hud_clock := 0.0

func _init() -> void:
	H = T
	save_path = "user://endless_expedition.json"
	# Fuel comes from what the engine does (vehicle.gd fuel_rate), not from the distance.
	fuel_per_metre = 0.0
	fuel_per_second = 0.0
	# An old engine burns about a litre of oil per 1000 km, and keeps its coolant.
	oil_per_metre = 0.000001
	coolant_per_metre = 0.0000002
	message = ""

func setup(root: Node3D, loading: CanvasLayer = null) -> void:
	super(root)
	set_process(false)
	Buildings.prepare()
	var film: Node = preload("res://offroad/road_fx.gd").new()
	film.name = "RoadFx"
	add_child(film)
	film.setup(root)
	car.fit_specs(_car_spec(), Garage.power_factor())
	car.fit_tyres(tyre_kind, chains_on)
	_build_window()
	audio = preload("res://offroad/car_audio.gd").new()
	audio.name = "CarAudio"
	add_child(audio)
	audio.setup(root)
	audio.set_process(false)
	var watch: Node = preload("res://offroad/quality_watch.gd").new()
	watch.game = root
	add_child(watch)
	get_tree().quit_on_go_back = false
	winch = preload("res://offroad/winch.gd").new()
	add_child(winch)
	winch.setup(root, car)
	over_layer = CanvasLayer.new()
	over_layer.layer = 11
	add_child(over_layer)
	checkpoints = T.checkpoints()
	await _resume(loading)
	set_process(true)
	audio.set_process(true)
	audio.crank()
	# (The road has nothing to say at the start.)
	if not world_changed: message = ""

## A saved run resumes where it was: the road is planned that far and the land around
## the car made before it drives on. If the road is no longer where it was (the world
## has changed since), the car goes on the road nearest, and the run goes on from there.
func _resume(loading: CanvasLayer) -> void:
	var p := Vector2(car.position.x, car.position.z)
	if p.distance_to(camp) <= 50.0 and saved_offset < 0.0: return
	await _plan_to(p.y + T.PLAN_AHEAD, loading)
	var line: PackedVector2Array = T.roads[0].points
	var along: PackedFloat32Array = T.roads[0].along
	var nearest := 0
	for k in line.size():
		if line[k].distance_squared_to(p) < line[nearest].distance_squared_to(p): nearest = k
	road_sample = nearest
	var offset := line[nearest].distance_to(p)
	# (A run saved outside what is now the road's corridor goes back to the road as well.)
	var moved := (saved_offset >= 0.0 and saved_offset < 40.0 and offset > saved_offset + 15.0) or not T.corridor.has(T.tile_of(p.x, p.y))
	if world_changed or moved:
		if offset > 20.0 or moved:
			var ahead: Vector2 = line[mini(nearest + 4, line.size() - 1)] - line[nearest]
			p = line[nearest]
			car.place_on_ground(Vector3(p.x, 0.0, p.y), atan2(-ahead.x, -ahead.y))
		# What was taken was counted by sites that are no more.
		explored.clear()
		taken = {}
		message = "Svet sa zmenil, výprava pokračuje na najbližšej ceste."
	await game.test_grounds.stream_all(p)
	car.place_on_ground(car.position, car.heading)
	previous_position = car.position
	distance = along[nearest]
	record = maxf(record, distance)
	game.camera_snap = true
	_save_game()

func _keeps_place_in_new_world() -> bool:
	return true

## A run with --seed= is a test: it does not touch the saved one.
func _save_game() -> void:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--seed="): return
	super()

func _notification(what: int) -> void:
	if car == null or not is_instance_valid(car): return
	# Phones may close a game in the background without a word.
	if what in [NOTIFICATION_WM_CLOSE_REQUEST, NOTIFICATION_APPLICATION_PAUSED, NOTIFICATION_WM_WINDOW_FOCUS_OUT]: _save_game()
	if what == NOTIFICATION_WM_GO_BACK_REQUEST:
		if over_window != null: _close_over()
		elif window != null and window.visible: window.visible = false
		elif game.paused: game.close_pause_menu()
		else: game.open_pause_menu()

## What the garage gives a new run.
func _start_loadout() -> void:
	engine_temp = 15.0
	fuel = tank_capacity() * Garage.start_fuel_share()
	var more := Garage.level("supplies")
	cargo.fuel += more * 10
	cargo.repair += more
	cargo.oil += more
	cargo.coolant += more
	tyre_kind = Garage.start_tyres()

func _generate() -> void:
	if not resumed: _start_loadout()
	var line: PackedVector2Array = T.roads[0].points
	var along := (line[mini(6, line.size() - 1)] - line[0]).normalized()
	_marker(camp + Vector2(-along.y, along.x) * 12.0 + along * 4.0, Color("4acbb7"), "TÁBOR · DOPLNENIE A OPRAVY")

func _car_spec() -> Dictionary:
	return Garage.CARS[game.lead_model]

func tank_capacity() -> int:
	return _car_spec().tank

## Scrap goes straight to the garage, whatever becomes of the run (not in test runs).
func _earn(amount: int) -> void:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--seed="): return
	Garage.earn(amount)

func _scrap_in(item: Array) -> int:
	match str(item[0]):
		"fuel", "oil", "coolant": return 2
		"repair": return 5
	return 8

func _process(delta: float) -> void:
	super(delta)
	if not game.paused:
		fuel = maxf(0.0, fuel - car.fuel_rate() * delta / 3600.0)
		# Consumption over the last stretch: at walking pace, per hour instead.
		burn_rate = lerpf(burn_rate, car.fuel_rate(), 1.0 - exp(-delta / 1.5))
	if not game.paused: _heat(delta)
	if window.visible and window_site >= 0 and absf(car.speed) > 2.0: window.visible = false
	if winch.news != "":
		message = winch.news
		winch.news = ""
	if floori(record / 1000.0) > paid_km:
		var km := floori(record / 1000.0)
		_earn((km - paid_km) * SCRAP_KM)
		paid_km = km
	if game.paused or not T.plan_lock.try_lock(): return
	_follow_road()
	_beacons()
	_light_box()
	T.plan_lock.unlock()
	_check_summit()

## Distance: the main road sample nearest the car, searched around the last. Off the
## road the search may lose it: once a second, wherever the car is, it is found again.
func _follow_road() -> void:
	var line: PackedVector2Array = T.roads[0].points
	var along: PackedFloat32Array = T.roads[0].along
	var p := Vector2(car.position.x, car.position.z)
	var best := clampi(road_sample, 0, line.size() - 1)
	road_search += get_process_delta_time()
	if road_search > 1.0 and line[best].distance_to(p) > 30.0:
		road_search = 0.0
		var near := T.roads_near(Rect2(p - Vector2(30, 30), Vector2(60, 60)))
		if near.has(0):
			for k: int in near[0]:
				if line[k].distance_squared_to(p) < line[best].distance_squared_to(p): best = k
			road_sample = best
	for k in range(maxi(0, road_sample - 60), mini(line.size(), road_sample + 60)):
		if line[k].distance_squared_to(p) < line[best].distance_squared_to(p): best = k
	road_sample = best
	# A recovery (or R) puts the car back on the road here.
	car.route_phase = along[best] / 100.0
	if line[best].distance_to(p) < 25.0:
		distance = maxf(distance, along[best])
		record = maxf(record, distance)
	# The last camp passed is where the camp services and an emergency tow are.
	for site: Dictionary in sites:
		if site.kind == "camp" and site.along <= distance + 10.0: camp = site.point

## Beacons, buildings and goods for the sites near the car.
func _beacons() -> void:
	if sites.size() != T.sites.size(): sites = T.sites.duplicate()
	while markers.size() < sites.size(): markers.append(null)
	var at := Vector2(car.position.x, car.position.z)
	for i in sites.size():
		var site: Dictionary = sites[i]
		var off: float = at.distance_to(site.point)
		var near: float = SUMMIT_SEEN if site.kind == "summit" else SHOW_NEAR
		if off < near and markers[i] == null: markers[i] = _put_up(i, site)
		elif off > near + SHOW_MARGIN and markers[i] != null:
			markers[i].queue_free()
			markers[i] = null

func _put_up(i: int, site: Dictionary) -> Node3D:
	var line: PackedVector2Array = T.roads[0].points
	var k := clampi((T.roads[0].along as PackedFloat32Array).bsearch(site.along), 0, line.size() - 1)
	var tangent := (line[mini(k + 2, line.size() - 1)] - line[maxi(k - 2, 0)]).normalized()
	var side := Vector2(-tangent.y, tangent.x)
	var point: Vector2 = site.point
	match site.kind:
		"summit":
			var tower := SummitTower.new()
			tower.position = Vector3(point.x, float(site.level) - 0.1, point.y)
			add_child(tower)
			return tower
		"camp":
			var marker := _marker(point - side * 5.5, Color("4acbb7"), "TÁBOR · DOPLNENIE A OPRAVY")
			_build_site_barrack(point + side * 12.0, tangent).reparent(marker)
			return marker
		"building":
			# The building stands on its lot, its front (-Z of this node) towards
			# the road. The station's forecourt is its -X.
			var facing: Vector2 = site.facing
			var at: Vector2 = site.at
			var holder := Node3D.new()
			holder.position = Vector3(at.x, site.level, at.y)
			holder.rotation.y = atan2(facing.x, facing.y)
			add_child(holder)
			if site.model == "":
				holder.add_child(Buildings.build(site.biome))
			else:
				var model := Roadside.spawn(site.model)
				if site.model == "gas_station":
					model.rotation.y = atan2(-facing.y, facing.x) - holder.rotation.y
					# (Its slab stands on the ground, which the car drives on.)
					model.position.y = -0.25
				holder.add_child(model)
			var kinds := []
			for item in _left_in(i):
				if str(item[0]).begins_with("tyre:"): kinds.append(str(item[0]).trim_prefix("tyre:"))
			var rubber := StandardMaterial3D.new()
			rubber.albedo_color = Color(0.05, 0.05, 0.05)
			rubber.roughness = 0.85
			Buildings.goods(holder, kinds, rubber, site.lot * 0.8)
			var box := _marker(point, LOOT_COLOUR, str(site.name))
			box.reparent(holder)
			box.visible = i not in explored
			boxes[i] = box
			return holder
	var spot := point - side * 4.5
	var stop := Node3D.new()
	stop.position = Vector3(spot.x, H.height_at(spot.x, spot.y), spot.y)
	add_child(stop)
	var crate_rubber := StandardMaterial3D.new()
	Buildings.goods(stop, [], crate_rubber, 0.0)
	var plain := _marker(spot, LOOT_COLOUR, str(site.name))
	plain.reparent(stop)
	plain.visible = i not in explored
	boxes[i] = plain
	return stop

const LOOT_COLOUR := Color(0.86, 0.62, 0.24)
## Floating boxes standing now: site → node; and the one the car is in reach of.
var boxes := {}
var lit_site := -1

## A place to stop at: a floating box with corner brackets, over its name.
func _marker(point: Vector2, color: Color, title: String) -> Node3D:
	var marker := Node3D.new()
	marker.position = Vector3(point.x, H.height_at(point.x, point.y) + 1.5, point.y)
	var body := MeshInstance3D.new()
	body.name = "Box"
	body.mesh = _box_mesh()
	body.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var material := ShaderMaterial.new()
	material.shader = preload("res://offroad/loot_box.gdshader")
	material.set_shader_parameter("tint", Vector3(color.r, color.g, color.b))
	body.material_override = material
	marker.add_child(body)
	var label := Label3D.new()
	label.text = title
	label.position.y = 1.5
	label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	label.font_size = 40
	label.outline_size = 8
	label.no_depth_test = true
	marker.add_child(label)
	add_child(marker)
	return marker

static var box_mesh: ArrayMesh

## A faint body and bright brackets at the eight corners (alpha in the colour).
static func _box_mesh() -> ArrayMesh:
	if box_mesh != null: return box_mesh
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	const HALF := 0.7
	st.set_color(Color(1, 1, 1, 0.05))
	_box_faces(st, Vector3.ZERO, Vector3.ONE * HALF)
	st.set_color(Color(1, 1, 1, 0.95))
	const BAR := 0.16
	const THICK := 0.022
	for sx: float in [-1.0, 1.0]:
		for sy: float in [-1.0, 1.0]:
			for sz: float in [-1.0, 1.0]:
				var corner := Vector3(sx, sy, sz) * (HALF + 0.03)
				_box_faces(st, corner - Vector3(sx * BAR, 0, 0), Vector3(BAR, THICK, THICK))
				_box_faces(st, corner - Vector3(0, sy * BAR, 0), Vector3(THICK, BAR, THICK))
				_box_faces(st, corner - Vector3(0, 0, sz * BAR), Vector3(THICK, THICK, BAR))
	box_mesh = st.commit()
	return box_mesh

static func _box_faces(st: SurfaceTool, c: Vector3, h: Vector3) -> void:
	for axis in 3:
		for sign_of: float in [-1.0, 1.0]:
			var n := Vector3.ZERO
			n[axis] = sign_of
			var u := Vector3.ZERO
			u[(axis + 1) % 3] = 1.0
			var v := Vector3.ZERO
			v[(axis + 2) % 3] = 1.0
			var at := c + n * h
			var du := u * h
			var dv := v * h
			for corner: Vector3 in [at - du - dv, at + du - dv, at + du + dv, at - du - dv, at + du + dv, at - du + dv]:
				st.add_vertex(corner)

# ---------------------------------------------------------------- the summit

## Getting to the transmitter ends the journey; the road and the land go on.
func _check_summit() -> void:
	if reached_goal or T.summit == Vector3.ZERO: return
	if Vector2(car.position.x, car.position.z).distance_to(Vector2(T.summit.x, T.summit.z)) > SUMMIT_REACH: return
	reached_goal = true
	if not jumped: _earn(SCRAP_SUMMIT)
	if audio != null: audio.shot("find", 0.8)
	_save_game()
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", roundi(14.0 * UI.k()))
	column.add_child(UI.label("VRCHOL", 72, UI.PEN))
	var text := UI.label("Stojíš pri vysielači, %d m nad morom. Výprava je dokončená: %.1f km od tábora." % [roundi(car.position.y), distance / 1000.0], 30, UI.PEN)
	text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(text)
	if not jumped: column.add_child(UI.label("+%d SCRAP · SPOLU %d" % [SCRAP_SUMMIT, Garage.scraps], 28, UI.RUST))
	var note := UI.label("Za vrcholom sa krajina stále tvorí, môžeš ísť ďalej.", 26, UI.PEN)
	note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(note)
	column.add_child(UI.button("ĎALEJ DO NEZNÁMA", _close_over, "pen", 72.0, 38.0))
	column.add_child(UI.button("HLAVNÉ MENU", func() -> void: game._to_main_menu(), "box"))
	_open_over(column, 980.0)

func _open_over(content: Control, width: float) -> void:
	game._set_paused(true)
	over_window = UI.modal(over_layer, content, width)

func _close_over() -> void:
	if over_window != null: over_window.queue_free()
	over_window = null
	game._set_paused(false)

# ---------------------------------------------------------------- jumps (for testing)

## The list of places; a tap jumps there.
func open_checkpoints() -> void:
	if over_window != null or game.paused or window.visible: return
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", roundi(10.0 * UI.k()))
	column.add_child(UI.label("SKOK · LEN NA TESTOVANIE", 50, UI.PEN))
	var note := UI.label("Plná nádrž a opravené auto. Prvý skok ďaleko trvá dlhšie: cesta sa musí naplánovať.", 24, UI.RUST)
	note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(note)
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", roundi(10.0 * UI.k()))
	grid.add_theme_constant_override("v_separation", roundi(8.0 * UI.k()))
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var rows := ceili(checkpoints.size() / 2.0)
	scroll.custom_minimum_size.y = minf(rows * 62.0 * UI.k() + (rows - 1) * 8.0 * UI.k(), get_viewport().get_visible_rect().size.y * 0.56)
	grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(grid)
	column.add_child(scroll)
	for i in checkpoints.size():
		var button := UI.button(str(checkpoints[i][0]).to_upper(), _jump.bind(i), "box", 62.0, 28.0)
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		grid.add_child(button)
	column.add_child(UI.button("ZAVRIEŤ", _close_over, "box", 62.0, 30.0))
	_open_over(column, 1180.0)

## Puts the car on the road before place i, the road planned that far first (on a
## worker, while the game waits under a cover that says how far it has got).
func _jump(i: int) -> void:
	_close_over()
	game._set_paused(true)
	var loading: CanvasLayer = preload("res://offroad/loading_screen.gd").open(get_tree())
	await get_tree().process_frame
	var world: Node = game.test_grounds
	var z: float = checkpoints[i][1]
	await _plan_to(z + T.PLAN_AHEAD, loading)
	loading.say("NAČÍTAVA SA…")
	var k: int = T.road_sample_at(z)
	var line: PackedVector2Array = T.roads[0].points
	var along: PackedFloat32Array = T.roads[0].along
	var p := line[k]
	var ahead := line[mini(k + 4, line.size() - 1)] - p
	var yaw := atan2(-ahead.x, -ahead.y)
	car.place_on_ground(Vector3(p.x, 0.0, p.y), yaw)
	await world.stream_all(p)
	car.place_on_ground(Vector3(p.x, 0.0, p.y), yaw)
	car._stop_emitters()
	car.reset_physics_interpolation()
	previous_position = car.position
	road_sample = k
	road_search = 0.0
	car.route_phase = along[k] / 100.0
	distance = along[k]
	record = maxf(record, distance)
	paid_km = maxi(paid_km, floori(record / 1000.0))
	jumped = true
	# (From here on the summit can be reached again.)
	reached_goal = false
	camp = Vector2(T.CAMP.x, T.CAMP.y)
	fuel = tank_capacity()
	condition = 100.0
	oil = maxf(oil, 4.0)
	coolant = maxf(coolant, 6.0)
	engine_temp = 70.0
	car.engine_running = true
	game.camera_snap = true
	_save_game()
	loading.close()
	game._set_paused(false)

## The road planned on to z on a worker, while the wheel turns and says how far it has got.
func _plan_to(z: float, loading: CanvasLayer) -> void:
	var world: Node = game.test_grounds
	# (Whatever the streaming had started first.)
	while world.planning != -1 and not WorkerThreadPool.is_task_completed(world.planning):
		await get_tree().process_frame
	if world.planning != -1:
		WorkerThreadPool.wait_for_task_completion(world.planning)
		world.planning = -1
	if T.planned_z >= z: return
	T._ensure_zones(z + 1000.0)
	world.planning = WorkerThreadPool.add_task(Callable(T, "plan_async").bind(z))
	var from: float = T.planned_z
	while not WorkerThreadPool.is_task_completed(world.planning):
		if loading != null: loading.say("NAČÍTAVA SA… %d %%" % roundi(clampf((T.planned_z - from) / maxf(z - from, 1.0), 0.0, 1.0) * 100.0))
		await get_tree().process_frame
	WorkerThreadPool.wait_for_task_completion(world.planning)
	world.planning = -1

# ---------------------------------------------------------------- the engine's heat

## The temperature follows where the load, the air and the coolant would take it: the
## thermostat holds a running engine near 90 °C (a little less in hard frost); hard climbing
## in the heat, slow air or little coolant take it over. Hot, it gives less power and wears;
## too hot, it stops and must cool down. Cold, it is short of power until warm.
func _heat(delta: float) -> void:
	var outside: float = game.test_grounds.weather.temperature
	var aim := outside
	var settle := 240.0
	if car.engine_running and fuel > 0.0:
		var pulling: float = clampf(car.wheel_power / car.engine_power, 0.0, 1.0)
		var air := clampf(absf(car.speed) / 15.0, 0.0, 1.0)
		var cooled := clampf(coolant / 4.0, 0.12, 1.0)
		aim = outside + (20.0 + 55.0 * pulling) * (1.35 - 0.35 * air) / cooled + (25.0 if oil < 0.5 else 0.0)
		aim = maxf(aim, 90.0 - clampf(-outside, 0.0, 25.0) * 0.6)
		settle = 130.0 if engine_temp < 70.0 else 50.0
	engine_temp = lerpf(engine_temp, aim, 1.0 - exp(-delta / settle))
	var worn := 0.0
	if engine_temp > DAMAGING: worn += 0.15
	if oil < 0.5 and car.engine_running: worn += 0.05
	condition = maxf(0.0, condition - worn * delta)
	if engine_temp > SEIZING and car.engine_running:
		car.engine_running = false
		if audio != null: audio.shot("engine_stop", 0.8)
		message = "Prehriaty motor zhasol. Nechaj ho vychladnúť, skontroluj chladiacu kvapalinu."
	var power: float = car.get_meta("expedition_engine_factor", 1.0)
	if engine_temp > HOT: power *= lerpf(1.0, 0.6, clampf((engine_temp - HOT) / (SEIZING - HOT), 0.0, 1.0))
	elif engine_temp < 40.0: power *= 0.88
	car.set_meta("expedition_engine_factor", power)

# ---------------------------------------------------------------- the winch

func toggle_winch() -> void:
	message = winch.toggle()

# ---------------------------------------------------------------- the engine

## Off at once; on after the starter, unless the tank is empty.
func toggle_engine() -> void:
	if starting: return
	if car.engine_running:
		car.engine_running = false
		if audio != null: audio.shot("engine_stop", 0.8)
		message = "Motor vypnutý."
		return
	if fuel <= 0.0:
		if audio != null: audio.shot("stall", 0.8)
		message = "Prázdna nádrž: motor nenaskočí."
		return
	if engine_temp > HOT:
		if audio != null: audio.shot("stall", 0.8)
		message = "Motor je ešte prehriaty, nechaj ho vychladnúť."
		return
	if engine_temp < COLD_START and randf() < 0.45:
		if audio != null: audio.shot("stall", 0.8)
		message = "Studený diesel nechytil, skús to znova."
		return
	starting = true
	if audio != null: audio.crank()
	await get_tree().create_timer(audio.STARTER_TIME - 0.2).timeout
	starting = false
	car.engine_running = true

## The box the car can loot from is the bright one.
func _light_box() -> void:
	var near := _nearby()
	if near == lit_site: return
	if boxes.has(lit_site) and is_instance_valid(boxes[lit_site]): (boxes[lit_site].get_node("Box") as MeshInstance3D).set_instance_shader_parameter("near", 0.0)
	lit_site = near
	if boxes.has(near) and is_instance_valid(boxes[near]): (boxes[near].get_node("Box") as MeshInstance3D).set_instance_shader_parameter("near", 1.0)

func _nearby() -> int:
	var pos := Vector2(car.position.x, car.position.z)
	if pos.distance_to(camp) < 18.0: return -2
	for i in sites.size():
		if sites[i].kind == "summit": continue
		# (At the box; a camp also serves all around.)
		var reach: float = 16.0 if sites[i].kind == "camp" else 8.0
		if pos.distance_to(sites[i].point) < reach:
			return -2 if sites[i].kind == "camp" else i
	return -1

func interact() -> void:
	if window != null and window.visible:
		window.visible = false
		return
	var index := _nearby()
	if index >= 0:
		if absf(car.speed) > 1.0:
			message = "Najprv zastavte auto."
			return
		_open_window(index)
		return
	super()

func _unhandled_key_input(event: InputEvent) -> void:
	super(event)
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_N:
		if over_window != null: _close_over()
		else: open_checkpoints()
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_I and not game.paused:
		if window.visible: window.visible = false
		else: _open_window(-1)

## What is left at a site: its loot without what was taken.
func _left_in(i: int) -> Array:
	var gone: Array = taken.get(str(i), [])
	var left := []
	var loot: Array = sites[i].loot
	for n in loot.size():
		if n not in gone: left.append(loot[n])
	return left

func cargo_weight() -> float:
	return super() + tyres.size() * TYRE_SET_KG + chain_sets * CHAIN_KG

func _item_weight(item: Array) -> float:
	match str(item[0]):
		"chains": return CHAIN_KG
		"fuel", "oil", "coolant": return float(item[1])
		"repair": return item[1] * 5.0
	return TYRE_SET_KG

func _item_name(item: Array) -> String:
	var key := str(item[0])
	if key == "chains": return "Snehové reťaze · sada na štyri kolesá"
	if key.begins_with("tyre:"): return "Pneumatiky: " + str(Tyres.TYPES[key.trim_prefix("tyre:")].name)
	return "%s: %d" % [ITEM_NAMES[key], item[1]]

func _take(i: int, n: int) -> void:
	var item: Array = sites[i].loot[n]
	if cargo_weight() + _item_weight(item) > _car_spec().cargo:
		message = "Náklad je plný."
		_refresh_window()
		return
	var key := str(item[0])
	if key == "chains": chain_sets += 1
	elif key.begins_with("tyre:"): tyres.append(key.trim_prefix("tyre:"))
	else: cargo[key] += int(item[1])
	if not taken.has(str(i)): taken[str(i)] = []
	taken[str(i)].append(n)
	if audio != null: audio.shot("find", 0.5)
	var scrap := _scrap_in(item)
	if _left_in(i).is_empty() and i not in explored:
		scrap += SCRAP_SITE
		explored.append(i)
		finds_since_camp += 1
		# Nothing left: the box goes out.
		if boxes.has(i) and is_instance_valid(boxes[i]): boxes[i].visible = false
	_earn(scrap)
	_apply_load()
	_save_game()
	_refresh_window()

func _take_all(i: int) -> void:
	var loot: Array = sites[i].loot
	for n in loot.size():
		if n not in taken.get(str(i), []): _take(i, n)

## Puts a carried set on the car; the one that was on goes into the cargo.
func _fit(kind: String) -> void:
	if absf(car.speed) > 1.0:
		message = "Pneumatiky sa menia len v stoji."
		_refresh_window()
		return
	tyres.erase(kind)
	tyres.append(tyre_kind)
	tyre_kind = kind
	car.fit_tyres(kind, chains_on)
	message = "Nasadené: " + str(Tyres.TYPES[kind].name)
	_apply_load()
	_save_game()
	_refresh_window()

func _toggle_chains() -> void:
	if absf(car.speed) > 1.0:
		message = "Pri montáži reťazí zastav."
	elif chains_on and cargo_weight() + CHAIN_KG > _car_spec().cargo:
		message = "Na odloženie reťazí chýba miesto."
	elif chains_on or chain_sets > 0:
		chains_on = not chains_on
		chain_sets += -1 if chains_on else 1
		car.fit_tyres(tyre_kind, chains_on)
		message = "Reťaze nasadené." if chains_on else "Reťaze odložené."
		_apply_load()
		_save_game()
	_refresh_window()

func _build_window() -> void:
	var layer := CanvasLayer.new()
	layer.layer = 5
	add_child(layer)
	window = PanelContainer.new()
	window.set_anchors_preset(Control.PRESET_CENTER)
	window.grow_horizontal = Control.GROW_DIRECTION_BOTH
	window.grow_vertical = Control.GROW_DIRECTION_BOTH
	window.custom_minimum_size = Vector2(1000, 560) * UI.k()
	window.visible = false
	window.add_theme_stylebox_override("panel", UI.card_style(0.96))
	layer.add_child(window)
	var rows := VBoxContainer.new()
	rows.add_theme_constant_override("separation", roundi(10.0 * UI.k()))
	window.add_child(rows)
	window_title = UI.label("", 48)
	rows.add_child(window_title)
	fitted_label = UI.label("", 26, UI.AMBER)
	fitted_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rows.add_child(fitted_label)
	var columns := HBoxContainer.new()
	columns.add_theme_constant_override("separation", 24)
	columns.size_flags_vertical = Control.SIZE_EXPAND_FILL
	rows.add_child(columns)
	loot_list = _list(columns)
	cargo_list = _list(columns)
	window_note = UI.label("", 26, UI.AMBER)
	window_note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rows.add_child(window_note)
	# Always in reach, however long the list is.
	var bottom := HBoxContainer.new()
	bottom.add_theme_constant_override("separation", roundi(10.0 * UI.k()))
	rows.add_child(bottom)
	take_all_button = UI.button("Vziať všetko", func() -> void:
		if window_site >= 0: _take_all(window_site)
		else: _use_all(), "primary")
	take_all_button.focus_mode = Control.FOCUS_NONE
	take_all_button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bottom.add_child(take_all_button)
	var close := UI.button("ZAVRIEŤ" if game.touch_ui else "ZAVRIEŤ (E)", func() -> void: window.visible = false, "ghost")
	close.focus_mode = Control.FOCUS_NONE
	close.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bottom.add_child(close)

## A column that scrolls when it does not fit (a phone with many tyre sets).
func _list(parent: Node) -> VBoxContainer:
	var scroll := ScrollContainer.new()
	scroll.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.custom_minimum_size.y = 320.0 * UI.k()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	# (Fingers scroll it in _input, wherever on the list they start; a wheel here.)
	scroll.scroll_deadzone = 100000
	scrolls.append(scroll)
	parent.add_child(scroll)
	var list := VBoxContainer.new()
	list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	list.add_theme_constant_override("separation", roundi(8.0 * UI.k()))
	scroll.add_child(list)
	return list

func _input(event: InputEvent) -> void:
	if window == null or not window.visible: return
	if event is InputEventScreenTouch and event.pressed:
		drag_travel = 0.0
		dragged = false
	elif event is InputEventScreenDrag:
		for scroll in scrolls:
			if not scroll.get_global_rect().has_point(event.position): continue
			scroll.scroll_vertical -= roundi(event.relative.y)
			drag_travel += absf(event.relative.y)
			if drag_travel > 14.0: dragged = true

func _open_window(i: int) -> void:
	window_site = i
	window.visible = true
	_refresh_window()

func _button(parent: Node, text: String, action: Callable, kind: String = "ghost", symbol: String = "", usable: bool = true) -> void:
	var button := UI.button(text, func() -> void:
		if not dragged: action.call(), kind, 58.0, 26.0)
	button.alignment = HORIZONTAL_ALIGNMENT_LEFT
	button.focus_mode = Control.FOCUS_NONE
	button.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	button.disabled = not usable
	if symbol != "":
		button.icon = load("res://assets/ui/%s.svg" % symbol)
		button.expand_icon = true
		button.add_theme_constant_override("icon_max_width", roundi(46.0 * UI.k()))
	parent.add_child(button)

func _line(parent: Node, text: String) -> void:
	var label := UI.label(text, 26)
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	parent.add_child(label)

## How much of an item the car can take now (repair kits: one while it is damaged).
func _can_use(key: String) -> int:
	match key:
		"fuel": return mini(cargo.fuel, floori(tank_capacity() - fuel))
		"oil": return mini(cargo.oil, floori(5.0 - oil))
		"coolant": return mini(cargo.coolant, floori(8.0 - coolant))
		"repair": return mini(cargo.repair, 1) if condition < 100.0 else 0
	return 0

## Uses what the cargo has of an item, as far as the car takes it.
func _use(key: String) -> void:
	if absf(car.speed) > 1.0:
		message = "Najprv zastav."
		_refresh_window()
		return
	var amount := _can_use(key)
	match key:
		"fuel": fuel += amount
		"oil": oil += amount
		"coolant": coolant += amount
		"repair": condition = minf(100.0, condition + amount * REPAIR_KIT)
	cargo[key] -= amount
	if audio != null and amount > 0: audio.shot("find", 0.4)
	_apply_load()
	_save_game()
	_refresh_window()

func _use_all() -> void:
	for key: String in ["fuel", "oil", "coolant"]: _use(key)

## Rebuilds the window: the site's goods or the car's state on the left, on the right what is
## carried, each to be used with a tap.
func _refresh_window() -> void:
	for list: VBoxContainer in [loot_list, cargo_list]:
		for child in list.get_children():
			list.remove_child(child)
			child.queue_free()
	var type: Dictionary = Tyres.TYPES[tyre_kind]
	fitted_label.text = "Na aute: %s (%s). Max. rýchlosť %d %%." % [type.name, type.note, roundi(float(car.tyre_type.speed) * 100.0) if not car.tyre_type.is_empty() else roundi(type.speed * 100.0)]
	window_title.text = "INVENTÁR" if window_site < 0 else str(sites[window_site].name)
	if window_site >= 0:
		_line(loot_list, "Tu sa dá vziať:")
		var loot: Array = sites[window_site].loot
		var gone: Array = taken.get(str(window_site), [])
		var any := false
		for n in loot.size():
			if n in gone: continue
			any = true
			var site := window_site
			var which := n
			_button(loot_list, "Vziať · %s" % _item_name(loot[n]), func() -> void: _take(site, which))
		take_all_button.text = "Vziať všetko"
		take_all_button.visible = any
		if not any: _line(loot_list, "Nič tu už nie je.")
	else:
		_line(loot_list, "Nádrž %.0f / %d l" % [fuel, tank_capacity()])
		_line(loot_list, "Olej %.1f / 5 l" % oil)
		_line(loot_list, "Chladiaca kvapalina %.1f / 8 l" % coolant)
		_line(loot_list, "Stav auta %.0f %%" % condition)
		_line(loot_list, "Scrap %d" % Garage.scraps)
		take_all_button.text = "Doplniť všetko"
		take_all_button.visible = _can_use("fuel") + _can_use("oil") + _can_use("coolant") > 0
	_line(cargo_list, "Náklad %.0f / %.0f kg" % [cargo_weight(), _car_spec().cargo])
	_button(cargo_list, "Palivo %d l · do nádrže" % cargo.fuel, _use.bind("fuel"), "ghost", "fuel", _can_use("fuel") > 0)
	_button(cargo_list, "Olej %d l · doliať" % cargo.oil, _use.bind("oil"), "ghost", "oil", _can_use("oil") > 0)
	_button(cargo_list, "Chladiaca %d l · doliať" % cargo.coolant, _use.bind("coolant"), "ghost", "coolant", _can_use("coolant") > 0)
	_button(cargo_list, "Opravné sady %d · opraviť" % cargo.repair, _use.bind("repair"), "ghost", "repairkit", _can_use("repair") > 0)
	if chains_on or chain_sets > 0:
		_button(cargo_list, "Zložiť reťaze" if chains_on else "Nasadiť reťaze · %d súprav" % chain_sets, _toggle_chains, "ghost", "tires")
	var shown := {}
	for kind: String in tyres:
		if shown.has(kind): continue
		shown[kind] = true
		var set_type: Dictionary = Tyres.TYPES[kind]
		var chosen := kind
		_button(cargo_list, "Nasadiť · %s (%s)" % [set_type.name, set_type.note], func() -> void: _fit(chosen), "ghost", "tires")
	window_note.text = message
	window_note.visible = message != ""

func _apply_load() -> void:
	car.payload_kg = cargo_weight() + (CHAIN_KG if chains_on else 0.0) + fuel * 0.84 + oil + coolant

## What the big phone button says where the car stands ("": nothing to do).
func action_text() -> String:
	var near := _nearby()
	if near == -2: return "TÁBOR: DOPLNIŤ A OPRAVIŤ"
	if near >= 0 and near not in explored: return "VZIAŤ · " + str(sites[near].name).to_upper()
	if fuel <= 0.0: return "ODŤAH DO TÁBORA"
	return ""

func _build_hud() -> void:
	super()
	var panel := hud.get_parent() as PanelContainer
	panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	hud.mouse_filter = Control.MOUSE_FILTER_IGNORE
	panel.add_theme_stylebox_override("panel", game._panel_style(0.72))
	hud.add_theme_font_override("font", UI.FONT)
	if game.touch_ui:
		panel.visible = false
		phone_hud = preload("res://offroad/phone_hud.gd").new()
		add_child(phone_hud)
		phone_hud.setup(game)
	else:
		hud.add_theme_font_size_override("font_size", 24)

func _update_hud() -> void:
	if message != last_message:
		last_message = message
		message_clock = MESSAGE_TIME
	message_clock = maxf(0.0, message_clock - get_process_delta_time())
	var shown: String = message if message_clock > 0.0 else ""
	if game.touch_ui:
		hud_clock -= get_process_delta_time()
		if hud_clock > 0.0: return
		hud_clock = 0.15
	var near := _nearby()
	var prompt: String = "E · tábor: doplniť a opraviť" if near == -2 else ("E · preskúmať: " + sites[near].name if near >= 0 else ("E · núdzový odťah do tábora" if fuel <= 0.0 else ""))
	var warning := "Málo oleja alebo chladiacej kvapaliny: znížený výkon!" if oil < 0.5 or coolant < 0.5 else ""
	if engine_temp > HOT: warning = "Motor sa prehrieva: uber plyn alebo zastav."
	var kmh := absf(car.speed) * 3.6
	var burn := "%.0f l/100 km" % (burn_rate / kmh * 100.0) if kmh > 6.0 else "%.1f l/h" % burn_rate
	var fuel_text := "Palivo %.0f / %d l · %s · Stav %.0f %%" % [fuel, tank_capacity(), burn, condition]
	if game.touch_ui:
		phone_hud.refresh({
			tank = tank_capacity(), fuel = fuel, burn = burn, condition = condition, warning = warning, message = shown,
			oil = oil, coolant = coolant, engine = engine_temp, outside = game.test_grounds.weather.temperature,
			place = "Prejdené %.1f km · výška %d m" % [distance / 1000.0, roundi(car.position.y)],
			weather = game.test_grounds.weather.weather_text().replace("   (K)", ""),
			fluids = "Olej %.1f · Chladiaca %.1f · %s" % [oil, coolant, Tyres.TYPES[tyre_kind].name], scraps = Garage.scraps})
		return
	var switches := "Motor %s %d °C · Vonku %d °C · Stierače %s (M, U) · N skok (test)" % ["zap." if car.engine_running else "VYP.", roundi(engine_temp), roundi(game.test_grounds.weather.temperature), "zap." if car.wipers_on else "vyp."]
	hud.text = "VÝPRAVA · seed %d\nPrejdené %.2f km · rekord %.2f km · výška %d m\n%s\n%s\nOlej %.1f / 5 l · Chladiaca %.1f / 8 l\nPneumatiky: %s · Náklad %.0f / %.0f kg (I)\nB · doplniť kvapaliny zo zásob\n%s\n%s\n%s\n%s" % [seed_value, distance / 1000.0, record / 1000.0, roundi(car.position.y), game.test_grounds.weather.weather_text(), fuel_text, oil, coolant, Tyres.TYPES[tyre_kind].name, cargo_weight(), _car_spec().cargo, switches, warning, prompt, shown]

func _save_more() -> Dictionary:
	var line: PackedVector2Array = T.roads[0].points
	var offset: float = line[clampi(road_sample, 0, line.size() - 1)].distance_to(Vector2(car.position.x, car.position.z))
	return {"road_offset": offset, "distance": distance, "record": record, "tyres": tyres, "tyre_kind": tyre_kind, "chains_on": chains_on, "chain_sets": chain_sets, "taken": taken, "start": T.start_biome, "car": game.lead_model, "jumped": jumped, "engine_temp": engine_temp}

func _load_more(data: Dictionary) -> void:
	resumed = true
	world_changed = int(data.get("world", 0)) != T.CACHE_VERSION
	saved_offset = float(data.get("road_offset", 1000.0))
	distance = float(data.get("distance", 0.0))
	record = float(data.get("record", 0.0))
	paid_km = floori(record / 1000.0)
	jumped = bool(data.get("jumped", false))
	engine_temp = float(data.get("engine_temp", 60.0))
	tyres = data.get("tyres", [])
	tyre_kind = str(data.get("tyre_kind", "road"))
	chain_sets = int(data.get("chain_sets", 0)) + tyres.count("chains")
	while tyres.has("chains"): tyres.erase("chains")
	chains_on = bool(data.get("chains_on", false)) or tyre_kind == "chains"
	if tyre_kind == "chains": tyre_kind = "winter"
	# (JSON numbers come back as floats.)
	taken = {}
	var saved: Dictionary = data.get("taken", {})
	for key in saved: taken[key] = saved[key].map(func(n) -> int: return int(n))
