package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadSurface

object WorldGenerator {
    private const val MIX = -0x61C8864680B583EBL

    /**
     * Plán segmentu zo seedu – dĺžka, poradie úsekov a počet budov.
     * Rovnaký seed dá vždy rovnaký plán, takže križovatka môže vopred
     * ukázať, čo vetva naozaj obsahuje.
     */
    fun planSegment(
        segmentSeed: Long,
        style: BranchStyle,
        tripDistance: Float,
        isTutorial: Boolean = false
    ): SegmentPlan {
        val rng = SeededRandom(segmentSeed xor PLAN_SALT)
        // Bez stropu – úseky sa predlžujú aj hlboko v jazde.
        val progress = MathX.growth(tripDistance, 4000f).coerceAtMost(3f)
        // Čím ďalej, tým dlhšie úseky pred ďalšou križovatkou.
        val lengthGrowth = 1f + progress * 0.45f
        val baseLen = if (isTutorial) {
            GameConfig.TUTORIAL_SEGMENT_LENGTH
        } else {
            rng.nextFloat(GameConfig.SEGMENT_LENGTH_MIN, GameConfig.SEGMENT_LENGTH_MAX) * lengthGrowth
        }
        val length = (baseLen * style.lengthMul).coerceAtLeast(400f)
        val features = planFeatures(rng, style, length, tripDistance, isTutorial)

        // Viac budov na dlhších úsekoch, ale s väčším odstupom (menej „husto“).
        val density = style.buildingDensity
        val roll = rng.nextFloat()
        val room = (length / GameConfig.BUILDING_MIN_SPACING).toInt().coerceAtLeast(1)
        val buildingCount = when {
            isTutorial -> 2
            roll < 0.06f * (1.35f - density) -> 0
            roll < 0.28f -> 1
            roll < 0.55f -> 2
            roll < 0.78f -> 3
            roll < 0.92f -> 4
            else -> 5
        }.coerceAtMost(room)
        return SegmentPlan(
            seed = segmentSeed,
            style = style,
            length = length,
            features = features,
            buildingCount = buildingCount,
            paving = if (isTutorial) RoadPaving.ASPHALT else pickPaving(rng, style, tripDistance)
        )
    }

    /**
     * Z čoho je vetva postavená. Priemysel drží asfalt a betón, vidiek býva
     * vyjazdená hlina, skratky vedú po štrku a piesku – rozdiel je vidno hneď.
     */
    private fun pickPaving(
        rng: SeededRandom,
        style: BranchStyle,
        tripDistance: Float
    ): RoadPaving {
        // Zima je odmena za dlhú jazdu – čím ďalej, tým väčšia šanca na sneh.
        // Na púšti a v piesočnej búrke nesneží, tam vládne piesok.
        if (!style.arid && tripDistance >= GameConfig.SNOW_START_M) {
            val winterChance = (MathX.growth(tripDistance - GameConfig.SNOW_START_M, 9000f) * 0.55f)
                .coerceAtMost(0.62f)
            if (rng.nextFloat() < winterChance) {
                return if (rng.chance(0.45f)) RoadPaving.PACKED_SNOW else RoadPaving.SNOW
            }
        }
        val roll = rng.nextFloat()
        return when (style) {
            BranchStyle.SAFE_RURAL -> when {
                roll < 0.30f -> RoadPaving.ASPHALT
                roll < 0.55f -> RoadPaving.CRACKED
                roll < 0.85f -> RoadPaving.DIRT
                else -> RoadPaving.GRAVEL_ROAD
            }
            // V lese vedie skôr vyjazdená lesná cesta než súvislý asfalt.
            BranchStyle.FOREST, BranchStyle.FOREST_ALIVE -> when {
                roll < 0.22f -> RoadPaving.ASPHALT
                roll < 0.42f -> RoadPaving.CRACKED
                roll < 0.80f -> RoadPaving.DIRT
                else -> RoadPaving.GRAVEL_ROAD
            }
            BranchStyle.INDUSTRIAL -> when {
                roll < 0.38f -> RoadPaving.ASPHALT
                roll < 0.66f -> RoadPaving.CONCRETE
                roll < 0.88f -> RoadPaving.CRACKED
                else -> RoadPaving.GRAVEL_ROAD
            }
            // Púšť: zaviaty asfalt sa strieda s čistou piesočnou stopou.
            BranchStyle.DESERT, BranchStyle.DESERT_DUSK -> when {
                roll < 0.44f -> RoadPaving.SAND_TRACK
                roll < 0.68f -> RoadPaving.CRACKED
                roll < 0.88f -> RoadPaving.GRAVEL_ROAD
                else -> RoadPaving.DIRT
            }
            BranchStyle.SHORTCUT_RISK -> when {
                roll < 0.30f -> RoadPaving.DIRT
                roll < 0.58f -> RoadPaving.SAND_TRACK
                roll < 0.84f -> RoadPaving.GRAVEL_ROAD
                else -> RoadPaving.CRACKED
            }
            // V búrke je cesta prakticky len stopa v naviatom piesku.
            BranchStyle.SANDSTORM, BranchStyle.DUST_STORM -> when {
                roll < 0.62f -> RoadPaving.SAND_TRACK
                roll < 0.84f -> RoadPaving.DIRT
                else -> RoadPaving.GRAVEL_ROAD
            }
        }
    }

