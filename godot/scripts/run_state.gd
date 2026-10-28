class_name DriveRun
extends RefCounted
## Engine-independent survival rules. SI units; no rendering or UI dependencies.
## First migration slice, not a replacement for the full Kotlin simulation yet.

const SAVE_VERSION := 6
const FUEL_CAPACITY := 40.0
const OIL_CAPACITY := 4.0
const COOLANT_CAPACITY := 6.0
const CARGO_SLOTS := 10
const CARGO_MAX_WEIGHT := 230.0
const RESOURCE_NAMES := {"fuel": "Palivo", "oil": "Motorový olej", "coolant": "Chladiaca voda", "scrap": "Šrot", "repairkit": "Servisná sada", "batterycell": "Štartovací zdroj", "paintcan": "Autolak"}
const RESOURCE_WEIGHTS := {"fuel": 0.75, "oil": 0.88, "coolant": 1.0, "scrap": 0.0, "repairkit": 2.0, "batterycell": 4.0, "paintcan": 1.2}
const FLUIDS := ["fuel", "oil", "coolant"]
const VARIANTS := {"engine": ["standard", "efficient", "torque"], "tires": ["road", "allterrain", "winter"], "brakes": ["drum", "disc", "vented"], "suspension": ["stock", "reinforced", "comfort"], "radiator": ["standard", "large"], "alternator": ["stock", "highoutput"]}
const VARIANT_NAMES := {"standard": "Štandard", "efficient": "Úsporný", "torque": "Ťažný", "road": "Cestné", "allterrain": "Terénne", "winter": "Zimné", "drum": "Bubnové", "disc": "Kotúčové", "vented": "Odvetrávané", "stock": "Sériové", "reinforced": "Zosilnené", "comfort": "Komfortné", "large": "Veľký", "highoutput": "Výkonný"}
const PAINTS := {"Piesková": "b9baaa", "Medená": "ba744a", "Lesná": "547660", "Modrá": "577c91", "Biela": "d8d8c9", "Červená": "a54e43"}
const PART_WEIGHTS := {"engine": 50.0, "tires": 18.0, "brakes": 6.0, "suspension": 12.0, "radiator": 7.0, "alternator": 5.0}
const PART_NAMES := {
	"engine": "Motor", "tires": "Pneumatiky", "brakes": "Brzdy",
	"suspension": "Odpruženie", "radiator": "Chladič", "alternator": "Alternátor",
}

var world_seed: int = 24601
# Legacy standalone fixtures/saves are Jeep; a new profile prepares the IZH.
var vehicle_id := "jeep"
var terrain_deformation: Array = []
var distance := 0.0
var fuel := 7.0
var oil := 2.6
var coolant := 3.8
var battery := 0.82
var temperature := 24.0
var engine_running := false
var headlights := false
var high_beams := false
var snorkel_installed := false
var water_ingress := 0.0
var current_water_depth := 0.0
var four_wheel_drive := true
var differential_locked := false
var elapsed := 0.0
var day_time := 0.31
var parts: Dictionary = {
	"engine": 0.68, "tires": 0.76, "brakes": 0.72,
	"suspension": 0.65, "radiator": 0.67, "alternator": 0.75,
}
var variants: Dictionary = {"engine": "standard", "tires": "road", "brakes": "drum", "suspension": "stock", "radiator": "standard", "alternator": "stock"}
var brake_type: String:
	get: return str(variants.brakes)
	set(value):
		if value in VARIANTS.brakes: variants.brakes = value
var fluid_quality: Dictionary = {"fuel": 1.0, "oil": 1.0, "coolant": 1.0}
var inventory_quality: Dictionary = {"fuel": 1.0, "oil": 1.0, "coolant": 1.0}
var inventory: Dictionary = {"fuel": 6.0, "oil": 1.0, "coolant": 2.0, "scrap": 8.0, "repairkit": 0.0, "batterycell": 0.0, "paintcan": 0.0}
var station_stock: Dictionary = {}
var paint_color := "b9baaa"
var route_choices: Dictionary = {}
var weather_state: Dictionary = {}
var run_id := "%d-%d" % [Time.get_unix_time_from_system(), randi()]
var found_scrap := 0
var deposited_scrap := 0
var searched: Dictionary = {}
var loot_piles: Dictionary = {}
var cargo_parts: Array[Dictionary] = []
var wear_distance := 0.0
var odometer := 0.0
var mechanical_events := 0
var body_damage := 0.0
var last_mechanical_event := ""


func toggle_engine() -> String:
	if engine_running:
		engine_running = false
		return "Motor vypnutý."
	if water_ingress >= 1.0:
		return "Nasávanie je zatopené. Vytiahni auto z vody a vysuš ho v servise."
	if fuel <= 0.0:
		return "V nádrži nie je palivo."
	if battery < 0.08:
		return "Batéria je vybitá."
	if float(parts.engine) <= 0.05:
		return "Najprv oprav motor."
	battery = maxf(0.0, battery - 0.035)
	engine_running = true
	return "Motor naštartovaný."


