extends RefCounted
## Shared nine-slice vector surfaces: crisp borders, quiet dashboard gradients.
static var _textures: Dictionary = {}
static func style(state: String = "idle", shape: String = "") -> StyleBoxTexture:
	var key := state + shape
	if not _textures.has(key): _textures[key] = load("res://assets/ui/skins/%s.svg" % key)
	var result := StyleBoxTexture.new()
	result.texture = _textures[key]
	# Rounded steering ends and the circular hub scale as complete images.
	# Slicing a 128px cap into a shorter touch button would overlap its corners.
	if shape == "":
		for side in [SIDE_LEFT, SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM]: result.set_texture_margin(side, 22)
	for side in [SIDE_LEFT, SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM]: result.set_content_margin(side, 10)
	return result
static func polish(node: Node) -> void:
	if node is Control and node.theme != null:
		# Theme defaults also cover cards/buttons created later by loot/service.
		var shared: Theme = node.theme.duplicate()
		for type: String in ["Button", "PanelContainer", "Panel", "TabContainer", "LineEdit"]:
			for name: String in shared.get_stylebox_list(type):
				var old := shared.get_stylebox(name, type) as StyleBoxFlat
				if old == null: continue
				var kind := "active" if name in ["pressed", "tab_selected"] else ("hover" if name in ["hover", "focus"] else ("disabled" if name == "disabled" else "idle"))
				var replacement := style(kind)
				for side in [SIDE_LEFT, SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM]: replacement.set_content_margin(side, old.get_content_margin(side))
				shared.set_stylebox(name, type, replacement)
		node.theme = shared
	if node is Button:
		for state: String in ["normal", "hover", "pressed", "disabled", "focus"]:
			var old := node.get_theme_stylebox(state) as StyleBoxFlat
			if old == null: continue # Preserve radial hit geometry and authored skins.
			var kind: String = {"normal": "idle", "hover": "hover", "pressed": "active", "disabled": "disabled", "focus": "hover"}[state]
			var replacement := style(kind)
			for side in [SIDE_LEFT, SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM]: replacement.set_content_margin(side, old.get_content_margin(side))
			node.add_theme_stylebox_override(state, replacement)
	elif node is Panel or node is PanelContainer or node is TabContainer:
		var old := node.get_theme_stylebox("panel") as StyleBoxFlat
		if old != null and old.bg_color.a > 0.4:
			var replacement := style("idle")
			for side in [SIDE_LEFT, SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM]: replacement.set_content_margin(side, old.get_content_margin(side))
			node.add_theme_stylebox_override("panel", replacement)
	for child in node.get_children(): polish(child)
