class_name DesertAudio
extends RefCounted
## Original synthesized exhaust loop; no audio copied from the Android game.


static func engine_loop() -> AudioStreamWAV:
	const RATE := 22050
	var bytes := PackedByteArray()
	bytes.resize(RATE * 2)
	var rng := RandomNumberGenerator.new()
	rng.seed = 87123
	var filtered := 0.0
	for index in RATE:
		var t := float(index) / RATE
		filtered = lerpf(filtered, rng.randf_range(-1, 1), 0.13)
		var pulse := sin(TAU * 52.0 * t) * 0.44 + sin(TAU * 104.0 * t) * 0.20
		pulse += sin(TAU * 156.0 * t) * 0.10 + filtered * 0.12
		pulse *= 0.82 + sin(TAU * 13.0 * t) * 0.18
		bytes.encode_s16(index * 2, int(clampf(pulse, -1, 1) * 19000.0))
	var stream := AudioStreamWAV.new()
	stream.format = AudioStreamWAV.FORMAT_16_BITS
	stream.mix_rate = RATE
	stream.stereo = false
	stream.loop_mode = AudioStreamWAV.LOOP_FORWARD
	stream.loop_begin = 0
	stream.loop_end = RATE
	stream.data = bytes
	return stream