func advance(dt: float, speed: float, throttle: float, offroad: bool, cruise_driving: bool = false, brake_load: float = 0.0, water_depth: float = 0.0) -> void:
	if dt <= 0.0 or not is_finite(dt):
		return
	elapsed += dt
	day_time = fposmod(day_time + dt / 1200.0, 1.0)
	var metres := absf(speed) * dt
	var wear := metres / 150000.0
	var old_fault := floori(wear_distance / 8000.0)
	wear_distance += metres
	for fault in range(old_fault + 1, floori(wear_distance / 8000.0) + 1):
		var rng := RandomNumberGenerator.new()
		rng.seed = world_seed + fault * 104729
		var slot: String = PART_NAMES.keys()[rng.randi_range(0, PART_NAMES.size() - 1)]
		parts[slot] = maxf(0, float(parts[slot]) - rng.randf_range(0.025, 0.060) * (1.2 if offroad else 1.0))
		mechanical_events += 1
		last_mechanical_event = "Opotrebovanie: %s. Skontroluj auto pri najbližšej zastávke." % PART_NAMES[slot]
	parts.brakes = maxf(0, float(parts.brakes) - wear * (0.12 + clampf(brake_load, 0, 1) * 1.8))
	parts.alternator = maxf(0, float(parts.alternator) - wear * 0.14)
	parts.radiator = maxf(0, float(parts.radiator) - wear * 0.12)
	parts.tires = maxf(0.0, float(parts.tires) - wear * (3.0 if offroad else 1.0))
	parts.suspension = maxf(0.0, float(parts.suspension) - wear * (2.5 if offroad else 0.7) * (0.72 if variants.suspension == "reinforced" else 1.0))
	# Driven components consume service life; a locked axle also scrubs tyres
	# on firm ground. Low range is useful in mud, expensive on asphalt.
	var driveline_load := (0.12 if four_wheel_drive else 0.0) + (0.35 if differential_locked else 0.0)
	parts.tires = maxf(0.0, float(parts.tires) - wear * (driveline_load + (1.7 if differential_locked and not offroad else 0.0)))
	parts.suspension = maxf(0.0, float(parts.suspension) - wear * driveline_load * 0.55)
	if headlights:
		battery = maxf(0.0, battery - dt * (0.00030 if high_beams else 0.00018))
	if engine_running:
		var engine_efficiency := 0.82 if variants.engine == "efficient" else (1.12 if variants.engine == "torque" else 1.0)
		fuel = maxf(0.0, fuel - dt * (0.0004 + absf(speed) * 0.00011 * (1.0 + throttle * 0.6)) * engine_efficiency * (1.08 if four_wheel_drive else 1.0) * (1.10 if differential_locked else 1.0) / maxf(0.5, float(fluid_quality.fuel)))
		oil = maxf(0.0, oil - dt * 0.000025 * (2.0 - float(parts.engine)))
		coolant = maxf(0.0, coolant - dt * 0.00004 * (2.0 - float(parts.radiator)))
		battery = minf(1.0, battery + dt * 0.0015 * float(parts.alternator) * (1.45 if variants.alternator == "highoutput" else 1.0))
		var cooling := coolant / COOLANT_CAPACITY * float(fluid_quality.coolant) * lerpf(0.35, 1.0, float(parts.radiator))
		var target := 78.0 + throttle * 20.0 + (1.0 - cooling) * 25.0 - (8.0 if variants.radiator == "large" else 0.0)
		temperature = move_toward(temperature, target, dt * 0.7)
		var damage := dt * (maxf(temperature - 110.0, 0.0) * 0.00015 + (0.0005 if oil < 0.4 else 0.0) + (1.0 - float(fluid_quality.oil)) * 0.000025)
		parts.engine = maxf(0.0, float(parts.engine) - wear * (0.6 + driveline_load * 0.35) - damage)
		if fuel <= 0.0 or float(parts.engine) <= 0.02:
			engine_running = false
	else:
		temperature = move_toward(temperature, 22.0, dt * 0.25)
	# Actual ford water cools a hot radiator gradually, without replenishing
	# coolant or refrigerating a cold engine.
	if is_finite(water_depth) and water_depth > 0.03 and temperature > 32.0:
		var immersion := smoothstep(0.03, 0.50, water_depth)
		temperature = move_toward(temperature, 32.0, dt * immersion * (0.55 + minf(absf(speed), 10.0) * 0.10))
	current_water_depth = maxf(water_depth, 0.0) if is_finite(water_depth) else 0.0
	var intake_height := 1.85 if snorkel_installed else 0.80
	if current_water_depth > intake_height:
		water_ingress = minf(1.5, water_ingress + dt * (current_water_depth - intake_height) * (1.8 if engine_running else 0.25))
		if water_ingress >= 1.0 and engine_running:
			engine_running = false
			parts.engine = maxf(0, float(parts.engine) - 0.12)
			last_mechanical_event = "Motor nasal vodu. Použi navijak a vysuš nasávanie."
			mechanical_events += 1
	elif water_ingress < 1.0:
		water_ingress = move_toward(water_ingress, 0.0, dt * 0.12)


