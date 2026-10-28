extends CanvasLayer
## Keep this script free of gameplay preloads. The first frame must not wait
## for imported vehicles, terrain snapshots or the gameplay class graph.
const GAME := "res://scenes/main.tscn"
var _status: Label
var _bar: ProgressBar

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	var backdrop := ColorRect.new()
	backdrop.color = Color("191c19")
	backdrop.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(backdrop)
	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(center)
	var stack := VBoxContainer.new()
	stack.custom_minimum_size.x = 360
	stack.add_theme_constant_override("separation", 20)
	center.add_child(stack)
	var title := Label.new()
	title.text = "ENDLESS / DRIVE"
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_font_size_override("font_size", 34)
	title.add_theme_color_override("font_color", Color("e9ba68"))
	stack.add_child(title)
	_status = Label.new()
	_status.text = "Pripravujem výpravu…"
	_status.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_status.add_theme_color_override("font_color", Color("b9baa9"))
	stack.add_child(_status)
	_bar = ProgressBar.new()
	_bar.show_percentage = false
	_bar.custom_minimum_size.y = 5
	for entry: Array in [["background", Color("35392f")], ["fill", Color("e9ba68")]]:
		var style := StyleBoxFlat.new()
		style.bg_color = entry[1]
		style.set_corner_radius_all(2)
		_bar.add_theme_stylebox_override(entry[0], style)
	stack.add_child(_bar)
	_bar.value = 15
	# One real frame before asking the loader to parse the gameplay scene.
	await get_tree().process_frame
	await get_tree().process_frame
	# Scene/script creation is kept on the main thread. The expensive numeric
	# terrain work then runs on DriveWorld's worker, behind the real menu.
	var scene := load(GAME) as PackedScene
	if scene == null:
		_fail()
		return
	_bar.value = 100
	var game := scene.instantiate()
	get_tree().root.add_child(game)
	get_tree().current_scene = game
	# Main owns its visible loading status and blocks unsafe play actions.
	queue_free()

func _fail() -> void:
	_status.text = "Hru sa nepodarilo načítať. Skús ju spustiť znova."
	push_error("Could not load " + GAME)
	var retry := Button.new()
	retry.text = "Skúsiť znova"
	retry.pressed.connect(func() -> void: get_tree().reload_current_scene())
	_status.get_parent().add_child(retry)
	var close := Button.new()
	close.text = "Ukončiť"
	close.pressed.connect(func() -> void: get_tree().quit())
	_status.get_parent().add_child(close)
