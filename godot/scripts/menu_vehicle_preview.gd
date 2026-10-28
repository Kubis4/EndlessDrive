extends SubViewportContainer
## Renders the same rig and paint material used by the running journey.
const Catalog = preload("res://scripts/vehicle_catalog.gd")
var _view: SubViewport
var _model: Node3D
var vehicle_id := ""

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	stretch = true
	_view = SubViewport.new()
	_view.size = Vector2i(400, 250)
	_view.transparent_bg = true
	_view.own_world_3d = true
	_view.render_target_update_mode = SubViewport.UPDATE_ONCE
	add_child(_view)
	var env := WorldEnvironment.new()
	env.environment = Environment.new()
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.environment.ambient_light_color = Color("f5e8d4")
	env.environment.ambient_light_energy = 0.8
	_view.add_child(env)
	var light := DirectionalLight3D.new()
	light.rotation_degrees = Vector3(-48, -40, 0)
	light.light_color = Color("ffe4bd")
	light.light_energy = 1.1
	_view.add_child(light)
	var camera := Camera3D.new()
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 5.7
	_view.add_child(camera)
	camera.position = Vector3(-6, 3.5, 5)
	camera.look_at(Vector3(0, 0.45, 0))

func set_vehicle(id: String, paint: Color, damage: float = 0.0) -> void:
	if _view == null or not Catalog.DATA.has(id): return
	if vehicle_id != id or not is_instance_valid(_model):
		if is_instance_valid(_model): _model.free()
		vehicle_id = id
		_model = (load(Catalog.DATA[id].scene) as PackedScene).instantiate() as Node3D
		_view.add_child(_model)
		for mesh: MeshInstance3D in _model.find_children("*", "MeshInstance3D", true, false):
			if mesh.material_override is ShaderMaterial:
				mesh.material_override = mesh.material_override.duplicate()
	for mesh: MeshInstance3D in _model.find_children("*", "MeshInstance3D", true, false):
		if mesh.material_override is ShaderMaterial:
			(mesh.material_override as ShaderMaterial).set_shader_parameter("tint", paint)
			(mesh.material_override as ShaderMaterial).set_shader_parameter("body_damage", damage)
	_view.render_target_update_mode = SubViewport.UPDATE_ONCE