func drain_intake() -> String:
	if current_water_depth > 0.20: return "Najprv vytiahni auto z vody."
	if water_ingress <= 0.01: return "Nasávanie je suché."
	if float(inventory.scrap) < 2: return "Na vysušenie potrebuješ 2 kusy šrotu."
	inventory.scrap = float(inventory.scrap) - 2
	water_ingress = 0.0
	return "Nasávanie vysušené. Skontroluj stav motora."


func install_snorkel(building_id: int) -> String:
	if snorkel_installed: return "Šnorchel je už namontovaný."
	if _stop_kind(building_id) not in ["repair", "workshop"]: return "Šnorchel namontujú v dielni alebo autoservise."
	if float(inventory.scrap) < 14: return "Na šnorchel potrebuješ 14 kusov šrotu."
	inventory.scrap = float(inventory.scrap) - 14
	snorkel_installed = true
	return "Šnorchel namontovaný. Nasávanie je chránené do hĺbky 1,85 m."


func drive_speed_limit() -> float:
	return 7.0 if differential_locked else (16.0 if four_wheel_drive else 22.0)


func power_factor() -> float:
	return (0.35 + float(parts.engine) * 0.65) * (0.4 if temperature > 115.0 else 1.0) * lerpf(0.65, 1.0, float(fluid_quality.fuel)) * (1.15 if variants.engine == "torque" else 1.0)


func brake_deceleration(grip: float, speed: float) -> float:
	var health := float(parts.brakes)
	if health <= 0.01: return 0.0
	var base: float = {"drum": 4.2, "disc": 6.2, "vented": 7.8}.get(brake_type, 4.2)
	var fade := lerpf(1.0, 0.78, smoothstep(18.0, 35.0, absf(speed))) if brake_type == "drum" else 1.0
	var mechanical := base * (0.18 + health * 0.82) * fade / (1.0 + cargo_weight() / 1250.0)
	return minf(mechanical, maxf(0.0, grip) * 9.81 * 0.90)


func tyre_grip(surface: Dictionary) -> float:
	var factor := 0.45 + float(parts.tires) * 0.55
	var grip := float(surface.grip)
	if variants.tires == "allterrain": factor *= 1.20 if float(surface.paved) < 0.4 else 0.94
	if variants.tires == "winter": factor *= 1.65 if float(surface.get("snow", 0.0)) > 0.4 or str(surface.label).begins_with("Sneh") else 0.90
	return clampf(grip * factor, 0.08, 1.0)


func compare_part(item: Dictionary) -> String:
	if item.get("kind", "") != "part" or not parts.has(item.get("slot", "")): return ""
	var slot: String = item.slot
	var candidate := DriveRun.new()
	candidate.parts = parts.duplicate()
	candidate.variants = variants.duplicate()
	candidate.parts[slot] = item.condition
	candidate.variants[slot] = item.get("variant", variants[slot])
	candidate.temperature = temperature
	candidate.fluid_quality = fluid_quality
	candidate.inventory = inventory
	candidate.cargo_parts = cargo_parts
	candidate.fuel = fuel
	candidate.oil = oil
	candidate.coolant = coolant
	match slot:
		"brakes":
			var dry := {"grip": 0.9, "paved": 1.0, "label": "Asfalt", "snow": 0.0}
			return "Brzdenie na suchu pri 72 km/h: %.1f → %.1f m/s²" % [brake_deceleration(tyre_grip(dry), 20), candidate.brake_deceleration(candidate.tyre_grip(dry), 20)]
		"engine":
			return "Dostupný ťah motora: %d %% → %d %%" % [roundi(power_factor() * 100), roundi(candidate.power_factor() * 100)]
		"tires":
			var asphalt := {"grip": 0.9, "paved": 1.0, "label": "Asfalt", "snow": 0.0}
			var snow := {"grip": 0.4, "paved": 0.0, "label": "Sneh", "snow": 1.0}
			return "Priľnavosť asfalt: %d → %d %% · sneh: %d → %d %%" % [roundi(tyre_grip(asphalt) * 100), roundi(candidate.tyre_grip(asphalt) * 100), roundi(tyre_grip(snow) * 100), roundi(candidate.tyre_grip(snow) * 100)]
	return item_effect(item)


func impact(speed_change: float) -> void:
	var damage := clampf((speed_change - 3.0) * 0.008, 0.0, 0.25)
	body_damage = clampf(body_damage + maxf(speed_change - 3.0, 0.0) * 0.025, 0.0, 1.0)
	if variants.suspension == "reinforced": damage *= 0.75
	elif variants.suspension == "comfort": damage *= 0.88
	parts.engine = maxf(0.0, float(parts.engine) - damage * 0.5)
	parts.suspension = maxf(0.0, float(parts.suspension) - damage)


