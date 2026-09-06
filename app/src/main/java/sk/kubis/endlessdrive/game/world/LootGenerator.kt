package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.core.MathX
import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemRarity
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.VehiclePaint

object LootGenerator {
    private data class Entry(val def: ItemDef, val weight: Float)

    fun generate(
        rng: SeededRandom,
        type: BuildingType,
        distance: Float,
        style: BranchStyle
    ): List<ItemStack> {
        val table = tableFor(type)
        // Bez stropu: aj na 30. km je nález o kúsok lepší než na 10.
        val distNorm = MathX.growth(distance, 3500f)
        val distBonus = distNorm * style.lootBias
        // Miernejšia lotéria: väčšina budov má 1–3 veci, prázdne / jackpoty sú vzácne.
        val luck = rng.nextFloat()
        val baseCount = when (type) {
            BuildingType.HOUSE -> when {
                luck < 0.05f -> 0
                luck < 0.28f -> 1
                luck < 0.72f -> 2
                luck < 0.92f -> 3
                else -> 4
            }
            BuildingType.GARAGE -> when {
                luck < 0.04f -> 0
                luck < 0.22f -> 1
                luck < 0.62f -> 2
                luck < 0.88f -> 3
                else -> 4
            }
            BuildingType.GAS_STATION -> when {
                luck < 0.03f -> 1
                luck < 0.45f -> 2
                luck < 0.82f -> 3
                else -> 4
            }
            BuildingType.AUTO_SHOP -> when {
                luck < 0.02f -> 1
                luck < 0.35f -> 2
                luck < 0.75f -> 3
                luck < 0.92f -> 4
                else -> 5
            }
            BuildingType.WRECK -> when {
                luck < 0.48f -> 0
                luck < 0.82f -> 1
                else -> 2
            }
        }
        // Na začiatku cesty budovy nie sú prázdne – hráč musí mať čo nájsť.
        val earlyFloor = when {
            distance < 500f -> 1
            distance < 1200f && type != BuildingType.HOUSE -> 1
            else -> 0
        }
        val count = (baseCount + (distBonus * 0.35f).toInt())
            .coerceAtLeast(earlyFloor)
            .coerceIn(0, 6)

        val result = ArrayList<ItemStack>(count + 1)
        val seen = HashSet<String>()
        val families = HashSet<String>()
        repeat(count) {
            // Už vylosované veci sa vyradia a rovnaká rodina sa výrazne tlmí:
            // namiesto troch kvapalín či troch plechov má budova zmiešaný obsah.
            val def = weightedPick(rng, table, distBonus, distance, seen, families)
                ?: return@repeat
            seen += def.id
            families += familyOf(def)
            result += makeStack(rng, type, def, distance)
        }

        // Dlhá jazda má rotujúci „zaujímavý kus“. Nie je v každej budove,
        // ale nemení sa na nekonečné opakovanie oleja a základnej pneumatiky.
        if (distance >= 1200f && result.isNotEmpty()) {
            val bucket = kotlin.math.floor(distance / 650f).toInt()
            val spotlightPool = when (type) {
                BuildingType.HOUSE -> listOf(
                    ItemCatalog.SEAT_REAR, ItemCatalog.DOOR_REAR, ItemCatalog.TRUNK_LID,
                    ItemCatalog.BACKPACK, ItemCatalog.SEAT_FRONT, ItemCatalog.HEADLIGHT,
                    ItemCatalog.REAR_BUMPER, ItemCatalog.ROOF_RACK
                )
                BuildingType.GARAGE -> listOf(
                    ItemCatalog.SEAT_REAR, ItemCatalog.SNOW_CHAINS, ItemCatalog.TIRE_WINTER,
                    ItemCatalog.SUSPENSION_LOW, ItemCatalog.TIRE_OFFROAD, ItemCatalog.BOOT_CRATE,
                    ItemCatalog.ALTERNATOR, ItemCatalog.ROOF_RACK
                )
                BuildingType.AUTO_SHOP -> listOf(
                    ItemCatalog.SNOW_CHAINS, ItemCatalog.TIRE_WINTER, ItemCatalog.SEAT_REAR,
                    ItemCatalog.DRIVE_AWD, ItemCatalog.SUSPENSION_LIFT, ItemCatalog.BRAKES_GOOD,
                    ItemCatalog.BATTERY_GOOD, ItemCatalog.TIRE_SPORT
                )
                BuildingType.GAS_STATION -> emptyList()
                BuildingType.WRECK -> emptyList()
            }
            var spotlight = spotlightPool.getOrNull((bucket + type.ordinal) % spotlightPool.size.coerceAtLeast(1))
            if (spotlight?.let { it.id == ItemCatalog.SNOW_CHAINS.id || it.id == ItemCatalog.TIRE_WINTER.id } == true &&
                distance < GameConfig.WINTER_GEAR_FROM_M
            ) {
                spotlight = ItemCatalog.SEAT_REAR
            }
            val useSpotlight = spotlight != null && spotlight.id !in seen &&
                (result.size >= 3 || MathX.hash01(bucket, type.ordinal + 4401) < 0.34f)
            if (useSpotlight) {
                result[result.lastIndex] = makeStack(rng, type, spotlight!!, distance)
            }
        }

        // Každá budova na začiatku aspoň jedna „užitočná“ vec (kvapalina / diel).
        if (result.isEmpty() && earlyFloor > 0) {
            val fallback = when (type) {
                BuildingType.HOUSE, BuildingType.GARAGE ->
                    listOf(ItemCatalog.OIL_BOTTLE, ItemCatalog.FUEL_CAN, ItemCatalog.DIESEL_CAN, ItemCatalog.COOLANT_BOTTLE)
                BuildingType.GAS_STATION -> listOf(ItemCatalog.FUEL_CAN, ItemCatalog.DIESEL_CAN)
                BuildingType.AUTO_SHOP ->
                    listOf(ItemCatalog.TIRE, ItemCatalog.BATTERY, ItemCatalog.OIL_BOTTLE)
                BuildingType.WRECK -> listOf(ItemCatalog.SCRAP_PILE)
            }
            result += makeStack(rng, type, rng.pick(fallback), distance)
        }

        // Kontrola musela byť na palivo, nie na „hocijakú kvapalinu“: stanica,
        // ktorá si vylosovala olej a chladiacu, prešla ako zásobená a hráč
        // dorazil na benzínku, kde benzín nebol vôbec.
        if (type == BuildingType.GAS_STATION &&
            result.none { it.def.fluid == FluidType.FUEL }
        ) {
            val fuel = if (rng.chance(0.42f)) ItemCatalog.DIESEL_CAN else ItemCatalog.FUEL_CAN
            result.add(
                ItemStack(
                    defId = fuel.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = 1,
                    purity = fluidPurity(rng, type, fuel)
                )
            )
        }
        return result
    }

