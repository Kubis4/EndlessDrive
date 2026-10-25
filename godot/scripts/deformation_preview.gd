@tool
extends Node3D
## Start world-space particles only after the saved preview enters the tree.
func _ready() -> void:
	_start_particles.call_deferred()
func _start_particles() -> void:
	var dust := get_node_or_null("DustSpritePreview") as CPUParticles3D
	if dust != null: dust.emitting = true
