@tool
extends HBoxContainer
signal vehicle_selected(id: String)
var selected := "izh"
func _ready() -> void:
	for id: String in ["izh", "jeep"]:
		var button := get_node(id) as Button
		button.pressed.connect(func() -> void: vehicle_selected.emit(id))
	select(selected)
func select(id: String) -> void:
	selected = id
	for key: String in ["izh", "jeep"]:
		var button := get_node(key) as Button
		button.set_pressed_no_signal(id == key)
		button.modulate = Color("e9ba68") if id == key else Color("c3c7b9")