    private fun makeStack(
        rng: SeededRandom,
        type: BuildingType,
        def: ItemDef,
        distance: Float
    ): ItemStack {
        if (def.id == ItemCatalog.SCRAP_PILE.id) {
            return ItemStack(
                defId = def.id,
                condition = ComponentCondition.NEW,
                health = 1f,
                count = 2 + rng.nextInt(3)
            )
        }
        if (def.id == ItemCatalog.PUNCTURE_KIT.id) {
            return ItemStack(
                defId = def.id,
                condition = ComponentCondition.NEW,
                health = 1f,
                count = 1
            )
        }
        if (def.fluid != null) {
            return ItemStack(
                defId = def.id,
                condition = ComponentCondition.NEW,
                health = 1f,
                count = 1,
                purity = fluidPurity(rng, type, def)
            )
        }
        // Skôr ojazdené diely; ďalej od štartu sa dajú nájsť aj slušné kusy.
        val early = (1f - (distance / 2000f).coerceIn(0f, 1f))
        val late = MathX.growth(distance, 6000f).coerceAtMost(1.4f)
        val roll = rng.nextFloat()
        val condition = when {
            roll < 0.08f + early * 0.04f + late * 0.10f -> ComponentCondition.NEW
            roll < 0.48f + early * 0.12f + late * 0.10f -> ComponentCondition.USED
            roll < 0.82f -> ComponentCondition.DAMAGED
            else -> ComponentCondition.CRITICAL
        }
        val health = condition.maxHealth * rng.nextFloat(0.72f, 1f)
        val paintIndex = if (def.mountsTo?.takesBodyPaint == true) {
            rng.nextInt(VehiclePaint.entries.size)
        } else -1
        return ItemStack(def.id, condition, health, paintIndex = paintIndex)
    }

