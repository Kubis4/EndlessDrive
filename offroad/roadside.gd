extends RefCounted
## The baked roadside models (build_roadside.gd): gas station, houses, barns,
## pylons, containers. Scenes with their colliders, shared meshes for the rest.
const DIR := "res://assets/models/roadside/"
const NAMES := ["gas_station", "house01", "house02", "house03", "barn01", "barn03", "barrack01", "barrack02", "barrack03", "barrack04",
	"pylon01", "pylon_line01", "container01", "container02", "container03", "watertank01", "trailer01", "trailer02", "truck01", "generator01",
	"cable_reel01", "bench01", "sign01", "sign02", "sign03", "sign04", "fence01", "fence02", "board01", "utilitybox01", "utilitybox02", "utilitybox03"]
static var scenes := {}

## Loads them all now (the load), not on the road.
static func prepare() -> void:
	for name: String in NAMES:
		if not scenes.has(name): scenes[name] = load(DIR + name + ".scn")

static func spawn(name: String) -> Node3D:
	prepare()
	return (scenes[name] as PackedScene).instantiate()

## The model's mesh alone (for what stands in many places without a collider).
static func mesh(name: String) -> Mesh:
	prepare()
	var state: SceneState = (scenes[name] as PackedScene).get_state()
	for n in state.get_node_count():
		for p in state.get_node_property_count(n):
			if state.get_node_property_name(n, p) == "mesh": return state.get_node_property_value(n, p)
	return null
