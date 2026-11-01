extends RefCounted
## What the player chose (quality, frame rate), kept in user://settings.cfg, and what
## "automatic" means on this device: judged from its graphics chip and memory, and
## lowered at run time if it does not keep up (quality_watch.gd).
const FILE := "user://settings.cfg"
const QUALITIES := ["auto", "low", "medium", "high"]
const QUALITY_NAMES := {"auto": "Auto", "low": "Nízka", "medium": "Stredná", "high": "Vysoká"}
const RATES := [0, 30, 60]
## The battlefield's render profile of each quality.
const PROFILE := {"low": "low", "medium": "notebook", "high": "reference"}
const ORDER := ["low", "medium", "high"]

static var quality := "auto"
## Frames a second: 0 automatic.
static var rate := 0
## Sound (0..1).
static var volume := 1.0
## Highest automatic quality this device has kept up with ("": not yet limited).
static var auto_cap := ""
static var loaded := false

## A phone or tablet, or a desktop run pretending to be one (--mobile).
static func is_mobile() -> bool:
	return OS.has_feature("mobile") or "--mobile" in OS.get_cmdline_user_args()

static func load_settings() -> void:
	if loaded: return
	loaded = true
	var file := ConfigFile.new()
	if file.load(FILE) != OK: return
	quality = str(file.get_value("graphics", "quality", "auto"))
	if quality not in QUALITIES: quality = "auto"
	rate = int(file.get_value("graphics", "fps", 0))
	if rate not in RATES: rate = 0
	auto_cap = str(file.get_value("graphics", "auto_cap_2", ""))
	volume = clampf(float(file.get_value("sound", "volume", 1.0)), 0.0, 1.0)

static func save_settings() -> void:
	var file := ConfigFile.new()
	file.set_value("graphics", "quality", quality)
	file.set_value("graphics", "fps", rate)
	file.set_value("graphics", "auto_cap_2", auto_cap)
	file.set_value("sound", "volume", volume)
	file.save(FILE)

## Quality for a graphics chip, memory (GB, 0: unknown) and kind of device.
static func detect_from(adapter: String, memory_gb: float, mobile: bool) -> String:
	var name := adapter.to_lower()
	if not mobile:
		return "medium" if RenderingServer.get_video_adapter_type() == RenderingDevice.DEVICE_TYPE_INTEGRATED_GPU else "high"
	var found := "low"
	var number := _number_after(name, "adreno")
	if number > 0:
		if number >= 730: found = "high"
		elif number >= 640: found = "medium"
	elif "immortalis" in name: found = "high"
	elif "xclipse" in name: found = "medium"
	else:
		number = _number_after(name, "mali-g")
		if number >= 710: found = "high"
		elif number >= 610: found = "medium"
		# (A chip this table does not know: memory is all there is to go by.)
		elif number == 0 and memory_gb >= 8.0: found = "medium"
	if memory_gb > 0.0 and memory_gb < 3.5: found = "low"
	elif memory_gb > 0.0 and memory_gb < 5.5 and found == "high": found = "medium"
	return found

static func _number_after(text: String, word: String) -> int:
	var at := text.find(word)
	if at < 0: return 0
	var digits := ""
	for character in text.substr(at + word.length()).strip_edges():
		if character in "0123456789": digits += character
		elif not digits.is_empty(): break
	return int(digits)

static func detected() -> String:
	var memory := float(OS.get_memory_info().get("physical", 0)) / 1073741824.0
	return detect_from(RenderingServer.get_video_adapter_name(), memory, is_mobile())

static func effective_quality() -> String:
	if quality != "auto": return quality
	var found := detected()
	if auto_cap in ORDER and ORDER.find(auto_cap) < ORDER.find(found): return auto_cap
	return found

## Frame limit in effect (0: none). Automatic: weak phones hold a steady 30, the rest
## 60; a desktop is not limited.
static func effective_rate() -> int:
	if rate != 0: return rate
	if not is_mobile(): return 0
	return 30 if effective_quality() == "low" else 60

static func profile() -> String:
	return PROFILE[effective_quality()]

static func apply_settings() -> void:
	Engine.max_fps = effective_rate()
	AudioServer.set_bus_volume_db(0, linear_to_db(maxf(volume, 0.0001)))
	AudioServer.set_bus_mute(0, volume <= 0.0)

## One step down from what automatic gives now; false if it is already the lowest.
static func lower_auto() -> bool:
	var at := ORDER.find(effective_quality())
	if at <= 0: return false
	auto_cap = ORDER[at - 1]
	save_settings()
	return true

## A line for the settings page: what automatic has chosen.
static func auto_text() -> String:
	var limit := effective_rate()
	var limit_text := "bez limitu" if limit == 0 else "%d FPS" % limit
	return "%s · %s · %s" % [QUALITY_NAMES[effective_quality()], limit_text, RenderingServer.get_video_adapter_name()]