    /** Postaví segment podľa plánu – terén, úseky, budovy a ďalšie križovatky. */
    fun createSegment(
        plan: SegmentPlan,
        worldOrigin: Float,
        tripDistance: Float,
        terrain: Terrain,
        isTutorial: Boolean = false
    ): RoadSegment {
        val rng = SeededRandom(plan.seed)
        val sections = layoutSections(plan)
        val choices = buildChoices(rng, plan.seed, tripDistance + plan.length, isTutorial)

        val segment = RoadSegment(
            seed = plan.seed,
            style = plan.style,
            length = plan.length,
            sections = sections,
            choices = choices,
            worldOrigin = worldOrigin,
            terrain = terrain,
            paving = plan.paving
        )
        placeSurfacePatches(rng, plan, segment, tripDistance, isTutorial)
        // Budovy potrebujú hotový profil segmentu (rovina, nie most).
        if (isTutorial) {
            tutorialBuildings(rng, segment, tripDistance)
        } else {
            placeBuildings(rng, plan, segment, tripDistance)
        }
        return segment
    }

    /** Skratka pre štart hry a testy. */
    fun createSegment(
        segmentSeed: Long,
        style: BranchStyle,
        worldOrigin: Float,
        tripDistance: Float,
        terrain: Terrain,
        isTutorial: Boolean = false
    ): RoadSegment = createSegment(
        plan = planSegment(segmentSeed, style, tripDistance, isTutorial),
        worldOrigin = worldOrigin,
        tripDistance = tripDistance,
        terrain = terrain,
        isTutorial = isTutorial
    )

    // --- Úseky trate -------------------------------------------------------

