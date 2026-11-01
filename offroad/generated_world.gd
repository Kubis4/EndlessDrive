extends "res://offroad/highlands_world.gd"
## The highlands dressing (ground, water, forest, rocks, camp, weather) over the
## seed-generated world: generated_terrain.gd answers the same queries as
## highlands.gd, so both worlds share every improvement made to the dressing.

func _init() -> void:
	H = preload("res://offroad/generated_terrain.gd")
