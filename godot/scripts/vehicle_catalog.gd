extends RefCounted
const DEFAULT := "izh"
const IDS := ["izh", "jeep"]
const DATA := {
	"izh": {"name": "IZH-2715", "drive": "Predný náhon", "scene": "res://assets/models/izh/izh_2715.tscn", "shape": "res://assets/models/izh/izh_chassis.res", "wheelbase": 2.63515, "track": 1.35805, "radius": 0.33208, "four_by_four": false, "differential": false},
	"jeep": {"name": "Jeep Cherokee", "drive": "4×4 · uzávierka", "scene": "res://assets/models/jeep/jeep_cherokee.tscn", "shape": "res://assets/models/jeep/jeep_chassis.res", "wheelbase": 2.96, "track": 1.606, "radius": 0.44, "four_by_four": true, "differential": true},
}
static func valid(id: String) -> bool: return id in IDS
