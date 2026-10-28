extends Node3D
var original := {}
func setup(world: DriveWorld) -> void:
 for node: Node3D in get_children():
  node.position.y = world.height_at(node.position.x, node.position.z) + float(node.get_meta("ground_offset", 0.5))
  original[node] = node.transform
func reset_objects() -> void:
 for node: Node3D in original:
  if node is RigidBody3D:
   node.freeze = true
   node.linear_velocity = Vector3.ZERO
   node.angular_velocity = Vector3.ZERO
  node.transform = original[node]
  if node is RigidBody3D:
   node.freeze = false
   node.sleeping = false
