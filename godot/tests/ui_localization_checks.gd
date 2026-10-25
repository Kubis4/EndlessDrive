extends SceneTree
const Locale = preload("res://scripts/localization.gd")
var failures := 0
var checks := 0

func _initialize() -> void: call_deferred("_run")
func check(ok: bool, title: String) -> void:
	checks += 1
	if not ok:
		failures += 1
		push_error(title)

func _run() -> void:
	Locale.configure(1)
	check(Locale.language_index() == 1 and TranslationServer.translate("REŠTARTOVAŤ CESTU") == "RESTART JOURNEY", "English catalog changes native source text")
	check(Locale.translate("Doplnené: 1.5 l · kvalita v aute 94 %") == "Refilled: 1.5 l · vehicle quality 94 %", "Formatted survival messages translate without changing their numbers")
	check(Locale.translate("Opotrebovanie: Motor. Skontroluj auto pri najbližšej zastávke.") == "Wear: Engine. Inspect your vehicle at the next stop.", "Formatted messages translate captured part names")
	check(Locale.translate("Navijak pripojený. H: pritiahnuť.") == "Winch attached. H: reel in.", "Winch status is localized")
	Locale.configure(0)
	check(Locale.translate("Navijak odpojený.") == "Navijak odpojený.", "Slovak source remains intact after switching back")
	var game := load("res://scenes/main.tscn").instantiate() as Node3D
	game._ephemeral = true
	root.add_child(game)
	for tick in 4: await process_frame
	check(game.hud._equipment_buttons.size() == 4, "Authored HUD exposes drivetrain and winch controls")
	check(not game.hud._assist_panel.visible and not game.hud._cruise_status.visible and not game.hud._road_info.get_parent().visible, "Driving helpers, surface descriptions and cruise percentages remain hidden")
	game.menu.show_panel("settings")
	check(game.menu._language_select != null and game.menu._language_select.item_count == 2, "Authored settings expose both supported languages")
	game.menu._language_select.item_selected.emit(1)
	await process_frame
	check(Locale.language_index() == 1 and game.menu._modal_title.text == "SETTINGS", "Language selection applies immediately through gameplay settings")
	var controls: Label = game.menu._panels.settings.get_node("Ovládanie")
	check(str(TranslationServer.translate(controls.text)).begins_with("CONTROLS"), "Multiline authored control instructions have an English translation: " + controls.text.c_escape())
	game.menu._language_select.item_selected.emit(0)
	game._menu_action("continue")
	game._action("pause")
	game.hud._dispatch("restart")
	check(game.hud._restart_confirmation.visible and game.run.distance == 0.0, "Restart opens a clear confirmation before replacing journey progress")
	game.hud._restart_confirmation.hide()
	game._action("resume")
	game.queue_free()
	for tick in 2: await process_frame
	print("UI LOCALIZATION: %d checks; %d failures" % [checks, failures])
	quit(0 if failures == 0 else 1)
