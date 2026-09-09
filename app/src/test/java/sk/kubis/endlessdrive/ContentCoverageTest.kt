package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.WorldGenerator
import sk.kubis.endlessdrive.game.world.LootGenerator
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.DebugOptions
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.GameEngine
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.ui.game.BackdropCatalog
import sk.kubis.endlessdrive.ui.game.BodyPart
import sk.kubis.endlessdrive.ui.game.BodyPartCatalog
import sk.kubis.endlessdrive.ui.game.CarSection
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

    @Test
    fun backdropVariantsAreStableAndIncludeEveryNewScene() {
        val expectedFarLayers = mapOf(
            BiomeType.RURAL to listOf(R.drawable.bg_rural_far),
            BiomeType.FOREST_ALIVE to listOf(R.drawable.bg_autumn_far),
            BiomeType.WASTELAND to listOf(R.drawable.bg_quarry_far),
            BiomeType.FOREST to listOf(R.drawable.bg_marsh_far),
            BiomeType.ALPINE to listOf(
                R.drawable.bg_winter_alpine_far,
                R.drawable.bg_winter_pines_far
            )
        )
        expectedFarLayers.forEach { (biome, expected) ->
            assertEquals(expected, BackdropCatalog.variants.getValue(biome).map { it.far })
        }
        BiomeType.entries.forEach { biome ->
            val seen = mutableSetOf<Int>()
            repeat(20) { seed ->
                val first = BackdropCatalog.variantIndex(biome, seed.toLong())
                val again = BackdropCatalog.variantIndex(biome, seed.toLong())
                assertEquals(first, again)
                assertTrue(first in BackdropCatalog.variants.getValue(biome).indices)
                seen += first
            }
            assertEquals(BackdropCatalog.variants.getValue(biome).indices.toSet(), seen)
        }
    }

    /**
     * Preklep v unlockDistance by celú vetvu nenápadne skryl – hráč by o nej
     * nikdy nevedel a nič by to nenahlásilo.
     */
    @Test
    fun everyRegionHasExactlyOneValidContinuation() {
        val terrain = TerrainProfile(5L)
        BranchStyle.entries.forEachIndexed { index, style ->
            val segment = WorldGenerator.createSegment(
                segmentSeed = index + 1L,
                style = style,
                worldOrigin = 0f,
                tripDistance = 15000f,
                terrain = terrain,
                isTutorial = false
            )
            assertEquals("$style nemá jedno pokračovanie", 1, segment.choices.size)
            val continuation = segment.choices.single().plan
            assertTrue(continuation.length >= GameConfig.SEGMENT_LENGTH_MIN)
            assertTrue(continuation.features.isNotEmpty())
            assertTrue("región sa nemá opakovať bez zmeny", continuation.style != style)
            assertFalse(
                "$style pokračuje do ${continuation.style} s tou istou krajinou",
                continuation.style.biome.sharesSceneryWith(style.biome)
            )
        }
    }

    @Test
    fun countrysideSegmentsAlwaysHaveBuildings() {
        val terrain = TerrainProfile(11L)
        for (seed in 1L..40L) {
            val segment = WorldGenerator.createSegment(
                segmentSeed = seed,
                style = BranchStyle.SAFE_RURAL,
                worldOrigin = seed * 700f,
                tripDistance = seed * 180f,
                terrain = terrain,
                isTutorial = false
            )
            val houses = segment.buildings.count { it.type != BuildingType.WRECK }
            assertTrue("vidiek (seed $seed) musí mať budovy, má $houses", houses >= 2)
        }
    }

    @Test
    fun forestSegmentsHaveBuildingsNotJustWrecks() {
        val terrain = TerrainProfile(23L)
        listOf(BranchStyle.FOREST, BranchStyle.FOREST_ALIVE).forEach { style ->
            for (seed in 1L..40L) {
                val segment = WorldGenerator.createSegment(
                    segmentSeed = seed,
                    style = style,
                    worldOrigin = seed * 700f,
                    tripDistance = seed * 180f,
                    terrain = terrain,
                    isTutorial = false
                )
                val houses = segment.buildings.count { it.type != BuildingType.WRECK }
                assertTrue(
                    "$style (seed $seed) musí mať budovy, má $houses",
                    houses >= 2
                )
            }
        }
    }

    @Test
    fun workshopsSometimesHoldScrapAndWrecksAreRare() {
        var shopScrap = 0
        for (seed in 1L..220L) {
            val loot = LootGenerator.generate(
                SeededRandom(seed),
                BuildingType.AUTO_SHOP,
                2800f,
                BranchStyle.INDUSTRIAL
            )
            if (loot.any { it.defId == ItemCatalog.SCRAP_PILE.id }) shopScrap++
        }
        assertTrue("servis má občas dať scrap, padlo $shopScrap", shopScrap in 8..110)

        val terrain = TerrainProfile(19L)
        var wrecks = 0
        var segments = 0
        for (seed in 1L..50L) {
            val segment = WorldGenerator.createSegment(
                seed, BranchStyle.SAFE_RURAL, seed * 800f, seed * 150f, terrain, false
            )
            segments++
            wrecks += segment.buildings.count {
                it.type == BuildingType.WRECK
            }
        }
        assertTrue("vraky pri ceste majú byť zriedkavé ($wrecks / $segments)", wrecks in 1..40)
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

    @Test
    fun frontAndRearDoorsMountIndependentlyAndWindowsAreNotLoot() {
        val car = Car().apply { installStarterKit(SeededRandom(4L)) }
        car.mount(ComponentSlot.DOOR_FRONT, ItemStack(ItemCatalog.DOOR_FRONT.id))
        assertTrue(car.hasPart(ComponentSlot.DOOR_FRONT))
        assertFalse(car.hasPart(ComponentSlot.DOOR_REAR))
        assertEquals(listOf(BodyPart.DOOR_FRONT), BodyPartCatalog.partsOf(ComponentSlot.DOOR_FRONT))
        assertTrue("okná už nesmú byť samostatný predmet", ItemCatalog.ALL.none { it.id == "windows" })
    }

    @Test
    fun headlightsAndTaillightsStayUnpainted() {
        assertFalse(ComponentSlot.HEADLIGHT.takesBodyPaint)
        assertFalse(ComponentSlot.TAILLIGHT.takesBodyPaint)
        assertFalse(BodyPartCatalog.takesBodyPaint(BodyPart.HEADLIGHT))
        assertFalse(BodyPartCatalog.takesBodyPaint(BodyPart.TAILLIGHT))
        assertFalse(BodyPartCatalog.appliesPaintTint(BodyPart.HEADLIGHT))
        assertFalse(BodyPartCatalog.appliesPaintTint(BodyPart.TAILLIGHT))
        assertTrue(BodyPartCatalog.takesBodyPaint(BodyPart.DOOR_FRONT))
        assertTrue(BodyPartCatalog.takesBodyPaint(BodyPart.HOOD))
        assertTrue(BodyPartCatalog.takesBodyPaint(BodyPart.BUMPER_FRONT))
        assertTrue(BodyPartCatalog.appliesPaintTint(BodyPart.ROOF_RACK))
        assertFalse(BodyPartCatalog.takesBodyPaint(BodyPart.ROOF_RACK))

        val car = Car().apply { installStarterKit(SeededRandom(4L)) }
        car.mount(ComponentSlot.HEADLIGHT, ItemStack(ItemCatalog.HEADLIGHT.id, paintIndex = 3))
        car.mount(ComponentSlot.TAILLIGHT, ItemStack(ItemCatalog.TAILLIGHT.id, paintIndex = 5))
        car.mount(ComponentSlot.DOOR_FRONT, ItemStack(ItemCatalog.DOOR_FRONT.id, paintIndex = 3))
        assertEquals(-1, car.parts[ComponentSlot.HEADLIGHT]?.paintIndex)
        assertEquals(-1, car.parts[ComponentSlot.TAILLIGHT]?.paintIndex)
        assertEquals(3, car.parts[ComponentSlot.DOOR_FRONT]?.paintIndex)
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
    fun theShedHasOnlyOneShowcaseAccessory() {
        val showcaseIds = setOf(
            ItemCatalog.SEAT_FRONT.id, ItemCatalog.SEAT_REAR.id,
            ItemCatalog.DOOR_FRONT.id, ItemCatalog.DOOR_REAR.id, ItemCatalog.HOOD.id
        )
        for (seed in 1L..40L) {
            val engine = GameEngine(seed, 0f)
            val shed = engine.segment.buildings.first()
            assertTrue("seed $seed: kôlňa je až za autom", shed.localX < 10f)
            assertEquals(
                "seed $seed: kôlňa má mať iba jeden ukážkový doplnok",
                1,
                shed.loot.count { it.defId in showcaseIds }
            )
            val firstRoadGarage = engine.segment.buildings.first {
                it.type == sk.kubis.endlessdrive.domain.model.BuildingType.GARAGE && it.localX > 100f
            }
            assertEquals("prvá cestná garáž má byť stručná", 2, firstRoadGarage.loot.size)
        }
    }
}

class VehiclePaintTest {
    @Test
    fun shellAndFoundPanelsUseTheWholePalette() {
        val shellPaints = (1L..80L).map {
            Car().apply { installStarterKit(SeededRandom(it)) }.bodyPaintIndex
        }.toSet()
        assertTrue("karoséria používa príliš málo farieb: $shellPaints", shellPaints.size >= 8)

        val panelPaints = mutableSetOf<Int>()
        var sawLights = false
        for (seed in 1L..240L) {
            LootGenerator.generate(
                SeededRandom(seed),
                sk.kubis.endlessdrive.domain.model.BuildingType.GARAGE,
                5000f,
                BranchStyle.INDUSTRIAL
            ).forEach { stack ->
                val slot = stack.def.mountsTo
                if (slot?.takesBodyPaint == true) panelPaints += stack.paintIndex
                if (slot == ComponentSlot.HEADLIGHT || slot == ComponentSlot.TAILLIGHT) {
                    sawLights = true
                    assertEquals(
                        "svetlá nesmú dostať lak karosérie",
                        -1,
                        stack.paintIndex
                    )
                }
            }
        }
        assertTrue("nájdené plechy používajú príliš málo farieb: $panelPaints", panelPaints.size >= 8)
        assertTrue("v loote musia byť aj svetlá, inak sa lak nestráži", sawLights)
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
    fun tuneSuspensionPresetMountsBestPartsAndCanStart() {
        val car = Car().apply { installStarterKit(SeededRandom(9L), DebugOptions.TUNE_SUSPENSION) }
        assertEquals(ItemCatalog.SUSPENSION_LIFT.id, car.parts[ComponentSlot.SUSPENSION]?.defId)
        assertEquals(car.fuelCapacity, car.fuel, 0.01f)
        assertTrue(car.canStart())
    }

    @Test
    fun fullFluidsFillsTheTanksClean() {
        val car = Car().apply { installStarterKit(SeededRandom(9L), DebugOptions(fullFluids = true)) }
        assertEquals(car.fuelCapacity, car.fuel, 0.01f)
        assertEquals(1f, car.fuelPurity, 0.001f)
        val hold = car.parts[ComponentSlot.BATTERY]?.health ?: 0f
        assertEquals(hold, car.batteryCharge, 0.001f)
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

/** Zóny v menu CAR – klik na zadok nesmie ukázať motor. */
class CarSectionZoneTest {

    @Test
    fun alternatorAndStarterSitWithTheEngineInFront() {
        assertTrue(ComponentSlot.ALTERNATOR in CarSection.PREDOK.slots)
        assertTrue(ComponentSlot.STARTER in CarSection.PREDOK.slots)
        assertTrue(ComponentSlot.ENGINE in CarSection.PREDOK.slots)
        assertFalse(ComponentSlot.ALTERNATOR in CarSection.STRED.slots)
        assertFalse(ComponentSlot.STARTER in CarSection.STRED.slots)
        assertFalse(ComponentSlot.ENGINE in CarSection.ZADOK.slots)
        assertFalse(ComponentSlot.ENGINE in CarSection.STRED.slots)
    }

    @Test
    fun zonesDoNotOverlapAndKeepEnginePartsOutOfTheRear() {
        val front = CarSection.PREDOK.slots.toSet()
        val middle = CarSection.STRED.slots.toSet()
        val rear = CarSection.ZADOK.slots.toSet()
        assertTrue(front.intersect(middle).isEmpty())
        assertTrue(front.intersect(rear).isEmpty())
        assertTrue(middle.intersect(rear).isEmpty())
        assertTrue(ComponentSlot.FUEL_TANK in rear)
        assertFalse(ComponentSlot.FUEL_TANK in front)
        assertTrue(ComponentSlot.HEADLIGHT in front)
        assertTrue(ComponentSlot.TAILLIGHT in rear)
        assertTrue(ComponentSlot.ROOF_RACK in middle)
        assertTrue(ComponentSlot.DRIVETRAIN in middle)
        assertFalse(ComponentSlot.ROOF_RACK in rear)
        assertFalse(ComponentSlot.DRIVETRAIN in rear)
        assertFalse(ComponentSlot.ROOF_RACK in front)
        assertFalse(ComponentSlot.DRIVETRAIN in front)
    }

    @Test
    fun roofRackAndDrivetrainSitInTheMiddle() {
        assertTrue(ComponentSlot.ROOF_RACK in CarSection.STRED.slots)
        assertTrue(ComponentSlot.DRIVETRAIN in CarSection.STRED.slots)
        assertFalse(ComponentSlot.ROOF_RACK in CarSection.ZADOK.slots)
        assertFalse(ComponentSlot.DRIVETRAIN in CarSection.ZADOK.slots)
        assertTrue(ComponentSlot.ENGINE in CarSection.PREDOK.slots)
        assertTrue(ComponentSlot.HEADLIGHT in CarSection.PREDOK.slots)
        assertTrue(ComponentSlot.TIRE_FRONT in CarSection.PREDOK.slots)
    }
}
