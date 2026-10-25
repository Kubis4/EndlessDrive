extends RefCounted
## Source-language keys stay readable in authored scenes. Add another JSON catalog
## and locale here to extend the game without changing saves or gameplay strings.
const LOCALES := ["sk", "en"]
static var _installed := false
static var _patterns: Array[Dictionary] = []

static func configure(index: int = 0) -> void:
	if not _installed:
		var catalog: Variant = JSON.parse_string(FileAccess.get_file_as_string("res://locales/en.json"))
		if catalog is Dictionary:
			var english := Translation.new()
			english.locale = "en"
			var slovak := Translation.new()
			slovak.locale = "sk"
			for key: String in catalog:
				english.add_message(key, str(catalog[key]))
				slovak.add_message(key, key)
				# Authored .tscn files on Windows preserve CRLF inside quoted text.
				english.add_message(key.replace("\n", "\r\n"), str(catalog[key]))
				slovak.add_message(key.replace("\n", "\r\n"), key)
				english.add_message(key.to_upper(), str(catalog[key]).to_upper())
				slovak.add_message(key.to_upper(), key.to_upper())
				# Godot auto-translates individual paragraphs in multiline labels.
				var source_lines := key.split("\n")
				var target_lines := str(catalog[key]).split("\n")
				if source_lines.size() == target_lines.size():
					for line in source_lines.size():
						if source_lines[line].is_empty(): continue
						english.add_message(source_lines[line], target_lines[line])
						slovak.add_message(source_lines[line], source_lines[line])
				_add_template(key, str(catalog[key]))
			TranslationServer.add_translation(english)
			TranslationServer.add_translation(slovak)
		_installed = true
	TranslationServer.set_locale(LOCALES[clampi(index, 0, LOCALES.size() - 1)])

static func language_index() -> int:
	return 1 if TranslationServer.get_locale().begins_with("en") else 0


## Gameplay messages can originate in save-independent RefCounted models. Match
## their formatted source templates at the display boundary, outside hot loops.
static func translate(source: String) -> String:
	if language_index() == 0: return source
	var direct := str(TranslationServer.translate(source))
	if direct != source: return direct
	for template: Dictionary in _patterns:
		var match_result: RegExMatch = template.regex.search(source)
		if match_result == null: continue
		var target: String = template.target
		var result := ""
		var cursor := 0
		var capture := 1
		for token: RegExMatch in _tokens(target):
			result += target.substr(cursor, token.get_start() - cursor)
			if token.get_string() == "%%": result += "%"
			else:
				result += _fragment(match_result.get_string(capture))
				capture += 1
			cursor = token.get_end()
		return result + target.substr(cursor)
	return _fragment(source)


static func _fragment(source: String) -> String:
	var parts := source.split(" · ")
	for index in parts.size(): parts[index] = str(TranslationServer.translate(parts[index]))
	return " · ".join(parts)


static func _tokens(source: String) -> Array[RegExMatch]:
	var regex := RegEx.new()
	regex.compile("%%|%[-+0-9.]*[sdf]")
	return regex.search_all(source)


static func _escape_regex(source: String) -> String:
	var escaped := ""
	for character: String in source:
		if character in "\\.^$|?*+()[]{}": escaped += "\\"
		escaped += character
	return escaped


static func _add_template(source: String, target: String) -> void:
	var tokens := _tokens(source)
	if tokens.is_empty(): return
	var pattern := "(?s)^"
	var cursor := 0
	for token: RegExMatch in tokens:
		pattern += _escape_regex(source.substr(cursor, token.get_start() - cursor))
		pattern += "%" if token.get_string() == "%%" else "(.+?)"
		cursor = token.get_end()
	pattern += _escape_regex(source.substr(cursor)) + "$"
	var regex := RegEx.new()
	if regex.compile(pattern) == OK: _patterns.append({"regex": regex, "target": target})
