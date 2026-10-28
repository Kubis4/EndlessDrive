extends RefCounted
## Saved, editable scene resources shared by gameplay and the art gallery.
const DIRECTORY := "res://assets/models/expedition/"
const MODELS := ["mountain_pine", "alpine_pine", "juniper", "woodland_tree", "wetland_tree", "dead_tree", "marsh_reeds", "fern_patch", "meadow_shrub", "locomotive", "freight_wagon", "tank_wagon", "timber_wagon", "mountain_ridge", "alpine_peak", "rocky_peak", "snowy_peak", "tunnel_portal", "tunnel_rib", "rail_portal"]
static var _scenes: Dictionary = {}

static func warmup() -> void:
	for label: String in MODELS:
		# Kept as editable art references, but gameplay now uses the supplied
		# tree scene bank. Avoid loading the old layered crowns at every startup.
		if label in ["mountain_pine", "alpine_pine", "juniper", "woodland_tree", "wetland_tree", "dead_tree"]: continue
		if not _scenes.has(label): _scenes[label] = load(DIRECTORY + label + ".tscn")

static func instance(label: String) -> Node3D:
	if not _scenes.has(label): _scenes[label] = load(DIRECTORY + label + ".tscn")
	return (_scenes[label] as PackedScene).instantiate() as Node3D

static func road_basis(tangent: Vector3) -> Basis:
	var forward := tangent.normalized()
	var right := Vector3(-forward.z, 0, forward.x).normalized()
	return Basis(right, right.cross(forward).normalized(), -forward)
