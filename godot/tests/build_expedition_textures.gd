extends SceneTree
## Packs the original generated PNG into independent, mipmapped texture layers.
## Odd-sized image-generation output is cropped per cell, never upscaled.
const SOURCE := "res://assets/textures/expedition-materials-v2.png"
const OUTPUT := "res://assets/textures/expedition-materials-v2.res"
func _initialize() -> void:
	# Dummy/headless rendering cannot read back Texture2DArray image data
	# during ResourceSaver serialization. Never silently save an empty atlas.
	if DisplayServer.get_name() == "headless":
		printerr("Build expedition textures with the real renderer, not --headless")
		quit(2)
		return
	var image := Image.load_from_file(SOURCE)
	if image == null:
		push_error("Missing expedition source atlas")
		quit(1)
		return
	image.convert(Image.FORMAT_RGBA8)
	var size := mini(image.get_width() / 4, image.get_height() / 2)
	var layers: Array[Image] = []
	var means: Array[Color] = []
	for index in 8:
		var x := roundi((index % 4) * image.get_width() / 4.0)
		var y := roundi((index / 4) * image.get_height() / 2.0)
		var tile := image.get_region(Rect2i(x, y, size, size))
		var average := Color(0, 0, 0, 0)
		var count := 0
		for sy in range(2, size, 8):
			for sx in range(2, size, 8):
				average += tile.get_pixel(sx, sy)
				count += 1
		means.append(average / count)
		tile.generate_mipmaps()
		layers.append(tile)
	var atlas := Texture2DArray.new()
	var error := atlas.create_from_images(layers)
	if error == OK:
		atlas.resource_name = "Expedition_Natural_Materials_v2"
		atlas.set_meta("layer_means", means)
		atlas.set_meta("source_png", SOURCE)
		atlas.set_meta("layer_names", ["sandstone", "granite", "bark", "foliage", "timber", "masonry", "painted_metal", "snow"])
		error = ResourceSaver.save(atlas, OUTPUT, ResourceSaver.FLAG_COMPRESS)
	if error == OK:
		var saved := ResourceLoader.load(OUTPUT, "", ResourceLoader.CACHE_MODE_IGNORE) as Texture2DArray
		if saved == null or saved.get_layers() != 8 or saved.get_width() != size: error = ERR_FILE_CORRUPT
	if error != OK: push_error("Cannot pack expedition textures: " + error_string(error))
	print("EXPEDITION TEXTURES: 8 independent %dx%d layers with mipmaps; %s" % [size, size, error_string(error)])
	quit(0 if error == OK else 1)
