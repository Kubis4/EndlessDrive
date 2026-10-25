class_name DriveProfile
extends RefCounted
const PATH := "user://garage-profile.json"
var bank := 0
var best_distance := 0.0
var upgrades: Dictionary = {"engine": 0, "tires": 0, "brakes": 0, "suspension": 0, "radiator": 0, "alternator": 0}
var receipts: Dictionary = {}


func prepare_run(run: DriveRun) -> void:
	var base := {"engine": 0.36, "tires": 0.42, "brakes": 0.35, "suspension": 0.30, "radiator": 0.40, "alternator": 0.35}
	for slot: String in upgrades:
		var level := int(upgrades[slot])
		run.parts[slot] = minf(0.88, float(base[slot]) + level * 0.14)
		if level > 0:
			run.variants[slot] = {"engine": "efficient", "tires": "allterrain", "brakes": "disc", "suspension": "reinforced", "radiator": "large", "alternator": "highoutput"}[slot]
		if slot == "brakes" and level >= 3: run.brake_type = "vented"
	run.fuel = 3.5
	run.oil = 1.8
	run.coolant = 2.2
	run.battery = 0.30
	run.inventory.scrap = 2.0


func price(slot: String) -> int:
	return 10 + int(upgrades.get(slot, 0)) * 12


func buy(slot: String) -> bool:
	if not upgrades.has(slot) or int(upgrades[slot]) >= 3 or bank < price(slot): return false
	bank -= price(slot)
	upgrades[slot] = int(upgrades[slot]) + 1
	return true


func deposit(run: DriveRun) -> int:
	var amount := maxi(0, mini(floori(float(run.inventory.scrap)), run.found_scrap - run.deposited_scrap))
	if amount == 0: return 0
	run.inventory.scrap = float(run.inventory.scrap) - amount
	run.deposited_scrap += amount
	var prior := int(receipts.get(run.run_id, 0))
	var credit := maxi(0, run.deposited_scrap - prior)
	receipts[run.run_id] = maxi(prior, run.deposited_scrap)
	bank += credit
	return credit


func store(path: String = PATH) -> Error:
	var file := FileAccess.open(path + ".tmp", FileAccess.WRITE)
	if file == null: return FileAccess.get_open_error()
	file.store_string(JSON.stringify({"bank": bank, "best_distance": best_distance, "upgrades": upgrades, "receipts": receipts}))
	file.flush()
	var error := file.get_error()
	file.close()
	if error != OK: return error
	if FileAccess.file_exists(path) and JSON.parse_string(FileAccess.get_file_as_string(path)) is Dictionary:
		var copied := DirAccess.copy_absolute(path, path + ".bak")
		if copied != OK: return copied
	return DirAccess.rename_absolute(path + ".tmp", path)


static func restore(path: String = PATH) -> DriveProfile:
	for candidate: String in [path, path + ".bak"]:
		if not FileAccess.file_exists(candidate): continue
		var data: Variant = JSON.parse_string(FileAccess.get_file_as_string(candidate))
		if not data is Dictionary or not data.get("upgrades") is Dictionary or not data.get("receipts", {}) is Dictionary: continue
		var money: Variant = data.get("bank", 0)
		var record: Variant = data.get("best_distance", 0.0)
		if not (record is int or record is float) or not is_finite(float(record)) or float(record) < 0 or float(record) > 100000000: continue
		if not (money is int or money is float) or not is_finite(float(money)) or float(money) < 0 or float(money) > 10000000: continue
		var profile := DriveProfile.new()
		var valid := true
		for slot: String in profile.upgrades:
			var level: Variant = data.upgrades.get(slot, 0)
			if not (level is int or level is float) or not is_finite(float(level)) or float(level) != floorf(float(level)) or float(level) < 0 or float(level) > 3:
				valid = false
				break
			profile.upgrades[slot] = int(level)
		for key: Variant in data.get("receipts", {}):
			var value: Variant = data.receipts[key]
			if not (value is int or value is float) or not is_finite(float(value)) or float(value) < 0 or float(value) > 10000000:
				valid = false
				break
			profile.receipts[str(key)] = int(value)
		if valid:
			profile.bank = int(money)
			profile.best_distance = float(record)
			return profile
	return DriveProfile.new()
