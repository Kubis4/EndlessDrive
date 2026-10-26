extends Node3D

const Locale = preload("res://scripts/localization.gd")

@export_group("Desert overview camera")
@export_range(30.0, 90.0, 1.0) var overview_size := 53.0
@export var overview_offset := Vector3(-36, 52, 44)

var run: DriveRun
var world: DriveWorld
var vehicle: DriveVehicle
var hud: DriveHUD
var camera: Camera3D
var sun: DirectionalLight3D
var environment: Environment
var _save_timer := 0.0
var _save_worker: Thread
var _camera_mode := 0
var _capturing := false
var _capture_path := ""
var _capture_frames := 0
var _ephemeral := false
var _focus_pause_enabled := true
var _camera_focus := Vector3.ZERO
var _camera_grade_offset := 0.0
var active_building_id := -1
var menu: DriveMenu
var _in_menu := false
var _garage_from_menu := false
var _session_started := false
var _had_save := false
var _camera_preference := 1.0
var _volume_preference := 0.8
var _language_preference := 0
var _quality_preference := 1 if OS.has_feature("mobile") else 2
var _fps_preference := 30 if OS.has_feature("mobile") else 60
var _paint_materials: Array[ShaderMaterial] = []
var weather: DriveWeather
var effects: DriveEffects
var winch: Node3D
var profile: DriveProfile
var route_decisions_enabled := true
var _sun_day_tint := Color("f0ede6")
var _sky_last_daylight := -1.0
var _last_dust_visibility := -1.0
var route_origin := Vector3.ZERO
var origin_rebasing_enabled := true
const ORIGIN_STEP := 2048.0
var _last_mechanical_events := 0
var _renderer_primer: SubViewport


func _ready() -> void:
	_register_inputs()
	get_tree().auto_accept_quit = false
	for argument: String in OS.get_cmdline_user_args():
		if argument.begins_with("--capture="):
			_capture_path = argument.trim_prefix("--capture=")
		if argument == "--fresh" or argument == "--test-session":
			_ephemeral = true
	run = DriveRun.new()
	origin_rebasing_enabled = not _ephemeral
	profile = DriveProfile.new() if _ephemeral else DriveProfile.restore()
	var saved := {} if _ephemeral else DriveSave.restore()
	_had_save = not saved.is_empty()
	_session_started = _ephemeral
	if not saved.is_empty():
		run = DriveRun.from_data(saved)
	elif not _ephemeral:
		run.world_seed = DriveTerrain.new_journey_seed()
		profile.prepare_run(run)
	_setup_lighting()
	world = DriveWorld.new()
	world.graphics_quality = _quality_preference
	add_child(world)
	world.configure(run.world_seed)
	world.terrain.soil_moisture = clampf(float(run.weather_state.get("wetness", 0.0)), 0, 1)
	world.terrain.route_choices = run.route_choices.duplicate()
	world.restore_deformation(run.terrain_deformation)
	world.clock = run.elapsed
	world.hazards_enabled = not _ephemeral
	var pose := Vector3(0, 1.0, world.center_at(0))
	var heading := 0.0
	if not saved.is_empty():
		pose = Vector3(saved.vehicle.x, saved.vehicle.y, saved.vehicle.z)
		heading = float(saved.vehicle.heading)
	pose = world.safe_position(pose)
	world.update_stream(pose.x)
	vehicle = get_node_or_null("Vehicle") as DriveVehicle
	var new_vehicle := vehicle == null
	if new_vehicle: vehicle = load("res://scenes/vehicle.tscn").instantiate() as DriveVehicle
	vehicle.run = run
	vehicle.world = world
	vehicle.position = pose
	vehicle.position.y = world.height_at(pose.x, pose.z) + 0.6
	vehicle.heading = heading
	if new_vehicle: add_child(vehicle)
	vehicle.reset_physics_interpolation()
	_collect_paint(vehicle._visual)
	_apply_paint()
	camera = get_node_or_null("Camera3D") as Camera3D
	if camera != null:
		# Authored camera position and size are the real follow-rig defaults.
		overview_offset = camera.position
		overview_size = camera.size
	else:
		camera = Camera3D.new()
		add_child(camera)
	camera.current = true
	camera.physics_interpolation_mode = Node.PHYSICS_INTERPOLATION_MODE_OFF
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = overview_size
	camera.far = 260.0
	camera.near = 0.3
	_update_camera(1.0, true)
	hud = get_node_or_null("HUD") as DriveHUD
	if hud == null:
		hud = load("res://scenes/ui/hud.tscn").instantiate() as DriveHUD
		add_child(hud)
	hud.run = run
	hud.vehicle = vehicle
	hud.world = world
	hud.requested.connect(_action)
	hud.touch_changed.connect(vehicle.set_touch)
	hud.visible = true
	menu = get_node_or_null("MainMenu") as DriveMenu
	if menu == null:
		menu = load("res://scenes/ui/main_menu.tscn").instantiate() as DriveMenu
		add_child(menu)
	menu.requested.connect(_menu_action)
	menu.setting_changed.connect(_settings_changed)
	menu.hide_menu()
	_load_settings()
	effects = DriveEffects.new()
	effects.vehicle = vehicle
	effects.world = world
	add_child(effects)
	weather = DriveWeather.new()
	weather.run = run
	weather.vehicle = vehicle
	weather.world = world
	add_child(weather)
	hud.weather = weather
	_apply_video_settings()
	winch = preload("res://scripts/winch.gd").new()
	winch.vehicle = vehicle
	winch.world = world
	add_child(winch)
	_last_mechanical_events = run.mechanical_events
	_rebase_world(true)
	world.async_streaming = not _ephemeral
	route_decisions_enabled = not _ephemeral
	if not saved.is_empty():
		hud.toast(tr("Cesta obnovená. Auto stojí a motor je vypnutý."))
	if "--night" in OS.get_cmdline_user_args():
		run.day_time = 0.82
		run.headlights = true
	if not _ephemeral:
		_open_menu(false)
		if DisplayServer.get_name() != "headless":
			var light_primer := preload("res://scripts/light_primer.gd").new()
			light_primer.vehicle = vehicle
			light_primer.environment = environment
			add_child(light_primer)
			_prime_renderer()


