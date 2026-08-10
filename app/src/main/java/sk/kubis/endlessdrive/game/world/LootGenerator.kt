package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.core.SeededRandom
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemRarity
import sk.kubis.endlessdrive.domain.model.ItemStack

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
        // Obsah budovy je lotéria: väčšina je priemer, občas vykradnutá ruina
        // a raz za čas poriadny sklad.
        val luck = rng.nextFloat()
        val spread = when {
            luck < 0.16f -> 0f          // niekto tu už bol
            luck < 0.72f -> 1f          // bežný nález
            luck < 0.93f -> 1.8f        // dobrý deň
            else -> 2.6f                // jackpot
        }
        val base = when (type) {
            BuildingType.HOUSE -> 2f
            BuildingType.GARAGE -> 3f
            BuildingType.GAS_STATION -> 3f
            BuildingType.AUTO_SHOP -> 3.5f
        }
        val count = (base * spread * rng.nextFloat(0.7f, 1.25f) + distBonus * 0.6f)
            .toInt()
            .coerceIn(0, 8)
        val result = ArrayList<ItemStack>(count)
        repeat(count) {
            val def = weightedPick(rng, table, distBonus) ?: return@repeat
            if (def.fluid != null) {
                // Kvapaliny sa nekazia – bývajú riedené. Kvalita závisí od miesta nálezu.
                result.add(
                    ItemStack(
                        defId = def.id,
                        condition = ComponentCondition.NEW,
                        health = 1f,
                        count = 1,
                        purity = fluidPurity(rng, type)
                    )
                )
                return@repeat
            }
            val condition = when {
                rng.chance(0.15f) -> ComponentCondition.NEW
                rng.chance(0.45f) -> ComponentCondition.USED
                rng.chance(0.70f) -> ComponentCondition.DAMAGED
                else -> ComponentCondition.CRITICAL
            }
            result.add(ItemStack(def.id, condition, condition.maxHealth * rng.nextFloat(0.7f, 1f)))
        }
        if (type == BuildingType.GAS_STATION && count > 0 && result.none { it.def.fluid != null }) {
            result.add(
                ItemStack(
                    defId = ItemCatalog.FUEL_CAN.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = 1 + rng.nextInt(2),
                    purity = fluidPurity(rng, type)
                )
            )
        }
        return result
    }

    /** Kanister z domu je zvyčajne riedený, zo servisu takmer čistý. */
    private fun fluidPurity(rng: SeededRandom, type: BuildingType): Float {
        val base = when (type) {
            BuildingType.HOUSE -> rng.nextFloat(0.35f, 0.85f)
            BuildingType.GARAGE -> rng.nextFloat(0.55f, 0.95f)
            BuildingType.GAS_STATION -> rng.nextFloat(0.70f, 1.0f)
            BuildingType.AUTO_SHOP -> rng.nextFloat(0.75f, 1.0f)
        }
        // Občas sa nájde plechovka, ktorá je naozaj len voda.
        return if (rng.chance(0.10f)) base * rng.nextFloat(0.4f, 0.7f) else base
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
