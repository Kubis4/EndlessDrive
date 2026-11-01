extends RefCounted
## What stays between expeditions, in user://garage.cfg: the scrap brought back from the
## road, the cars bought with it and which one starts the next run, and what that run
## starts with. A run keeps the car it set out in (its save says which).
const FILE := "user://garage.cfg"
## Curb weight (kg), power (W), top speed (m/s), air drag, tank (l), payload (kg).
const CARS := {
	"m1009": {"price": 0, "mass": 2359.0, "power": 116000.0, "top": 33.5, "drag": 0.8, "tank": 102, "cargo": 540.0, "note": "Ťažký a smädný, ale odvezie najviac a v teréne sa nezľakne."},
	"jeep": {"price": 300, "mass": 1640.0, "power": 130000.0, "top": 46.0, "drag": 0.62, "tank": 76, "cargo": 450.0, "note": "Ľahší a oveľa rýchlejší, s menšou nádržou aj nákladom."},
	"izh": {"price": 150, "mass": 1150.0, "power": 55000.0, "top": 31.0, "drag": 0.5, "tank": 46, "cargo": 400.0, "note": "Malý a úsporný, s predným náhonom: na cestu, nie do bahna."},
}
const ORDER := ["m1009", "jeep", "izh"]
## Price of each step up. What each level means: start_fuel(), start_tyres() and so on.
const UPGRADES := {
	"fuel": {"name": "PALIVO", "prices": [60, 140, 260], "steps": ["54 %", "70 %", "85 %", "PLNÉ"]},
	"supplies": {"name": "ZÁSOBY", "prices": [60, 140, 260], "steps": ["ZÁKLAD", "+1", "+2", "+3"]},
	"tyres": {"name": "PNEUMATIKY", "prices": [100, 220], "steps": ["CESTNÉ", "TERÉNNE", "DO BAHNA"]},
	"engine": {"name": "MOTOR", "prices": [80, 180, 320], "steps": ["ZÁKLAD", "+6 %", "+12 %", "+18 %"]},
}
const FUEL_SHARES := [0.54, 0.7, 0.85, 1.0]
const TYRES := ["road", "offroad", "mud"]

static var scraps := 0
static var car := "m1009"
static var owned: Array = ["m1009"]
static var levels := {}
static var loaded := false

static func load_garage() -> void:
	if loaded: return
	loaded = true
	var file := ConfigFile.new()
	if file.load(FILE) != OK: return
	scraps = int(file.get_value("garage", "scraps", 0))
	car = str(file.get_value("garage", "car", "m1009"))
	owned = file.get_value("garage", "owned", ["m1009"])
	levels = file.get_value("garage", "levels", {})
	if not CARS.has(car) or car not in owned: car = "m1009"

static func save_garage() -> void:
	var file := ConfigFile.new()
	file.set_value("garage", "scraps", scraps)
	file.set_value("garage", "car", car)
	file.set_value("garage", "owned", owned)
	file.set_value("garage", "levels", levels)
	file.save(FILE)

static func earn(amount: int) -> void:
	load_garage()
	scraps += amount
	save_garage()

static func level(upgrade: String) -> int:
	return int(levels.get(upgrade, 0))

## Price of the next step, or -1 at the top.
static func next_price(upgrade: String) -> int:
	var prices: Array = UPGRADES[upgrade].prices
	return prices[level(upgrade)] if level(upgrade) < prices.size() else -1

static func buy_upgrade(upgrade: String) -> bool:
	var price := next_price(upgrade)
	if price < 0 or price > scraps: return false
	scraps -= price
	levels[upgrade] = level(upgrade) + 1
	save_garage()
	return true

static func buy_car(id: String) -> bool:
	if id in owned or CARS[id].price > scraps: return false
	scraps -= int(CARS[id].price)
	owned.append(id)
	car = id
	save_garage()
	return true

static func choose_car(id: String) -> void:
	if id not in owned: return
	car = id
	save_garage()

## The car of the run being started or carried on (endless_menu.gd says which).
static func run_car() -> String:
	load_garage()
	var id := str(Engine.get_meta("endless_car", car))
	return id if CARS.has(id) else "m1009"

static func start_fuel_share() -> float:
	return FUEL_SHARES[level("fuel")]

static func start_tyres() -> String:
	return TYRES[level("tyres")]

static func power_factor() -> float:
	return 1.0 + 0.06 * level("engine")