func _prime_renderer() -> void:
	if _ephemeral or DisplayServer.get_name() == "headless": return
	if is_instance_valid(_renderer_primer): _renderer_primer.queue_free()
	_renderer_primer = load("res://scripts/render_warmup.gd").new() as SubViewport
	_renderer_primer.environment_template = environment
	_renderer_primer.vehicle_template = vehicle._visual
	add_child(_renderer_primer)


func _process(dt: float) -> void:
	_finish_auto_save()
	_rebase_world()
	if run.mechanical_events > _last_mechanical_events:
		_last_mechanical_events = run.mechanical_events
		hud.toast(run.last_mechanical_event)
	if route_decisions_enabled:
		var choice := world.choose_route(vehicle.position)
		if not choice.is_empty():
			run.route_choices = world.terrain.route_choices.duplicate()
			_auto_save()
	world.update_stream(vehicle.position.x)
	_update_camera(dt)
	_update_lighting()
	_save_timer += dt
	if _save_timer >= 20.0:
		_save_timer = 0.0
		_auto_save()
	if not _capture_path.is_empty() and not _capturing:
		_capture_frames += 1
		if _capture_frames >= 20:
			_capturing = true
			_capture()


func _action(action: String) -> void:
	match action:
		"menu":
			_open_menu()
		"engine":
			hud.toast(run.toggle_engine())
			if not run.engine_running: vehicle.cancel_cruise()
		"lights":
			if not run.headlights:
				run.headlights = true
				run.high_beams = false
			elif not run.high_beams: run.high_beams = true
			else:
				run.headlights = false
				run.high_beams = false
		"high_beams":
			run.high_beams = not run.high_beams
			run.headlights = true
		"drain_intake":
			run.current_water_depth = world.terrain.water_depth(vehicle.position.x, vehicle.position.z)
			hud.toast(tr(run.drain_intake()))
			hud.refresh_service()
		"snorkel":
			var stop := world.nearest_building(vehicle.position)
			if stop.is_empty(): hud.toast(tr("Šnorchel namontujú v dielni alebo autoservise."))
			else: hud.toast(tr(run.install_snorkel(int(stop.id))))
			hud.refresh_service()
		"camera":
			_camera_mode = (_camera_mode + 1) % 3
		"cruise":
			vehicle.toggle_cruise()
		"drive_mode":
			hud.toast(tr(vehicle.toggle_drive_mode()))
		"differential":
			hud.toast(tr(vehicle.toggle_differential()))
		"winch":
			hud.toast(tr(winch.toggle_attachment()))
		"winch_pull":
			hud.toast(tr(winch.toggle_reel()))
		"search":
			if absf(vehicle.speed) > 0.5:
				hud.toast("Pred prehľadaním budovy zastav.")
				return
			var building := world.nearest_building(vehicle.position)
			if building.is_empty():
				hud.toast("Zastav bližšie pri budove.")
			else:
				active_building_id = int(building.id)
				run.open_loot(active_building_id)
				hud.set_loot_building(active_building_id, str(building.title))
				_modal("loot")
		"service":
			if absf(vehicle.speed) > 0.5:
				hud.toast("Pred opravou auta zastav.")
			else:
				_modal("service")
		"pause":
			_modal("pause")
		"resume":
			if _garage_from_menu:
				_garage_from_menu = false
				_open_menu()
				return
			active_building_id = -1
			hud.show_modal("")
			get_tree().paused = false
			vehicle.clear_inputs()
			vehicle.controls_enabled = true
			_save(false)
		"save":
			_save(true)
		"new", "restart":
			_finish_auto_save(true)
			winch.disconnect_cable()
			_in_menu = false
			_garage_from_menu = false
			_session_started = true
			menu.hide_menu()
			hud.visible = true
			get_tree().paused = false
			var previous_seed: int = run.world_seed
			profile.best_distance = maxf(profile.best_distance, run.distance)
			if not _ephemeral: profile.store()
			run = DriveRun.new()
			_last_mechanical_events = 0
			run.world_seed = previous_seed if action == "restart" else DriveTerrain.new_journey_seed(previous_seed)
			if not _ephemeral: profile.prepare_run(run)
			active_building_id = -1
			world.async_streaming = false
			world.configure(run.world_seed)
			world.update_stream(0)
			world.async_streaming = not _ephemeral
			vehicle.run = run
			weather.run = run
			weather.reset_weather()
			_apply_paint()
			vehicle.park()
			vehicle.heading = 0.0
			var start_z := world.center_at(0)
			vehicle.position = Vector3(0, world.height_at(0, start_z) + 0.55, start_z)
			vehicle.reset_physics_interpolation()
			effects.reset_trails()
			vehicle.controls_enabled = true
			hud.run = run
			hud.show_modal("")
			_rebase_world(true)
			_update_camera(1.0, true)
			_save(false)
			hud.toast(tr("Cesta reštartovaná.") if action == "restart" else tr("Nová cesta. Motor čaká na teba."))
		"quit":
			if _save(true):
				get_tree().quit()
		_:
			if action == "take_selected":
				_take_selected_loot()
				return
			if action.begins_with("fluid:") and hud.modal_open:
				hud.toast(run.top_up(action.trim_prefix("fluid:")))
			elif action.begins_with("repair:") and hud.modal_open:
				hud.toast(run.repair(action.trim_prefix("repair:")))
			elif action.begins_with("install:") and hud.modal_open:
				hud.toast(run.install_spare(action.trim_prefix("install:")))
			elif action.begins_with("salvage:") and hud.modal_open:
				hud.toast(run.salvage_spare(action.trim_prefix("salvage:")))
			elif action == "usekit" and hud.modal_open:
				hud.toast(run.use_supply("repairkit", hud._selected_part))
			elif action == "use:batterycell" and hud.modal_open:
				hud.toast(run.use_supply("batterycell"))
			elif action in ["pump", "shop_repair"] and hud.modal_open:
				var stop := world.nearest_building(vehicle.position)
				if stop.is_empty(): hud.toast("Zastav bližšie pri prevádzke.")
				elif action == "pump": hud.toast(run.buy_fuel(int(stop.id)))
				else: hud.toast(run.repair_at_shop(int(stop.id), hud._selected_part))
			elif action.begins_with("paint:") and hud.modal_open:
				var stop := world.nearest_building(vehicle.position)
				hud.toast(run.paint_vehicle(action.trim_prefix("paint:"), int(stop.get("id", -1))))
				_apply_paint()
			hud.refresh_service()
			if hud.modal_open:
				_save(false)


