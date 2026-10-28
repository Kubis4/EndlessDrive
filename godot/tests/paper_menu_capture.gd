extends SceneTree
## Native menu visual QA, no player data or driving world required.
const Locale = preload("res://scripts/localization.gd")
var failures := 0

func _initialize() -> void: call_deferred("_run")

func _check(ok: bool, message: String) -> void:
	if not ok:
		failures += 1
		push_error(message)

func _save(name: String) -> void:
	for frame in 4: await process_frame
	await RenderingServer.frame_post_draw
	var path := ProjectSettings.globalize_path("res://../build/paper-menu/" + name + ".png")
	DirAccess.make_dir_recursive_absolute(path.get_base_dir())
	_check(root.get_texture().get_image().save_png(path) == OK, "Capture saved: " + name)

func _run() -> void:
	Locale.configure(0)
	var menu := (load("res://scenes/ui/main_menu.tscn") as PackedScene).instantiate() as DriveMenu
	root.add_child(menu)
	for dimensions: Vector2i in [Vector2i(1280, 720), Vector2i(1920, 1080), Vector2i(1920, 900), Vector2i(1024, 768)]:
		root.size = dimensions
		menu.show_menu(true, 12400)
		for frame in 4: await process_frame
		var previous := Rect2()
		for button: Button in menu._home.find_children("*", "Button", true, false):
			if not button.is_visible_in_tree(): continue
			var rect := button.get_global_rect()
			_check(menu._root.get_global_rect().encloses(rect), "Action fits viewport: " + button.text)
			_check(not rect.intersects(previous), "Actions do not overlap: " + button.text)
			_check(rect.size.y >= 44, "Action retains touch target: " + button.text)
			previous = rect
		await _save("saved-%dx%d" % [dimensions.x, dimensions.y])
	root.size = Vector2i(1280, 720)
	menu.show_menu(false, 0)
	_check(menu._continue_button.disabled and not menu._new_button.disabled, "Fresh run availability")
	await _save("new-journey")
	menu.set_loading(0.42)
	_check(menu._new_button.disabled and menu._card_status.visible, "Loading prevents starting a run")
	await _save("loading")
	menu.set_loading(1.0)
	_check(not menu._new_button.disabled and menu._continue_button.disabled, "Loading restores fresh run availability")
	menu.show_menu(true, 12400)
	menu._continue_button.grab_focus()
	await _save("keyboard-focus")
	var actions: Array[String] = []
	menu.requested.connect(func(action: String) -> void: actions.append(action))
	var accept := InputEventAction.new()
	accept.action = "ui_accept"
	accept.pressed = true
	root.push_input(accept)
	accept = InputEventAction.new()
	accept.action = "ui_accept"
	root.push_input(accept)
	await process_frame
	_check(actions == ["continue"], "Keyboard activates the focused native action")
	for kind: String in ["settings", "howto", "new", "quit", "upgrades"]:
		menu.show_panel(kind)
		await _save(kind)
	Locale.configure(1)
	menu.show_menu(true, 12400)
	await _save("english")
	menu.hide_menu()
	_check(not menu.visible and not menu._root.visible, "Menu artwork disappears with controls on resume")
	menu.queue_free()
	await process_frame
	print("PAPER MENU: viewport, loading, focus, modal and visibility checks; failures=", failures)
	quit(0 if failures == 0 else 1)
