extends Node3D
## Small reusable pool of colliding sand clumps; the fine spray uses 3D particles.
const CAPACITY := 40
var vehicle: DriveVehicle
var world: DriveWorld
var enabled := true
var bodies: Array[RigidBody3D] = []
var ages: Array[float] = []
var cursor := 0
var emission_left := 0.0
var rng := RandomNumberGenerator.new()

func _ready() -> void:
 rng.seed = 49171
 var mat := StandardMaterial3D.new()
 mat.albedo_color = Color("cbb184")
 mat.roughness = 1.0
 var mesh := SphereMesh.new()
 mesh.radius = 0.045
 mesh.height = 0.09
 mesh.radial_segments = 5
 mesh.rings = 2
 mesh.material = mat
 var shape := SphereShape3D.new()
 shape.radius = 0.045
 for i in CAPACITY:
  var body := RigidBody3D.new()
  body.name = "SandClump%02d" % i
  body.mass = 0.04
  body.collision_layer = 0
  body.collision_mask = 0
  body.freeze = true
  body.visible = false
  body.can_sleep = true
  var visual := MeshInstance3D.new()
  visual.mesh = mesh
  visual.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
  body.add_child(visual)
  var collider := CollisionShape3D.new()
  collider.shape = shape
  body.add_child(collider)
  add_child(body)
  body.add_collision_exception_with(vehicle)
  bodies.append(body)
  ages.append(0.0)

func clear() -> void:
 for i in bodies.size(): _retire(i)
 emission_left = 0.0

func _retire(index: int) -> void:
 var body := bodies[index]
 body.freeze = true
 body.collision_mask = 0
 body.visible = false
 body.linear_velocity = Vector3.ZERO
 body.angular_velocity = Vector3.ZERO
 ages[index] = 0.0

func _physics_process(dt: float) -> void:
 for i in bodies.size():
  if ages[i] <= 0.0: continue
  ages[i] -= dt
  if ages[i] <= 0.0: _retire(i)
 if not enabled or vehicle == null or vehicle.wheel_contacts.size() != 4 or not vehicle.is_on_floor(): return
 var motion := maxf(absf(vehicle.speed) / 12.0, vehicle.soil_slip * vehicle.throttle_input)
 if motion < 0.12: return
 emission_left -= dt
 if emission_left > 0.0: return
 emission_left = 0.13
 var forward := Vector3(cos(vehicle.heading), 0, sin(vehicle.heading))
 var side := Vector3(-forward.z, 0, forward.x)
 var motion_sign := -1.0 if vehicle.wheel_speed < -0.2 else 1.0
 for wheel in 4:
  var point := vehicle.wheel_contacts[wheel]
  var body := bodies[cursor]
  _retire(cursor)
  var outward := -1.0 if (point - vehicle.position).dot(side) < 0 else 1.0
  body.position = point + Vector3.UP * 0.20 + side * outward * 0.12
  body.collision_mask = 1
  body.visible = true
  body.freeze = false
  body.sleeping = false
  body.linear_velocity = (-forward * motion_sign * rng.randf_range(1.4, 3.0) + side * outward * rng.randf_range(0.3, 1.0) + Vector3.UP * rng.randf_range(1.8, 3.8)) * clampf(0.55 + motion * 0.7, 0.6, 1.4)
  body.angular_velocity = Vector3(rng.randf_range(-8, 8), rng.randf_range(-8, 8), rng.randf_range(-8, 8))
  ages[cursor] = 1.4
  cursor = (cursor + 1) % CAPACITY
