@tool
extends Node3D
## Editable layout of Lesná vrchovina (open offroad/highlands_layout.tscn):
## trails (Path3D curves), ponds, test spots (markers facing -Z), terrain
## stamps (hills and hollows) and hand-placed extras, over a preview of the
## baked terrain. "Uložiť a prepiecť" writes the layout for the game
## (offroad/highlands_layout.json), saves the extras as their own scene and
## re-bakes the world in a background Godot process; the preview then reloads.
const H = preload("res://offroad/highlands.gd")
const Surface = preload("res://desert_battlefield/terrain.gd")
const Trail = preload("res://offroad/layout/layout_trail.gd")
const Pond = preload("res://offroad/layout/layout_pond.gd")
const Stamp = preload("res://offroad/layout/layout_stamp.gd")
const EXTRAS_PATH := "res://offroad/highlands_extras.tscn"
## Preview grid step (m).
const PREVIEW_STEP := 2.0

@export_tool_button("Uložiť a prepiecť", "Bake") var bake_action = save_and_bake
@export_tool_button("Načítať rozloženie z hry", "Reload") var load_action = load_from_game
@export_tool_button("Obnoviť náhľad terénu", "Terrain") var preview_action = build_preview

func _ready() -> void:
	if Engine.is_editor_hint(): build_preview()

## Groups and nodes of the layout, filled from the game's current layout.
func load_from_game() -> void:
	H.reload()
	for group in ["Trasy", "Jazierka", "Testy", "Pečiatky"]:
		var old := get_node_or_null(group)
		if old != null:
			remove_child(old)
			old.free()
	var trails := _group("Trasy")
	for trail: Dictionary in H.trails:
		var path := Path3D.new()
		path.set_script(Trail)
		path.from_trail(trail, H.height_at)
		_own(trails, path)
	var ponds := _group("Jazierka")
	for i in H.ponds.size():
		var p: Vector4 = H.ponds[i]
		var pond := Marker3D.new()
		pond.set_script(Pond)
		pond.name = "Jazierko%d" % (i + 1)
		pond.position = Vector3(p.x, p.w, p.y)
		pond.radius = p.z
		_own(ponds, pond)
	var spots := _group("Testy")
	for spot: Array in H.spots:
		var marker := Marker3D.new()
		marker.name = spot[0]
		var at: Vector2 = spot[1]
		var facing: Vector2 = (spot[2] - at).normalized()
		marker.position = Vector3(at.x, H.height_at(at.x, at.y) + 1.0, at.y)
		marker.rotation.y = atan2(-facing.x, -facing.y)
		_own(spots, marker)
	var stamps := _group("Pečiatky")
	for i in H.stamps.size():
		var s: Vector4 = H.stamps[i]
		var stamp := Marker3D.new()
		stamp.set_script(Stamp)
		stamp.name = "Pečiatka%d" % (i + 1)
		stamp.position = Vector3(s.x, H.height_at(s.x, s.y), s.y)
		stamp.radius = s.z
		stamp.height = s.w
		_own(stamps, stamp)
	_group("Doplnky")

func _group(title: String) -> Node3D:
	var group := get_node_or_null(title) as Node3D
	if group == null:
		group = Node3D.new()
		group.name = title
		_own(self, group)
	return group

func _own(parent: Node, child: Node) -> void:
	parent.add_child(child)
	var root := get_tree().edited_scene_root if Engine.is_editor_hint() and get_tree() != null and get_tree().edited_scene_root != null else self
	child.owner = root if child != root else null

