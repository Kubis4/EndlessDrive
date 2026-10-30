extends RefCounted
## Partition the existing triangles without changing their positions or normals.
## A single 360 m mesh sends the whole desert to every visible shadow cascade.
const QUADS := 480
const SOURCE_SIDE := QUADS + 1

## chunk_quads: 32 (24 m, fine culling) on desktop; mobile uses 48 (36 m) so the
## GL renderer issues fewer draw calls per view and shadow cascade.
static func partition(base: MeshInstance3D, chunk_quads: int = 32) -> void:
	var CHUNK_QUADS := chunk_quads
	var SIDE := CHUNK_QUADS + 1
	var source := base.mesh.surface_get_arrays(0)
	var positions: PackedVector3Array = source[Mesh.ARRAY_VERTEX]
	var normals: PackedVector3Array = source[Mesh.ARRAY_NORMAL]
	if positions.size() != SOURCE_SIDE * SOURCE_SIDE:
		push_warning("Unexpected dune topology; keeping the authored terrain mesh.")
		return
	var indices := PackedInt32Array()
	indices.resize(CHUNK_QUADS * CHUNK_QUADS * 6)
	for z in CHUNK_QUADS:
		for x in CHUNK_QUADS:
			var a := z * SIDE + x
			var offset := (z * CHUNK_QUADS + x) * 6
			indices[offset] = a
			indices[offset + 1] = a + 1
			indices[offset + 2] = a + SIDE
			indices[offset + 3] = a + 1
			indices[offset + 4] = a + SIDE + 1
			indices[offset + 5] = a + SIDE
	for tz in QUADS / CHUNK_QUADS:
		for tx in QUADS / CHUNK_QUADS:
			var vertices := PackedVector3Array()
			var local_normals := PackedVector3Array()
			vertices.resize(SIDE * SIDE)
			local_normals.resize(SIDE * SIDE)
			for z in SIDE:
				for x in SIDE:
					var src := (tz * CHUNK_QUADS + z) * SOURCE_SIDE + tx * CHUNK_QUADS + x
					vertices[z * SIDE + x] = positions[src]
					local_normals[z * SIDE + x] = normals[src]
			var arrays := []
			arrays.resize(Mesh.ARRAY_MAX)
			arrays[Mesh.ARRAY_VERTEX] = vertices
			arrays[Mesh.ARRAY_NORMAL] = local_normals
			arrays[Mesh.ARRAY_INDEX] = indices
			var chunk := MeshInstance3D.new()
			chunk.name = "Dune_%d_%d" % [tx, tz]
			var mesh := ArrayMesh.new()
			mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
			chunk.mesh = mesh
			chunk.material_override = base.material_override
			chunk.cast_shadow = base.cast_shadow
			base.add_child(chunk)
	base.mesh = null