    private fun planFeatures(
        rng: SeededRandom,
        style: BranchStyle,
        length: Float,
        tripDistance: Float,
        isTutorial: Boolean
    ): List<RoadFeature> {
        if (isTutorial) return listOf(
            RoadFeature.STRAIGHT,
            RoadFeature.HILLS,
            RoadFeature.STRAIGHT,
            RoadFeature.HILLS,
            RoadFeature.BRIDGE,
            RoadFeature.STRAIGHT
        )

        val out = mutableListOf<RoadFeature>()
        // Krátky nájazd, potom hneď členitý terén – nie kilometer roviny.
        out += RoadFeature.STRAIGHT
        var covered = GameConfig.FEATURE_MIN_LENGTH * 1.4f
        val hardness = MathX.growth(tripDistance, 6000f).coerceAtMost(1.8f)
        // Skoré km: jemné kopce áno, ostré crest/ravine ešte zriedka.
        val earlySoft = (1f - (tripDistance / 2500f).coerceIn(0f, 1f))

        while (covered < length - GameConfig.FEATURE_MIN_LENGTH) {
            val roll = rng.nextFloat()
            val next = when (style) {
                // Les kopíruje vidiek – len s ostrejšími zákrutami medzi stromami.
                BranchStyle.SAFE_RURAL, BranchStyle.FOREST, BranchStyle.FOREST_ALIVE -> when {
                    roll < 0.18f + earlySoft * 0.06f -> RoadFeature.STRAIGHT
                    roll < 0.52f + earlySoft * 0.08f -> RoadFeature.HILLS
                    roll < 0.64f -> if (earlySoft > 0.65f) RoadFeature.HILLS else RoadFeature.CREST
                    roll < 0.74f -> if (earlySoft > 0.5f) RoadFeature.SWITCHBACK else RoadFeature.RAVINE
                    roll < 0.86f -> RoadFeature.SWITCHBACK
                    roll < 0.94f -> RoadFeature.BRIDGE
                    else -> RoadFeature.BROKEN
                }
                // Púšť je dlhá a otvorená: rovinky a duny, mosty takmer nikdy.
                BranchStyle.DESERT, BranchStyle.DESERT_DUSK -> when {
                    roll < 0.30f -> RoadFeature.STRAIGHT
                    roll < 0.62f -> RoadFeature.HILLS
                    roll < 0.74f -> if (earlySoft > 0.5f) RoadFeature.HILLS else RoadFeature.CREST
                    roll < 0.86f -> RoadFeature.SWITCHBACK
                    roll < 0.92f -> RoadFeature.BRIDGE
                    else -> RoadFeature.BROKEN
                }
                // V búrke sa jazdí naslepo – krátke, rozbité a zaviate úseky.
                BranchStyle.SANDSTORM, BranchStyle.DUST_STORM -> when {
                    roll < 0.24f -> RoadFeature.STRAIGHT
                    roll < 0.54f -> RoadFeature.HILLS
                    roll < 0.68f -> RoadFeature.SWITCHBACK
                    roll < 0.80f -> if (earlySoft > 0.5f) RoadFeature.HILLS else RoadFeature.CREST
                    else -> RoadFeature.BROKEN
                }
                BranchStyle.INDUSTRIAL -> when {
                    roll < 0.14f + earlySoft * 0.05f -> RoadFeature.STRAIGHT
                    roll < 0.42f + earlySoft * 0.06f -> RoadFeature.HILLS
                    roll < 0.56f -> if (earlySoft > 0.55f) RoadFeature.HILLS else RoadFeature.CREST
                    roll < 0.68f -> if (earlySoft > 0.4f) RoadFeature.SWITCHBACK else RoadFeature.RAVINE
                    roll < 0.82f -> RoadFeature.SWITCHBACK
                    roll < 0.92f -> RoadFeature.BRIDGE
                    else -> RoadFeature.BROKEN
                }
                BranchStyle.SHORTCUT_RISK -> when {
                    roll < 0.10f + earlySoft * 0.04f -> RoadFeature.STRAIGHT
                    roll < 0.34f -> RoadFeature.HILLS
                    roll < 0.50f -> if (earlySoft > 0.5f) RoadFeature.HILLS else RoadFeature.CREST
                    roll < 0.64f -> if (earlySoft > 0.35f) RoadFeature.SWITCHBACK else RoadFeature.RAVINE
                    roll < 0.78f -> RoadFeature.SWITCHBACK
                    roll < 0.88f -> RoadFeature.BRIDGE
                    else -> RoadFeature.BROKEN
                }
            }
            val escalated = if (next == RoadFeature.STRAIGHT && rng.chance(0.35f + hardness * 0.35f)) {
                RoadFeature.HILLS
            } else {
                next
            }
            if (escalated == RoadFeature.BRIDGE && out.lastOrNull() == RoadFeature.BRIDGE) {
                out += RoadFeature.HILLS
            } else {
                out += escalated
            }
            covered += GameConfig.FEATURE_MIN_LENGTH
        }
        if (out.size < 2) out += RoadFeature.HILLS
        return out
    }

