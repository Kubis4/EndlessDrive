extends Node
## On automatic quality: if the game does not keep up for a while, one step down, and
## it stays down (game_settings.gd remembers).
const Settings = preload("res://offroad/game_settings.gd")
const SAMPLE := 12.0
## Below this share of the frame limit it does not keep up.
const KEEP_UP := 0.72
var game: Node3D
var clock := 0.0
var frames := 0
var samples := 0

func _process(delta: float) -> void:
	if game.paused: return
	clock += delta
	frames += 1
	if clock < SAMPLE: return
	var average := frames / clock
	clock = 0.0
	frames = 0
	samples += 1
	# (The first one holds the load.)
	if samples < 2 or Settings.quality != "auto": return
	var limit := Settings.effective_rate()
	if average >= (limit if limit > 0 else 30) * KEEP_UP: return
	if not Settings.lower_auto(): return
	game._apply_render_profile(Settings.profile())
	Settings.apply_settings()
	print("QUALITY lowered to %s at %.0f FPS" % [Settings.effective_quality(), average])
