package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemRarity
import sk.kubis.endlessdrive.domain.model.ItemStack

data class BranchChoice(
    val id: Int,
    val style: BranchStyle,
    val segmentSeed: Long
) {
    val label: String get() = style.label
    val hint: String get() = style.hint
}

data class WorldBuilding(
    val id: Long,
    val type: BuildingType,
    val localX: Float,
    val loot: MutableList<ItemStack> = mutableListOf()
)

/**
 * Úsek cesty medzi križovatkami. Výška = [TerrainProfile] (HillRush kopce).
 */
class RoadSegment(
    val seed: Long,
    val style: BranchStyle,
    val length: Float,
    val buildings: List<WorldBuilding>,
    val choices: List<BranchChoice>,
    var worldOrigin: Float,
    private val terrain: TerrainProfile
) {
    val biome: BiomeType get() = style.biome
    val endWorldX: Float get() = worldOrigin + length

    fun heightAtLocal(localX: Float): Float =
        terrain.heightAt(worldOrigin + localX, style)

    fun heightAtWorld(worldX: Float): Float = terrain.heightAt(worldX, style)

    fun slopeAtLocal(localX: Float): Float =
        terrain.slopeAt(worldOrigin + localX, style)
}

object WorldGenerator {
    private const val MIX = -0x61C8864680B583EBL

    fun createSegment(
        segmentSeed: Long,
        style: BranchStyle,
        worldOrigin: Float,
        tripDistance: Float,
        terrain: TerrainProfile,
        isTutorial: Boolean = false
    ): RoadSegment {
        val rng = SeededRandom(segmentSeed)
        val baseLen = if (isTutorial) {
            GameConfig.TUTORIAL_SEGMENT_LENGTH
        } else {
            rng.nextFloat(GameConfig.SEGMENT_LENGTH_MIN, GameConfig.SEGMENT_LENGTH_MAX)
        }
        val length = (baseLen * style.lengthMul).coerceAtLeast(320f)

        val buildings = mutableListOf<WorldBuilding>()
        if (isTutorial) {
            val houseX = terrain.flattestLocalX(worldOrigin, style, 160f, 260f)
            val garageX = terrain.flattestLocalX(worldOrigin, style, 380f, 480f)
            buildings += makeBuilding(rng, BuildingType.HOUSE, houseX, tripDistance, style)
            val garage = makeBuilding(rng, BuildingType.GARAGE, garageX, tripDistance, style)
            garage.loot.add(0, ItemStack(ItemCatalog.DOORS.id, ComponentCondition.USED, 0.7f))
            garage.loot.add(1, ItemStack(ItemCatalog.HOOD.id, ComponentCondition.USED, 0.75f))
            garage.loot.add(2, ItemStack(ItemCatalog.WINDOWS.id, ComponentCondition.USED, 0.7f))
            buildings += garage
        } else {
            placeBuildings(rng, style, length, tripDistance, worldOrigin, terrain, buildings)
        }

        val choices = buildChoices(rng, segmentSeed, tripDistance + length, isTutorial)

        return RoadSegment(
            seed = segmentSeed,
            style = style,
            length = length,
            buildings = buildings,
            choices = choices,
            worldOrigin = worldOrigin,
            terrain = terrain
        )
    }

