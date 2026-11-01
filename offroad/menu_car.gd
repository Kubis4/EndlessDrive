extends Node3D
## The menu's picture: the chosen car turning slowly on a plain floor.
const Models = preload("res://desert_battlefield/endless_models.gd")
const TURN := 0.35
var stand: Node3D
var camera: Camera3D

func _ready() -> void:
	var world := WorldEnvironment.new()
	var environment := Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.background_color = Color(0.93, 0.86, 0.7)
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color(0.86, 0.82, 0.74)
	environment.ambient_light_energy = 0.75
	environment.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	# (Without a sky to mirror, the glass shows black patches.)
	var sky_material := ProceduralSkyMaterial.new()
	sky_material.sky_top_color = Color(0.72, 0.8, 0.86)
	sky_material.sky_horizon_color = Color(0.93, 0.86, 0.7)
	sky_material.ground_bottom_color = Color(0.72, 0.64, 0.5)
	sky_material.ground_horizon_color = Color(0.93, 0.86, 0.7)
	environment.sky = Sky.new()
	environment.sky.sky_material = sky_material
	environment.sky.radiance_size = Sky.RADIANCE_SIZE_32
	environment.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	world.environment = environment
	add_child(world)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-52.0, -38.0, 0.0)
	sun.light_energy = 1.5
	sun.shadow_enabled = true
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_ORTHOGONAL
	sun.directional_shadow_max_distance = 30.0
	add_child(sun)
	var floor_mesh := MeshInstance3D.new()
	var disc := CylinderMesh.new()
	disc.top_radius = 300.0
	disc.bottom_radius = 300.0
	disc.height = 0.02
	disc.radial_segments = 48
	var floor_material := StandardMaterial3D.new()
	floor_material.albedo_color = Color(0.72, 0.64, 0.5)
	floor_material.roughness = 1.0
	disc.material = floor_material
	floor_mesh.mesh = disc
	floor_mesh.position.y = -0.01
	add_child(floor_mesh)
	camera = Camera3D.new()
	camera.fov = 26.0
	add_child(camera)

## The stand with another car on it, turned as the last one was.
func show_car(id: String) -> void:
	var turned := stand.rotation.y if stand != null else 0.9
	if stand != null: stand.free()
	stand = Node3D.new()
	stand.rotation.y = turned
	add_child(stand)
	var holder := Node3D.new()
	stand.add_child(holder)
	Models.vehicle(holder, id, Color("737762"))
	var model: Dictionary = Models.Catalog.DATA[id]
	# With its wheels on the floor.
	var wheel := holder.get_node("Wheels").get_child(0) as Node3D
	holder.position.y = float(model.radius) - wheel.position.y
	# From far enough for the longest of them.
	var reach: float = float(model.wheelbase) * 4.2
	camera.position = Vector3(0.0, reach * 0.3, reach)
	camera.look_at(Vector3(0.0, 0.75, 0.0))

func _process(delta: float) -> void:
	if stand != null: stand.rotation.y += delta * TURN
