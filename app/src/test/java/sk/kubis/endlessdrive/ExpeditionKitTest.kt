package sk.kubis.endlessdrive

import org.junit.Assert.*
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.save.RunCodec
import sk.kubis.endlessdrive.ui.game.ExpeditionEquipment

class ExpeditionKitTest {
    @Test fun roofLightsSwitchIndependentlyAndPersist() {
        val engine = GameEngine(41L,0f)
        engine.toggleRoofLights()
        assertFalse(engine.roofLightsOn)
        engine.car.mount(ComponentSlot.ROOF_RACK,ItemStack(ItemCatalog.EXPEDITION_RACK.id,ComponentCondition.USED,1f))
        engine.toggleRoofLights()
        assertTrue(engine.roofLightsOn)
        assertFalse(engine.headlightsOn)
        val restored = GameEngine.restore(RunCodec.decode(RunCodec.encode(engine.snapshot()))!!,0f)
        assertTrue(restored.roofLightsOn)
        restored.toggleRoofLights()
        assertFalse(restored.roofLightsOn)
        restored.toggleRoofLights()
        restored.car.mount(ComponentSlot.ROOF_RACK,ItemStack(ItemCatalog.ROOF_RACK.id,ComponentCondition.USED,1f))
        assertFalse(restored.roofLightsOn)
    }

    @Test fun optionalKitMountsPersistsAndCanBeReplacedByStandardRack() {
        val engine = GameEngine(41L,0f)
        assertFalse(ExpeditionEquipment.installed(engine.car))
        val kit = ItemCatalog.EXPEDITION_RACK
        assertTrue(kit.canMountTo(ComponentSlot.ROOF_RACK))
        engine.car.mount(ComponentSlot.ROOF_RACK,ItemStack(kit.id,ComponentCondition.USED,.8f))
        assertTrue(ExpeditionEquipment.installed(engine.car))
        val snapshot = RunCodec.decode(RunCodec.encode(engine.snapshot()))!!
        val restored = GameEngine.restore(snapshot,0f)
        assertTrue(ExpeditionEquipment.installed(restored.car))
        assertEquals(kit.id,restored.car.parts.getValue(ComponentSlot.ROOF_RACK).defId)
        restored.car.mount(ComponentSlot.ROOF_RACK,ItemStack(ItemCatalog.ROOF_RACK.id,ComponentCondition.USED,.8f))
        assertFalse(ExpeditionEquipment.installed(restored.car))
    }
}
