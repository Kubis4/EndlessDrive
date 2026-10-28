extends SceneTree
const OUT := "res://assets/models/supplied_environment/"
var materials := {}
var textures := {}
var failed := false
func _initialize() -> void: call_deferred("_run")
func copy_mesh(source: MeshInstance3D, label: String) -> Mesh:
 var mesh := source.mesh.duplicate(true) as ArrayMesh
 for i in mesh.get_surface_count():
  var original := source.mesh.surface_get_material(i) as StandardMaterial3D
  if original == null: continue
  var id := original.get_instance_id()
  if not materials.has(id):
   var mat := original.duplicate() as StandardMaterial3D
   for prop in ["albedo_texture", "normal_texture", "roughness_texture", "metallic_texture", "emission_texture", "ao_texture"]:
    var tex := mat.get(prop) as Texture2D
    if tex == null: continue
    var tid := tex.get_instance_id()
    if not textures.has(tid):
     var image := tex.get_image()
     if maxi(image.get_width(), image.get_height()) > 1024:
      var ratio := 1024.0 / maxi(image.get_width(), image.get_height())
      image.resize(maxi(1, roundi(image.get_width() * ratio)), maxi(1, roundi(image.get_height() * ratio)), Image.INTERPOLATE_LANCZOS)
     image.generate_mipmaps()
     var texture := ImageTexture.create_from_image(image)
     var texpath := OUT + "texture_%d.res" % textures.size()
     if ResourceSaver.save(texture, texpath, ResourceSaver.FLAG_COMPRESS) != OK: failed = true
     textures[tid] = load(texpath)
    mat.set(prop, textures[tid])
   var matpath := OUT + "material_%d.tres" % materials.size()
   if ResourceSaver.save(mat, matpath) != OK: failed = true
   materials[id] = load(matpath)
  mesh.surface_set_material(i, materials[id])
 var path := OUT + label + ".res"
 if ResourceSaver.save(mesh, path, ResourceSaver.FLAG_COMPRESS) != OK: failed = true
 return load(path)
func save_scene(node: Node3D, path: String) -> void:
 for child in node.get_children(): child.owner = node
 var scene := PackedScene.new()
 if scene.pack(node) != OK or ResourceSaver.save(scene, path) != OK:
  push_error("Cannot save environment scene " + path)
  failed = true
 node.free()
func get_mesh(source: Node, name: String) -> MeshInstance3D:
 var found := source.find_children(name, "MeshInstance3D", true, false)
 assert(not found.is_empty(), "Missing mesh " + name)
 return found[0] as MeshInstance3D
func make_building(source: Node, kind: String, names: Array[String], size: float = 1.0) -> void:
 var originals: Array[MeshInstance3D] = []
 var bounds := AABB()
 for name in names:
  var mesh := get_mesh(source, name + "_elements_0")
  var box := mesh.global_transform * mesh.mesh.get_aabb()
  bounds = box if originals.is_empty() else bounds.merge(box)
  originals.append(mesh)
 var origin := Vector3(bounds.get_center().x, bounds.position.y, bounds.get_center().z)
 var result := Node3D.new()
 result.name = "Supplied_" + kind
 for original in originals:
  var visual := MeshInstance3D.new()
  visual.name = original.name
  visual.mesh = copy_mesh(original, "building_" + str(original.name).to_lower())
  visual.transform = original.global_transform
  visual.position -= origin
  visual.transform = Transform3D(Basis.from_scale(Vector3.ONE * size), Vector3.ZERO) * visual.transform
  result.add_child(visual)
 var body := StaticBody3D.new()
 body.name = "BuildingCollision"
 result.add_child(body)
 var shape := CollisionShape3D.new()
 shape.shape = originals[0].mesh.create_trimesh_shape()
 shape.transform = Transform3D(Basis.from_scale(Vector3.ONE * size), Vector3.ZERO) * Transform3D(originals[0].global_transform.basis, originals[0].global_position - origin)
 body.add_child(shape)
 shape.owner = result
 save_scene(result, "res://assets/models/stops/" + kind + ".tscn")
 print("BUILDING ",kind," bounds ",bounds.size * size)