    private fun layoutSections(plan: SegmentPlan): List<RoadSection> {
        val n = plan.features.size
        if (n == 0) return listOf(RoadSection(RoadFeature.STRAIGHT, 0f, plan.length))
        val rng = SeededRandom(plan.seed xor SECTION_SALT)
        // Náhodné, ale súčtom presné rozdelenie dĺžky.
        val weights = FloatArray(n) {
            val f = plan.features[it]
            val bias = when (f) {
                RoadFeature.CREST, RoadFeature.RAVINE -> rng.nextFloat(0.55f, 0.95f)
                RoadFeature.BRIDGE -> rng.nextFloat(0.6f, 1.0f)
                RoadFeature.STRAIGHT -> rng.nextFloat(0.8f, 1.5f)
                else -> rng.nextFloat(0.7f, 1.4f)
            }
            bias
        }
        val total = weights.sum()
        val out = ArrayList<RoadSection>(n)
        var cursor = 0f
        for (i in 0 until n) {
            val len = if (i == n - 1) plan.length - cursor else plan.length * (weights[i] / total)
            val end = (cursor + len).coerceAtMost(plan.length)
            out += RoadSection(plan.features[i], cursor, end)
            cursor = end
        }
        return out
    }

    // --- Budovy ------------------------------------------------------------

    private fun tutorialBuildings(
        rng: SeededRandom,
        segment: RoadSegment,
        tripDistance: Float
    ) {
        // Tutoriálové budovy musia byť vždy – okno rozšírime, kým sa nenájde
        // miesto mimo mosta. Prvá jazda sa bez nich nedá rozbehnúť.
        val houseX = flattestLocalX(segment, 180f, 300f)
            .takeUnless { it.isNaN() } ?: flattestLocalX(segment, 120f, 420f)
        val garageX = flattestLocalX(segment, 480f, 620f)
            .takeUnless { it.isNaN() } ?: flattestLocalX(segment, 380f, 780f)
        if (houseX.isNaN() || garageX.isNaN()) return
        segment.buildings += makeBuilding(rng, BuildingType.HOUSE, houseX, tripDistance, segment.style)
        val garage = makeBuilding(rng, BuildingType.GARAGE, garageX, tripDistance, segment.style)
        garage.loot.add(0, ItemStack(ItemCatalog.DOORS.id, ComponentCondition.USED, 0.7f))
        garage.loot.add(1, ItemStack(ItemCatalog.HOOD.id, ComponentCondition.USED, 0.75f))
        garage.loot.add(2, ItemStack(ItemCatalog.WINDOWS.id, ComponentCondition.USED, 0.7f))
        segment.buildings += garage
    }

    /**
     * Naplaveniny na vozovke. Nie sú náhodné pasce – ležia na rovných miestach,
     * nikdy na moste ani tesne pred rázcestím, a je ich viac, čím ďalej si.
     */
    private fun placeSurfacePatches(
        rng: SeededRandom,
        plan: SegmentPlan,
        segment: RoadSegment,
        tripDistance: Float,
        isTutorial: Boolean
    ) {
        if (isTutorial) return
        val usableEnd = plan.length - GameConfig.JUNCTION_ZONE - 40f
        var cursor = 60f
        if (usableEnd <= cursor + 30f) return

        // Na začiatku hry sporadicky, neskôr bežná súčasť cesty.
        val density = 0.4f + MathX.growth(tripDistance, 4000f) * 0.9f
        val count = (plan.length / 260f * density).toInt().coerceIn(0, 6)

        repeat(count) {
            val gap = 70f + rng.nextFloat() * 190f
            val start = cursor + gap
            val len = 7f + rng.nextFloat() * 18f
            cursor = start + len
            if (cursor > usableEnd) return
            val sec = segment.sectionAtLocal(start)?.feature
            // Most je holá lávka a na skoku by naplavenina bola len nefér.
            // Testovať treba celú dĺžku, nie len začiatok: mláka dlhá 7–25 m
            // začínala pred mostom a tiekla ďalej po mostovke.
            if (spansFeature(segment, start, cursor, RoadFeature.BRIDGE)) return@repeat
            if (spansFeature(segment, start, cursor, RoadFeature.CREST)) return@repeat
            // Voda musí byť v rovine po celej dĺžke – mláka, ktorej druhý
            // koniec vybieha do kopca, by stiekla.
            val standing = segment.sections
                .filter { it.start < cursor && it.end > start }
                .all { it.feature == RoadFeature.RAVINE || it.feature == RoadFeature.STRAIGHT }
            segment.patches += SurfacePatch(
                pickSurface(rng, plan.style, sec, plan.paving.winter, standing), start, cursor
            )
        }
    }