func _take_selected_loot() -> void:
	if active_building_id == -1 or not hud.modal_open or hud._modal_kind != "loot":
		return
	var moved := 0
	var message := "Najprv označ zásoby, ktoré chceš vziať."
	var refused := ""
	for selection: Dictionary in hud.selected_loot():
		var result := run.take_loot(active_building_id, str(selection.uid), float(selection.amount))
		if result.ok:
			moved += 1
		else:
			refused = str(result.message)
			message = refused
	if moved > 0:
		message = "Zobraté položky: %d. Ostatné zásoby zostávajú v budove." % moved
		if not refused.is_empty(): message += " " + refused
	hud.toast(message)
	hud.refresh_loot()
	hud.refresh_service()
	_save(false)


func _modal(kind: String) -> void:
	if winch != null: winch.stop_reeling()
	vehicle.clear_inputs()
	hud._message_time = 0.0
	if kind in ["service", "loot"]: vehicle.park()
	vehicle.controls_enabled = false
	hud.show_modal(kind)
	get_tree().paused = true
	_save(false)


func _save(show_message: bool) -> bool:
	_finish_auto_save(true)
	world.flush_soil_updates()
	run.terrain_deformation = world.terrain.deformation.to_data()
	if _ephemeral or (not _session_started and not _had_save):
		return true
	var result := DriveSave.store(run, vehicle.position, vehicle.heading)
	if result != OK:
		hud.toast("Cestu sa nepodarilo uložiť: %s" % error_string(result))
		return false
	if run.distance > profile.best_distance:
		profile.best_distance = run.distance
		profile.store()
	if show_message:
		hud.toast("Cesta uložená.")
	return true