## The layout as edited here, in the shape highlands.gd reads.
func collect() -> Dictionary:
	var trails := []
	for node in _group("Trasy").get_children(): if node.has_method("to_trail"): trails.append(node.to_trail())
	var ponds := []
	for node in _group("Jazierka").get_children(): if node.has_method("to_pond"): ponds.append(node.to_pond())
	var spots := []
	for node in _group("Testy").get_children():
		if node is Node3D:
			var at := Vector2(node.position.x, node.position.z)
			var ahead: Vector3 = -node.basis.z
			spots.append([String(node.name), at, at + Vector2(ahead.x, ahead.z) * 10.0])
	var stamps := []
	for node in _group("Pečiatky").get_children(): if node.has_method("to_stamp"): stamps.append(node.to_stamp())
	return {"trails": trails, "ponds": ponds, "spots": spots, "stamps": stamps}

func save_and_bake() -> void:
	var layout := collect()
	if layout.trails.is_empty():
		push_warning("Rozloženie nemá žiadne trasy; najprv ho načítajte z hry.")
		return
	H.save_layout(layout.trails, layout.ponds, layout.spots, layout.stamps)
	_save_extras()
	print("LAYOUT uložené: %d trás, %d jazierok, %d testov, %d pečiatok; pečiem svet…" % [layout.trails.size(), layout.ponds.size(), layout.spots.size(), layout.stamps.size()])
	var output := []
	var code := OS.execute(OS.get_executable_path(), ["--headless", "--path", ProjectSettings.globalize_path("res://"), "--script", "res://offroad/build_highlands.gd"], output, true)
	print("LAYOUT bake exit=%d\n%s" % [code, "".join(output).right(600)])
	H.reload()
	build_preview()
	if Engine.is_editor_hint(): EditorInterface.get_resource_filesystem().scan()

## Hand-placed extras become their own scene, which the game adds to the world.
func _save_extras() -> void:
	var extras := _group("Doplnky")
	var copy := extras.duplicate()
	for child in copy.get_children(): _set_owner_recursive(child, copy)
	var scene := PackedScene.new()
	scene.pack(copy)
	ResourceSaver.save(scene, EXTRAS_PATH)
	copy.free()

func _set_owner_recursive(node: Node, owner_node: Node) -> void:
	node.owner = owner_node
	if node.scene_file_path.is_empty():
		for child in node.get_children(): _set_owner_recursive(child, owner_node)

## A coloured preview of the baked terrain (not saved with the scene).
func build_preview() -> void:
	var old := get_node_or_null("Náhľad terénu")
	if old != null:
		remove_child(old)
		old.free()
	if H.heights.is_empty(): return
	var side := int(2.0 * H.HALF / PREVIEW_STEP) + 1
	var stride := int(PREVIEW_STEP / H.STEP)
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var palette := {Surface.GRASS: Color(0.32, 0.45, 0.18), Surface.FOREST: Color(0.2, 0.3, 0.12), Surface.DIRT: Color(0.45, 0.33, 0.2),
		Surface.MUD: Color(0.28, 0.2, 0.12), Surface.GRAVEL: Color(0.55, 0.53, 0.5), Surface.ROCK: Color(0.45, 0.45, 0.45),
		Surface.SNOW: Color(0.92, 0.94, 0.98), Surface.ICE: Color(0.6, 0.75, 0.85), Surface.WATER: Color(0.15, 0.35, 0.45)}
	for z in side:
		for x in side:
			var i := mini(z * stride, H.N - 1) * H.N + mini(x * stride, H.N - 1)
			st.set_color(palette.get(H.ground[i] & 15, Color(0.4, 0.4, 0.3)))
			st.add_vertex(Vector3(-H.HALF + x * PREVIEW_STEP, H.heights[i], -H.HALF + z * PREVIEW_STEP))
	for z in side - 1:
		for x in side - 1:
			var a := z * side + x
			for index in [a, a + 1, a + side, a + 1, a + side + 1, a + side]: st.add_index(index)
	st.generate_normals()
	var preview := MeshInstance3D.new()
	preview.name = "Náhľad terénu"
	preview.mesh = st.commit()
	var material := StandardMaterial3D.new()
	material.vertex_color_use_as_albedo = true
	material.roughness = 0.95
	preview.material_override = material
	add_child(preview)
	move_child(preview, 0)