    /** Kanister z domu je zvyčajne riedený, zo servisu takmer čistý. Voda je vždy voda. */
    private fun fluidPurity(rng: SeededRandom, type: BuildingType, def: ItemDef? = null): Float {
        if (def?.id == ItemCatalog.WATER.id) return rng.nextFloat(0.02f, 0.10f)
        val base = when (type) {
            BuildingType.HOUSE -> rng.nextFloat(0.40f, 0.88f)
            BuildingType.GARAGE -> rng.nextFloat(0.55f, 0.95f)
            BuildingType.GAS_STATION -> rng.nextFloat(0.70f, 1.0f)
            BuildingType.AUTO_SHOP -> rng.nextFloat(0.75f, 1.0f)
            BuildingType.WRECK -> rng.nextFloat(0.40f, 0.80f)
        }
        return if (rng.chance(0.08f)) base * rng.nextFloat(0.4f, 0.7f) else base
    }

    private fun tableFor(type: BuildingType): List<Entry> = when (type) {
        // Dom je najčastejšia budova, takže práve on určuje, či sa loot opakuje.
        // Kvapaliny tu mali 64 % váhy a vypadávali stále dokola – teraz je
        // z domu skôr zmes haraburdia než ďalší kanister.
        BuildingType.HOUSE -> listOf(
            Entry(ItemCatalog.FUEL_CAN, 11f),
            Entry(ItemCatalog.DIESEL_CAN, 7f),
            Entry(ItemCatalog.OIL_BOTTLE, 12f),
            Entry(ItemCatalog.COOLANT_BOTTLE, 10f),
            Entry(ItemCatalog.WATER, 3f),
            Entry(ItemCatalog.HOOD, 9f),
            Entry(ItemCatalog.DOOR_FRONT, 4f),
            Entry(ItemCatalog.DOOR_REAR, 5f),
            Entry(ItemCatalog.BACKPACK, 8f),
            Entry(ItemCatalog.BOOT_CRATE, 5f),
            Entry(ItemCatalog.ROOF_RACK, 4f),
            Entry(ItemCatalog.EXPEDITION_RACK, 2f),
            Entry(ItemCatalog.FRONT_BUMPER, 7f),
            Entry(ItemCatalog.TRUNK_LID, 7f),
            Entry(ItemCatalog.HEADLIGHT, 6f),
            Entry(ItemCatalog.TAILLIGHT, 7f),
            Entry(ItemCatalog.SEAT_FRONT, 6f),
            Entry(ItemCatalog.SEAT_REAR, 10f),
            Entry(ItemCatalog.REAR_BUMPER, 7f),
            Entry(ItemCatalog.TIRE_POOR, 8f),
            Entry(ItemCatalog.TIRE, 5f),
            Entry(ItemCatalog.BATTERY, 5f),
            Entry(ItemCatalog.SNOW_CHAINS, 3f),
            Entry(ItemCatalog.PUNCTURE_KIT, 4f),
            Entry(ItemCatalog.ALTERNATOR, 4f),
            Entry(ItemCatalog.BRAKES, 4f)
        )
        BuildingType.GARAGE -> listOf(
            Entry(ItemCatalog.FUEL_CAN, 14f),
            Entry(ItemCatalog.DIESEL_CAN, 10f),
            Entry(ItemCatalog.OIL_BOTTLE, 12f),
            Entry(ItemCatalog.COOLANT_BOTTLE, 10f),
            Entry(ItemCatalog.TIRE_POOR, 5f),
            Entry(ItemCatalog.TIRE, 8f),
            Entry(ItemCatalog.TIRE_SPORT, 2f),
            Entry(ItemCatalog.TIRE_OFFROAD, 2f),
            Entry(ItemCatalog.TIRE_WINTER, 4f),
            Entry(ItemCatalog.SNOW_CHAINS, 7f),
            Entry(ItemCatalog.PUNCTURE_KIT, 8f),
            Entry(ItemCatalog.BOOT_CRATE, 6f),
            Entry(ItemCatalog.ROOF_RACK, 4f),
            Entry(ItemCatalog.EXPEDITION_RACK, 2f),
            Entry(ItemCatalog.SUSPENSION, 4f),
            Entry(ItemCatalog.SUSPENSION_LOW, 2f),
            Entry(ItemCatalog.DRIVE_RWD, 3f),
            Entry(ItemCatalog.DRIVE_FWD, 2f),
            Entry(ItemCatalog.BATTERY, 7f),
            Entry(ItemCatalog.BRAKES, 5f),
            Entry(ItemCatalog.RADIATOR, 5f),
            Entry(ItemCatalog.ALTERNATOR, 5f),
            Entry(ItemCatalog.DOOR_FRONT, 5f),
            Entry(ItemCatalog.DOOR_REAR, 6f),
            Entry(ItemCatalog.HOOD, 10f),
            Entry(ItemCatalog.TRUNK_LID, 8f),
            Entry(ItemCatalog.HEADLIGHT, 7f),
            Entry(ItemCatalog.TAILLIGHT, 8f),
            Entry(ItemCatalog.SEAT_FRONT, 7f),
            Entry(ItemCatalog.SEAT_REAR, 10f),
            Entry(ItemCatalog.ENGINE_A, 2f)
        )
        BuildingType.GAS_STATION -> listOf(
            Entry(ItemCatalog.FUEL_CAN, 28f),
            Entry(ItemCatalog.DIESEL_CAN, 22f),
            Entry(ItemCatalog.OIL_BOTTLE, 20f),
            Entry(ItemCatalog.COOLANT_BOTTLE, 20f),
            Entry(ItemCatalog.TIRE_POOR, 5f),
            Entry(ItemCatalog.TIRE, 6f),
            Entry(ItemCatalog.BATTERY, 5f),
            Entry(ItemCatalog.PUNCTURE_KIT, 10f),
            Entry(ItemCatalog.FUEL_TANK, 3f)
        )
        BuildingType.AUTO_SHOP -> listOf(
            Entry(ItemCatalog.FUEL_CAN, 6f),
            Entry(ItemCatalog.DIESEL_CAN, 7f),
            Entry(ItemCatalog.ENGINE_A, 8f),
            Entry(ItemCatalog.ENGINE_D, 6f),
            Entry(ItemCatalog.ENGINE_B, 4f),
            Entry(ItemCatalog.ENGINE_E, 3f),
            Entry(ItemCatalog.RADIATOR, 12f),
            Entry(ItemCatalog.RADIATOR_GOOD, 5f),
            Entry(ItemCatalog.BRAKES, 12f),
            Entry(ItemCatalog.BRAKES_GOOD, 5f),
            Entry(ItemCatalog.BATTERY, 10f),
            Entry(ItemCatalog.BATTERY_GOOD, 4f),
            Entry(ItemCatalog.ALTERNATOR, 8f),
            Entry(ItemCatalog.TIRE_POOR, 3f),
            Entry(ItemCatalog.TIRE, 7f),
            Entry(ItemCatalog.TIRE_SPORT, 5f),
            Entry(ItemCatalog.TIRE_OFFROAD, 5f),
            Entry(ItemCatalog.TIRE_WINTER, 6f),
            Entry(ItemCatalog.SNOW_CHAINS, 8f),
            Entry(ItemCatalog.PUNCTURE_KIT, 11f),
            Entry(ItemCatalog.BOOT_CRATE, 5f),
            Entry(ItemCatalog.ROOF_RACK, 5f),
            Entry(ItemCatalog.EXPEDITION_RACK, 3f),
            Entry(ItemCatalog.SUSPENSION_GOOD, 4f),
            Entry(ItemCatalog.SUSPENSION_LIFT, 3f),
            Entry(ItemCatalog.SUSPENSION_LOW, 3f),
            Entry(ItemCatalog.DRIVE_AWD, 3f),
            Entry(ItemCatalog.DRIVE_FWD, 3f),
            Entry(ItemCatalog.FUEL_TANK_BIG, 3f),
            Entry(ItemCatalog.DOOR_FRONT, 6f),
            Entry(ItemCatalog.DOOR_REAR, 7f),
            Entry(ItemCatalog.HOOD, 8f),
            Entry(ItemCatalog.FRONT_BUMPER, 7f),
            Entry(ItemCatalog.TRUNK_LID, 7f),
            Entry(ItemCatalog.HEADLIGHT, 6f),
            Entry(ItemCatalog.TAILLIGHT, 7f),
            Entry(ItemCatalog.SEAT_FRONT, 6f),
            Entry(ItemCatalog.SEAT_REAR, 9f),
            Entry(ItemCatalog.REAR_BUMPER, 7f),
            Entry(ItemCatalog.OIL_BOTTLE, 8f),
            Entry(ItemCatalog.SCRAP_PILE, 9f)
        )
        BuildingType.WRECK -> listOf(
            Entry(ItemCatalog.SCRAP_PILE, 18f),
            Entry(ItemCatalog.TIRE_POOR, 6f),
            Entry(ItemCatalog.PUNCTURE_KIT, 3f),
            Entry(ItemCatalog.BATTERY, 4f),
            Entry(ItemCatalog.HOOD, 5f),
            Entry(ItemCatalog.DOOR_FRONT, 4f)
        )
    }

