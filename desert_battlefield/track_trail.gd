extends RefCounted
## Immutable finished chunks, one small writable tail. Never rebuild old tracks.
const Surface = preload("res://desert_battlefield/terrain.gd")
const CHUNK_SAMPLES := 32
const MAX_CHUNKS := 24
const SAMPLE_SPACING := 0.28
var root: Node3D
var half_track := 1.12
var material: ShaderMaterial
var chunks: Array[MeshInstance3D] = []
var chunk_starts: Array[float] = []
var vertices := PackedVector3Array()
var normals := PackedVector3Array()
var uvs := PackedVector2Array()
var indices := PackedInt32Array()
var has_previous := false
var previous := Vector3.ZERO
var previous_side := Vector3.RIGHT
var path_distance := 0.0
var generated_vertices := 0
var active_count := 0
var active: MeshInstance3D

func configure(parent: Node3D, shared_material: ShaderMaterial) -> void:
	root = parent
	material = shared_material
	indices.resize((CHUNK_SAMPLES - 1) * 12)
	for i in CHUNK_SAMPLES - 1:
		for tyre in 2:
			var a := i * 4 + tyre * 2
			var at := i * 12 + tyre * 6
			indices[at] = a
			indices[at + 1] = a + 1
			indices[at + 2] = a + 4
			indices[at + 3] = a + 1
			indices[at + 4] = a + 5
			indices[at + 5] = a + 4

func break_trail() -> void:
	has_previous = false
	active = null
	active_count = 0

func append(point: Vector3, side: Vector3) -> void:
	if not has_previous:
		previous = point
		previous_side = side
		has_previous = true
		_start_chunk()
		_add_point(point, side)
		return
	var distance := point.distance_to(previous)
	if distance > 4.0:
		break_trail()
		append(point, side)
		return
	if distance < 0.04: return
	var steps := maxi(1, ceili(distance / SAMPLE_SPACING))
	for i in steps:
		var f := float(i + 1) / steps
		var p := previous.lerp(point, f)
		var s := previous_side.lerp(side, f).normalized()
		path_distance += distance / steps
		_add_point(p, s)
		if active_count == CHUNK_SAMPLES:
			_flush_tail()
			_start_chunk()
			_add_point(p, s)
	previous = point
	previous_side = side
	_flush_tail()
	material.set_shader_parameter("trail_start_distance", chunk_starts.front())

func _start_chunk() -> void:
	vertices.clear()
	normals.clear()
	uvs.clear()
	active_count = 0
	active = MeshInstance3D.new()
	active.name = "TreadChunk"
	active.material_override = material
	active.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	active.extra_cull_margin = 1.0
	root.add_child(active)
	chunks.append(active)
	chunk_starts.append(path_distance)
	if chunks.size() > MAX_CHUNKS:
		chunks.pop_front().queue_free()
		chunk_starts.pop_front()

func _add_point(point: Vector3, side: Vector3) -> void:
	for tyre in [-1.0, 1.0]:
		for edge in [-1.0, 1.0]:
			var p: Vector3 = point + side * (tyre * half_track + edge * 0.16)
			p.y = Surface.height_at(p.x, p.z) + 0.025
			vertices.append(p)
			normals.append(Surface.normal_at(p.x, p.z))
			uvs.append(Vector2((edge + 1.0) * 0.5, path_distance))
	active_count += 1
	generated_vertices += 4

func _flush_tail() -> void:
	if active_count < 2: return
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_TEX_UV] = uvs
	arrays[Mesh.ARRAY_INDEX] = indices.slice(0, (active_count - 1) * 12)
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	active.mesh = mesh