    /** Zasahuje úsek [from]–[to] niekde do sekcie s daným prvkom? */
    private fun spansFeature(
        segment: RoadSegment,
        from: Float,
        to: Float,
        feature: RoadFeature
    ): Boolean = segment.sections.any {
        it.feature == feature && it.start < to && it.end > from
    }

    private fun pickSurface(
        rng: SeededRandom,
        style: BranchStyle,
        feature: RoadFeature?,
        winterRoad: Boolean,
        /** Drží úsek stojatú vodu? Roklina a rovina áno, kopec nie. */
        standing: Boolean = true
    ): RoadSurface {
        // Na snehu sa nenaplaví bahno – tam číha ľad a rozbrédnutý sneh.
        if (winterRoad) {
            return if (rng.chance(0.42f)) RoadSurface.ICE else RoadSurface.SLUSH
        }
        // Rozbitá cesta sype štrk, roklina drží vodu, pustatina zaváta pieskom.
        if (feature == RoadFeature.BROKEN && rng.nextFloat() < 0.55f) return RoadSurface.GRAVEL
        if (standing && feature == RoadFeature.RAVINE && rng.nextFloat() < 0.75f) {
            return RoadSurface.WATER
        }
        val roll = rng.nextFloat()
        val picked = when (style) {
            BranchStyle.SAFE_RURAL -> when {
                roll < 0.42f -> RoadSurface.MUD
                roll < 0.70f -> RoadSurface.WATER
                else -> RoadSurface.GRAVEL
            }
            // V lese je mokro a bahno – slnko sa na cestu nedostane.
            BranchStyle.FOREST, BranchStyle.FOREST_ALIVE -> when {
                roll < 0.50f -> RoadSurface.MUD
                roll < 0.80f -> RoadSurface.WATER
                else -> RoadSurface.GRAVEL
            }
            BranchStyle.INDUSTRIAL -> when {
                roll < 0.45f -> RoadSurface.GRAVEL
                roll < 0.72f -> RoadSurface.MUD
                else -> RoadSurface.WATER
            }
            BranchStyle.DESERT, BranchStyle.DESERT_DUSK -> when {
                roll < 0.66f -> RoadSurface.SAND
                else -> RoadSurface.GRAVEL
            }
            BranchStyle.SHORTCUT_RISK -> when {
                roll < 0.44f -> RoadSurface.SAND
                roll < 0.72f -> RoadSurface.GRAVEL
                else -> RoadSurface.MUD
            }
            // Búrka zaváta cestu závejmi piesku – iná prekážka tu ani nie je.
            BranchStyle.SANDSTORM, BranchStyle.DUST_STORM ->
                if (roll < 0.82f) RoadSurface.SAND else RoadSurface.GRAVEL
        }
        // Voda stojí len tam, kam steká – v rokline alebo na rovine. Na kopci
        // ani v serpentíne by mláka nevydržala, tam ostane rozmoknuté bahno.
        return if (picked == RoadSurface.WATER && !standing) RoadSurface.MUD else picked
    }