func _auto_save(path: String = DriveSave.PATH) -> void:
	if _ephemeral or (not _session_started and not _had_save) or _save_worker != null: return
	var snapshot := DriveDeformation.new()
	var field := world.terrain.deformation
	snapshot.cells = field.cells.duplicate()
	snapshot._order = field._order.slice(field._order_head)
	snapshot._order_head = 0
	_save_worker = Thread.new()
	_save_worker.start(DriveSave.store_soil_snapshot.bind(run.to_data(false), snapshot, vehicle.position, vehicle.heading, path), Thread.PRIORITY_LOW)


func _finish_auto_save(wait: bool = false) -> void:
	if _save_worker == null or (not wait and _save_worker.is_alive()): return
	var result: int = _save_worker.wait_to_finish()
	_save_worker = null
	if result != OK:
		hud.toast("Cestu sa nepodarilo uložiť: %s" % error_string(result))
	elif run.distance > profile.best_distance:
		profile.best_distance = run.distance
		profile.store()


func _exit_tree() -> void:
	if _save_worker != null:
		_save_worker.wait_to_finish()
		_save_worker = null


func _open_menu(save_before: bool = true) -> void:
	if save_before and not _save(false): return
	if winch != null: winch.stop_reeling()
	vehicle.park()
	vehicle.controls_enabled = false
	hud.show_modal("")
	hud.visible = false
	_in_menu = true
	menu.set_parts(run.parts)
	menu.best_distance = maxf(profile.best_distance, run.distance)
	menu.show_menu(_had_save or _session_started, run.distance)
	get_tree().paused = true


func _menu_action(action: String) -> void:
	if action == "deposit":
		_deposit_scrap()
		return
	if action.begins_with("upgrade:"):
		_buy_upgrade(action.trim_prefix("upgrade:"))
		return
	match action:
		"continue":
			if not (_had_save or _session_started): return
			_session_started = true
			_in_menu = false
			menu.hide_menu()
			hud.visible = true
			_action("resume")
		"new": _action("new")
		"garage":
			if not (_had_save or _session_started): return
			_in_menu = false
			_garage_from_menu = true
			menu.hide_menu()
			hud.visible = true
			_modal("service")
		"upgrades": _show_upgrades()
		"quit": _action("quit")


