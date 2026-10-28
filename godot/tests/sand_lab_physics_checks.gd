extends SceneTree
func _initialize() -> void: call_deferred("_run")
func _run() -> void:
 var lab = load("res://scenes/sand_lab.tscn").instantiate()
 root.add_child(lab)
 var crate: RigidBody3D = lab.get_node("TestObjects/CrateC")
 crate.position = Vector3(-12, lab.world.height_at(-12, -4) + 0.61, -4)
 var start := crate.position
 for body: RigidBody3D in lab.sand_grains.bodies:
  body.contact_monitor = true
  body.max_contacts_reported = 4
 var emitted := false
 var landed := false
 for tick in 280:
  if tick > 30: Input.action_press("throttle", 0.7)
  await physics_frame
  await process_frame
  for body: RigidBody3D in lab.sand_grains.bodies:
   if body.visible: emitted = true
   if body.visible and body.get_contact_count() > 0: landed = true
 Input.action_release("throttle")
 var displacement := Vector2(crate.position.x - start.x, crate.position.z - start.z).length()
 var hit: bool = displacement > 0.5 and lab.vehicle.obstacle_impacts > 0
 print("CRATE movement=%.2f m impacts=%d" % [displacement, lab.vehicle.obstacle_impacts])
 print("%s Car physically moves the crate" % ("PASS" if hit else "FAIL"))
 print("%s Bounded sand chunks are emitted and collide with terrain" % ("PASS" if emitted and landed else "FAIL"))
 lab.reset_objects()
 var reset: Transform3D = lab.get_node("TestObjects").original[crate]
 var restored := crate.position.distance_to(reset.origin) < 0.02 and crate.linear_velocity.length() < 0.01
 print("%s Object reset restores position and velocity" % ("PASS" if restored else "FAIL"))
 lab.queue_free()
 await process_frame
 quit(0 if hit and emitted and landed and restored else 1)

