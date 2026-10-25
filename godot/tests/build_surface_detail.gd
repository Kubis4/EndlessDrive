extends SceneTree
## Reproducible material data, generated from noise rather than enlarged images.
## RGBA independently stores sand/gravel/soil/grass micro-height at native 2K.
func _initialize() -> void:
	var size := 2048
	var channels: Array[PackedByteArray] = []
	for channel in 4:
		var noise := FastNoiseLite.new()
		noise.seed = 19037 + channel * 139
		noise.frequency = [0.17, 0.035, 0.055, 0.12][channel]
		noise.fractal_octaves = 4
		noise.fractal_gain = 0.54
		if channel == 1 or channel == 3:
			noise.noise_type = FastNoiseLite.TYPE_CELLULAR
			noise.cellular_return_type = FastNoiseLite.RETURN_DISTANCE
		var image := noise.get_seamless_image(size, size, false, false, 0.12, true)
		image.convert(Image.FORMAT_L8)
		channels.append(image.get_data())
	var bytes := PackedByteArray()
	bytes.resize(size * size * 4)
	for pixel in size * size:
		for channel in 4: bytes[pixel * 4 + channel] = channels[channel][pixel]
	var detail := Image.create_from_data(size, size, false, Image.FORMAT_RGBA8, bytes)
	detail.save_png("res://assets/textures/surface-microheight-2k.png")
	# Noise-modulated, soft-edged plumes replace the tiny smooth radial dots.
	var cloud := Image.create(512, 512, false, Image.FORMAT_RGBA8)
	var smoke := FastNoiseLite.new()
	smoke.seed = 48191
	smoke.frequency = 0.022
	smoke.fractal_octaves = 5
	for y in 512:
		for x in 512:
			var p := (Vector2(x, y) - Vector2(255.5, 255.5)) / 255.5
			var envelope := pow(maxf(0, 1.0 - p.length_squared()), 2.2)
			var density := clampf(0.68 + smoke.get_noise_2d(x, y) * 0.8, 0.12, 1)
			cloud.set_pixel(x, y, Color(1, 1, 1, envelope * density))
	cloud.save_png("res://assets/textures/dust-plume-512.png")
	print("Generated native 2048x2048 four-channel material height + 512x512 dust plume")
	quit()
