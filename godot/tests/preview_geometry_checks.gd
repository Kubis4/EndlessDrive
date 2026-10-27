extends SceneTree
## Covers stale external mesh caches and baked copies of the rounded peaks.
class PreviewHarness:
	extends "res://scripts/editor_preview.gd"
	func _ready() -> void: pass

var failures := 0
var checks := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, label: String) -> void:
	checks += 1
	if not ok: failures += 1
	print("%s %s" % ["PASS" if ok else "FAIL", label])
func _run() -> void:
	var mesh_path := "user://preview-cache-test.tres"
	var scene_path := "user://preview-cache-test.tscn"
	var old_mesh := BoxMesh.new()
	old_mesh.size = Vector3.ONE
	check(ResourceSaver.save(old_mesh, mesh_path) == OK, "Create old external mesh fixture")
	var cached := load(mesh_path) as BoxMesh
	var geometry := Node3D.new()
	geometry.name = "GeneratedTerrain"
	var stone := MeshInstance3D.new()
	stone.name = "Stone"
	stone.mesh = cached
	geometry.add_child(stone)
	stone.owner = geometry
	var packed := PackedScene.new()
	packed.pack(geometry)
	check(ResourceSaver.save(packed, scene_path) == OK, "Create saved scene with external mesh")
	geometry.free()
	var preview := PreviewHarness.new()
	preview.geometry_path = scene_path
	root.add_child(preview)
	preview.position = Vector3(7, 3, -2)
	preview.scale = Vector3.ONE * 1.1
	preview.add_child((load(scene_path) as PackedScene).instantiate())
	var replacement := BoxMesh.new()
	replacement.size = Vector3(4, 2, 3)
	check(ResourceSaver.save(replacement, mesh_path) == OK and cached.size == Vector3.ONE, "Disk mesh changes while the original resource remains cached")
	preview._reload_geometry()
	var fresh := preview.get_node("GeneratedTerrain/Stone") as MeshInstance3D
	check(fresh.mesh.get_aabb().size.is_equal_approx(replacement.size), "Preview bypasses stale external mesh cache")
	check(preview.get_child_count() == 1 and preview.position == Vector3(7, 3, -2) and preview.scale.is_equal_approx(Vector3.ONE * 1.1), "Reload replaces only generated geometry and preserves authored placement")
	preview.queue_free()
	for label in ["editor_terrain", "bridge", "rail", "tunnel", "alpine", "mountain", "canyon", "forest", "swamp"]:
		var scene := (load("res://scenes/world/generated/%s.scn" % label) as PackedScene).instantiate()
		var peaks := 0
		var rounded := true
		for mesh: MeshInstance3D in scene.find_children("SculptedPeak", "MeshInstance3D", true, false):
			peaks += 1
			var bounds := mesh.mesh.get_aabb()
			var crown := AABB()
			var started := false
			for point: Vector3 in mesh.mesh.surface_get_arrays(0)[Mesh.ARRAY_VERTEX]:
				if point.y > bounds.end.y - 2.5:
					crown = crown.expand(point) if started else AABB(point, Vector3.ZERO)
					started = true
			rounded = rounded and crown.size.x > 8.0 and crown.size.z > 6.0
		check(rounded and (peaks > 0 if label in ["editor_terrain", "bridge", "alpine"] else true), "%s saved preview contains no pointed peak crowns (%d peaks)" % [label, peaks])
		scene.free()
	await process_frame
	print("PREVIEW GEOMETRY: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