    private fun placeBuildings(
        rng: SeededRandom,
        plan: SegmentPlan,
        segment: RoadSegment,
        tripDistance: Float
    ) {
        val usableEnd = plan.length - GameConfig.JUNCTION_ZONE - 30f
        val minStart = GameConfig.BUILDING_MIN_GAP_FROM_START
        if (usableEnd <= minStart + 20f) return

        var cursor = minStart
        for (i in 0 until plan.buildingCount) {
            val latest = usableEnd - (plan.buildingCount - 1 - i) * GameConfig.BUILDING_MIN_SPACING
            if (cursor >= latest) return
            val windowEnd = latest.coerceAtLeast(cursor + 1f)
            val lx = flattestLocalX(segment, cursor, windowEnd)
            if (lx.isNaN()) {
                // Celé okno padlo na most – budovu preskočíme a posunieme sa ďalej.
                cursor = windowEnd + GameConfig.BUILDING_MIN_SPACING
                continue
            }
            segment.buildings += makeBuilding(
                rng,
                weightedBuilding(rng, plan.style),
                lx,
                tripDistance,
                plan.style
            )
            cursor = lx + GameConfig.BUILDING_MIN_SPACING
        }
        placeLandmark(rng, plan, segment, tripDistance)
    }

    /**
     * Depo na každých [GameConfig.LANDMARK_SPACING] metrov. Je isté, má palivo
     * aj diely a dáva jazde rytmus – hráč má stále kam mieriť.
     */
    private fun placeLandmark(
        rng: SeededRandom,
        plan: SegmentPlan,
        segment: RoadSegment,
        tripDistance: Float
    ) {
        val spacing = GameConfig.LANDMARK_SPACING
        val from = segment.worldOrigin
        val to = segment.worldOrigin + plan.length - GameConfig.JUNCTION_ZONE - 40f
        val index = kotlin.math.floor(from / spacing).toInt() + 1
        val at = index * spacing
        if (at < from + GameConfig.BUILDING_MIN_GAP_FROM_START * 0.5f || at > to) return

        // Depo je garantované, tak mu okno rozšírime, kým nenájde miesto mimo mosta.
        var localX = flattestLocalX(segment, at - from - 40f, at - from + 40f)
        if (localX.isNaN()) localX = flattestLocalX(segment, at - from - 140f, at - from + 140f)
        if (localX.isNaN()) return
        val depot = makeBuilding(
            rng, BuildingType.GAS_STATION, localX, tripDistance, plan.style,
            landmark = true
        )
        // Depo nikdy nesklame: plný stojan a niekoľko poriadnych dielov.
        depot.pumpFuelL = rng.nextFloat(GameConfig.PUMP_FUEL_MAX * 0.8f, GameConfig.PUMP_FUEL_MAX * 1.4f)
        depot.pumpPurity = rng.nextFloat(0.88f, 1f)
        val tier3 = listOf(ItemCatalog.ENGINE_C, ItemCatalog.RADIATOR_HD, ItemCatalog.FUEL_TANK_LONG)
        val solid = listOf(
            ItemCatalog.TIRE, ItemCatalog.TIRE_OFFROAD, ItemCatalog.BATTERY_GOOD,
            ItemCatalog.BRAKES_GOOD, ItemCatalog.RADIATOR_GOOD, ItemCatalog.SUSPENSION_GOOD,
            ItemCatalog.ENGINE_B, ItemCatalog.FUEL_TANK_BIG, ItemCatalog.DRIVE_AWD
        )
        repeat(2 + rng.nextInt(2)) {
            val def = rng.pick(solid)
            depot.loot.add(ItemStack(def.id, ComponentCondition.USED, rng.nextFloat(0.6f, 0.92f)))
        }
        // Čím ďalej depo je, tým vyššia šanca na kus z tretieho stupňa.
        val eliteChance = 0.18f + MathX.growth(at, 12000f) * 0.30f
        if (rng.chance(eliteChance.coerceAtMost(0.75f))) {
            val def = rng.pick(tier3)
            depot.loot.add(ItemStack(def.id, ComponentCondition.USED, rng.nextFloat(0.65f, 0.95f)))
        }
        depot.loot.add(
            ItemStack(
                defId = ItemCatalog.OIL_BOTTLE.id,
                condition = ComponentCondition.NEW,
                health = 1f,
                count = 2,
                purity = rng.nextFloat(0.9f, 1f)
            )
        )
        segment.buildings += depot
    }

