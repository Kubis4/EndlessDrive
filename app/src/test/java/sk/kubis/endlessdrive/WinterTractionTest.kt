package sk.kubis.endlessdrive

import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.world.LootGenerator

class WinterTractionTest {

    private fun carWith(front: String, rear: String): Car {
        val car = Car()
        car.installStarterKit(SeededRandom(5L))
        car.mount(ComponentSlot.TIRE_FRONT, ItemStack(front, ComponentCondition.NEW, 1f))
        car.mount(ComponentSlot.TIRE_REAR, ItemStack(rear, ComponentCondition.NEW, 1f))
        car.mount(
            ComponentSlot.DRIVETRAIN,
            ItemStack(ItemCatalog.DRIVE_RWD.id, ComponentCondition.NEW, 1f)
        )
        return car
    }

    /**
     * Regresia: zimná guma sa priemerovala cez obe nápravy, takže na RWD
     * s letnou vpredu bola takmer bez účinku. Rozhodovať má hnaná náprava.
     */
    @Test
    fun winterTyreOnTheDrivenAxleActuallyHelps() {
        val summer = carWith(ItemCatalog.TIRE.id, ItemCatalog.TIRE.id)
        val winterRear = carWith(ItemCatalog.TIRE.id, ItemCatalog.TIRE_WINTER.id)
        val winterFrontOnly = carWith(ItemCatalog.TIRE_WINTER.id, ItemCatalog.TIRE.id)

        assertTrue(
            "zimná guma vzadu musí na RWD výrazne pomôcť " +
                "(${summer.winterTraction} → ${winterRear.winterTraction})",
            winterRear.winterTraction > summer.winterTraction + 0.18f
        )
        assertTrue(
            "na hnanej náprave musí pomôcť viac než na voľnej",
            winterRear.winterTraction > winterFrontOnly.winterTraction + 0.15f
        )
    }

    /**
     * Regresia: v neskorších budovách sa nachádzali prakticky len snehové gumy.
     */
    @Test
    fun lateLootIsNotAllWinterTyres() {
        var winterish = 0
        var total = 0
        for (seed in 1L..250L) {
            val loot = LootGenerator.generate(
                rng = SeededRandom(seed),
                type = BuildingType.GARAGE,
                distance = 14_000f,
                style = BranchStyle.SAFE_RURAL
            )
            loot.forEach {
                total++
                if (it.defId == ItemCatalog.TIRE_WINTER.id ||
                    it.defId == ItemCatalog.SNOW_CHAINS.id
                ) winterish++
            }
        }
        val share = winterish.toFloat() / total.coerceAtLeast(1)
        assertTrue(
            "zimná výbava nesmie tvoriť väčšinu nálezov, tvorí ${(share * 100).toInt()} %",
            share < 0.30f
        )
    }
}
