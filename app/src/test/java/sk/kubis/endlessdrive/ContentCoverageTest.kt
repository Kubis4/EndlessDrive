package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.WorldGenerator
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.ui.game.BackdropCatalog
import sk.kubis.endlessdrive.ui.game.BodyPart
import sk.kubis.endlessdrive.ui.game.BodyPartCatalog
import sk.kubis.endlessdrive.ui.game.WheelCatalog

/**
 * Stráži, že pridaný obsah je naozaj celý zapojený. Chýbajúci kus by hru
 * nezhodil ani by nič nenahlásil – len by sa ticho správala inak.
 */
class ContentCoverageTest {

    /**
     * Regresia čakajúca na svoju chvíľu: tabuľka pozadí sa píše ručne a
     * zabudnutý bióm by hra vykreslila bez kresby, teda inak než ostatné vetvy.
     */
    @Test
    fun everyBiomeHasABackdrop() {
        val missing = BiomeType.entries.filter { it !in BackdropCatalog.specs }
        assertTrue("biómy bez pozadia: $missing", missing.isEmpty())
    }

    /** Každý bióm má vlastnú kresbu alebo prefarbenú požičanú – nie kópiu bez rozdielu. */
    @Test
    fun borrowedBackdropsAreAlwaysRecoloured() {
        val byArt = BackdropCatalog.specs.entries.groupBy { it.value.far }
        byArt.forEach { (_, users) ->
            if (users.size < 2) return@forEach
            val tints = users.map { it.value.tint }
            assertEquals(
                "biómy ${users.map { it.key }} zdieľajú kresbu, musia sa líšiť tónovaním",
                tints.size, tints.distinct().size
            )
        }
    }

    /**
     * Preklep v unlockDistance by celú vetvu nenápadne skryl – hráč by o nej
     * nikdy nevedel a nič by to nenahlásilo.
     */
    @Test
    fun everyBranchStyleBecomesOfferable() {
        val seen = mutableSetOf<BranchStyle>()
        val terrain = TerrainProfile(5L)
        var distance = 0f
        while (distance <= 14000f) {
            for (seed in 1L..25L) {
                val segment = WorldGenerator.createSegment(
                    segmentSeed = seed,
                    style = BranchStyle.SAFE_RURAL,
                    worldOrigin = 0f,
                    tripDistance = distance,
                    terrain = terrain,
                    isTutorial = false
                )
                segment.choices.forEach { seen += it.plan.style }
            }
            distance += 500f
        }
        val never = BranchStyle.entries.filterNot { it in seen }
        assertTrue("vetvy, ktoré sa nikdy neponúknu: $never", never.isEmpty())
    }
}

/** Karoséria: čo je namontované, to je na aute vidieť – a naopak. */
class BodyPartTest {

    @Test
    fun everyBodySlotHasArtAndEveryPartHasASlot() {
        BodyPart.entries.forEach { part ->
            assertTrue("$part nemá kresbu", part in BodyPartCatalog.specs)
            assertTrue(
                "$part sa nekreslí – chýba v poradí",
                part in BodyPartCatalog.order
            )
            assertTrue(
                "$part nesedí na svojom slote",
                part in BodyPartCatalog.partsOf(BodyPartCatalog.slotOf(part))
            )
        }
    }

    /**
     * Kotva je ľavý horný roh dielu. Malý presah mimo základu je v poriadku –
     * veko kufra aj nárazníky karosériu zámerne prečnievajú –, takže sa stráži
     * len to, že diel neodletel niekam úplne mimo auta.
     */
    @Test
    fun anchorsStayNearTheBody() {
        BodyPartCatalog.specs.forEach { (part, spec) ->
            assertTrue("$part je vodorovne mimo auta (${spec.fx})", spec.fx in -0.15f..0.98f)
            assertTrue("$part je zvisle mimo auta (${spec.fy})", spec.fy in -0.15f..0.98f)
        }
    }

    /**
     * Regresia na nové pravidlo: svetlo je diel ako každý iný. Bez neho sa
     * nedá rozsvietiť, nech je batéria akokoľvek nabitá.
     */
    @Test
    fun headlightsNeedTheHeadlightPart() {
        val engine = GameEngine(12L, 0f)
        engine.car.parts.remove(ComponentSlot.HEADLIGHT)
        engine.car.batteryCharge = 1f
        assertFalse("bez svetlometu sa nesmie dať svietiť", engine.toggleHeadlights())
        assertFalse(engine.headlightsOn)

        engine.car.mount(ComponentSlot.HEADLIGHT, ItemStack(ItemCatalog.HEADLIGHT.id))
        assertTrue("so svetlometom už áno", engine.toggleHeadlights())
        assertTrue(engine.headlightsOn)

        // Zloženie svetlometu musí svetlá aj zhasnúť.
        engine.inventory.clear()
        assertTrue(engine.unmountSlot(ComponentSlot.HEADLIGHT))
        assertFalse("bez svetlometu nemá čo svietiť", engine.headlightsOn)
    }

