extends CanvasLayer
## Over everything while the game gets ready: a wheel turning and a line of words, one and the
## same from the menu's last tap until the car can be driven (it lives on the window, not in a
## scene). The work behind it must leave frames free (worker threads, a little a frame), or the
## wheel stands.
const UI = preload("res://offroad/menu_ui.gd")
const TURN := 3.2
static var current: CanvasLayer

## The screen, put up if it is not there yet.
static func open(tree: SceneTree) -> CanvasLayer:
	if current == null or not is_instance_valid(current):
		current = new()
		tree.root.add_child(current)
	return current
var label: Label
var wheel: Control
var angle := 0.0

func _init(text: String = "NAČÍTAVA SA…") -> void:
	layer = 20
	process_mode = Node.PROCESS_MODE_ALWAYS
	var cover := ColorRect.new()
	cover.color = Color(0.04, 0.035, 0.025)
	cover.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(cover)
	var centre := CenterContainer.new()
	centre.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	cover.add_child(centre)
	var column := VBoxContainer.new()
	column.alignment = BoxContainer.ALIGNMENT_CENTER
	column.add_theme_constant_override("separation", roundi(22.0 * UI.k()))
	centre.add_child(column)
	wheel = Control.new()
	wheel.custom_minimum_size = Vector2.ONE * 150.0 * UI.k()
	wheel.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	wheel.draw.connect(_draw_wheel)
	column.add_child(wheel)
	label = UI.label(text, 44, UI.PAPER)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	column.add_child(label)

func close() -> void:
	current = null
	queue_free()

func say(text: String) -> void:
	if label.text != text: label.text = text

func _process(delta: float) -> void:
	angle = fmod(angle + delta * TURN, TAU)
	wheel.queue_redraw()

## A tyre with its tread and a five-spoke rim, rolling.
func _draw_wheel() -> void:
	var size := wheel.custom_minimum_size.x * 0.5
	var at := wheel.custom_minimum_size * 0.5
	wheel.draw_circle(at, size * 0.94, Color(0.11, 0.1, 0.085))
	for i in 18:
		var turn := angle + TAU * i / 18.0
		var along := Vector2.from_angle(turn)
		wheel.draw_line(at + along * size * 0.82, at + along * size, UI.PAPER, size * 0.11)
	wheel.draw_arc(at, size * 0.56, 0.0, TAU, 48, UI.BUTTON_AMBER, size * 0.09, true)
	for i in 5:
		var along := Vector2.from_angle(angle + TAU * i / 5.0)
		wheel.draw_line(at + along * size * 0.14, at + along * size * 0.54, UI.BUTTON_AMBER, size * 0.1, true)
	wheel.draw_circle(at, size * 0.17, UI.PAPER)
