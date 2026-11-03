extends SceneTree
## The icon's SVG layers (tools/make_icon.py) as the PNGs the Android export uses:
## godot --headless --script tools/render_icon.gd -- <folder with the SVGs> <folder for the PNGs>
func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	var from: String = args[0]
	var to: String = args[1]
	for layer: Array in [["adaptive_background", 1.0], ["adaptive_foreground", 1.0], ["adaptive_monochrome", 1.0], ["icon", 192.0 / 432.0, "main"]]:
		var image := Image.new()
		var error := image.load_svg_from_string(FileAccess.get_file_as_string(from.path_join(layer[0] + ".svg")), layer[1])
		if error != OK:
			printerr("cannot draw ", layer[0], ": ", error)
			quit(1)
			return
		image.save_png(to.path_join((layer[2] if layer.size() > 2 else layer[0]) + ".png"))
	print("icon drawn into ", to)
	quit()