    /** Jazda začína holou karosériou – plechy sa musia nájsť. */
    @Test
    fun theCarStartsAsABareShell() {
        for (seed in 1L..40L) {
            val car = Car()
            car.installStarterKit(SeededRandom(seed))
            BodyPart.entries.forEach { part ->
                val slot = BodyPartCatalog.slotOf(part)
                assertFalse(
                    "seed $seed: auto začína s dielom ${slot.displayName}",
                    car.hasPart(slot)
                )
            }
            // Na kolesách ale stáť musí, inak sa nepohne.
            assertTrue(car.hasPart(ComponentSlot.TIRE_FRONT))
            assertTrue(car.hasPart(ComponentSlot.TIRE_REAR))
        }
    }
}

/** Ladiace prepínače z nastavení – menia výbavu, nie jazdu. */
class DebugOptionsTest {

    @Test
    fun fullBodyMountsEveryPanelAndDefaultLeavesItBare() {
        val bare = Car().apply { installStarterKit(SeededRandom(4L)) }
        val full = Car().apply {
            installStarterKit(SeededRandom(4L), DebugOptions(fullBody = true))
        }
        BodyPart.entries.forEach { part ->
            val slot = BodyPartCatalog.slotOf(part)
            assertFalse("bez prepínača musí ${slot.displayName} chýbať", bare.hasPart(slot))
            assertTrue("s prepínačom musí ${slot.displayName} byť", full.hasPart(slot))
        }
        // Mechanika sa prepínačom meniť nesmie – ladí sa vzhľad, nie jazda.
        assertEquals(
            bare.parts[ComponentSlot.ENGINE]?.defId,
            full.parts[ComponentSlot.ENGINE]?.defId
        )
    }
}

/** Prvé hľadanie musí dať hráčovi čo namontovať, nielen čo opraviť. */
class StarterShedTest {

    @Test
    fun theShedAlwaysHasSeats() {
        for (seed in 1L..40L) {
            val engine = GameEngine(seed, 0f)
            val shed = engine.segment.buildings.first()
            assertTrue("seed $seed: kôlňa je až za autom", shed.localX < 10f)
            assertTrue(
                "seed $seed: v kôlni chýba predná sedačka",
                shed.loot.any { it.defId == ItemCatalog.SEAT_FRONT.id }
            )
        }
    }
}

/** Ostatné prepínače: obsadené sloty, najlepšie diely, plné kvapaliny. */
class DebugOptionsExtraTest {

    @Test
    fun allComponentsFillsEverySlotButKeepsWhatIsAlreadyThere() {
        val car = Car().apply { installStarterKit(SeededRandom(9L), DebugOptions(allComponents = true)) }
        // Reťaze sú výnimka – na suchu by priľnavosť zhoršili.
        ComponentSlot.entries
            .filter { it != ComponentSlot.CHAINS }
            .forEach { assertTrue("$it ostal prázdny", car.hasPart(it)) }
        assertFalse("reťaze sa montovať nemajú", car.hasPart(ComponentSlot.CHAINS))
    }

    @Test
    fun fullUpgradesPicksTheBestPartInEverySlot() {
        val car = Car().apply { installStarterKit(SeededRandom(9L), DebugOptions(fullUpgrades = true)) }
        assertEquals(ItemCatalog.ENGINE_C.id, car.parts[ComponentSlot.ENGINE]?.defId)
        assertEquals(ItemCatalog.FUEL_TANK_LONG.id, car.parts[ComponentSlot.FUEL_TANK]?.defId)
        assertEquals(1f, car.parts[ComponentSlot.ENGINE]?.health ?: 0f, 0.001f)
    }

    @Test
    fun fullFluidsFillsTheTanksClean() {
        val car = Car().apply { installStarterKit(SeededRandom(9L), DebugOptions(fullFluids = true)) }
        assertEquals(car.fuelCapacity, car.fuel, 0.01f)
        assertEquals(1f, car.fuelPurity, 0.001f)
        assertEquals(1f, car.batteryCharge, 0.001f)
    }

    /** Vypnuté prepínače nesmú zmeniť vôbec nič. */
    @Test
    fun offChangesNothing() {
        val a = Car().apply { installStarterKit(SeededRandom(11L)) }
        val b = Car().apply { installStarterKit(SeededRandom(11L), DebugOptions.OFF) }
        assertEquals(a.parts.keys, b.parts.keys)
        assertEquals(a.fuel, b.fuel, 0.001f)
    }
}

/** Každá guma z katalógu má svoju kresbu kolesa. */
class WheelArtTest {

    @Test
    fun everyTyreResolvesToAWheelDrawing() {
        val tyres = ItemCatalog.ALL.filter { it.axleTire }
        assertTrue("v katalógu musia byť gumy", tyres.size >= 4)
        tyres.forEach { tyre ->
            // resFor nikdy nevracia 0 – neznáma guma dostane štandardné koleso.
            assertTrue("${tyre.id} nemá kresbu", WheelCatalog.resFor(tyre.id) != 0)
        }
    }

    /** Zodraté, štandardné, športové a terénne sa musia líšiť na pohľad. */
    @Test
    fun theFourTiersLookDifferent() {
        val ids = listOf("tire_poor", "tire_std", "tire_sport", "tire_offroad")
        val art = ids.map { WheelCatalog.resFor(it) }
        assertEquals("štyri stupne musia mať štyri rôzne kresby", 4, art.distinct().size)
    }
}