func top_up(fluid: String) -> String:
	var capacity: float = {"fuel": FUEL_CAPACITY, "oil": OIL_CAPACITY, "coolant": COOLANT_CAPACITY}.get(fluid, 0.0)
	if capacity == 0.0:
		return "Neznáma kvapalina."
	var current: float = get(fluid)
	var amount := minf(float(inventory[fluid]), capacity - current)
	if amount < 0.01:
		return "Chýbajú zásoby alebo je nádrž plná."
	fluid_quality[fluid] = (current * float(fluid_quality[fluid]) + amount * float(inventory_quality[fluid])) / (current + amount)
	set(fluid, current + amount)
	inventory[fluid] = float(inventory[fluid]) - amount
	return "Doplnené: %.1f l · kvalita v aute %d %%" % [amount, roundi(float(fluid_quality[fluid]) * 100)]


func repair(part: String) -> String:
	if not parts.has(part):
		return "Neznámy diel."
	if float(parts[part]) >= 0.99:
		return "Diel je v poriadku."
	if float(inventory.scrap) < 2.0:
		return "Na opravu potrebuješ 2 kusy šrotu."
	inventory.scrap = float(inventory.scrap) - 2.0
	parts[part] = minf(1.0, float(parts[part]) + 0.25)
	return "%s: opravené." % PART_NAMES[part]


func search_building(building_id: int) -> String:
	var contents := open_loot(building_id)
	return "Budova je prázdna." if contents.is_empty() else "Vyber zásoby, ktoré chceš odviezť."


func open_loot(building_id: int) -> Array:
	var key := str(building_id)
	if loot_piles.has(key):
		return loot_piles[key]
	# V1 saves marked already-collected buildings. Never generate their loot again.
	if searched.has(key):
		loot_piles[key] = []
		return loot_piles[key]
	var rng := RandomNumberGenerator.new()
	rng.seed = world_seed + building_id * 7919
	var contents: Array = []
	var stop := DriveStops.by_id(building_id, DriveTerrain.route_seed(world_seed, route_choices, building_id * 96.0))
	var stop_kind := str(stop.get("kind", "workshop"))
	var supplies: Array = ["fuel", "oil", "coolant", "scrap"]
	var part_pool: Array = PART_NAMES.keys()
	var part_count := 2
	if building_id <= -3000000:
		stop_kind = "wreck"
		supplies = ["fuel", "oil", "coolant", "scrap"]
		part_count = 1
	elif building_id <= -2000000:
		stop_kind = "cache"
		supplies = ["fuel", "coolant", "scrap", "repairkit"]
		part_count = 0
	elif building_id <= -1000000:
		stop_kind = "debris"
		supplies = ["scrap"]
		part_count = 0
	match stop_kind:
		"home":
			supplies = ["fuel", "coolant", "scrap", "batterycell"]
			part_pool = ["tires", "alternator", "radiator"]
			part_count = 1
		"gas":
			supplies = ["fuel", "oil", "coolant", "scrap"]
			part_pool = ["tires", "alternator"]
			part_count = 1
		"repair":
			supplies = ["oil", "coolant", "scrap", "repairkit"]
			part_pool = ["brakes", "suspension", "engine", "radiator"]
			part_count = 3
		"paint":
			supplies = ["scrap", "paintcan", "repairkit"]
			part_pool = ["tires", "brakes"]
			part_count = 1
	for kind: String in supplies:
		if building_id != 0 and rng.randf() < 0.30: continue
		var amount: float
		match kind:
			"fuel": amount = rng.randf_range(9.0, 16.0) if stop_kind == "gas" else rng.randf_range(1.5, 5.0)
			"oil": amount = rng.randf_range(0.6, 2.8)
			"coolant": amount = rng.randf_range(1.0, 4.0)
			"scrap": amount = rng.randi_range(4, 9) if stop_kind == "repair" else rng.randi_range(2, 6)
			_: amount = rng.randi_range(1, 2)
		if stop_kind in ["wreck", "cache"] and kind in FLUIDS: amount *= 0.45
		# The small cabin and the larger station/repair building have different
		# storage capacity; keep special caches and the tutorial unchanged.
		if building_id > 0:
			amount *= float({"home": 0.55, "paint": 0.7, "workshop": 0.9, "repair": 1.0, "gas": 1.1}.get(stop_kind, 1.0))
		if building_id != 0: amount *= 0.75
		if stop_kind == "cache" and kind == "repairkit": amount = 1
		var quality := 1.0
		if kind in FLUIDS:
			quality = snappedf(rng.randf_range(0.52, 1.0), 0.01)
			if stop_kind == "gas" or building_id == 0: quality = rng.randf_range(0.93, 1.0)
		contents.append({"uid": key + "/" + kind, "kind": kind, "amount": snappedf(amount, 0.1), "quality": quality})
	var first := rng.randi_range(0, part_pool.size() - 1)
	for index in part_count:
		if building_id != 0 and rng.randf() < 0.30: continue
		var slot: String = part_pool[(first + index) % part_pool.size()]
		if building_id == 0:
			slot = "engine" if index == 0 else "radiator"
		var choices: Array = VARIANTS[slot]
		var variant: String = choices[rng.randi_range(0, choices.size() - 1)]
		var condition := rng.randf_range(0.18, 0.96)
		if stop_kind == "repair" or building_id == 0: condition = rng.randf_range(0.65, 0.96)
		contents.append({"uid": key + "/part/" + str(index), "kind": "part", "slot": slot,
			"condition": snappedf(condition, 0.01), "variant": variant, "amount": 1.0})
	searched[key] = true
	loot_piles[key] = contents
	return loot_piles[key]