func _load_settings() -> void:
	var config := ConfigFile.new()
	if not _ephemeral and config.load("user://preferences.cfg") == OK:
		var volume: Variant = config.get_value("audio", "volume", 0.8)
		var zoom: Variant = config.get_value("view", "camera", 1.0)
		var language: Variant = config.get_value("language", "index", 0)
		var quality: Variant = config.get_value("view", "quality", _quality_preference)
		var fps: Variant = config.get_value("view", "fps", _fps_preference)
		if (volume is float or volume is int) and is_finite(float(volume)): _volume_preference = clampf(float(volume), 0.0, 1.0)
		if (zoom is float or zoom is int) and is_finite(float(zoom)): _camera_preference = clampf(float(zoom), 0.76, 1.3)
		if (language is float or language is int) and is_finite(float(language)): _language_preference = clampi(int(language), 0, 1)
		if (quality is float or quality is int) and is_finite(float(quality)): _quality_preference = clampi(int(quality), 0, 2)
		if (fps is float or fps is int) and is_finite(float(fps)) and int(fps) in [0, 30, 60]: _fps_preference = int(fps)
	Locale.configure(_language_preference)
	AudioServer.set_bus_volume_db(0, linear_to_db(maxf(_volume_preference, 0.0001)))
	menu.set_settings(_volume_preference, _camera_preference)
	menu.set_language(_language_preference)
	menu.set_video_settings(_quality_preference, _fps_preference)


func _apply_video_settings() -> void:
	preload("res://scripts/quality_settings.gd").apply(self, _quality_preference)
	# Benchmarks/test sessions own their clocks; never cap a fixed-step test.
	if not _ephemeral: Engine.max_fps = _fps_preference
	if menu != null: menu.set_video_settings(_quality_preference, _fps_preference)


func _show_upgrades() -> void:
	menu.show_panel("upgrades")
	menu._upgrade_bank.text = Locale.translate("ŠROT V GARÁŽI: %d\nVylepšenia sa použijú pri NOVEJ jazde. Aktuálne auto si zachová svoj stav." % profile.bank)
	var available := maxi(0, mini(int(run.inventory.scrap), run.found_scrap - run.deposited_scrap))
	menu._deposit_button.text = Locale.translate("ODLOŽIŤ NAJDENÝ ŠROT Z JAZDY: %d" % available)
	menu._deposit_button.disabled = available <= 0
	for slot: String in profile.upgrades:
		var button: Button = menu._upgrade_buttons[slot]
		button.text = Locale.translate("%s · ÚROVEŇ %d/3\n%d ŠROTU · LEPŠÍ ŠTART" % [DriveRun.PART_NAMES[slot], profile.upgrades[slot], profile.price(slot)])
		button.disabled = profile.bank < profile.price(slot) or int(profile.upgrades[slot]) >= 3
	menu._layout()


func _deposit_scrap() -> void:
	var before := profile.bank
	var receipts := profile.receipts.duplicate()
	var stored := run.deposited_scrap
	var carried := float(run.inventory.scrap)
	profile.deposit(run)
	if _ephemeral or profile.store() == OK:
		_save(false)
	else:
		profile.bank = before
		profile.receipts = receipts
		run.deposited_scrap = stored
		run.inventory.scrap = carried
	_show_upgrades()


func _buy_upgrade(slot: String) -> void:
	if not profile.upgrades.has(slot): return
	var before := profile.bank
	var level := int(profile.upgrades[slot])
	if profile.buy(slot) and not _ephemeral and profile.store() != OK:
		profile.bank = before
		profile.upgrades[slot] = level
	_show_upgrades()


func _settings_changed(key: String, value: float) -> void:
	if not is_finite(value): return
	if key == "volume":
		_volume_preference = clampf(value, 0.0, 1.0)
		AudioServer.set_bus_volume_db(0, linear_to_db(maxf(value, 0.0001)))
	elif key == "camera":
		_camera_preference = clampf(value, 0.76, 1.3)
		_update_camera(1.0, true)
	elif key == "language":
		_language_preference = clampi(roundi(value), 0, 1)
		Locale.configure(_language_preference)
		menu.set_language(_language_preference)
		menu.set_video_settings(_quality_preference, _fps_preference)
	elif key == "quality":
		var changed := _quality_preference != clampi(roundi(value), 0, 2)
		_quality_preference = clampi(roundi(value), 0, 2)
		_apply_video_settings()
		if changed: _prime_renderer()
	elif key == "fps" and roundi(value) in [0, 30, 60]:
		_fps_preference = roundi(value)
		_apply_video_settings()
	if _ephemeral: return
	var config := ConfigFile.new()
	config.set_value("audio", "volume", _volume_preference)
	config.set_value("view", "camera", _camera_preference)
	config.set_value("language", "index", _language_preference)
	config.set_value("view", "quality", _quality_preference)
	config.set_value("view", "fps", _fps_preference)
	var result := config.save("user://preferences.cfg")
	if result != OK: hud.toast("Nastavenia sa nepodarilo uložiť.")