    private fun placeBuildings(
        rng: SeededRandom,
        style: BranchStyle,
        length: Float,
        tripDistance: Float,
        worldOrigin: Float,
        terrain: TerrainProfile,
        out: MutableList<WorldBuilding>
    ) {
        val usableEnd = length - GameConfig.JUNCTION_ZONE - 30f
        val minStart = GameConfig.BUILDING_MIN_GAP_FROM_START
        if (usableEnd <= minStart + 20f) return

        val density = style.buildingDensity
        // Po križovatke vždy aspoň 1 budova; hustota pridá druhú.
        val count = when {
            density >= 0.75f && rng.chance(0.55f) -> 2
            density >= 0.45f && rng.chance(0.35f) -> 2
            else -> 1
        }

        var cursor = minStart
        repeat(count) {
            val latest = usableEnd - (count - 1 - it) * GameConfig.BUILDING_MIN_SPACING
            if (cursor >= latest) return
            val windowEnd = latest.coerceAtLeast(cursor + 1f)
            // Preferuj rovinu v okne, nie náhodný bod na svahu.
            val lx = terrain.flattestLocalX(worldOrigin, style, cursor, windowEnd)
            out += makeBuilding(rng, weightedBuilding(rng, style, tripDistance), lx, tripDistance, style)
            cursor = lx + GameConfig.BUILDING_MIN_SPACING
        }
    }

    private fun buildChoices(
        rng: SeededRandom,
        parentSeed: Long,
        atDistance: Float,
        tutorial: Boolean
    ): List<BranchChoice> {
        if (tutorial) {
            return listOf(
                BranchChoice(0, BranchStyle.SAFE_RURAL, parentSeed xor 0xA11L xor MIX),
                BranchChoice(1, BranchStyle.INDUSTRIAL, parentSeed xor 0xB22L xor MIX),
                BranchChoice(2, BranchStyle.SHORTCUT_RISK, parentSeed xor 0xC33L xor MIX)
            )
        }
        val styles = BranchStyle.entries.toMutableList()
        val picked = mutableListOf<BranchStyle>()
        picked += BranchStyle.SAFE_RURAL
        styles.remove(BranchStyle.SAFE_RURAL)
        picked += rng.pick(styles)
        if (rng.chance(0.55f + (atDistance / 4000f).coerceAtMost(0.25f))) {
            styles.removeAll(picked.toSet())
            if (styles.isNotEmpty()) picked += rng.pick(styles)
        }
        return picked.mapIndexed { i, style ->
            BranchChoice(
                id = i,
                style = style,
                segmentSeed = parentSeed xor (style.ordinal + 1L) * MIX xor atDistance.toRawBits().toLong()
            )
        }
    }

    private fun weightedBuilding(rng: SeededRandom, style: BranchStyle, distance: Float): BuildingType {
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
        return WorldBuilding(
            id = id,
            type = type,
            localX = localX,
            loot = LootGenerator.generate(rng, type, distance, style).toMutableList()
        )
    }
}

object LootGenerator {
    private data class Entry(val def: ItemDef, val weight: Float)

    fun generate(
        rng: SeededRandom,
        type: BuildingType,
        distance: Float,
        style: BranchStyle
    ): List<ItemStack> {
        val table = tableFor(type)
        val distBonus = (distance / 800f).coerceIn(0f, 1.5f) * style.lootBias
        val count = when (type) {
            BuildingType.HOUSE -> rng.nextInt(2) + 1
            BuildingType.GARAGE -> rng.nextInt(3) + 2
            BuildingType.GAS_STATION -> rng.nextInt(3) + 2
            BuildingType.AUTO_SHOP -> rng.nextInt(3) + 2
        }
        val result = ArrayList<ItemStack>(count)
        repeat(count) {
            val def = weightedPick(rng, table, distBonus) ?: return@repeat
            val condition = when {
                rng.chance(0.15f) -> ComponentCondition.NEW
                rng.chance(0.45f) -> ComponentCondition.USED
                rng.chance(0.70f) -> ComponentCondition.DAMAGED
                else -> ComponentCondition.CRITICAL
            }
            result.add(ItemStack(def.id, condition, condition.maxHealth * rng.nextFloat(0.7f, 1f)))
        }
        if (type == BuildingType.GAS_STATION && result.none { it.def.fluid != null }) {
            result.add(ItemStack(ItemCatalog.FUEL_CAN.id, count = 1 + rng.nextInt(2)))
        }
        return result
    }