func cargo_weight() -> float:
	var weight := 0.0
	for kind: String in RESOURCE_WEIGHTS:
		weight += float(inventory[kind]) * float(RESOURCE_WEIGHTS[kind])
	for item: Dictionary in cargo_parts:
		weight += float(PART_WEIGHTS[item.slot])
	return weight


func cargo_used_slots() -> int:
	var slots := cargo_parts.size()
	for kind: String in RESOURCE_WEIGHTS:
		if kind == "scrap": continue
		if float(inventory[kind]) > 0.001:
			slots += 1
	return slots


static func item_name(item: Dictionary) -> String:
	if item.kind == "part":
		return str(PART_NAMES.get(item.get("slot", ""), "Náhradný diel")) + " · " + str(VARIANT_NAMES.get(item.get("variant", "standard"), "Štandard"))
	return RESOURCE_NAMES[item.kind]


static func quality_label(value: float) -> String:
	if value >= 0.90: return "Výborná"
	if value >= 0.70: return "Použiteľná"
	if value >= 0.45: return "Opotrebovaná"
	return "Poškodená"


static func item_effect(item: Dictionary) -> String:
	if item.kind == "part":
		var variant := str(item.get("variant", "standard"))
		return {"drum": "Základné brzdy; dlhšia brzdná dráha a slabší účinok vo vysokej rýchlosti.", "disc": "Vyšší brzdný účinok; stále ho limitujú pneumatiky a povrch.", "vented": "Najsilnejšie brzdy; bez útlmu vo vysokej rýchlosti.", "efficient": "O 18 % nižšia spotreba paliva.", "torque": "O 15 % vyšší ťah, ale o 12 % vyššia spotreba.", "allterrain": "Lepšia trakcia mimo asfaltu, mierne horšia na asfalte.", "winter": "Výrazne lepšia trakcia na snehu, horšia na suchom povrchu.", "large": "Znižuje pracovnú teplotu motora.", "highoutput": "Rýchlejšie dobíja batériu.", "reinforced": "O 28 % nižšie priebežné opotrebovanie a o 25 % menšie poškodenie pri náraze.", "comfort": "O 12 % menšie poškodenie pri náraze."}.get(variant, "Náhradný diel. Jeho stav ovplyvňuje spoľahlivosť a účinok.")
	return {"fuel": "Čistejšie palivo má vyšší výkon a nižšiu spotrebu. Zásoby rovnakého druhu sa miešajú.", "oil": "Kvalita mazania ovplyvňuje opotrebovanie motora. Doplnenie zmení kvalitu náplne.", "coolant": "Čistejšia chladiaca kvapalina účinnejšie chladí motor.", "scrap": "Opravy na ceste a platidlo v dielni, lakovni a na pumpe.", "repairkit": "Jednorazovo opraví zvolený diel o 40 percentuálnych bodov.", "batterycell": "Jednorazovo dobije batériu o 50 percentuálnych bodov.", "paintcan": "Jeden autolak umožní zmeniť farbu karosérie mimo lakovne."}.get(item.kind, "")


static func item_weight(item: Dictionary, amount: float = -1.0) -> float:
	if item.kind == "part":
		return float(PART_WEIGHTS[item.slot])
	return float(RESOURCE_WEIGHTS[item.kind]) * (float(item.amount) if amount < 0.0 else amount)


func can_take(item: Dictionary, amount: float) -> bool:
	if not is_finite(amount) or amount <= 0.0 or amount > float(item.amount) + 0.0001:
		return false
	if item.kind not in FLUIDS and not is_equal_approx(amount, floorf(amount)):
		return false
	if item.kind == "scrap":
		return true
	var extra_slot := 0
	if item.kind == "part" or (item.kind != "scrap" and float(inventory[item.kind]) <= 0.001):
		extra_slot = 1
	return cargo_weight() + item_weight(item, amount) <= CARGO_MAX_WEIGHT + 0.0001 and cargo_used_slots() + extra_slot <= CARGO_SLOTS


func take_loot(building_id: int, uid: String, amount: float) -> Dictionary:
	var key := str(building_id)
	if not loot_piles.has(key):
		return {"ok": false, "message": "Najprv prehľadaj budovu."}
	var contents: Array = loot_piles[key]
	for index in contents.size():
		var item: Dictionary = contents[index]
		if item.uid != uid:
			continue
		if not can_take(item, amount):
			return {"ok": false, "message": "Neplatné množstvo alebo plný kufor. Uprav výber."}
		if item.kind == "part":
			cargo_parts.append(item.duplicate(true))
		else:
			if item.kind in FLUIDS:
				var old := float(inventory[item.kind])
				inventory_quality[item.kind] = (old * float(inventory_quality[item.kind]) + amount * float(item.get("quality", 1.0))) / (old + amount)
			inventory[item.kind] = float(inventory[item.kind]) + amount
			if item.kind == "scrap": found_scrap += int(amount)
		item.amount = maxf(0.0, snappedf(float(item.amount) - amount, 0.0001))
		if float(item.amount) < 0.0001:
			contents.remove_at(index)
		return {"ok": true, "message": "Zobraté: " + item_name(item), "amount": amount}
	return {"ok": false, "message": "Táto položka už v budove nie je."}


