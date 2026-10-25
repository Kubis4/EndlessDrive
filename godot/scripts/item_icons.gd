class_name DriveIcons
extends RefCounted
## Original, runtime SVG pictograms. No imported art or asset dependencies.
## The transparent 128 px textures are shared; callers should not mutate them.

static var _cache: Dictionary = {}

const SHAPES := {
	"fuel": '<path d="M15 53V13h24v40M12 53h30M19 18h16v13H19zM39 20h7l5 7v18c0 6-8 6-8 0V33h-4M46 20v9h5"/><path stroke="#e9ba68" d="M24 38h7m-3.5-3.5v7"/>',
	"oil": '<path d="M12 29h28v23H12zM17 29v-8h16v8M40 32l10-8 5 5-15 13M7 35h5M18 21h14"/><path fill="#e9ba68" stroke="none" d="M49 40c-8 10-7 16 0 16s8-6 0-16z"/>',
	"coolant": '<path d="M32 8C25 19 15 29 15 39a17 17 0 0034 0C49 29 39 19 32 8z"/><path stroke="#e9ba68" d="M32 29v22M22 35l20 10M22 45l20-10m-13-3 3-3 3 3m-6 16 3 3 3-3"/>',
	"scrap": '<path d="M10 40l14-7 9 8-5 13H12zM34 14l15-5 7 13-12 10-11-6zM9 16l12-6 7 10-6 9-11-2zM39 42l10-7 8 12-7 8-12-5z"/><path stroke="#e9ba68" d="M16 42l7-3m17-20 7-2m-2 27 5 5"/>',
	"engine": '<path d="M9 26h8l7-9h20v9h10v23H22l-7-8H9zM30 17V10m-7 0h14M9 29H5v15h4m45-12h5v13h-5"/><path stroke="#e9ba68" d="M36 26l-8 11h10l-7 11"/>',
	"tires": '<ellipse cx="32" cy="32" rx="20" ry="25"/><ellipse cx="32" cy="32" rx="10" ry="16"/><path stroke="#e9ba68" d="M17 16l5 4M13 28l8 1M14 41l7-3m-2 13 6-5m22-30-5 4m9 8-8 1m7 12-7-3m2 13-6-5"/>',
	"brakes": '<circle cx="30" cy="33" r="21"/><circle cx="30" cy="33" r="7"/><path stroke="#e9ba68" d="M44 14h10v26H44l-4-8V22zM30 18v3M16 32h3m10 15v-3m-7-24 2 3m-8 16 3-1m18 9-2-3"/>',
	"suspension": '<path d="M28 8h8v8h-8zM28 48h8v8h-8zM32 16v32"/><path stroke="#e9ba68" d="M23 17l18 6-18 6 18 6-18 6 18 6M22 17h20M22 47h20"/>',
	"radiator": '<rect x="12" y="16" width="40" height="36" rx="3"/><path d="M21 16v-6h10v6M18 52v5m28-5v5M8 25h4m40 18h5"/><path stroke="#e9ba68" d="M20 23v22m8-22v22m8-22v22m8-22v22"/>',
	"alternator": '<path d="M19 14h26l9 12v18l-9 8H19L9 42V26zM23 14V8h15v6M20 52v5m22-5v5"/><circle cx="31" cy="33" r="13"/><path stroke="#e9ba68" d="M32 22l-7 12h10l-6 11M49 27h5m-5 12h5"/>',
	"home": '<path d="M7 30L32 9l25 21M14 26v29h36V26M27 55V37h11v18"/><path stroke="#e9ba68" d="M20 29h7v7h-7zM40 14v-4h7v10"/>',
	"gas": '<path d="M7 21h50L48 9H16zM12 21v34m40-34v34M7 55h50M23 51V29h17v22M27 33h9v8h-9zM40 34h4v13h-4"/><path stroke="#e9ba68" d="M20 15h24M22 51h20"/>',
	"repair": '<path d="M8 28L32 10l24 18M13 25v30h38V25"/><path stroke="#e9ba68" d="M39 28a9 9 0 01-12 11L20 49l-5-5 11-10a9 9 0 0110-10l-5 6 4 4z"/>',
	"paint": '<path d="M11 12h33v13H11zM44 17h8v15H30v9M26 41h8v15h-8z"/><path stroke="#e9ba68" d="M16 17h22M12 43c-6 8-6 12 0 12s6-4 0-12z"/>',
	"workshop": '<path d="M8 54V23l24-13 24 13v31zM19 54V33h26v21M19 39h26m-26 7h26"/><path stroke="#e9ba68" d="M26 23h12M32 18v10"/>',
	"repairkit": '<rect x="9" y="22" width="46" height="32" rx="5"/><path d="M23 22v-8h18v8M9 33h46M17 29v8m30-8v8"/><path stroke="#e9ba68" d="M32 36v12m-6-6h12"/>',
	"batterycell": '<rect x="10" y="18" width="44" height="36" rx="4"/><path d="M17 18v-7h10v7m10 0v-7h10v7M16 27h10m-5-5v10m17-5h10"/><path stroke="#e9ba68" d="M33 33l-8 10h11l-7 9"/>',
	"paintcan": '<ellipse cx="32" cy="16" rx="19" ry="7"/><path d="M13 16v34c0 9 38 9 38 0V16M9 23C0 51 64 51 55 23"/><path stroke="#e9ba68" d="M25 28h14v18H25zM17 18c8 3 22 3 30 0"/>',
}


static func texture(key: String) -> Texture2D:
	var canonical := key.to_lower()
	# Dashboard and service screens share the same authored vehicle symbols.
	var asset := "res://assets/ui/%s.svg" % canonical
	if ResourceLoader.exists(asset):
		if not _cache.has(canonical): _cache[canonical] = load(asset)
		return _cache[canonical]
	if not SHAPES.has(canonical):
		canonical = "unknown"
	if _cache.has(canonical):
		return _cache[canonical] as Texture2D
	var shape: String = SHAPES.get(canonical, '<circle cx="32" cy="32" r="23"/><path d="M25 24a7 7 0 0114 0c0 7-7 7-7 14m0 7v1"/>')
	var svg := '<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64"><g fill="none" stroke="#f5ecd7" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">' + shape + '</g></svg>'
	var image := Image.new()
	var error := image.load_svg_from_string(svg, 2.0)
	if error != OK:
		push_error("DriveIcons: SVG rendering failed for %s (%s)" % [canonical, error_string(error)])
		image = Image.create(128, 128, false, Image.FORMAT_RGBA8)
	var result := ImageTexture.create_from_image(image)
	_cache[canonical] = result
	return result
