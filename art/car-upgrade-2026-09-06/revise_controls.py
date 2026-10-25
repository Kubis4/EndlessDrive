from pathlib import Path
root=Path('app/src/main/java/sk/kubis/endlessdrive')
def edit(file,old,new):
    p=root/file
    s=p.read_text(encoding='utf-8')
    assert s.count(old)==1,(file,old,s.count(old))
    p.write_text(s.replace(old,new),encoding='utf-8')
edit('game/GameEngine.kt','    private var nightWarned = false', '''    private var roofLightsRequested = false
    val hasExpeditionKit: Boolean
        get() = car.parts[ComponentSlot.ROOF_RACK]?.defId == ItemCatalog.EXPEDITION_RACK.id
    val roofLightsOn: Boolean
        get() = roofLightsRequested && hasExpeditionKit

    fun toggleRoofLights() {
        roofLightsRequested = hasExpeditionKit && !roofLightsOn
        message = if (roofLightsOn) "Roof lights on" else "Roof lights off"
    }

    private var nightWarned = false''')
edit('game/GameEngine.kt','''            headlightsOn,
            headlightDrainMultiplier = if (highBeamsOn) GameConfig.HIGH_BEAM_DRAIN_MULTIPLIER else 1f,''','''            headlightsOn || roofLightsOn,
            headlightDrainMultiplier = (if (headlightsOn) {
                if (highBeamsOn) GameConfig.HIGH_BEAM_DRAIN_MULTIPLIER else 1f
            } else 0f) + (if (roofLightsOn) 0.65f else 0f),''')
edit('game/GameEngine.kt','        highBeamsOn = highBeamsOn\n','        highBeamsOn = highBeamsOn,\n        roofLightsOn = roofLightsOn\n')
edit('game/GameEngine.kt','        highBeamsOn = snap.highBeamsOn && snap.headlightsOn','        highBeamsOn = snap.highBeamsOn && snap.headlightsOn\n        roofLightsRequested = snap.roofLightsOn && hasExpeditionKit')
edit('game/save/RunSnapshot.kt','    val highBeamsOn: Boolean = false\n','    val highBeamsOn: Boolean = false,\n    val roofLightsOn: Boolean = false\n')
edit('game/save/RunSnapshot.kt','s.batteryRescues, s.scrap, s.highBeamsOn','s.batteryRescues, s.scrap, s.highBeamsOn, s.roofLightsOn')
edit('game/save/RunSnapshot.kt','            highBeamsOn = h.getOrNull(13)?.toBooleanStrictOrNull() ?: false','            highBeamsOn = h.getOrNull(13)?.toBooleanStrictOrNull() ?: false,\n            roofLightsOn = h.getOrNull(14)?.toBooleanStrictOrNull() ?: false')
edit('ui/game/GameUiState.kt','    val highBeamsOn: Boolean = false,','    val highBeamsOn: Boolean = false,\n    val hasExpeditionKit: Boolean = false,\n    val roofLightsOn: Boolean = false,')
edit('ui/game/GameViewModel.kt','            highBeamsOn = e.highBeamsOn,','            highBeamsOn = e.highBeamsOn,\n            hasExpeditionKit = e.hasExpeditionKit,\n            roofLightsOn = e.roofLightsOn,')
edit('ui/game/GameViewModel.kt','    fun toggleHeadlights() {','    fun toggleRoofLights() {\n        engine.toggleRoofLights()\n        bump()\n    }\n\n    fun toggleHeadlights() {')
edit('ui/game/GameScreen.kt','                onToggleLights = { viewModel.toggleHeadlights() },','                onToggleLights = { viewModel.toggleHeadlights() },\n                onToggleRoofLights = { viewModel.toggleRoofLights() },')
edit('ui/game/GameHud.kt','    onToggleLights: () -> Unit,','    onToggleLights: () -> Unit,\n    onToggleRoofLights: () -> Unit = {},')
edit('ui/game/GameHud.kt','''        HudActionToggleButton(
            icon = if (ui.paused)''','''        if (ui.hasExpeditionKit) {
            AutomotiveIconToggleButton(
                AutomotiveIcon.HIGH_BEAM,
                ui.roofLightsOn,
                onToggleRoofLights,
                activeColor = Color(0xFFFFCF75),
                label = if (ui.roofLightsOn) "Switch roof lights off" else "Switch roof lights on"
            )
        }
        HudActionToggleButton(
            icon = if (ui.paused)''')
# Keep auxiliary light emission independent from the standard headlight assembly.
p=root/'ui/game/CarArtist.kt'
s=p.read_text(encoding='utf-8'); a=s.index('            if (expedition) {'); b=s.index('            val tail =',a)
s=s[:a]+s[b:]; p.write_text(s,encoding='utf-8')
edit('ui/game/GameRenderer.kt','        drawRimSparkNightGlow(engine, day, pose, depth.ppm, engine.elapsed)', '''        if (engine.roofLightsOn && engine.car.hasPart(ComponentSlot.BATTERY)) {
            val power = (engine.car.batteryCharge * 8f).coerceIn(0f, 1f)
            val layout = carArtist.spriteLayoutAtBody(assets.sedan, pose.bodyX, pose.bodyY, depth.ppm)
            rotate(-Math.toDegrees(pose.bodyPitch.toDouble()).toFloat(), Offset(pose.bodyX, pose.bodyY)) {
                with(ExpeditionEquipment) { drawLight(layout, power, night) }
            }
        }
        drawRimSparkNightGlow(engine, day, pose, depth.ppm, engine.elapsed)''')
