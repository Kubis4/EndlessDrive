extends SceneTree
## Bakes light trees from the supplied winter conifers (Downloads/fbx+file, imported in a
## small project of their own, under res://src). Each tree becomes two meshes under res://out:
## _hi for up close and _lo for further off, surface 0 the bark, surface 1 the needles: a
## share of the needle cards, each grown to keep the crown as full, and a simple trunk.
## Usage: godot --headless --path <project> --script build_winter_trees.gd
const TREES := {"winter_a": "obovata_A_winter", "winter_b": "obovata_B_winter", "winter_c": "jeffreyi_D-winter"}
const HI_NEEDLES := 1100
const LO_NEEDLES := 120
const HI_BARK := 300

func _initialize() -> void:
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://out"))
	for id: String in TREES:
		var root: Node = (load("res://src/%s.fbx" % TREES[id]) as PackedScene).instantiate()
		var node: MeshInstance3D = root.find_children("*", "MeshInstance3D", true, false)[0]
		var to_world := Transform3D()
		var n: Node = node
		while n != null and n is Node3D:
			to_world = (n as Node3D).transform * to_world
			n = n.get_parent()
		var bark: Array = []
		var needles: Array = []
		for s in node.mesh.get_surface_count():
			var material: StandardMaterial3D = node.mesh.surface_get_material(s)
			var arrays := node.mesh.surface_get_arrays(s)
			var moved := PackedVector3Array()
			for v: Vector3 in arrays[Mesh.ARRAY_VERTEX]: moved.append(to_world * v)
			arrays[Mesh.ARRAY_VERTEX] = moved
			var turned := PackedVector3Array()
			for v: Vector3 in arrays[Mesh.ARRAY_NORMAL]: turned.append((to_world.basis * v).normalized())
			arrays[Mesh.ARRAY_NORMAL] = turned
			if material.resource_name.begins_with("Bark"):
				bark = arrays
				_save_image(material.albedo_texture, "res://out/%s_bark.png" % id, 256, false)
			elif material.resource_name.begins_with("Leaf"):
				needles = arrays
				_save_image(material.albedo_texture, "res://out/%s_needles.png" % id, 1024, true)
		var groups := _cards(needles[Mesh.ARRAY_INDEX], (needles[Mesh.ARRAY_VERTEX] as PackedVector3Array).size())
		var base := _foot(bark)
		print("%s: bark %d tris, needles %d tris in %d cards" % [id, (bark[Mesh.ARRAY_INDEX] as PackedInt32Array).size() / 3, (needles[Mesh.ARRAY_INDEX] as PackedInt32Array).size() / 3, groups.size()])
		_save("res://out/%s_hi.res" % id, _reduced_bark(bark, base), _thinned(needles, groups, HI_NEEDLES, base), id)
		_save("res://out/%s_lo.res" % id, _simple_trunk(bark, base), _thinned(needles, groups, LO_NEEDLES, base), id)
		root.free()
	quit()

## Where the trunk stands, at its lowest point.
func _foot(bark: Array) -> Vector3:
	var vertices: PackedVector3Array = bark[Mesh.ARRAY_VERTEX]
	var low := INF
	for v in vertices: low = minf(low, v.y)
	var total := Vector3.ZERO
	var count := 0
	for v in vertices:
		if v.y < low + 0.4:
			total += v
			count += 1
	var middle := total / count
	return Vector3(middle.x, low, middle.z)

## The needle cards: triangles joined by shared vertices.
func _cards(indices: PackedInt32Array, vertex_count: int) -> Array:
	var parent := PackedInt32Array()
	parent.resize(vertex_count)
	for i in vertex_count: parent[i] = i
	var find := func(start: int) -> int:
		var at := start
		while parent[at] != at:
			parent[at] = parent[parent[at]]
			at = parent[at]
		return at
	for t in range(0, indices.size(), 3):
		var a: int = find.call(indices[t])
		parent[a] = find.call(indices[t + 1])
		a = find.call(indices[t])
		parent[a] = find.call(indices[t + 2])
	var by_root := {}
	for t in range(0, indices.size(), 3):
		var r: int = find.call(indices[t])
		if not by_root.has(r): by_root[r] = PackedInt32Array()
		by_root[r].append_array([indices[t], indices[t + 1], indices[t + 2]])
	return by_root.values()

