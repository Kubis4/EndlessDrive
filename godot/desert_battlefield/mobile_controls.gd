extends CanvasLayer
## Multitouch pedals and steering; the same actions feed the vehicle integrator.
var game: Node3D
var pads: Array[TouchScreenButton] = []
## About 1.5× the previous controls, sized for thumbs on a phone.
const PAD_SIZE := Vector2(176, 150)
const BUTTON_SIZE := Vector2(260, 78)

func _ready() -> void:
	game = get_parent()
	layer = 3
	for action in ["battlefield_left", "battlefield_right", "battlefield_throttle", "battlefield_reverse", "battlefield_handbrake"]:
		if not InputMap.has_action(action): InputMap.add_action(action)
	for item in [["◀", "left"], ["▶", "right"], ["BRZDA / R", "reverse"], ["PLYN", "throttle"], ["RUČNÁ", "handbrake"]]:
		var pad := TouchScreenButton.new()
		pad.action = "battlefield_" + item[1]
		pad.passby_press = true
		var shape := RectangleShape2D.new()
		shape.size = PAD_SIZE
		pad.shape = shape
		add_child(pad)
		pads.append(pad)
		var panel := Panel.new()
		panel.position = -shape.size * 0.5
		panel.size = shape.size
		panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var style := StyleBoxFlat.new()
		style.bg_color = Color(0.09, 0.08, 0.06, 0.72)
		style.set_corner_radius_all(20)
		style.border_color = Color(0.95, 0.89, 0.78, 0.35)
		style.set_border_width_all(2)
		panel.add_theme_stylebox_override("panel", style)
		pad.add_child(panel)
		var label := Label.new()
		label.text = item[0]
		label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		label.add_theme_font_size_override("font_size", 32)
		label.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		panel.add_child(label)
	for item in [["JAZDA / KAMERA", KEY_SPACE], ["POHĽAD", KEY_C], ["TEST", KEY_T], ["RESET AUTA", KEY_R], ["AUTO", KEY_V], ["PAUZA", KEY_P]]:
		var button := Button.new()
		button.text = item[0]
		button.custom_minimum_size = BUTTON_SIZE
		button.size = BUTTON_SIZE
		button.position = Vector2(0, 0)
		button.add_theme_font_size_override("font_size", 28)
		button.pressed.connect(func():
			var key := InputEventKey.new()
			key.keycode = item[1]
			key.pressed = true
			game._unhandled_key_input(key))
		add_child(button)
	get_viewport().size_changed.connect(_layout)
	_layout()

func _layout() -> void:
	var size := get_viewport().get_visible_rect().size
	# Pads are centred on their positions; keep a 24 px margin from the edges.
	var half := PAD_SIZE * 0.5 + Vector2(24, 24)
	var gap := PAD_SIZE.x + 22.0
	var low := size.y - half.y
	var locations := [Vector2(half.x, low), Vector2(half.x + gap, low), Vector2(size.x - half.x - gap, low),
		Vector2(size.x - half.x, low), Vector2(size.x - half.x, low - PAD_SIZE.y - 22.0)]
	for i in pads.size(): pads[i].position = locations[i]
	var index := 0
	for child in get_children():
		if child is Button:
			# Two columns of three in the top-right corner, clear of the pedals.
			var column := index / 3
			child.position = Vector2(size.x - (BUTTON_SIZE.x + 12) * (column + 1) - 12, 24 + (index % 3) * (BUTTON_SIZE.y + 12))
			index += 1

func _exit_tree() -> void:
	for pad in pads: Input.action_release(pad.action)