func install_spare(uid: String) -> String:
	for item: Dictionary in cargo_parts:
		if item.uid == uid:
			var old_condition := float(parts[item.slot])
			var old_variant := str(variants[item.slot])
			parts[item.slot] = float(item.condition)
			variants[item.slot] = str(item.get("variant", VARIANTS[item.slot][0]))
			item.condition = old_condition
			item.variant = old_variant
			return "%s vymenené. Pôvodný diel zostal v kufri." % PART_NAMES[item.slot]
	return "Náhradný diel nie je v kufri."


func salvage_spare(uid: String) -> String:
	for index in cargo_parts.size():
		var item: Dictionary = cargo_parts[index]
		if item.uid == uid:
			var amount := maxi(1, roundi(float(item.condition) * 5.0))
			inventory.scrap = float(inventory.scrap) + amount
			cargo_parts.remove_at(index)
			return "Diel rozobratý: +%d šrotu." % amount
	return "Náhradný diel nie je v kufri."


func use_supply(kind: String, slot: String = "engine") -> String:
	if kind not in ["repairkit", "batterycell"] or float(inventory.get(kind, 0.0)) < 1.0:
		return "Táto zásoba nie je v kufri."
	if kind == "repairkit":
		if not parts.has(slot) or float(parts[slot]) >= 0.99: return "Tento diel opravu nepotrebuje."
		parts[slot] = minf(1.0, float(parts[slot]) + 0.40)
	else:
		if battery >= 0.99: return "Batéria je už nabitá."
		battery = minf(1.0, battery + 0.50)
	inventory[kind] = float(inventory[kind]) - 1.0
	return "Použité: " + str(RESOURCE_NAMES[kind])


func pump_remaining(building_id: int) -> float:
	var key := str(building_id)
	if not station_stock.has(key):
		station_stock[key] = 35.0 + float(posmod(world_seed + building_id * 13, 26))
	return float(station_stock[key])


func buy_fuel(building_id: int, requested: float = 5.0) -> String:
	if _stop_kind(building_id) != "gas": return "Tu nie je čerpacia stanica."
	if not is_finite(requested) or requested <= 0.0: return "Neplatné množstvo."
	# 1 scrap / litre. Never charge for more fuel than actually enters the tank.
	var amount := minf(requested, minf(FUEL_CAPACITY - fuel, minf(pump_remaining(building_id), float(inventory.scrap))))
	amount = floorf(amount)
	if amount < 1.0: return "Chýba šrot, zásoba stojana alebo miesto v nádrži."
	fluid_quality.fuel = (fuel * float(fluid_quality.fuel) + amount) / (fuel + amount)
	fuel += amount
	inventory.scrap = float(inventory.scrap) - amount
	station_stock[str(building_id)] = pump_remaining(building_id) - amount
	return "Natankované %.0f l čistého paliva za %.0f šrotu." % [amount, amount]


func repair_at_shop(building_id: int, slot: String) -> String:
	if _stop_kind(building_id) not in ["repair", "workshop"] or not parts.has(slot): return "Potrebuješ servisnú dielňu."
	var cost := maxi(2, ceili((0.98 - float(parts[slot])) * 12.0))
	if float(parts[slot]) >= 0.98: return "Diel je v poriadku."
	if float(inventory.scrap) < cost: return "Oprava potrebuje %d šrotu." % cost
	inventory.scrap = float(inventory.scrap) - cost
	parts[slot] = 0.98
	return "%s: profesionálna oprava na 98 %% za %d šrotu." % [PART_NAMES[slot], cost]


func paint_vehicle(color: String, building_id: int = -1) -> String:
	if color not in PAINTS.values(): return "Neznámy lak."
	if color == paint_color: return "Auto už má túto farbu."
	var shop: bool = building_id >= 0 and _stop_kind(building_id) == "paint"
	if shop:
		if float(inventory.scrap) < 4.0: return "Lakovňa potrebuje 4 šrotu."
		inventory.scrap = float(inventory.scrap) - 4.0
	else:
		if float(inventory.paintcan) < 1.0: return "Potrebuješ autolak z kufra alebo lakovňu."
		inventory.paintcan = float(inventory.paintcan) - 1.0
	paint_color = color
	return "Karoséria má nový lak."


func _stop_kind(building_id: int) -> String:
	if building_id < 0: return "debris"
	return str(DriveStops.by_id(building_id, DriveTerrain.route_seed(world_seed, route_choices, building_id * 96.0)).get("kind", ""))


