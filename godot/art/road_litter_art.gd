extends RefCounted
## Shared opaque leaf geometry and material: one ground batch per chunk.
static var _leaf: ArrayMesh
static var _material: StandardMaterial3D
static func leaf_mesh() -> ArrayMesh:
	if _leaf != null: return _leaf
	var vertices := PackedVector3Array([Vector3(0, 0.006, 0), Vector3(0, 0, -0.14), Vector3(0.07, 0, -0.04), Vector3(0.06, 0, 0.06), Vector3(0, 0.002, 0.15), Vector3(-0.06, 0, 0.06), Vector3(-0.07, 0, -0.04)])
	var normals := PackedVector3Array()
	var colors := PackedColorArray()
	for i in vertices.size():
		normals.append(Vector3.UP)
		colors.append(Color("8d6e3a") if i % 2 == 0 else Color("6e5834"))
	var indices := PackedInt32Array()
	for i in range(1, 7): indices.append_array(PackedInt32Array([0, i, 1 if i == 6 else i + 1]))
	var arrays: Array = []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_COLOR] = colors
	arrays[Mesh.ARRAY_INDEX] = indices
	_leaf = ArrayMesh.new()
	_leaf.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	return _leaf
static func leaf_material() -> StandardMaterial3D:
	if _material == null:
		_material = StandardMaterial3D.new()
		_material.vertex_color_use_as_albedo = true
		_material.vertex_color_is_srgb = true
		_material.cull_mode = BaseMaterial3D.CULL_DISABLED
		_material.roughness = 0.98
	return _material
