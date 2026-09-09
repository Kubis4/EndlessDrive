package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadPaving
import sk.kubis.endlessdrive.domain.model.RoadFeature
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.VehiclePaint
import sk.kubis.endlessdrive.game.Journey

object WorldGenerator {
    private const val MIX = -0x61C8864680B583EBL
    private const val START_STYLE_SALT = 0x4D595DF4D0F33173L

    /**
     * Bezpečný, ale nie zakaždým rovnaký začiatok. Sneh ostáva na neskôr;
     * tutoriál môže začať na vidieku, v priemysle, na púšti aj v lese.
     */
    fun startingStyle(worldSeed: Long): BranchStyle = SeededRandom(
        worldSeed xor START_STYLE_SALT
    ).pick(
        listOf(
            BranchStyle.SAFE_RURAL,
            BranchStyle.SAFE_RURAL,
            BranchStyle.INDUSTRIAL,
            BranchStyle.INDUSTRIAL,
            BranchStyle.DESERT,
            BranchStyle.SHORTCUT_RISK,
            BranchStyle.FOREST_ALIVE,
            BranchStyle.FOREST
        )
    )

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
        val progress = MathX.growth(tripDistance, 30_000f).coerceAtMost(1.35f)
        // Regióny ostávajú čitateľné aj hlboko v jazde; nerastú na desiatky km.
        val lengthGrowth = 1f + progress * 0.25f
        val baseLen = if (isTutorial) {
            GameConfig.TUTORIAL_SEGMENT_LENGTH
        } else {
            rng.nextFloat(GameConfig.SEGMENT_LENGTH_MIN, GameConfig.SEGMENT_LENGTH_MAX) * lengthGrowth
        }
        val length = (baseLen * style.lengthMul).coerceAtLeast(400f)
        val features = planFeatures(rng, style, length, tripDistance, isTutorial)