    /**
     * Najrovnejšie miesto v okne – a nikdy nie na moste, tam by budova visela
     * nad roklinou.
     */
    /**
     * Najrovnejšie miesto v okne, na ktorom sa dá postaviť.
     *
     * Most je tvrdo vylúčený, nie len penalizovaný. Pri penalizácii stačilo,
     * aby celé okno padlo na most – všetky body dostali rovnakú prirážku a
     * budova aj tak vyrástla na mostovke, teda vo vzduchu nad roklinou.
     *
     * @return lokálne X, alebo [Float.NaN] keď je celé okno na moste
     */
    private fun flattestLocalX(
        segment: RoadSegment,
        fromLocal: Float,
        toLocal: Float,
        step: Float = 2.5f
    ): Float {
        var bestX = Float.NaN
        var best = Float.MAX_VALUE
        var x = fromLocal
        while (x <= toLocal) {
            // Odstup aj od nájazdu a zjazdu – tam cesta stúpa na mostovku
            // a dom by stál na rampe.
            val clear = listOf(-BRIDGE_CLEARANCE, 0f, BRIDGE_CLEARANCE).none { off ->
                segment.sectionAtLocal(x + off)?.feature == RoadFeature.BRIDGE
            }
            if (clear) {
                val s = kotlin.math.abs(segment.slopeAtLocal(x))
                if (s < best) {
                    best = s
                    bestX = x
                }
            }
            x += step
        }
        return bestX
    }

    // --- Križovatky --------------------------------------------------------

    private fun buildChoices(
        rng: SeededRandom,
        parentSeed: Long,
        atDistance: Float,
        tutorial: Boolean
    ): List<BranchChoice> {
        val picked = mutableListOf<BranchStyle>()
        if (tutorial) {
            // Prvá križovatka ukazuje tri základné svety, nie celý katalóg.
            picked += listOf(BranchStyle.SAFE_RURAL, BranchStyle.INDUSTRIAL, BranchStyle.SHORTCUT_RISK)
        } else {
            // Ponuka rastie so vzdialenosťou – púšť a búrka prídu až neskôr.
            val pool = BranchStyle.entries.filter { atDistance >= it.unlockDistance }.toMutableList()
            // Vždy aspoň dve vetvy, tretia pribúda so vzdialenosťou.
            val wanted = if (rng.chance(0.45f + (atDistance / 4000f).coerceAtMost(0.35f))) 3 else 2
            repeat(wanted.coerceAtMost(pool.size)) {
                val style = pickWeighted(rng, pool)
                pool.remove(style)
                picked += style
            }
            picked.sortBy { it.ordinal }
        }
        if (picked.isEmpty()) picked += BranchStyle.SAFE_RURAL
        return picked.mapIndexed { i, style ->
            val seed = parentSeed xor (style.ordinal + 1L) * MIX xor atDistance.toRawBits().toLong()
            BranchChoice(id = i, plan = planSegment(seed, style, atDistance))
        }
    }

    /** Ruleta podľa [BranchStyle.pickWeight] – bežné cesty padajú častejšie než búrka. */
    private fun pickWeighted(rng: SeededRandom, pool: List<BranchStyle>): BranchStyle {
        val total = pool.sumOf { it.pickWeight.toDouble() }.toFloat()
        var roll = rng.nextFloat() * total
        for (style in pool) {
            roll -= style.pickWeight
            if (roll <= 0f) return style
        }
        return pool.last()
    }

    // --- Loot / budovy -----------------------------------------------------