func make_station(source: Node) -> void:
 var main := get_mesh(source, "STATION_LOW_POLY_mat_0")
 var bounds := main.global_transform * main.mesh.get_aabb()
 var origin := Vector3(bounds.get_center().x, 0, bounds.get_center().z)
 var result := Node3D.new()
 result.name = "SuppliedGasStation"
 for original: MeshInstance3D in source.find_children("*", "MeshInstance3D", true, false):
  if original.name.begins_with("ROAD_") or original.name.begins_with("GROUND_") or original.name.begins_with("FENCE_") or original.name.begins_with("GATE_") or original.name.begins_with("POST_") or original.name.begins_with("CABLE_") or original.name.begins_with("ARRAY_"): continue
  var visual := MeshInstance3D.new()
  visual.name = original.name
  visual.mesh = copy_mesh(original, "gas_" + str(original.name).to_lower())
  visual.transform = original.global_transform
  visual.position -= origin
  visual.transform = Transform3D(Basis.from_scale(Vector3.ONE * 0.01), Vector3.ZERO) * visual.transform
  result.add_child(visual)
 var body := StaticBody3D.new()
 body.name = "StationCollision"
 result.add_child(body)
 var shape := CollisionShape3D.new()
 shape.shape = main.mesh.create_trimesh_shape()
 shape.transform = Transform3D(Basis.from_scale(Vector3.ONE * 0.01), Vector3.ZERO) * Transform3D(main.global_transform.basis, main.global_position - origin)
 body.add_child(shape)
 shape.owner = result
 # The source forecourt points towards -X; stops face the road on local +Z.
 var turn := Transform3D(Basis(Vector3.UP, PI * 0.5), Vector3.ZERO)
 for child: Node3D in result.get_children(): child.transform = turn * child.transform
 save_scene(result, "res://assets/models/stops/gas.tscn")
 print("GAS bounds ", bounds.size * 0.01)
func make_rock(source: Node, family: String, index: int) -> void:
 var original := get_mesh(source, "%s_Rock%d_M_%s_Rock_0" % [family,index,family])
 var bounds := original.global_transform * original.mesh.get_aabb()
 var origin := Vector3(bounds.get_center().x, bounds.position.y, bounds.get_center().z)
 var result := Node3D.new()
 result.name = "SuppliedRock_%s_%d" % [family,index]
 var visual := MeshInstance3D.new()
 visual.name = "RockVisual"
 visual.mesh = copy_mesh(original, "rock_%s_%d" % [family.to_lower(),index])
 visual.transform = original.global_transform
 visual.position -= origin
 visual.transform = Transform3D(Basis.from_scale(Vector3(1.0/bounds.size.x,1.0/bounds.size.y,1.0/bounds.size.z)), Vector3.ZERO) * visual.transform
 result.add_child(visual)
 var body := StaticBody3D.new()
 body.name = "RockCollision"
 result.add_child(body)
 var shape := CollisionShape3D.new()
 var box := BoxShape3D.new()
 box.size = Vector3(0.75, 0.8, 0.75)
 shape.shape = box
 shape.position.y = 0.4
 body.add_child(shape)
 shape.owner = result
 save_scene(result, OUT + "rock_%s_%d.tscn" % [family.to_lower(),index])
func _run() -> void:
 var buildings := (load("res://art/source_environment/lowpoly_buildings.glb") as PackedScene).instantiate()
 root.add_child(buildings)
 make_building(buildings, "home", ["house03", "house03_add", "house03_roof"])
 make_building(buildings, "workshop", ["barn03"])
 make_building(buildings, "repair", ["barrack04"])
 make_building(buildings, "paint", ["barrack02"])
 buildings.free()
 var gas := (load("res://art/source_environment/old_gas_station.glb") as PackedScene).instantiate()
 root.add_child(gas)
 make_station(gas)
 gas.free()
 var rocks := (load("res://art/source_environment/stylised_rocks_asset_pack.glb") as PackedScene).instantiate()
 root.add_child(rocks)
 for family in ["Plain", "Mossy", "Snowy", "Desert"]:
  for index in [1,2,3,4]: make_rock(rocks, family, index)
 rocks.free()
 print("ENVIRONMENT BUILD ", "FAILED" if failed else "COMPLETE")
 quit(1 if failed else 0)
