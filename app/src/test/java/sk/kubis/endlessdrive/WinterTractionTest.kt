package sk.kubis.endlessdrive

import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.domain.model.TIRE_SLOTS
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

    @Test
    fun winterTyresWearFasterOnARegularRoad() {
        val dryRoad = carWith(ItemCatalog.TIRE_WINTER.id, ItemCatalog.TIRE_WINTER.id)
        val snowRoad = carWith(ItemCatalog.TIRE_WINTER.id, ItemCatalog.TIRE_WINTER.id)
        dryRoad.speed = 30f
        snowRoad.speed = 30f
        dryRoad.engineRunning = true
        snowRoad.engineRunning = true

        repeat(7_200) {
            dryRoad.tickWear(1f / 60f, bumpMul = 0.15f, brake = 0f, winterRoad = false)
            snowRoad.tickWear(1f / 60f, bumpMul = 0.15f, brake = 0f, winterRoad = true)
        }

        val dryHealth = dryRoad.parts[ComponentSlot.TIRE_FRONT]!!.health
        val snowHealth = snowRoad.parts[ComponentSlot.TIRE_FRONT]!!.health
        assertTrue(
            "zimná guma na suchej ceste sa musí opotrebovať citeľne rýchlejšie " +
                "($dryHealth oproti $snowHealth)",
            dryHealth < snowHealth - 0.005f
        )
    }

    @Test
    fun summerSportAndOffroadSnowGripStayWellBelowWinter() {
        assertTrue(
            "šport na snehu horší ako letná",
            ItemCatalog.TIRE_SPORT.snowGrip < ItemCatalog.TIRE.snowGrip
        )
        assertTrue(
            "ojazdená horšia ako štandard",
            ItemCatalog.TIRE_POOR.snowGrip < ItemCatalog.TIRE.snowGrip
        )
        assertTrue(
            "off-road kúsok nad letnou, ďaleko pod zimnou",
            ItemCatalog.TIRE_OFFROAD.snowGrip > ItemCatalog.TIRE.snowGrip &&
                ItemCatalog.TIRE_OFFROAD.snowGrip < ItemCatalog.TIRE_WINTER.snowGrip * 0.55f
        )
        assertTrue(ItemCatalog.TIRE_WINTER.snowGrip >= 1f)
        assertTrue(ItemCatalog.TIRE.snowGrip < 0.35f)
        assertTrue(ItemCatalog.TIRE_SPORT.snowGrip < 0.22f)
    }

    /**
     * Snehový kopec: letná/šport sa točí a neide hore, zimná vyjde,
     * reťaze ešte pridajú. Off-road je medzi letnou a zimnou.
     */
    @Test
    fun snowClimbPunishesSummerAndRewardsWinterGear() {
        val summer = snowClimb(ItemCatalog.TIRE.id)
        val sport = snowClimb(ItemCatalog.TIRE_SPORT.id)
        val offroad = snowClimb(ItemCatalog.TIRE_OFFROAD.id)
        val winter = snowClimb(ItemCatalog.TIRE_WINTER.id)
        val poorChains = snowClimb(ItemCatalog.TIRE_POOR.id, chains = true)
        val winterChains = snowClimb(ItemCatalog.TIRE_WINTER.id, chains = true)

        assertTrue(
            "zimná musí vyjsť snehový kopec (${winter.speed}), letná ${summer.speed}",
            winter.speed > 2.2f && winter.speed > summer.speed + 1.4f
        )
        assertTrue(
            "šport na snehu nesmie byť rýchlejší ako letná (${sport.speed} vs ${summer.speed})",
            sport.speed <= summer.speed + 0.35f
        )
        assertTrue(
            "off-road lepší ako letná, horší ako zimná " +
                "(${offroad.speed} / ${summer.speed} / ${winter.speed})",
            offroad.speed > summer.speed && offroad.speed < winter.speed - 0.6f
        )
        assertTrue(
            "letná sa točí viac ako zimná (${summer.wheelSlip} vs ${winter.wheelSlip})",
            summer.wheelSlip > winter.wheelSlip + 0.18f
        )
        assertTrue(
            "reťaze na slabej gume bijú letnú (${poorChains.speed} vs ${summer.speed})",
            poorChains.speed > summer.speed + 0.8f
        )
        assertTrue(
            "reťaze na zimnej sú najlepšie (${winterChains.speed} vs ${winter.speed})",
            winterChains.speed > winter.speed
        )
        assertTrue(
            "reťaze na snehu tlmia pretáčanie voči letnej " +
                "(${winterChains.wheelSlip} vs ${summer.wheelSlip})",
            winterChains.wheelSlip < summer.wheelSlip - 0.22f
        )
    }

    /**
     * Ľadový kopec: letná takmer stojí, zimná bez reťazí plazí,
     * zimná + reťaze vyjde a točí sa oveľa menej.
     */
    @Test
    fun winterChainsClimbIceAndSpinLessThanSummer() {
        val summer = iceClimb(ItemCatalog.TIRE.id)
        val winter = iceClimb(ItemCatalog.TIRE_WINTER.id)
        val winterChains = iceClimb(ItemCatalog.TIRE_WINTER.id, chains = true)

        assertTrue(
            "letná na ľade takmer stojí (${summer.speed})",
            summer.speed < 1.6f
        )
        assertTrue(
            "zimná bez reťazí aspoň plazenie (${winter.speed} vs letná ${summer.speed})",
            winter.speed > summer.speed
        )
        assertTrue(
            "zimná + reťaze vyjde ľadový kopec (${winterChains.speed}), letná ${summer.speed}",
            winterChains.speed > 2.2f && winterChains.speed > summer.speed + 1.4f
        )
        assertTrue(
            "reťaze tlmia pretáčanie voči zimnej bez nich " +
                "(${winterChains.wheelSlip} vs ${winter.wheelSlip})",
            winterChains.wheelSlip < winter.wheelSlip - 0.10f
        )
        assertTrue(
            "reťaze tlmia pretáčanie voči letnej " +
                "(${winterChains.wheelSlip} vs ${summer.wheelSlip})",
            winterChains.wheelSlip < summer.wheelSlip - 0.22f
        )
    }

    /**
     * Silný motor na letných nesmie vymazať chýbajúci snehový grip –
     * pretáča sa, kopec nevyjde ako zimná so skromným výkonom.
     */
    @Test
    fun highHpSummerCannotFlyASnowClimbLikeWinter() {
        val summerHot = snowClimb(
            ItemCatalog.TIRE.id,
            engineId = ItemCatalog.ENGINE_C.id
        )
        val winterModest = snowClimb(
            ItemCatalog.TIRE_WINTER.id,
            engineId = ItemCatalog.ENGINE_A.id
        )
        val summerWeak = snowClimb(
            ItemCatalog.TIRE.id,
            engineId = ItemCatalog.ENGINE_A.id
        )
        assertTrue(
            "zimná 92 hp musí na snehu biť letnú 230 hp " +
                "(${winterModest.speed} vs ${summerHot.speed})",
            winterModest.speed > summerHot.speed + 0.8f
        )
        assertTrue(
            "silný motor na letných pridá preklz, nie rýchlosť " +
                "(slip ${summerHot.wheelSlip} vs ${summerWeak.wheelSlip}, " +
                "v ${summerHot.speed} vs ${summerWeak.speed})",
            summerHot.wheelSlip >= summerWeak.wheelSlip - 0.02f &&
                summerHot.speed < winterModest.speed
        )
    }

    @Test
    fun chainsStillHurtOnDryTarmac() {
        val bare = drySprint(chains = false)
        val chained = drySprint(chains = true)
        assertTrue(
            "reťaze na suchom asfalte musia spomaliť ($chained vs $bare)",
            chained < bare * 0.96f
        )
        val car = carWith(ItemCatalog.TIRE.id, ItemCatalog.TIRE.id)
        car.mount(
            ComponentSlot.CHAINS,
            ItemStack(ItemCatalog.SNOW_CHAINS.id, ComponentCondition.NEW, 1f)
        )
        assertTrue(car.tarmacPenalty < 1f)
    }

    private fun snowClimb(
        tire: String,
        engineId: String = ItemCatalog.ENGINE_A.id,
        chains: Boolean = false,
        slope: Float = 0.16f
    ): Car = climbOn(
        tire, engineId, chains, slope,
        RoadSurface.ASPHALT, RoadPaving.SNOW.gripMul, winter = true
    )

    private fun iceClimb(
        tire: String,
        engineId: String = ItemCatalog.ENGINE_A.id,
        chains: Boolean = false,
        slope: Float = 0.16f
    ): Car = climbOn(
        tire, engineId, chains, slope,
        RoadSurface.ICE, RoadPaving.SNOW.gripMul, winter = true
    )

    private fun climbOn(
        tire: String,
        engineId: String,
        chains: Boolean,
        slope: Float,
        surface: RoadSurface,
        pavingGrip: Float,
        winter: Boolean
    ): Car {
        val car = Car()
        car.installStarterKit(SeededRandom(5L))
        TIRE_SLOTS.forEach {
            car.mount(it, ItemStack(tire, ComponentCondition.NEW, 1f))
        }
        car.mount(ComponentSlot.ENGINE, ItemStack(engineId, ComponentCondition.NEW, 1f))
        car.mount(
            ComponentSlot.DRIVETRAIN,
            ItemStack(ItemCatalog.DRIVE_RWD.id, ComponentCondition.NEW, 1f)
        )
        if (chains) {
            car.mount(
                ComponentSlot.CHAINS,
                ItemStack(ItemCatalog.SNOW_CHAINS.id, ComponentCondition.NEW, 1f)
            )
        }
        car.fuelDieselFraction =
            if (ItemCatalog.byId(engineId)?.fuelKind == FuelKind.DIESEL) 1f else 0f
        car.fuel = 25f
        car.engineRunning = true
        val wb = SedanSpec.wheelOffsetX * 2f
        car.snapToGround(0f, slope)
        repeat(420) {
            car.applyDrive(
                1f / 60f, 1f, 0f, 0f, slope * wb, 0f,
                surface, pavingGrip, winter = winter
            )
        }
        return car
    }

    private fun drySprint(chains: Boolean): Float {
        val car = carWith(ItemCatalog.TIRE.id, ItemCatalog.TIRE.id)
        if (chains) {
            car.mount(
                ComponentSlot.CHAINS,
                ItemStack(ItemCatalog.SNOW_CHAINS.id, ComponentCondition.NEW, 1f)
            )
        }
        car.fuel = 20f
        car.engineRunning = true
        car.snapToGround(0f, 0f)
        repeat(240) {
            car.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f)
        }
        return car.speed
    }
}
