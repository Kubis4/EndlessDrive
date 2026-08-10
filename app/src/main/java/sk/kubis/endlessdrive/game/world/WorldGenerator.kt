package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.RoadFeature

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
        val baseLen = if (isTutorial) {
            GameConfig.TUTORIAL_SEGMENT_LENGTH
        } else {
            rng.nextFloat(GameConfig.SEGMENT_LENGTH_MIN, GameConfig.SEGMENT_LENGTH_MAX)
        }
        val length = (baseLen * style.lengthMul).coerceAtLeast(320f)
        val features = planFeatures(rng, style, length, tripDistance, isTutorial)

        // Počet budov je naozaj náhodný – niekedy prejdeš celý úsek naprázdno,
        // inokedy natrafíš na celú osadu.
        val density = style.buildingDensity
        val roll = rng.nextFloat()
        val room = (length / GameConfig.BUILDING_MIN_SPACING).toInt().coerceAtLeast(1)
        val buildingCount = when {
            isTutorial -> 2
            roll < 0.12f * (1.4f - density) -> 0
            roll < 0.45f -> 1
            roll < 0.75f -> 2
            roll < 0.92f -> 3
            else -> 4
        }.coerceAtMost(room)
        return SegmentPlan(
            seed = segmentSeed,
            style = style,
            length = length,
            features = features,
            buildingCount = buildingCount
        )
    }

    /** Postaví segment podľa plánu – terén, úseky, budovy a ďalšie križovatky. */
    fun createSegment(
        plan: SegmentPlan,
        worldOrigin: Float,
        tripDistance: Float,
        terrain: TerrainProfile,
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
            terrain = terrain
        )
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
        terrain: TerrainProfile,
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
        if (isTutorial) return listOf(RoadFeature.STRAIGHT, RoadFeature.HILLS, RoadFeature.STRAIGHT)

        val out = mutableListOf<RoadFeature>()
        // Prvý úsek je vždy zjazdný – hráč práve odbočil z križovatky.
        out += RoadFeature.STRAIGHT
        var covered = GameConfig.FEATURE_MAX_LENGTH
        val hardness = (tripDistance / 5000f).coerceIn(0f, 1f)

        while (covered < length - GameConfig.FEATURE_MIN_LENGTH) {
            val roll = rng.nextFloat()
            val next = when (style) {
                BranchStyle.SAFE_RURAL -> when {
                    roll < 0.34f -> RoadFeature.STRAIGHT
                    roll < 0.66f -> RoadFeature.HILLS
                    roll < 0.80f -> RoadFeature.SWITCHBACK
                    roll < 0.92f -> RoadFeature.BRIDGE
                    else -> RoadFeature.BROKEN
                }
                BranchStyle.INDUSTRIAL -> when {
                    roll < 0.24f -> RoadFeature.STRAIGHT
                    roll < 0.48f -> RoadFeature.HILLS
                    roll < 0.66f -> RoadFeature.SWITCHBACK
                    roll < 0.80f -> RoadFeature.BRIDGE
                    else -> RoadFeature.BROKEN
                }
                BranchStyle.SHORTCUT_RISK -> when {
                    roll < 0.12f -> RoadFeature.STRAIGHT
                    roll < 0.42f -> RoadFeature.HILLS
                    roll < 0.66f -> RoadFeature.SWITCHBACK
                    roll < 0.78f -> RoadFeature.BRIDGE
                    else -> RoadFeature.BROKEN
                }
            }
            // Ďalej od štartu sa rovinky menia na náročnejší terén.
            val escalated = if (next == RoadFeature.STRAIGHT && rng.chance(hardness * 0.5f)) {
                RoadFeature.HILLS
            } else {
                next
            }
            // Dva mosty za sebou nedávajú zmysel.
            if (escalated == RoadFeature.BRIDGE && out.lastOrNull() == RoadFeature.BRIDGE) {
                out += RoadFeature.STRAIGHT
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
        val weights = FloatArray(n) { rng.nextFloat(0.7f, 1.4f) }
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
        val houseX = flattestLocalX(segment, 160f, 260f)
        val garageX = flattestLocalX(segment, 380f, 480f)
        segment.buildings += makeBuilding(rng, BuildingType.HOUSE, houseX, tripDistance, segment.style)
        val garage = makeBuilding(rng, BuildingType.GARAGE, garageX, tripDistance, segment.style)
        garage.loot.add(0, ItemStack(ItemCatalog.DOORS.id, ComponentCondition.USED, 0.7f))
        garage.loot.add(1, ItemStack(ItemCatalog.HOOD.id, ComponentCondition.USED, 0.75f))
        garage.loot.add(2, ItemStack(ItemCatalog.WINDOWS.id, ComponentCondition.USED, 0.7f))
        segment.buildings += garage
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

    /**
     * Najrovnejšie miesto v okne – a nikdy nie na moste, tam by budova visela
     * nad roklinou.
     */
    private fun flattestLocalX(
        segment: RoadSegment,
        fromLocal: Float,
        toLocal: Float,
        step: Float = 2.5f
    ): Float {
        var bestX = fromLocal
        var best = Float.MAX_VALUE
        var x = fromLocal
        while (x <= toLocal) {
            val onBridge = segment.sectionAtLocal(x)?.feature == RoadFeature.BRIDGE
            val s = kotlin.math.abs(segment.slopeAtLocal(x)) + if (onBridge) 100f else 0f
            if (s < best) {
                best = s
                bestX = x
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
        val styles = BranchStyle.entries.toMutableList()
        val picked = mutableListOf<BranchStyle>()
        if (tutorial) {
            picked += styles
        } else {
            // Vždy aspoň dve vetvy, tretia pribúda so vzdialenosťou.
            picked += rng.pick(styles)
            styles.removeAll(picked.toSet())
            picked += rng.pick(styles)
            if (rng.chance(0.45f + (atDistance / 4000f).coerceAtMost(0.35f))) {
                styles.removeAll(picked.toSet())
                if (styles.isNotEmpty()) picked += rng.pick(styles)
            }
            picked.sortBy { it.ordinal }
        }
        return picked.mapIndexed { i, style ->
            val seed = parentSeed xor (style.ordinal + 1L) * MIX xor atDistance.toRawBits().toLong()
            BranchChoice(id = i, plan = planSegment(seed, style, atDistance))
        }
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
        style: BranchStyle
    ): WorldBuilding {
        val id = (type.ordinal.toLong() shl 32) xor localX.toRawBits().toLong() xor rng.nextLong()
        // Čím ďalej, tým väčšia šanca, že stojan je vyčerpaný.
        val pump = if (type == BuildingType.GAS_STATION) {
            val drought = (distance / 6000f).coerceIn(0f, 0.55f)
            if (rng.chance(0.18f + drought)) 0f
            else rng.nextFloat(GameConfig.PUMP_FUEL_MIN, GameConfig.PUMP_FUEL_MAX)
        } else 0f
        return WorldBuilding(
            id = id,
            type = type,
            localX = localX,
            loot = LootGenerator.generate(rng, type, distance, style).toMutableList(),
            pumpFuelL = pump,
            // Stojan býva slušný, ale po rokoch je v ňom aj kondenz.
            pumpPurity = rng.nextFloat(0.78f, 0.99f)
        )
    }

    private const val PLAN_SALT = 0x5EED_91A4L
    private const val SECTION_SALT = 0x53EC_7104L
}