func _collect_paint(node: Node) -> void:
	if node is MeshInstance3D:
		var source := node.material_override as ShaderMaterial
		if source != null and source.resource_name == "Desert_paint":
			var copy := source.duplicate() as ShaderMaterial
			node.material_override = copy
			_paint_materials.append(copy)
	for child: Node in node.get_children(): _collect_paint(child)


func _apply_paint() -> void:
	for material: ShaderMaterial in _paint_materials: material.set_shader_parameter("tint", Color(run.paint_color))


func _notification(what: int) -> void:
	if vehicle == null or hud == null:
		return
	if what == NOTIFICATION_APPLICATION_FOCUS_OUT or what == NOTIFICATION_APPLICATION_PAUSED:
		if _focus_pause_enabled and not _in_menu: _modal("pause")
	elif what == NOTIFICATION_WM_CLOSE_REQUEST:
		_action("quit")
	elif what == NOTIFICATION_WM_GO_BACK_REQUEST:
		if not _in_menu: _action("resume" if hud.modal_open else "pause")


func _setup_lighting() -> void:
	var authored := get_node_or_null("WorldEnvironment") as WorldEnvironment
	if authored != null:
		environment = authored.environment.duplicate(true)
		authored.environment = environment
		sun = get_node("Sun") as DirectionalLight3D
		_sun_day_tint = sun.light_color
		return
	var sky_material := ProceduralSkyMaterial.new()
	sky_material.sky_top_color = Color("809da8")
	sky_material.sky_horizon_color = Color("bbc7c6")
	sky_material.ground_bottom_color = Color("858073")
	sky_material.ground_horizon_color = Color("ada898")
	var sky := Sky.new()
	sky.sky_material = sky_material
	environment = Environment.new()
	environment.background_mode = Environment.BG_SKY
	environment.sky = sky
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_sky_contribution = 0.0
	environment.ambient_light_color = Color("c8cccb")
	environment.ambient_light_energy = 0.28
	environment.tonemap_mode = Environment.TONE_MAPPER_LINEAR
	environment.fog_enabled = false
	environment.fog_light_color = Color("d0b68b")
	environment.fog_density = 0.0012
	var node := WorldEnvironment.new()
	node.environment = environment
	add_child(node)
	sun = DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-28, 75, 0)
	sun.light_color = Color("f0ede6")
	sun.light_energy = 0.85
	sun.shadow_bias = 0.3
	sun.shadow_normal_bias = 3.0
	sun.shadow_enabled = true
	sun.directional_shadow_max_distance = 120.0
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
	add_child(sun)


func _update_lighting() -> void:
	var daylight := clampf(sin((run.day_time - 0.23) * TAU) * 2.0, 0.0, 1.0)
	sun.light_energy = lerpf(0.28, 0.85, daylight)
	sun.light_color = Color("b1c6df").lerp(_sun_day_tint, daylight)
	sun.rotation.x = -0.4 - daylight * 0.12
	environment.ambient_light_energy = lerpf(0.55, 0.28, daylight)
	environment.ambient_light_color = Color("9eb6d6").lerp(Color("c8cccb"), daylight)
	# Weather is expressed by precipitation, wind and the local dust front.
	# Keep the landscape clear while the material palettes set its colour.
	var dust_visibility := weather.storm_visibility if weather != null else 0.0
	if absf(dust_visibility - _last_dust_visibility) > 0.005:
		_last_dust_visibility = dust_visibility
		environment.fog_enabled = dust_visibility > 0.02
		environment.fog_light_color = Color("b49b72")
		environment.fog_density = dust_visibility * 0.012
	if weather != null:
		sun.light_energy *= 1.0 - weather.intensity * 0.18
	var sky_material := environment.sky.sky_material as ProceduralSkyMaterial
	# Every tiny colour write invalidates the sky's reflection cubemap. Quantize
	# below perceptible colour differences instead of rebuilding it every frame.
	if absf(daylight - _sky_last_daylight) > 0.003:
		_sky_last_daylight = daylight
		sky_material.sky_top_color = Color("050c19").lerp(Color("809da8"), daylight)
		sky_material.sky_horizon_color = Color("182936").lerp(Color("bbc7c6"), daylight)
		sky_material.ground_horizon_color = sky_material.sky_horizon_color


