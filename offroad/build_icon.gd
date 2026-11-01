extends SceneTree
## Generates the game's icon from shapes: icon.svg for the project, and for Android the
## pictures of a launcher icon and its adaptive layers, in assets/icon/. Run with
## godot --headless --path . --script offroad/build_icon.gd
const SIZE := 432
const DIR := "res://assets/icon/"

const BACK := """<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2d4a63"/><stop offset="0.42" stop-color="#e08a45"/><stop offset="0.62" stop-color="#f6cf7d"/></linearGradient></defs>
<rect width="432" height="432" fill="url(#sky)"/>
<circle cx="216" cy="196" r="58" fill="#fff0bd"/>
<path d="M0 232 62 164l44 38 56-72 54 76 44-46 62 62 50-44 60 58v30H0Z" fill="#6f4a4c"/>
<path d="M0 252 70 206l52 30 66-34 54 40 62-30 58 30 70-24v44H0Z" fill="#44323a"/>
<rect y="246" width="432" height="186" fill="#b98a3a"/>
<path d="M0 262C90 250 150 256 204 248L0 330Z" fill="#d7ab4c"/>
<path d="M432 262C340 250 280 256 228 248L432 330Z" fill="#8f7a35"/>"""

const ROAD := """<path d="M196 246h40L346 432H86Z" fill="#2c2722"/>
<path d="M213 250h6l2 22h-10Zm-3 36h12l3 34h-18Zm-6 52h24l5 50h-34Z" fill="#f2d48a"/>"""

## The M1009 from behind, on the road.
const CAR := """<rect x="150" y="334" width="26" height="40" rx="6" fill="#141210"/><rect x="256" y="334" width="26" height="40" rx="6" fill="#141210"/>
<path d="M170 262h92l14 46v44H156v-44Z" fill="#6b7350"/>
<path d="M178 272h76l9 34H169Z" fill="#a9c4c9"/>
<rect x="156" y="346" width="120" height="14" fill="#2b2a24"/>
<rect x="160" y="314" width="20" height="24" rx="3" fill="#d9402c"/><rect x="252" y="314" width="20" height="24" rx="3" fill="#d9402c"/>
<rect x="196" y="322" width="40" height="14" rx="2" fill="#efe6cf"/>"""

const SHADOW := """<rect x="150" y="334" width="26" height="40" rx="6"/><rect x="256" y="334" width="26" height="40" rx="6"/>
<path d="M170 262h92l14 46v44H156v-44Z"/>"""

func _initialize() -> void:
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(DIR))
	_save("adaptive_background", BACK, SIZE)
	_save("adaptive_foreground", ROAD + CAR, SIZE)
	_save("adaptive_monochrome", "<g fill=\"#fff\"><path d=\"M84 246 132 186l34 30 50-62 46 62 34-30 52 60Z\"/>" + SHADOW + "</g>", SIZE)
	var whole := "<clipPath id=\"round\"><rect width=\"432\" height=\"432\" rx=\"88\"/></clipPath><g clip-path=\"url(#round)\">" + BACK + ROAD + CAR + "</g>"
	_save("main", whole, 192)
	var file := FileAccess.open("res://icon.svg", FileAccess.WRITE)
	file.store_string(_svg(whole))
	quit()

func _svg(content: String) -> String:
	return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">%s</svg>" % [SIZE, SIZE, SIZE, SIZE, content]

func _save(name: String, content: String, pixels: int) -> void:
	var image := Image.new()
	var error := image.load_svg_from_string(_svg(content), float(pixels) / SIZE)
	if error != OK: push_error("%s: %s" % [name, error_string(error)])
	image.save_png(ProjectSettings.globalize_path(DIR + name + ".png"))
	print("ICON %s %s" % [name, image.get_size()])
