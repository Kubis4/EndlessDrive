extends SceneTree
func _initialize() -> void: call_deferred("_run")
func frame() -> void:
 await physics_frame
 await process_frame
func trial(lab: Node, drift: float) -> float:
 lab.reset_sand()
 lab.vehicle.sand_lab_drift = drift
 for t in 30: await frame()
 var peak := 0.0
 for t in 270:
  Input.action_press("throttle", 0.7)
  if t > 130: Input.action_press("right", 0.7)
  await frame()
  if t > 150: peak = maxf(peak, absf(lab.vehicle._lateral_speed))
 Input.action_release("throttle")
 Input.action_release("right")
 return peak
func _run() -> void:
 var lab = load("res://scenes/sand_lab.tscn").instantiate()
 root.add_child(lab)
 lab.toggle_dust()
 var baseline := await trial(lab, 0.0)
 var sliding := await trial(lab, 1.0)
 print("LATERAL SPEED normal=%.3f m/s sand=%.3f m/s" % [baseline, sliding])
 var pass_test: bool = sliding > baseline * 1.6 and sliding > 0.35 and lab.vehicle.boundary_recoveries == 0
 print("%s Loose sand produces a controlled, measurable sideways slide" % ["PASS" if pass_test else "FAIL"])
 lab.queue_free()
 await process_frame
 quit(0 if pass_test else 1)

