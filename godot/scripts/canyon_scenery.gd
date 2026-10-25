extends RefCounted
## Continuous eroded sandstone walls. Adjacent segments share their edge
## samples; broad strata replace the repeated scaled cube-like boulders.
static var _material: ShaderMaterial
static func wall(world: DriveWorld, parent: Node3D, start: float, side: float) -> void:
	if _material == null:
		_material = ShaderMaterial.new()
		_material.shader = preload("res://shaders/canyon_wall.gdshader")
		_material.set_shader_parameter("stone_texture", load("res://assets/textures/world-materials-atlas.png"))
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for i in 4:
		for layer in 7:
			var a := _point(world, start + i * 2.0, side, layer)
			var b := _point(world, start + (i + 1) * 2.0, side, layer)
			var c := _point(world, start + i * 2.0, side, layer + 1)
			var d := _point(world, start + (i + 1) * 2.0, side, layer + 1)
			for p: Vector3 in [a, c, b, b, c, d]:
				st.add_vertex(p)
	st.generate_normals()
	var mesh := MeshInstance3D.new()
	mesh.name = "SandstoneWall"
	mesh.mesh = st.commit()
	mesh.material_override = _material
	# It receives shadows; the visible face stays clear of grazing-angle acne.
	mesh.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	parent.add_child(mesh, true)

static func _point(world: DriveWorld, x: float, side: float, layer: int) -> Vector3:
	var z := world.terrain.corridor_center(x) + world.terrain.boundary_width(x) * side
	var floor_y := world.height_at(x, z)
	var t := layer / 5.0
	if layer >= 6:
		var cap := floor_y + 11.0 + sin(x * 0.033) * 3.0
		return Vector3(x, cap if layer == 6 else floor_y - 1.0, z + side * (17.0 if layer == 6 else 27.0))
	var eroded := sin(x * 0.21 + layer * 0.45) * sin(t * PI) * 1.5
	z += side * (-1.5 + t * 9.0 + eroded)
	return Vector3(x, floor_y - 1.0 + t * (12.0 + sin(x * 0.033) * 3.0) + sin(t * PI) * sin(x * 0.11) * 0.45, z)
