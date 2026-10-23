extends SceneTree
## Pure inventory/equipment/save/service regression; no scene or user save needed.
var checks := 0
var failures := 0


func _initialize() -> void:
	call_deferred("_run")


func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error("FAIL: " + label)


func _run() -> void:
	var run := DriveRun.new()
	var strengths: Array[float] = []
	for kind: String in ["drum", "disc", "vented"]:
		run.brake_type = kind
		strengths.append(run.brake_deceleration(1.0, 20.0))
	check(strengths[0] < strengths[1] and strengths[1] < strengths[2], "Brake types have progressively stronger mechanical deceleration")
	var good := run.brake_deceleration(1.0, 20)
	run.parts.brakes = 0.3
	check(run.brake_deceleration(1.0, 20) < good * 0.6, "Worn brakes materially lengthen stopping distance")
	run.parts.brakes = 1.0
	check(run.brake_deceleration(0.2, 20) <= 0.2 * 9.81, "Even upgraded brakes cannot exceed available tyre grip")
	var old_fuel := float(run.inventory.fuel)
	run.loot_piles["0"] = [{"uid": "quality/fuel", "kind": "fuel", "amount": 6.0, "quality": 0.5}]
	run.take_loot(0, "quality/fuel", 6.0)
	check(is_equal_approx(float(run.inventory_quality.fuel), 0.75) and run.inventory.fuel == old_fuel + 6, "Fluid inventory blends quantity and purity without manufacturing supplies")
	var before := run.fuel
	run.top_up("fuel")
	check(run.fuel > before and float(run.fluid_quality.fuel) < 1.0, "Pouring mixed stock changes actual tank quality")
	var dirty := DriveRun.new()
	var clean := DriveRun.new()
	dirty.fluid_quality.fuel = 0.5
	dirty.engine_running = true
	clean.engine_running = true
	dirty.advance(10, 15, 1, false)
	clean.advance(10, 15, 1, false)
	check(dirty.power_factor() < clean.power_factor() and dirty.fuel < clean.fuel, "Fuel purity has actual power and consumption consequences")
	run.cargo_parts.append({"uid": "new/brakes", "kind": "part", "slot": "brakes", "variant": "disc", "condition": 0.9, "amount": 1.0})
	var old_type := run.brake_type
	run.install_spare("new/brakes")
	check(run.brake_type == "disc" and run.cargo_parts[0].variant == old_type, "Installing brakes exchanges the type as well as the condition")
	run.inventory.repairkit = 1
	run.parts.engine = 0.3
	run.use_supply("repairkit", "engine")
	check(is_equal_approx(float(run.parts.engine), 0.7) and run.inventory.repairkit == 0, "Repair kits repair the selected component and are consumed once")
	run.inventory.batterycell = 1
	run.battery = 0.2
	run.use_supply("batterycell")
	check(is_equal_approx(run.battery, 0.7) and run.inventory.batterycell == 0, "Jump pack charges the battery and is consumed")
	var paint_before := run.paint_color
	run.paint_vehicle("a54e43")
	check(run.paint_color == paint_before, "Painting cannot occur without a can or paid shop")
	run.inventory.paintcan = 1
	run.paint_vehicle("a54e43")
	check(run.paint_color == "a54e43" and run.inventory.paintcan == 0, "A paint can changes the persistent car colour exactly once")
	var gas_id := -1
	var repair_id := -1
	var seen: Dictionary = {}
	for region in 12:
		var stop := DriveStops.next_stop(region * 960.0, run.world_seed)
		seen[stop.kind] = run.open_loot(int(stop.id))
		if stop.kind == "gas": gas_id = int(stop.id)
		if stop.kind == "repair": repair_id = int(stop.id)
	check(seen.size() == 5, "All building types have their own loot tables")
	check(seen.paint.size() != seen.repair.size(), "Homes, shops and stations do not all contain the same six items")
	run.fuel = 10
	run.inventory.scrap = 20
	var stock := run.pump_remaining(gas_id)
	run.buy_fuel(gas_id, 5)
	check(run.fuel == 15 and run.inventory.scrap == 15 and run.pump_remaining(gas_id) == stock - 5, "Gas pump deducts actual stock and scrap for the fuel transferred")
	run.parts.brakes = 0.5
	run.repair_at_shop(repair_id, "brakes")
	check(is_equal_approx(float(run.parts.brakes), 0.98), "Professional repair services the chosen component")
	var loaded := DriveRun.from_data(run.to_data())
	check(loaded != null and loaded.brake_type == run.brake_type and loaded.paint_color == run.paint_color and loaded.inventory_quality == run.inventory_quality and loaded.station_stock == run.station_stock, "Save v3 preserves variants, fluid quality, paint and finite station stock")
	var legacy := DriveRun.new().to_data()
	legacy.version = 2
	legacy.erase("variants")
	legacy.erase("fluid_quality")
	loaded = DriveRun.from_data(legacy)
	check(loaded != null and loaded.brake_type == "drum" and loaded.fluid_quality.fuel == 1.0, "Legacy v2 saves upgrade safely without losing inventory")
	var bad := run.to_data()
	bad.variants.brakes = "magic"
	check(DriveRun.from_data(bad) == null, "Invalid equipment variants cannot bypass save recovery")
	bad = run.to_data()
	bad.fluid_quality.oil = NAN
	check(DriveRun.from_data(bad) == null, "Non-finite fluid purity is rejected")
	var debris := DriveRun.new()
	var salvage := debris.open_loot(-1000000)
	var pieces := int(salvage[0].amount)
	debris.take_loot(-1000000, str(salvage[0].uid), pieces)
	check(debris.found_scrap == pieces and DriveRun.from_data(debris.to_data()).open_loot(-1000000).is_empty(), "Road-debris salvage awards earned scrap only once across saves")
	print("EQUIPMENT: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