        // Viac budov na dlhších úsekoch, ale s väčším odstupom (menej „husto“).
        val density = (
            style.buildingDensity *
                (1f - MathX.growth(tripDistance, Journey.FINAL_DISTANCE_M) * 0.42f)
            ).coerceAtLeast(0.18f)
        val roll = rng.nextFloat()
        val room = (length / GameConfig.BUILDING_MIN_SPACING).toInt().coerceAtLeast(1)
        val minCount = when {
            isTutorial -> 2
            density >= 0.7f -> 3
            density >= 0.45f -> 2
            else -> 1
        }
        val buildingCount = when {
            isTutorial -> 2
            roll < 0.16f -> minCount
            roll < 0.38f -> minCount + 1
            roll < 0.64f -> minCount + 2
            roll < 0.86f -> minCount + 3
            else -> minCount + 4
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
        if (style == BranchStyle.ALPINE) {
            return if (rng.chance(0.58f)) RoadPaving.SNOW else RoadPaving.PACKED_SNOW
        }
        // Zima je odmena za dlhú jazdu – čím ďalej, tým väčšia šanca na sneh.
        // Na púšti a v piesočnej búrke nesneží, tam vládne piesok.
        if (!style.arid && tripDistance >= GameConfig.SNOW_START_M) {
            val winterChance = (MathX.growth(tripDistance - GameConfig.SNOW_START_M, 30_000f) * 0.55f)
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
            // Močiar: tmavá vyjazdená cesta, asfalt iba výnimočne.
            BranchStyle.FOREST -> when {
                roll < 0.14f -> RoadPaving.ASPHALT
                roll < 0.30f -> RoadPaving.CRACKED
                roll < 0.78f -> RoadPaving.DIRT
                else -> RoadPaving.GRAVEL_ROAD
            }
            // Jesenné údolie: starší asfalt sa strieda s hlinou a štrkom.
            BranchStyle.FOREST_ALIVE -> when {
                roll < 0.20f -> RoadPaving.ASPHALT
                roll < 0.42f -> RoadPaving.CRACKED
                roll < 0.78f -> RoadPaving.DIRT
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
                roll < 0.16f -> RoadPaving.DIRT
                roll < 0.72f -> RoadPaving.GRAVEL_ROAD
                else -> RoadPaving.CRACKED
            }
            // V búrke je cesta prakticky len stopa v naviatom piesku.
            BranchStyle.SANDSTORM, BranchStyle.DUST_STORM -> when {
                roll < 0.62f -> RoadPaving.SAND_TRACK
                roll < 0.84f -> RoadPaving.DIRT
                else -> RoadPaving.GRAVEL_ROAD
            }
            BranchStyle.ALPINE -> error("alpine paving is selected above")
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
        // Už žiadne rázcestie: región pozná jediné plynulé pokračovanie.
        val choices = buildContinuation(
            rng, plan.seed, plan.style, tripDistance + plan.length, isTutorial
        )

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
        placeRoadsideWrecks(rng, plan, segment, tripDistance, isTutorial)
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
            RoadFeature.STRAIGHT,
            RoadFeature.STRAIGHT
        )

        val out = mutableListOf<RoadFeature>()
        // Krátky nájazd, potom hneď členitý terén – nie kilometer roviny.
        out += RoadFeature.STRAIGHT
        var covered = GameConfig.FEATURE_MIN_LENGTH * 1.4f
        val hardness = MathX.growth(tripDistance, 35_000f).coerceAtMost(2.4f)
        // Skoré km: jemné kopce áno, ostré crest/ravine ešte zriedka.
        val earlySoft = (1f - (tripDistance / 2500f).coerceIn(0f, 1f))

        while (covered < length - GameConfig.FEATURE_MIN_LENGTH) {
            val roll = rng.nextFloat()
            val next = when (style) {
                // Vidiek a jesenné údolie sa vlnia medzi stromami.
                BranchStyle.SAFE_RURAL, BranchStyle.FOREST_ALIVE -> when {
                    roll < 0.18f + earlySoft * 0.06f -> RoadFeature.STRAIGHT
                    roll < 0.52f + earlySoft * 0.08f -> RoadFeature.HILLS
                    roll < 0.64f -> if (earlySoft > 0.65f) RoadFeature.HILLS else RoadFeature.CREST
                    roll < 0.74f -> if (earlySoft > 0.5f) RoadFeature.SWITCHBACK else RoadFeature.RAVINE
                    roll < 0.86f -> RoadFeature.SWITCHBACK
                    roll < 0.94f -> RoadFeature.HILLS
                    else -> RoadFeature.BROKEN
                }
                // Močiar ostáva nízky; náročnosť robia preliačiny a rozmočená cesta.
                BranchStyle.FOREST -> when {
                    roll < 0.34f + earlySoft * 0.08f -> RoadFeature.STRAIGHT
                    roll < 0.62f -> RoadFeature.HILLS
                    roll < 0.78f -> RoadFeature.RAVINE
                    roll < 0.90f -> RoadFeature.BROKEN
                    else -> RoadFeature.SWITCHBACK
                }
                // Púšť je dlhá a otvorená: rovinky a duny, mosty takmer nikdy.
                BranchStyle.DESERT, BranchStyle.DESERT_DUSK -> when {
                    roll < 0.30f -> RoadFeature.STRAIGHT
                    roll < 0.62f -> RoadFeature.HILLS
                    roll < 0.74f -> if (earlySoft > 0.5f) RoadFeature.HILLS else RoadFeature.CREST
                    roll < 0.86f -> RoadFeature.SWITCHBACK
                    roll < 0.92f -> RoadFeature.HILLS
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
                BranchStyle.ALPINE -> when {
                    roll < 0.16f -> RoadFeature.STRAIGHT
                    roll < 0.45f -> RoadFeature.HILLS
                    roll < 0.66f -> RoadFeature.CREST
                    roll < 0.81f -> RoadFeature.SWITCHBACK
                    roll < 0.91f -> RoadFeature.RAVINE
                    else -> RoadFeature.BROKEN
                }
                BranchStyle.INDUSTRIAL -> when {
                    roll < 0.14f + earlySoft * 0.05f -> RoadFeature.STRAIGHT
                    roll < 0.42f + earlySoft * 0.06f -> RoadFeature.HILLS
                    roll < 0.56f -> if (earlySoft > 0.55f) RoadFeature.HILLS else RoadFeature.CREST
                    roll < 0.68f -> if (earlySoft > 0.4f) RoadFeature.SWITCHBACK else RoadFeature.RAVINE
                    roll < 0.82f -> RoadFeature.SWITCHBACK
                    roll < 0.92f -> RoadFeature.HILLS
                    else -> RoadFeature.BROKEN
                }
                BranchStyle.SHORTCUT_RISK -> when {
                    roll < 0.10f + earlySoft * 0.04f -> RoadFeature.STRAIGHT
                    roll < 0.34f -> RoadFeature.HILLS
                    roll < 0.50f -> if (earlySoft > 0.5f) RoadFeature.HILLS else RoadFeature.CREST
                    roll < 0.64f -> if (earlySoft > 0.35f) RoadFeature.SWITCHBACK else RoadFeature.RAVINE
                    roll < 0.78f -> RoadFeature.SWITCHBACK
                    roll < 0.88f -> RoadFeature.HILLS
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
                // Most je krátky prechod cez roklinu, nie samostatný región.
                // Menšia váha drží jeho dĺžku pod kontrolou aj pri dlhších mapách.
                RoadFeature.BRIDGE -> rng.nextFloat(0.32f, 0.58f)
                RoadFeature.STRAIGHT -> rng.nextFloat(0.8f, 1.5f)
                else -> rng.nextFloat(0.7f, 1.4f)
            }
            bias
        }
        val total = weights.sum()
        val sectionLengths = FloatArray(n) { plan.length * (weights[it] / total) }
        // Pri dlhšom segmente by sa aj nízka šanca mohla premeniť na most
        // dlhý stovky metrov. Most má byť krátka prekážka s rampami, preto
        // jeho prebytočnú dĺžku rozdelíme do susedných pevných úsekov.
        var bridgeExcess = 0f
        for (i in 0 until n) {
            if (plan.features[i] == RoadFeature.BRIDGE && sectionLengths[i] > MAX_BRIDGE_LENGTH) {
                bridgeExcess += sectionLengths[i] - MAX_BRIDGE_LENGTH
                sectionLengths[i] = MAX_BRIDGE_LENGTH
            }
        }
        if (bridgeExcess > 0f) {
            val nonBridgeLength = sectionLengths.indices
                .filter { plan.features[it] != RoadFeature.BRIDGE }
                .sumOf { sectionLengths[it].toDouble() }
                .toFloat()
            if (nonBridgeLength > 0f) {
                for (i in 0 until n) {
                    if (plan.features[i] != RoadFeature.BRIDGE) {
                        sectionLengths[i] += bridgeExcess * sectionLengths[i] / nonBridgeLength
                    }
                }
            }
        }
        val out = ArrayList<RoadSection>(n)
        var cursor = 0f
        for (i in 0 until n) {
            val len = if (i == n - 1) plan.length - cursor else sectionLengths[i]
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
        // Prvá garáž je ochutnávka, nie katalóg. Jeden náhodný pár plechu a
        // jedna užitočná vec stačia; ďalšie diely si hráč hľadá na ceste.
        val useful = garage.loot.firstOrNull { it.def.mountsTo?.group != "Body" }
            ?: ItemStack(ItemCatalog.TIRE.id, ComponentCondition.USED, 0.65f)
        val firstDoor = if (rng.chance(0.5f)) ItemCatalog.DOOR_FRONT else ItemCatalog.DOOR_REAR
        garage.loot.clear()
        garage.loot += ItemStack(
            firstDoor.id,
            ComponentCondition.USED,
            0.7f,
            paintIndex = rng.nextInt(VehiclePaint.entries.size)
        )
        garage.loot += useful
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
        val density = 0.4f + MathX.growth(tripDistance, 30_000f) * 1.1f
        val count = (plan.length / 260f * density).toInt().coerceIn(0, 6)

        repeat(count) {
            val gap = 70f + rng.nextFloat() * 190f
            val start = cursor + gap
            val sec = segment.sectionAtLocal(start)?.feature
            val standingAtStart = sec == RoadFeature.RAVINE || sec == RoadFeature.STRAIGHT
            var surface = pickSurface(rng, plan.style, sec, plan.paving.winter, standingAtStart)
            val len = surfacePatchLength(rng, surface)
            val end = start + len
            cursor = end
            if (end > usableEnd) return
            // Most je holá lávka a na skoku by naplavenina bola len nefér.
            // Testovať treba celú dĺžku, nie len začiatok: dlhý nános môže
            // začať pred mostom a pokračovať ďalej po mostovke.
            if (spansFeature(segment, start, end, RoadFeature.BRIDGE)) return@repeat
            if (spansFeature(segment, start, end, RoadFeature.CREST)) return@repeat
            // Voda musí byť v rovine po celej dĺžke – mláka, ktorej druhý
            // koniec vybieha do kopca, by stiekla.
            val standing = segment.sections
                .filter { it.start < end && it.end > start }
                .all { it.feature == RoadFeature.RAVINE || it.feature == RoadFeature.STRAIGHT }
            if (surface == RoadSurface.WATER && !standing) surface = RoadSurface.MUD
            segment.patches += SurfacePatch(surface, start, end)
        }
    }

    /** Dlhé úseky sú čitateľné vopred a ich rozdiel v trakcii hráč aj pocíti. */
    private fun surfacePatchLength(rng: SeededRandom, surface: RoadSurface): Float = when (surface) {
        RoadSurface.SAND -> rng.nextFloat(36f, 74f)
        RoadSurface.MUD -> rng.nextFloat(32f, 66f)
        RoadSurface.ICE, RoadSurface.SLUSH -> rng.nextFloat(28f, 58f)
        RoadSurface.GRAVEL -> rng.nextFloat(24f, 54f)
        RoadSurface.WATER -> rng.nextFloat(32f, 48f)
        RoadSurface.ASPHALT -> rng.nextFloat(24f, 48f)
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
        // Voda bola zdrojom nečitateľných kolízií a spikeov pri mostoch.
        // Roklina ostáva náročná, ale ako stabilné bahno.
        if (standing && feature == RoadFeature.RAVINE && rng.nextFloat() < 0.75f) {
            return RoadSurface.MUD
        }
        val roll = rng.nextFloat()
        val picked = when (style) {
            BranchStyle.SAFE_RURAL -> when {
                roll < 0.42f -> RoadSurface.MUD
                roll < 0.70f -> RoadSurface.MUD
                else -> RoadSurface.GRAVEL
            }
            // Močiar drží tmavé bahno; štrk je len spevnený krátky úsek.
            BranchStyle.FOREST -> when {
                roll < 0.86f -> RoadSurface.MUD
                else -> RoadSurface.GRAVEL
            }
            BranchStyle.FOREST_ALIVE -> when {
                roll < 0.62f -> RoadSurface.MUD
                else -> RoadSurface.GRAVEL
            }
            BranchStyle.INDUSTRIAL -> when {
                roll < 0.45f -> RoadSurface.GRAVEL
                roll < 0.72f -> RoadSurface.MUD
                else -> RoadSurface.MUD
            }
            BranchStyle.DESERT, BranchStyle.DESERT_DUSK -> when {
                roll < 0.66f -> RoadSurface.SAND
                else -> RoadSurface.GRAVEL
            }
            BranchStyle.SHORTCUT_RISK -> when {
                roll < 0.74f -> RoadSurface.GRAVEL
                else -> RoadSurface.MUD
            }
            // Búrka zaváta cestu závejmi piesku – iná prekážka tu ani nie je.
            BranchStyle.SANDSTORM, BranchStyle.DUST_STORM ->
                if (roll < 0.82f) RoadSurface.SAND else RoadSurface.GRAVEL
            BranchStyle.ALPINE -> if (roll < 0.55f) RoadSurface.ICE else RoadSurface.GRAVEL
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
        if (usableEnd > minStart + 20f) {
            var cursor = minStart
            for (i in 0 until plan.buildingCount) {
                val latest = usableEnd - (plan.buildingCount - 1 - i) * GameConfig.BUILDING_MIN_SPACING
                if (cursor >= latest) break
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
        }
        placeRelays(rng, plan, segment, tripDistance)
    }

    /**
     * Odstavené autá pri ceste. Je ich málo a nie v každom regióne – scrap
     * pri nich má byť nález, nie istota.
     */
    private fun placeRoadsideWrecks(
        rng: SeededRandom,
        plan: SegmentPlan,
        segment: RoadSegment,
        tripDistance: Float,
        isTutorial: Boolean
    ) {
        if (isTutorial) return
        val count = if (plan.style.arid) {
            // Piesok už nemá pôsobiť ako cintorín nelootovateľných áut.
            when {
                rng.chance(0.30f) -> 2
                rng.chance(0.42f) -> 1
                else -> 0
            }
        } else {
            when {
                rng.chance(0.08f) -> 2
                rng.chance(0.28f) -> 1
                else -> 0
            }
        }
        if (count <= 0) return
        val usableEnd = plan.length - GameConfig.JUNCTION_ZONE - 40f
        val minStart = GameConfig.BUILDING_MIN_GAP_FROM_START
        if (usableEnd <= minStart + 20f) return
        var cursor = minStart + rng.nextFloat() * 80f
        repeat(count) { wreckIndex ->
            val windowEnd = (usableEnd - (count - 1) * GameConfig.BUILDING_MIN_SPACING)
                .coerceAtLeast(cursor + 1f)
            val lx = flattestLocalX(segment, cursor, windowEnd)
            if (lx.isNaN()) {
                cursor = windowEnd
                return@repeat
            }
            // Vrak musí stáť na pevnej zemi. Predtým sa roadside loot umiestnil
            // aj do stredu mosta, kde potom auto vyzeralo ako dekorácia vo vzduchu.
            if (spansFeature(segment, lx - 18f, lx + 18f, RoadFeature.BRIDGE)) {
                cursor = lx + GameConfig.BUILDING_MIN_SPACING
                return@repeat
            }
            val occupied = segment.buildings.any {
                kotlin.math.abs(it.localX - lx) < GameConfig.BUILDING_MIN_SPACING * 0.55f
            }
            if (!occupied) {
                segment.buildings += makeBuilding(
                    rng, BuildingType.WRECK, lx, tripDistance, plan.style,
                    wreckLootOverride = plan.style.arid && wreckIndex == 0
                )
            }
            cursor = lx + GameConfig.BUILDING_MIN_SPACING
        }
    }

    /**
     * Skutočné relay checkpointy sedia priamo na meta-cieľoch Journey.
     * Bežné čerpacie stanice ostávajú obyčajnými zastávkami; iba týchto sedem
     * miest má stožiar, modul na opravu a trvalý progres.
     */
    private fun placeRelays(
        rng: SeededRandom,
        plan: SegmentPlan,
        segment: RoadSegment,
        tripDistance: Float
    ) {
        val from = segment.worldOrigin
        val to = segment.worldOrigin + plan.length - GameConfig.JUNCTION_ZONE - 40f
        if (to <= from) return

        Journey.goals.forEachIndexed { relayIndex, goal ->
            val at = goal.distanceKm * 1000f
            if (at <= from + 0.5f || at > to) return@forEachIndexed

            val localX = relayLocalX(segment, at - from, to - from)
            if (localX.isNaN()) return@forEachIndexed
            val depot = makeBuilding(
                rng, BuildingType.GAS_STATION, localX, tripDistance, plan.style,
                landmark = true,
                relayIndex = relayIndex
            )
            // A relay is a useful resupply point, but restoration needs an
            // actual module and increasing scrap investment.
            val longRouteSupply = 1f - MathX.growth(at, Journey.FINAL_DISTANCE_M) * 0.22f
            val depotFuel = rng.nextFloat(GameConfig.PUMP_FUEL_MAX * 0.8f, GameConfig.PUMP_FUEL_MAX * 1.4f) * longRouteSupply
            depot.pumpFuelL = depotFuel * 0.55f
            depot.pumpDieselL = depotFuel * 0.45f
            depot.pumpPurity = rng.nextFloat(0.88f, 1f)
            val tier3 = listOf(ItemCatalog.ENGINE_C, ItemCatalog.RADIATOR_HD, ItemCatalog.FUEL_TANK_LONG)
            val solid = listOf(
                ItemCatalog.TIRE, ItemCatalog.TIRE_OFFROAD, ItemCatalog.BATTERY_GOOD,
                ItemCatalog.BRAKES_GOOD, ItemCatalog.RADIATOR_GOOD, ItemCatalog.SUSPENSION_GOOD,
                ItemCatalog.ENGINE_B, ItemCatalog.FUEL_TANK_BIG, ItemCatalog.DRIVE_AWD
            )
            val depotIds = depot.loot.mapTo(HashSet()) { it.defId }
            repeat(2 + rng.nextInt(2)) {
                val available = solid.filter { it.id !in depotIds }
                val def = rng.pick(if (available.isEmpty()) solid else available)
                depotIds += def.id
                depot.loot.add(ItemStack(def.id, ComponentCondition.USED, rng.nextFloat(0.6f, 0.92f)))
            }
            val eliteChance = 0.18f + MathX.growth(at, 12000f) * 0.30f
            if (rng.chance(eliteChance.coerceAtMost(0.75f))) {
                val def = rng.pick(tier3)
                depot.loot.add(ItemStack(def.id, ComponentCondition.USED, rng.nextFloat(0.65f, 0.95f)))
            }
            // The route-specific utility item keeps relay loot valuable even
            // when the player already has enough fuel and repair parts.
            val travelRotation = listOf(
                ItemCatalog.SEAT_REAR, ItemCatalog.ROOF_RACK, ItemCatalog.BOOT_CRATE,
                ItemCatalog.DOOR_FRONT, ItemCatalog.DOOR_REAR, ItemCatalog.TRUNK_LID, ItemCatalog.SUSPENSION_LIFT
            )
            val travel = travelRotation[relayIndex % travelRotation.size]
            if (depot.loot.none { it.defId == travel.id }) {
                depot.loot.add(ItemStack(travel.id, ComponentCondition.USED, rng.nextFloat(0.55f, 0.9f)))
            }
            if (at >= GameConfig.WINTER_GEAR_FROM_M) {
                val winter = if (relayIndex % 2 == 0) ItemCatalog.SNOW_CHAINS else ItemCatalog.TIRE_WINTER
                if (depot.loot.none { it.defId == winter.id }) {
                    depot.loot.add(ItemStack(winter.id, ComponentCondition.USED, rng.nextFloat(0.58f, 0.92f)))
                }
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
            depot.loot.add(
                ItemStack(
                    defId = ItemCatalog.PUNCTURE_KIT.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = 1
                )
            )
            depot.loot.add(
                ItemStack(
                    defId = ItemCatalog.SCRAP_PILE.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = Journey.relayRestoreCost(relayIndex)
                )
            )
            depot.loot.add(
                ItemStack(
                    defId = ItemCatalog.RELAY_MODULE.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = 1
                )
            )
            segment.buildings += depot
        }
    }

    /**
     * Hľadá miesto čo najbližšie k míľniku, ale nikdy na moste ani pod
     * existujúcou budovou. Posledný široký priechod je poistka pri dlhom
     * moste alebo veľmi členitom úseku.
     */
    private fun relayLocalX(segment: RoadSegment, targetLocal: Float, usableEnd: Float): Float {
        val minStart = GameConfig.BUILDING_MIN_GAP_FROM_START
        val windows = listOf(45f, 140f, 280f, usableEnd)
        for (radius in windows) {
            val from = if (radius == usableEnd) minStart else (targetLocal - radius).coerceAtLeast(minStart)
            val to = if (radius == usableEnd) usableEnd else (targetLocal + radius).coerceAtMost(usableEnd)
            val candidate = flattestLocalX(segment, from, to) { candidateX ->
                segment.buildings.any {
                    kotlin.math.abs(it.localX - candidateX) < GameConfig.BUILDING_MIN_SPACING * 0.55f
                }
            }
            if (!candidate.isNaN()) return candidate
        }
        return Float.NaN
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
        step: Float = 2.5f,
        blocked: (Float) -> Boolean = { false }
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
            if (clear && !blocked(x)) {
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

    // --- Plynulé pokračovanie regiónov -------------------------------------

    private fun buildContinuation(
        rng: SeededRandom,
        parentSeed: Long,
        current: BranchStyle,
        atDistance: Float,
        tutorial: Boolean
    ): List<BranchChoice> {
        // Štart už používa zelenú kresbu živého lesa. Nasledujúci región preto
        // musí byť naozaj iný; RURAL -> FOREST_ALIVE vyzeralo aj po siedmich
        // kilometroch ako jedno nezmenené pozadie. Živý a suchý les sa tiež
        // nestriedajú naslepo – krátke regióny z toho spravili blikanie.
        val alpineReady = atDistance >= maxOf(
            BranchStyle.ALPINE.unlockDistance,
            GameConfig.SNOW_START_M
        )
        // Nie je to aktivita ani udalosť na ceste: jediné pokračovanie regiónu
        // berie suseda zo zmysluplného poolu. Les ide do vidieka / priemyslu /
        // pustatiny / púšte, nie do druhého variantu lesa.
        val next = if (tutorial) {
            when (current) {
                BranchStyle.SAFE_RURAL -> BranchStyle.FOREST
                BranchStyle.FOREST, BranchStyle.FOREST_ALIVE -> BranchStyle.INDUSTRIAL
                BranchStyle.INDUSTRIAL -> BranchStyle.SAFE_RURAL
                BranchStyle.DESERT -> BranchStyle.INDUSTRIAL
                BranchStyle.SHORTCUT_RISK -> BranchStyle.FOREST_ALIVE
                else -> BranchStyle.SAFE_RURAL
            }
        } else {
            val pool = when (current) {
                BranchStyle.SAFE_RURAL -> listOf(
                    BranchStyle.FOREST, BranchStyle.FOREST,
                    BranchStyle.INDUSTRIAL, BranchStyle.SHORTCUT_RISK
                )
                BranchStyle.FOREST_ALIVE -> listOf(
                    BranchStyle.SHORTCUT_RISK, BranchStyle.SHORTCUT_RISK,
                    BranchStyle.INDUSTRIAL, BranchStyle.DESERT
                )
                BranchStyle.FOREST -> listOf(
                    BranchStyle.SHORTCUT_RISK, BranchStyle.SHORTCUT_RISK,
                    BranchStyle.INDUSTRIAL, BranchStyle.DESERT, BranchStyle.SAFE_RURAL
                )
                BranchStyle.SHORTCUT_RISK -> listOf(
                    BranchStyle.DESERT_DUSK, BranchStyle.DESERT_DUSK,
                    BranchStyle.INDUSTRIAL, BranchStyle.FOREST_ALIVE, BranchStyle.DUST_STORM
                )
                BranchStyle.DESERT -> listOf(
                    BranchStyle.DESERT_DUSK, BranchStyle.DESERT_DUSK,
                    BranchStyle.SANDSTORM, BranchStyle.INDUSTRIAL
                )
                BranchStyle.SANDSTORM -> listOf(
                    BranchStyle.DESERT_DUSK, BranchStyle.DESERT_DUSK, BranchStyle.SHORTCUT_RISK
                )
                BranchStyle.DESERT_DUSK -> if (alpineReady && rng.chance(0.42f)) {
                    listOf(BranchStyle.ALPINE)
                } else listOf(
                    BranchStyle.SHORTCUT_RISK, BranchStyle.INDUSTRIAL, BranchStyle.FOREST_ALIVE
                )
                BranchStyle.ALPINE -> listOf(
                    BranchStyle.INDUSTRIAL, BranchStyle.FOREST, BranchStyle.SAFE_RURAL
                )
                BranchStyle.INDUSTRIAL -> if (alpineReady && rng.chance(0.18f)) {
                    listOf(BranchStyle.ALPINE)
                } else listOf(
                    BranchStyle.DUST_STORM, BranchStyle.SHORTCUT_RISK,
                    BranchStyle.FOREST_ALIVE, BranchStyle.DESERT
                )
                BranchStyle.DUST_STORM -> listOf(
                    BranchStyle.INDUSTRIAL, BranchStyle.DESERT_DUSK, BranchStyle.SHORTCUT_RISK
                )
            }
            val distinct = pool.filter { !it.biome.sharesSceneryWith(current.biome) }
            rng.pick(if (distinct.isEmpty()) pool else distinct)
        }
        val nextSeed = parentSeed xor (next.ordinal + 1L) * MIX xor atDistance.toRawBits().toLong()
        return listOf(BranchChoice(id = 0, plan = planSegment(nextSeed, next, atDistance)))
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
            BranchStyle.ALPINE -> when {
                roll < 0.18f -> BuildingType.HOUSE
                roll < 0.54f -> BuildingType.GARAGE
                roll < 0.76f -> BuildingType.GAS_STATION
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
        landmark: Boolean = false,
        relayIndex: Int = -1,
        wreckLootOverride: Boolean = false
    ): WorldBuilding {
        val id = (type.ordinal.toLong() shl 32) xor localX.toRawBits().toLong() xor rng.nextLong()
        // Stojany nie sú univerzálne. Diesel je o niečo vzácnejší, ale dosť
        // častý na to, aby dieselový motor nebol pasca bez zásobovania.
        val pumpFuelKind = if (type == BuildingType.GAS_STATION && rng.chance(0.40f)) {
            FuelKind.DIESEL
        } else FuelKind.PETROL
        // Čím ďalej, tým väčšia šanca, že stojan je vyčerpaný.
        val pump = if (type == BuildingType.GAS_STATION) {
            // Ďaleko od štartu je palivo čoraz vzácnejšie – dojazd sa stáva témou.
            val drought = MathX.growth(distance, 40_000f).coerceIn(0f, 0.68f)
            if (rng.chance(0.18f + drought)) 0f
            else rng.nextFloat(GameConfig.PUMP_FUEL_MIN, GameConfig.PUMP_FUEL_MAX)
        } else 0f
        // Skutočná poloha budovy posúva rotáciu lootov aj progres rarity;
        // všetky stavby v jednom segmente už nedostanú ten istý „kilometer“.
        val lootDistance = distance + localX
        val loot = if (type == BuildingType.WRECK) {
            wreckLoot(rng, guaranteed = wreckLootOverride).toMutableList()
        } else {
            LootGenerator.generate(rng, type, lootDistance, style).toMutableList()
        }
        // Vyschnutý stojan neznamená prázdnu stanicu – v sklade ostal kanister.
        // Zájsť na benzínku a odísť bez paliva je najhorší možný záver zastávky.
        if (type == BuildingType.GAS_STATION && pump <= 0.05f) {
            // V prvých kilometroch je suchá pumpa ešte záchrana. Neskôr môže
            // zostať naozaj prázdna; vzdialené zásobovanie má byť rozhodnutie.
            val fallback = when {
                distance < 8_000f -> listOf(ItemCatalog.FUEL_CAN, ItemCatalog.DIESEL_CAN)
                rng.chance(0.42f) -> listOf(if (pumpFuelKind == FuelKind.DIESEL) ItemCatalog.DIESEL_CAN else ItemCatalog.FUEL_CAN)
                else -> emptyList()
            }
            fallback.forEach { fallbackFuel ->
                if (loot.none { it.defId == fallbackFuel.id }) loot.add(
                    ItemStack(
                        defId = fallbackFuel.id,
                        condition = ComponentCondition.NEW,
                        health = 1f,
                        count = 1,
                        purity = rng.nextFloat(0.62f, 0.92f)
                    )
                )
            }
        }
        return WorldBuilding(
            id = id,
            type = type,
            localX = localX,
            loot = loot,
            // Zásoba ostáva rovnaká ako predtým, iba sa rozdelí medzi dve
            // samostatné hadice. Plná stanica vždy ponúka benzín aj diesel.
            pumpFuelL = pump * 0.55f,
            pumpDieselL = pump * 0.45f,
            // Stojan býva slušný, ale po rokoch je v ňom aj kondenz.
            pumpPurity = rng.nextFloat(0.78f, 0.99f),
            pumpFuelKind = pumpFuelKind,
            landmark = landmark,
            relayIndex = relayIndex
        )
    }

    /** Vrak pri ceste: občas scrap, zriedkavo poškodený diel, často prázdny. */
    private fun wreckLoot(rng: SeededRandom, guaranteed: Boolean = false): List<ItemStack> {
        val loot = ArrayList<ItemStack>(2)
        if (guaranteed || rng.chance(0.52f)) {
            loot += ItemStack(
                defId = ItemCatalog.SCRAP_PILE.id,
                condition = ComponentCondition.NEW,
                health = 1f,
                count = 1 + rng.nextInt(3)
            )
        }
        if (guaranteed || rng.chance(0.22f)) {
            val part = rng.pick(
                listOf(
                    ItemCatalog.TIRE_POOR, ItemCatalog.BATTERY, ItemCatalog.ALTERNATOR,
                    ItemCatalog.HOOD, ItemCatalog.DOOR_FRONT, ItemCatalog.STARTER
                )
            )
            loot += ItemStack(part.id, ComponentCondition.DAMAGED, rng.nextFloat(0.28f, 0.62f))
        }
        return loot
    }

    /** Odstup budov od mosta vrátane nájazdu (m). */
    private const val BRIDGE_CLEARANCE = 12f
    private const val MAX_BRIDGE_LENGTH = 72f

    private const val PLAN_SALT = 0x5EED_91A4L
    private const val SECTION_SALT = 0x53EC_7104L
}