    private fun tableFor(type: BuildingType): List<Entry> = when (type) {
        BuildingType.HOUSE -> listOf(
            Entry(ItemCatalog.FUEL_CAN, 18f),
            Entry(ItemCatalog.OIL_BOTTLE, 22f),
            Entry(ItemCatalog.COOLANT_BOTTLE, 18f),
            Entry(ItemCatalog.HOOD, 8f),
            Entry(ItemCatalog.FRONT_BUMPER, 6f),
            Entry(ItemCatalog.REAR_BUMPER, 6f),
            Entry(ItemCatalog.TIRE, 6f),
            Entry(ItemCatalog.BATTERY, 4f)
        )
        BuildingType.GARAGE -> listOf(
            Entry(ItemCatalog.FUEL_CAN, 25f),
            Entry(ItemCatalog.OIL_BOTTLE, 20f),
            Entry(ItemCatalog.COOLANT_BOTTLE, 18f),
            Entry(ItemCatalog.TIRE, 10f),
            Entry(ItemCatalog.BATTERY, 7f),
            Entry(ItemCatalog.BRAKES, 5f),
            Entry(ItemCatalog.RADIATOR, 5f),
            Entry(ItemCatalog.DOORS, 8f),
            Entry(ItemCatalog.HOOD, 10f),
            Entry(ItemCatalog.WINDOWS, 6f),
            Entry(ItemCatalog.ENGINE_A, 2f)
        )
        BuildingType.GAS_STATION -> listOf(
            Entry(ItemCatalog.FUEL_CAN, 45f),
            Entry(ItemCatalog.OIL_BOTTLE, 20f),
            Entry(ItemCatalog.COOLANT_BOTTLE, 20f),
            Entry(ItemCatalog.TIRE, 8f),
            Entry(ItemCatalog.BATTERY, 5f),
            Entry(ItemCatalog.FUEL_TANK, 3f)
        )
        BuildingType.AUTO_SHOP -> listOf(
            Entry(ItemCatalog.ENGINE_A, 10f),
            Entry(ItemCatalog.ENGINE_B, 4f),
            Entry(ItemCatalog.RADIATOR, 12f),
            Entry(ItemCatalog.RADIATOR_GOOD, 5f),
            Entry(ItemCatalog.BRAKES, 12f),
            Entry(ItemCatalog.BRAKES_GOOD, 5f),
            Entry(ItemCatalog.BATTERY, 10f),
            Entry(ItemCatalog.BATTERY_GOOD, 4f),
            Entry(ItemCatalog.TIRE_GOOD, 8f),
            Entry(ItemCatalog.FUEL_TANK_BIG, 3f),
            Entry(ItemCatalog.DOORS, 10f),
            Entry(ItemCatalog.HOOD, 8f),
            Entry(ItemCatalog.WINDOWS, 10f),
            Entry(ItemCatalog.FRONT_BUMPER, 7f),
            Entry(ItemCatalog.REAR_BUMPER, 7f),
            Entry(ItemCatalog.OIL_BOTTLE, 8f)
        )
    }

    private fun weightedPick(rng: SeededRandom, table: List<Entry>, distBonus: Float): ItemDef? {
        if (table.isEmpty()) return null
        var total = 0f
        val adjusted = table.map { e ->
            val rarityBoost = when (e.def.rarity) {
                ItemRarity.COMMON -> 1f
                ItemRarity.UNCOMMON -> 1f + distBonus * 0.2f
                ItemRarity.RARE -> 1f + distBonus * 0.5f
                ItemRarity.VERY_RARE -> 1f + distBonus * 0.9f
                ItemRarity.LEGENDARY -> 1f + distBonus * 1.2f
            }
            val w = e.weight * rarityBoost
            total += w
            e to w
        }
        var roll = rng.nextFloat() * total
        for ((e, w) in adjusted) {
            roll -= w
            if (roll <= 0f) return e.def
        }
        return table.last().def
    }
}