## A share of the cards, evenly up the tree, grown about their middles; lit mostly from above.
func _thinned(arrays: Array, groups: Array, wanted: int, base: Vector3) -> Array:
	var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL]
	var uvs: PackedVector2Array = arrays[Mesh.ARRAY_TEX_UV]
	var heights := []
	for g in groups.size():
		var card: PackedInt32Array = groups[g]
		heights.append([vertices[card[0]].y, g])
	heights.sort()
	var total := 0
	for g: PackedInt32Array in groups: total += g.size() / 3
	var share := minf(1.0, float(wanted) / total)
	var grow := minf(sqrt(1.0 / share), 3.4)
	var random := RandomNumberGenerator.new()
	random.seed = wanted
	var out_v := PackedVector3Array()
	var out_n := PackedVector3Array()
	var out_uv := PackedVector2Array()
	var out_i := PackedInt32Array()
	var carry := 0.0
	for entry: Array in heights:
		carry += share
		if carry < 1.0: continue
		carry -= 1.0
		var card: PackedInt32Array = groups[entry[1]]
		var middle := Vector3.ZERO
		for i in card: middle += vertices[i]
		middle /= card.size()
		var seen := {}
		for i in card:
			if not seen.has(i):
				seen[i] = out_v.size()
				out_v.append(middle + (vertices[i] - middle) * grow - base)
				out_n.append((normals[i] * 0.4 + Vector3.UP).normalized())
				out_uv.append(uvs[i])
			out_i.append(seen[i])
	return _arrays(out_v, out_n, out_uv, out_i)

func _reduced_bark(arrays: Array, base: Vector3) -> Array:
	var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var moved := PackedVector3Array()
	for v in vertices: moved.append(v - base)
	var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
	if indices.size() / 3 > HI_BARK:
		var importer := ImporterMesh.new()
		var copy := arrays.duplicate()
		copy[Mesh.ARRAY_VERTEX] = moved
		importer.add_surface(Mesh.PRIMITIVE_TRIANGLES, copy)
		importer.generate_lods(60.0, 0.0, [])
		for lod in importer.get_surface_lod_count(0):
			var candidate := importer.get_surface_lod_indices(0, lod)
			if candidate.size() / 3 <= HI_BARK:
				indices = candidate
				break
	return _arrays(moved, arrays[Mesh.ARRAY_NORMAL], arrays[Mesh.ARRAY_TEX_UV], indices)

## A six-sided cone up to the height where the crown takes over.
func _simple_trunk(arrays: Array, base: Vector3) -> Array:
	var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var top := 0.0
	var foot := 0.0
	for v in vertices:
		top = maxf(top, v.y - base.y)
		if v.y - base.y < 0.5: foot = maxf(foot, Vector2(v.x - base.x, v.z - base.z).length())
	var height := top * 0.75
	var out_v := PackedVector3Array()
	var out_n := PackedVector3Array()
	var out_uv := PackedVector2Array()
	var out_i := PackedInt32Array()
	for side in 7:
		var around := Vector2.from_angle(TAU * side / 6.0)
		var out := Vector3(around.x, 0.0, around.y)
		out_v.append(out * minf(foot, 0.4))
		out_n.append(out)
		out_uv.append(Vector2(side / 3.0, 3.0))
		out_v.append(out * 0.03 + Vector3.UP * height)
		out_n.append(out)
		out_uv.append(Vector2(side / 3.0, 0.0))
	for side in 6:
		var a := side * 2
		out_i.append_array([a, a + 1, a + 2, a + 2, a + 1, a + 3])
	return _arrays(out_v, out_n, out_uv, out_i)

func _arrays(vertices: PackedVector3Array, normals: PackedVector3Array, uvs: PackedVector2Array, indices: PackedInt32Array) -> Array:
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = vertices
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_TEX_UV] = uvs
	arrays[Mesh.ARRAY_INDEX] = indices
	return arrays

func _save(path: String, bark: Array, needles: Array, id: String) -> void:
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, bark)
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, needles)
	ResourceSaver.save(mesh, path, ResourceSaver.FLAG_COMPRESS)
	print("  %s: bark %d, needles %d tris" % [path.get_file(), (bark[Mesh.ARRAY_INDEX] as PackedInt32Array).size() / 3, (needles[Mesh.ARRAY_INDEX] as PackedInt32Array).size() / 3])

func _save_image(texture: Texture2D, path: String, largest: int, keep_alpha: bool) -> void:
	var image := texture.get_image()
	if image.is_compressed(): image.decompress()
	var factor := minf(1.0, float(largest) / maxi(image.get_width(), image.get_height()))
	image.resize(maxi(4, roundi(image.get_width() * factor)), maxi(4, roundi(image.get_height() * factor)), Image.INTERPOLATE_LANCZOS)
	if keep_alpha: image.fix_alpha_edges()
	image.save_png(path)