    private fun weightedBuilding(rng: SeededRandom, style: BranchStyle): BuildingType {
        val roll = rng.nextFloat()
        return when (style) {
            BranchStyle.SAFE_RURAL -> when {
                roll < 0.45f -> BuildingType.HOUSE
                roll < 0.75f -> BuildingType.GARAGE
                roll < 0.92f -> BuildingType.GAS_STATION
                else -> BuildingType.AUTO_SHOP
            }
            // V lese stoja skôr chaty a kôlne než pumpy.
            BranchStyle.FOREST, BranchStyle.FOREST_ALIVE -> when {
                roll < 0.52f -> BuildingType.HOUSE
                roll < 0.82f -> BuildingType.GARAGE
                roll < 0.94f -> BuildingType.GAS_STATION
                else -> BuildingType.AUTO_SHOP
            }
            // Na púšti prežije len to, čo stálo pri ceste – stanica a servis.
            BranchStyle.DESERT, BranchStyle.DESERT_DUSK, BranchStyle.SANDSTORM -> when {
                roll < 0.18f -> BuildingType.HOUSE
                roll < 0.42f -> BuildingType.GARAGE
                roll < 0.76f -> BuildingType.GAS_STATION
                else -> BuildingType.AUTO_SHOP
            }
            // Prachová búrka stojí nad mestom – domov je tu najviac zo všetkého.
            BranchStyle.DUST_STORM -> when {
                roll < 0.46f -> BuildingType.HOUSE
                roll < 0.68f -> BuildingType.GARAGE
                roll < 0.88f -> BuildingType.GAS_STATION
                else -> BuildingType.AUTO_SHOP
            }
            BranchStyle.INDUSTRIAL -> when {
                roll < 0.15f -> BuildingType.HOUSE
                roll < 0.40f -> BuildingType.GARAGE
                roll < 0.70f -> BuildingType.GAS_STATION
                else -> BuildingType.AUTO_SHOP
            }
            BranchStyle.SHORTCUT_RISK -> when {
                roll < 0.30f -> BuildingType.GARAGE
                roll < 0.55f -> BuildingType.GAS_STATION
                else -> BuildingType.AUTO_SHOP
            }
        }
    }

    private fun makeBuilding(
        rng: SeededRandom,
        type: BuildingType,
        localX: Float,
        distance: Float,
        style: BranchStyle,
        landmark: Boolean = false
    ): WorldBuilding {
        val id = (type.ordinal.toLong() shl 32) xor localX.toRawBits().toLong() xor rng.nextLong()
        // Čím ďalej, tým väčšia šanca, že stojan je vyčerpaný.
        val pump = if (type == BuildingType.GAS_STATION) {
            // Ďaleko od štartu je palivo čoraz vzácnejšie – dojazd sa stáva témou.
            val drought = MathX.growth(distance, 6000f).coerceIn(0f, 0.78f)
            if (rng.chance(0.18f + drought)) 0f
            else rng.nextFloat(GameConfig.PUMP_FUEL_MIN, GameConfig.PUMP_FUEL_MAX)
        } else 0f
        val loot = LootGenerator.generate(rng, type, distance, style).toMutableList()
        // Vyschnutý stojan neznamená prázdnu stanicu – v sklade ostal kanister.
        // Zájsť na benzínku a odísť bez paliva je najhorší možný záver zastávky.
        if (type == BuildingType.GAS_STATION && pump <= 0.05f) {
            loot.add(
                ItemStack(
                    defId = ItemCatalog.FUEL_CAN.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = 1,
                    purity = rng.nextFloat(0.62f, 0.92f)
                )
            )
        }
        return WorldBuilding(
            id = id,
            type = type,
            localX = localX,
            loot = loot,
            pumpFuelL = pump,
            // Stojan býva slušný, ale po rokoch je v ňom aj kondenz.
            pumpPurity = rng.nextFloat(0.78f, 0.99f),
            landmark = landmark
        )
    }

    /** Odstup budov od mosta vrátane nájazdu (m). */
    private const val BRIDGE_CLEARANCE = 12f

    private const val PLAN_SALT = 0x5EED_91A4L
    private const val SECTION_SALT = 0x53EC_7104L
}
