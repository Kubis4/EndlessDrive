extends SceneTree
var failures := 0
func _initialize() -> void: call_deferred("_run")
func check(ok: bool, label: String) -> void:
 print("%s %s" % ["PASS" if ok else "FAIL", label])
 if not ok: failures += 1
func _run() -> void:
 var world := DriveWorld.new()
 root.add_child(world)
 world.configure(24601)
 world.batch_scenery = false
 var chunk := Node3D.new()
 world.add_child(chunk)
 for kind in DriveStops.KINDS:
  var scene := load("res://assets/models/stops/" + kind + ".tscn") as PackedScene
  var model := scene.instantiate() as Node3D
  check(str(model.name).begins_with("Supplied"), "%s uses supplied building" % kind)
  check(not model.find_children("*", "CollisionShape3D", true, false).is_empty(), "%s building has collision" % kind)
  check(not model.find_children("*", "MeshInstance3D", true, false).is_empty(), "%s building has visual geometry" % kind)
  if kind == "gas":
   check(model.find_children("ROAD*", "MeshInstance3D", true, false).is_empty() and model.find_children("GROUND*", "MeshInstance3D", true, false).is_empty(), "Station has no source road or ground")
  model.free()
 for biome in range(6):
  var pos := Vector3(200 + biome * 10, 0, 0)
  world._place_supplied_rock(chunk, pos, biome, biome, Vector3.ONE, 0)
  var rock := chunk.get_child(chunk.get_child_count() - 1)
  check(str(rock.name).begins_with("SuppliedRock_" + world._rock_family(biome).capitalize()), "Biome %d selects appropriate rock" % biome)
  check(not rock.find_children("*", "CollisionShape3D", true, false).is_empty(), "Biome %d rock has collision" % biome)
 var run := DriveRun.new()
 run.world_seed = 24601
 for region in range(1, 6):
  var stop := DriveStops.next_stop(region * DriveStops.REGION_LENGTH, 24601)
  var loot := run.open_loot(int(stop.id)).duplicate(true)
  check(loot == run.open_loot(int(stop.id)), "Loot stays stable for %s" % stop.kind)
 world.queue_free()
 await process_frame
 print("ENVIRONMENT CHECKS: %d failures" % failures)
 quit(1 if failures else 0)
