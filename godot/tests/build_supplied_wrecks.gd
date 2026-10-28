extends SceneTree
## Convert the two full-size cars from the supplied FBX into independent assets.
const OUT := "res://assets/models/wrecks/"
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
	var source := (load("res://art/source_loot/supplied/fbx.fbx") as PackedScene).instantiate()
	root.add_child(source)
	for index in 2:
		var group := source.get_node("RustyCarsFree/Group%03d" % (index + 1)) as Node3D
		var body := group.find_children("SM_asset*", "MeshInstance3D", true, false)[0] as MeshInstance3D
		var bounds: AABB = body.global_transform * body.mesh.get_aabb()
		var origin := Vector3(bounds.get_center().x, bounds.position.y, bounds.get_center().z)
		var car := Node3D.new()
		car.name = "SuppliedWreck%d" % index
		for original: MeshInstance3D in group.find_children("*", "MeshInstance3D", true, false):
			var mesh := original.mesh.duplicate(true) as ArrayMesh
			for surface in mesh.get_surface_count():
				var material := mesh.surface_get_material(surface) as StandardMaterial3D
				if material == null: continue
				for property in ["albedo_texture", "normal_texture", "roughness_texture", "metallic_texture", "emission_texture"]:
					var texture := material.get(property) as Texture2D
					if texture != null and not texture.resource_path.is_empty():
						var path := OUT + "textures/" + texture.resource_path.get_file()
						if ResourceLoader.exists(path): material.set(property, load(path))
			mesh.resource_name = original.name
			var mesh_path := OUT + "wreck_%d_%s.res" % [index, original.name.to_lower()]
			ResourceSaver.save(mesh, mesh_path, ResourceSaver.FLAG_COMPRESS)
			var visual := MeshInstance3D.new()
			visual.name = original.name
			visual.mesh = load(mesh_path)
			visual.transform = original.global_transform
			visual.position -= origin
			car.add_child(visual)
			visual.owner = car
		var scene := PackedScene.new()
		scene.pack(car)
		ResourceSaver.save(scene, OUT + "wreck_%d.tscn" % index)
		print("WRECK ", index, " bounds ", bounds, " parts ", car.get_child_count())
		car.free()
	source.queue_free()
	quit()