    private fun weightedPick(
        rng: SeededRandom,
        table: List<Entry>,
        distBonus: Float,
        distance: Float,
        excludedIds: Set<String> = emptySet(),
        usedFamilies: Set<String> = emptySet()
    ): ItemDef? {
        if (table.isEmpty()) return null
        // Na začiatku cesty drž rare/legendary nízko – žiadny „jackpot dom“ hneď.
        val earlySuppress = (1f - (distance / 1800f).coerceIn(0f, 1f))
        var total = 0f
        val adjusted = table.map { e ->
            if (e.def.id in excludedIds) return@map e to 0f
            var rarityBoost = when (e.def.rarity) {
                ItemRarity.COMMON -> 1f + earlySuppress * 0.15f
                ItemRarity.UNCOMMON -> 1f + distBonus * 0.15f - earlySuppress * 0.25f
                ItemRarity.RARE -> (1f + distBonus * 0.45f) * (1f - earlySuppress * 0.75f)
                ItemRarity.VERY_RARE -> (1f + distBonus * 0.85f) * (1f - earlySuppress * 0.9f)
                ItemRarity.LEGENDARY -> (1f + distBonus * 1.1f) * (1f - earlySuppress * 0.95f)
            }.coerceAtLeast(0.05f)
            // 4×4 je až neskorší upgrade – na začiatku cesty prakticky nevypadne.
            if (e.def.id == ItemCatalog.DRIVE_AWD.id) {
                rarityBoost *= MathX.growth(distance, 2800f).coerceIn(0.02f, 1.6f)
            }
            // Zimná výbava sa objaví o kúsok skôr, než začne mrznúť – aby sa
            // hráč stihol pripraviť, nie aby ju zháňal už v snehu.
            if (e.def.id == ItemCatalog.TIRE_WINTER.id || e.def.id == ItemCatalog.SNOW_CHAINS.id) {
                // Strop 1.35, nie 2.2: zimná výbava sa má dať nájsť, nie
                // vytlačiť všetko ostatné. Pri 2.2 sa v neskorších budovách
                // nachádzali prakticky len snehové gumy.
                rarityBoost *= if (distance < GameConfig.WINTER_GEAR_FROM_M) 0f
                else MathX.growth(distance - GameConfig.WINTER_GEAR_FROM_M, 5000f)
                    .coerceIn(0.15f, 1.35f)
            }
            // Žiadna položka nesmie prerásť ostatné natoľko, že budova dá
            // stále to isté – vzdialenosť má loot vylepšovať, nie zúžiť.
            val familyMul = if (familyOf(e.def) in usedFamilies) 0.28f else 1f
            val w = e.weight * rarityBoost.coerceAtMost(3.2f) * familyMul
            total += w
            e to w
        }
        if (total <= 0f) return null
        var roll = rng.nextFloat() * total
        for ((e, w) in adjusted) {
            roll -= w
            if (roll <= 0f) return e.def
        }
        return table.last().def
    }

    private fun familyOf(def: ItemDef): String = when {
        def.id == ItemCatalog.SCRAP_PILE.id -> "scrap"
        def.id == ItemCatalog.PUNCTURE_KIT.id -> "tool"
        def.fluid != null -> "fluid"
        def.axleTire -> "tyre"
        def.mountsTo in setOf(
            sk.kubis.endlessdrive.domain.model.ComponentSlot.DOOR_FRONT,
            sk.kubis.endlessdrive.domain.model.ComponentSlot.DOOR_REAR,
            sk.kubis.endlessdrive.domain.model.ComponentSlot.HOOD,
            sk.kubis.endlessdrive.domain.model.ComponentSlot.FRONT_BUMPER,
            sk.kubis.endlessdrive.domain.model.ComponentSlot.REAR_BUMPER,
            sk.kubis.endlessdrive.domain.model.ComponentSlot.TRUNK_LID
        ) -> "body"
        def.mountsTo == sk.kubis.endlessdrive.domain.model.ComponentSlot.SEAT_FRONT ||
            def.mountsTo == sk.kubis.endlessdrive.domain.model.ComponentSlot.SEAT_REAR -> "interior"
        def.extraSlots > 0 -> "storage"
        else -> "mechanical"
    }
}