func _update_camera(dt: float, instant: bool = false) -> void:
	# Fixed world azimuth: steering rotates the vehicle, never the landscape.
	var car_position := vehicle.position if instant else to_local(vehicle.get_global_transform_interpolated().origin)
	var target := car_position + Vector3(2.0, 0.4, -1.0)
	if instant: vehicle.reset_physics_interpolation()
	_camera_focus = target if instant else _camera_focus.lerp(target, 1.0 - exp(-4.0 * dt))
	camera.position = _camera_focus + overview_offset
	# Tyre compaction must not make the entire camera bob every soil update.
	var ahead := world.height_at(car_position.x + 5, car_position.z) + world.terrain.deformation.depth_at(car_position.x + 5, car_position.z)
	var behind := world.height_at(car_position.x - 5, car_position.z) + world.terrain.deformation.depth_at(car_position.x - 5, car_position.z)
	var wanted_grade := clampf((ahead - behind) * 0.8, -4.0, 4.0)
	_camera_grade_offset = wanted_grade if instant else lerpf(_camera_grade_offset, wanted_grade, 1.0 - exp(-3.0 * dt))
	camera.position.y += _camera_grade_offset
	camera.look_at(to_global(_camera_focus), Vector3.UP)
	var zoom: float = overview_size * _camera_preference * [1.0, 0.76, 1.3][_camera_mode]
	camera.size = zoom if instant else lerpf(camera.size, zoom, 1.0 - exp(-5.0 * dt))


func _rebase_world(force: bool = false) -> void:
	if not origin_rebasing_enabled: return
	var wanted := Vector3(roundf(vehicle.position.x / ORIGIN_STEP) * ORIGIN_STEP, 0, roundf(vehicle.position.z / ORIGIN_STEP) * ORIGIN_STEP)
	if not force and Vector2(vehicle.position.x - route_origin.x, vehicle.position.z - route_origin.z).length() < ORIGIN_STEP * 1.25: return
	if wanted == route_origin: return
	route_origin = wanted
	position = -route_origin
	world.set_route_origin(route_origin)
	vehicle.reset_physics_interpolation()
	if effects != null: effects.reset_particles()
	if weather != null: weather.reset_particles()
	# Camera focus/zoom are logical route coordinates too. Keep the current
	# follow lag and lens through a rebase instead of snapping back to the car.
	camera.reset_physics_interpolation()


func _register_inputs() -> void:
	var keys := {
		"throttle": [KEY_W, KEY_UP], "brake": [KEY_S, KEY_DOWN],
		"left": [KEY_A, KEY_LEFT], "right": [KEY_D, KEY_RIGHT],
		"engine": [KEY_E], "search": [KEY_F], "service": [KEY_TAB],
		"lights": [KEY_L], "camera": [KEY_C], "pause": [KEY_ESCAPE],
		"cruise": [KEY_R],
		"drive_mode": [KEY_V], "differential": [KEY_B],
		"winch": [KEY_G], "winch_pull": [KEY_H],
		"handbrake": [KEY_SPACE], "high_beams": [KEY_K],
	}
	for action: String in keys:
		if not InputMap.has_action(action):
			InputMap.add_action(action)
		for key: int in keys[action]:
			var event := InputEventKey.new()
			event.physical_keycode = key
			if not InputMap.action_has_event(action, event):
				InputMap.action_add_event(action, event)


func _capture() -> void:
	await RenderingServer.frame_post_draw
	var result := get_viewport().get_texture().get_image().save_png(_capture_path)
	print("CAPTURE: ", error_string(result), " ", _capture_path)
	get_tree().quit(0 if result == OK else 1)