func to_data(include_terrain: bool = true) -> Dictionary:
	return {
		"version": SAVE_VERSION, "seed": world_seed, "distance": distance, "vehicle_id": vehicle_id,
		"fuel": fuel, "oil": oil, "coolant": coolant, "battery": battery,
		"temperature": temperature, "elapsed": elapsed, "day_time": day_time,
		"headlights": headlights, "four_wheel_drive": four_wheel_drive, "differential_locked": differential_locked, "parts": parts.duplicate(),
		"high_beams": high_beams, "snorkel_installed": snorkel_installed, "water_ingress": water_ingress,
		"inventory": inventory.duplicate(), "searched": searched.duplicate(),
		"loot_piles": loot_piles.duplicate(true), "cargo_parts": cargo_parts.duplicate(true),
		"variants": variants.duplicate(), "fluid_quality": fluid_quality.duplicate(),
		"inventory_quality": inventory_quality.duplicate(), "station_stock": station_stock.duplicate(), "paint_color": paint_color,
		"route_choices": route_choices.duplicate(), "weather_state": weather_state.duplicate(),
		"terrain_deformation": terrain_deformation.duplicate(true) if include_terrain else [],
		"run_id": run_id, "found_scrap": found_scrap, "deposited_scrap": deposited_scrap,
		"wear_distance": wear_distance, "odometer": odometer, "mechanical_events": mechanical_events,
		"body_damage": body_damage,
	}


static func from_data(data: Dictionary) -> DriveRun:
	var version: Variant = data.get("version", -1)
	var saved_seed: Variant = data.get("seed", 24601)
	if not (version is int or version is float) or not is_finite(float(version)) or float(version) not in [1.0, 2.0, 3.0, 4.0, 5.0, float(SAVE_VERSION)]:
		return null
	if not (saved_seed is int or saved_seed is float) or not is_finite(float(saved_seed)) or absf(float(saved_seed)) > 2147483647.0:
		return null
	var result := DriveRun.new()
	var model: Variant = data.get("vehicle_id", "jeep")
	if not model is String or model not in ["izh", "jeep"]: return null
	result.vehicle_id = model
	var ruts: Variant = data.get("terrain_deformation", [])
	if not ruts is Array or ruts.size() > DriveDeformation.MAX_CELLS: return null
	var field := DriveDeformation.new()
	field.restore(ruts)
	result.terrain_deformation = field.to_data()
	result.world_seed = int(saved_seed)
	var limits := {
		"distance": [0.0, 100000000.0], "fuel": [0.0, FUEL_CAPACITY],
		"oil": [0.0, OIL_CAPACITY], "coolant": [0.0, COOLANT_CAPACITY],
		"battery": [0.0, 1.0], "temperature": [-40.0, 180.0],
		"elapsed": [0.0, 100000000.0], "day_time": [0.0, 1.0],
		"wear_distance": [0.0, 100000000.0], "mechanical_events": [0.0, 1000000.0],
		"odometer": [0.0, 100000000.0],
		"body_damage": [0.0, 1.0],
	}
	for key: String in limits:
		var number: Variant = data.get(key, result.get(key))
		if not (number is float or number is int) or not is_finite(float(number)):
			return null
		result.set(key, clampf(float(number), limits[key][0], limits[key][1]))
	if not data.has("wear_distance"): result.wear_distance = result.distance
	if not data.has("odometer"): result.odometer = result.wear_distance
	for group: String in ["parts", "inventory"]:
		var source: Variant = data.get(group, {})
		if not source is Dictionary:
			return null
		var target: Dictionary = result.get(group)
		for key: String in target:
			var value: Variant = source.get(key, target[key])
			if not (value is float or value is int) or not is_finite(float(value)):
				return null
			target[key] = clampf(float(value), 0.0, 1.0 if group == "parts" else 100000.0)
	var visited: Variant = data.get("searched", {})
	if not visited is Dictionary:
		return null
	for key: Variant in visited:
		if str(key).is_valid_int() and visited[key] == true:
			result.searched[str(key)] = true
	result.headlights = data.get("headlights", false) == true
	for key: String in ["high_beams", "snorkel_installed"]:
		if not data.get(key, false) is bool: return null
		result.set(key, data.get(key, false))
	var ingress: Variant = data.get("water_ingress", 0.0)
	if not (ingress is float or ingress is int) or not is_finite(float(ingress)) or float(ingress) < 0 or float(ingress) > 1.5: return null
	result.water_ingress = float(ingress)
	for key: String in ["four_wheel_drive", "differential_locked"]:
		var mode: Variant = data.get(key, result.get(key))
		if not mode is bool: return null
		result.set(key, mode)
	if result.differential_locked and not result.four_wheel_drive: return null
	if result.vehicle_id == "izh" and (result.four_wheel_drive or result.differential_locked): return null
	var choices: Variant = data.get("route_choices", {})
	if not choices is Dictionary: return null
	for key: Variant in choices:
		if not str(key).is_valid_int() or int(key) < 0 or int(key) > 200000: return null
		if not (choices[key] is float or choices[key] is int) or float(choices[key]) not in [-1.0, 1.0]: return null
		result.route_choices[str(key)] = int(choices[key])
	var weather: Variant = data.get("weather_state", {})
	if not weather is Dictionary: return null
	for key: String in ["next", "edge", "left", "front_until", "front_serial", "front_index", "front_strength", "front_biome", "wetness", "intensity", "rain_mix", "snow_mix", "wind_mix", "warning", "shelter_x", "shelter_y", "shelter_z", "has_shelter", "storm_serial", "storm_variant", "storm_x", "storm_z", "storm_vx", "storm_vz", "storm_duration", "storm_visibility"]:
		var value: Variant = weather.get(key, 0.0)
		if not (value is float or value is int) or not is_finite(float(value)) or absf(float(value)) > 100000000: return null
		if weather.has(key): result.weather_state[key] = float(value)
	var identity: Variant = data.get("run_id", "legacy-%d" % result.world_seed)
	if not identity is String or str(identity).length() > 128: return null
	result.run_id = identity
	for key: String in ["found_scrap", "deposited_scrap"]:
		var amount: Variant = data.get(key, 0)
		if not (amount is int or amount is float) or not is_finite(float(amount)) or float(amount) < 0 or float(amount) > 10000000: return null
		result.set(key, int(amount))
	if result.deposited_scrap > result.found_scrap: return null
	if int(version) >= 3:
		var saved_variants: Variant = data.get("variants", {})
		if not saved_variants is Dictionary: return null
		for slot: String in result.variants:
			var variant: Variant = saved_variants.get(slot, result.variants[slot])
			if not variant is String or variant not in VARIANTS[slot]: return null
			result.variants[slot] = variant
		for group: String in ["fluid_quality", "inventory_quality"]:
			var qualities: Variant = data.get(group, {})
			if not qualities is Dictionary: return null
			for kind: String in FLUIDS:
				var quality: Variant = qualities.get(kind, 1.0)
				if not (quality is float or quality is int) or not is_finite(float(quality)) or float(quality) < 0.01 or float(quality) > 1.0: return null
				result.get(group)[kind] = float(quality)
		var stocks: Variant = data.get("station_stock", {})
		if not stocks is Dictionary: return null
		for key: Variant in stocks:
			if not str(key).is_valid_int() or not (stocks[key] is float or stocks[key] is int): return null
			if not is_finite(float(stocks[key])) or float(stocks[key]) < 0.0 or float(stocks[key]) > 200.0: return null
			result.station_stock[str(key)] = float(stocks[key])
		var paint: Variant = data.get("paint_color", "b9baaa")
		if not paint is String or paint not in PAINTS.values(): return null
		result.paint_color = paint
	if int(version) >= 2:
		var piles: Variant = data.get("loot_piles", {})
		var spares: Variant = data.get("cargo_parts", [])
		if not piles is Dictionary or not spares is Array:
			return null
		var seen_uids: Dictionary = {}
		for key: Variant in piles:
			if not str(key).is_valid_int() or not piles[key] is Array or piles[key].size() > 64:
				return null
			var validated: Array = []
			for entry: Variant in piles[key]:
				var item := _validate_item(entry)
				if item.is_empty() or seen_uids.has(item.uid):
					return null
				seen_uids[item.uid] = true
				validated.append(item)
			result.loot_piles[str(key)] = validated
			result.searched[str(key)] = true
		for entry: Variant in spares:
			var item := _validate_item(entry)
			if item.is_empty() or item.kind != "part" or seen_uids.has(item.uid):
				return null
			seen_uids[item.uid] = true
			result.cargo_parts.append(item)
	# Resume safely parked with the engine off, as in the Android game.
	return result


