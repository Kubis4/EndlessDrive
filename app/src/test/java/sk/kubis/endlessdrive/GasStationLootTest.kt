package sk.kubis.endlessdrive

import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.game.world.LootGenerator

class GasStationLootTest {

    /**
     * Regresia: kontrola zásoby sa pýtala na „hocijakú kvapalinu“, takže stanica
     * s olejom a chladiacou prešla ako zásobená a hráč na benzínke nenašiel
     * benzín. Zastávka bez paliva je najhorší možný záver cesty na pumpu.
     */
    @Test
    fun everyGasStationCarriesFuel() {
        for (seed in 1L..400L) {
            val rng = SeededRandom(seed)
            val loot = LootGenerator.generate(
                rng = rng,
                type = BuildingType.GAS_STATION,
                distance = seed * 37f,
                style = BranchStyle.SAFE_RURAL
            )
            assertTrue(
                "benzínka (seed $seed) musí mať palivo, má: " +
                    loot.joinToString { it.def.name },
                loot.any { it.def.fluid == FluidType.FUEL }
            )
        }
    }
}
