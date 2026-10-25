extends RigidBody3D
## Pushable, lootable road cargo. Bounded impact energy, no bouncy tumbling top.
func _init() -> void:
	mass = 22
	collision_layer = 2
	collision_mask = 3
	continuous_cd = true
	linear_damp = 0.65
	angular_damp = 1.8
	var material := PhysicsMaterial.new()
	material.bounce = 0.0
	material.friction = 0.7
	physics_material_override = material

func _integrate_forces(state: PhysicsDirectBodyState3D) -> void:
	var motion := state.linear_velocity
	var horizontal := Vector2(motion.x, motion.z).limit_length(12.0)
	state.linear_velocity = Vector3(horizontal.x, clampf(motion.y, -22, 5), horizontal.y)
	state.angular_velocity = state.angular_velocity.limit_length(4.0)