static func _validate_item(entry: Variant) -> Dictionary:
	if not entry is Dictionary or not entry.get("uid") is String or str(entry.uid).length() > 128 or str(entry.uid).is_empty():
		return {}
	var kind: Variant = entry.get("kind")
	if not kind is String or (kind != "part" and not RESOURCE_NAMES.has(kind)):
		return {}
	var amount: Variant = entry.get("amount")
	if not (amount is int or amount is float) or not is_finite(float(amount)) or float(amount) <= 0.0 or float(amount) > 100000.0:
		return {}
	var item := {"uid": str(entry.uid), "kind": str(kind), "amount": float(amount)}
	if kind not in FLUIDS and not is_equal_approx(float(amount), floorf(float(amount))):
		return {}
	if kind == "part":
		var slot: Variant = entry.get("slot")
		var condition: Variant = entry.get("condition")
		if not slot is String or not PART_NAMES.has(slot) or float(amount) != 1.0:
			return {}
		if not (condition is int or condition is float) or not is_finite(float(condition)) or float(condition) < 0.0 or float(condition) > 1.0:
			return {}
		item.slot = slot
		item.condition = float(condition)
		var variant: Variant = entry.get("variant", VARIANTS[slot][0])
		if not variant is String or variant not in VARIANTS[slot]: return {}
		item.variant = variant
	else:
		var quality: Variant = entry.get("quality", 1.0)
		if not (quality is float or quality is int) or not is_finite(float(quality)) or float(quality) < 0.01 or float(quality) > 1.0: return {}
		item.quality = float(quality)
	return item
