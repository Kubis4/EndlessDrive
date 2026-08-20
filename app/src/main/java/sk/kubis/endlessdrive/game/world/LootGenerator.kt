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
        repeat(count) {
            // Pri duplicite skús ešte raz iný los – zahodiť ťah znamenalo, že
            // budova mala menej vecí práve vtedy, keď padla tá istá dvakrát.
            var def = weightedPick(rng, table, distBonus, distance) ?: return@repeat
            if (def.fluid == null && def.id in seen) {
                def = weightedPick(rng, table, distBonus, distance) ?: def
            }
            // Dva rovnaké diely v jednej budove nedávajú zmysel; kanistre áno.
            if (def.fluid == null && !seen.add(def.id)) return@repeat
            result += makeStack(rng, type, def, distance)
        }

        // Každá budova na začiatku aspoň jedna „užitočná“ vec (kvapalina / diel).
        if (result.isEmpty() && earlyFloor > 0) {
            val fallback = when (type) {
                BuildingType.HOUSE, BuildingType.GARAGE ->
                    listOf(ItemCatalog.OIL_BOTTLE, ItemCatalog.FUEL_CAN, ItemCatalog.COOLANT_BOTTLE)
                BuildingType.GAS_STATION -> listOf(ItemCatalog.FUEL_CAN)
                BuildingType.AUTO_SHOP ->
                    listOf(ItemCatalog.TIRE, ItemCatalog.BATTERY, ItemCatalog.OIL_BOTTLE)
            }
            result += makeStack(rng, type, rng.pick(fallback), distance)
        }

        // Kontrola musela byť na palivo, nie na „hocijakú kvapalinu“: stanica,
        // ktorá si vylosovala olej a chladiacu, prešla ako zásobená a hráč
        // dorazil na benzínku, kde benzín nebol vôbec.
        if (type == BuildingType.GAS_STATION &&
            result.none { it.def.fluid == FluidType.FUEL }
        ) {
            result.add(
                ItemStack(
                    defId = ItemCatalog.FUEL_CAN.id,
                    condition = ComponentCondition.NEW,
                    health = 1f,
                    count = 1,
                    purity = fluidPurity(rng, type, ItemCatalog.FUEL_CAN)
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
        return ItemStack(def.id, condition, health)
    }

    /** Kanister z domu je zvyčajne riedený, zo servisu takmer čistý. Voda je vždy voda. */
    private fun fluidPurity(rng: SeededRandom, type: BuildingType, def: ItemDef? = null): Float {
        if (def?.id == ItemCatalog.WATER.id) return rng.nextFloat(0.02f, 0.10f)
        val base = when (type) {
            BuildingType.HOUSE -> rng.nextFloat(0.40f, 0.88f)
            BuildingType.GARAGE -> rng.nextFloat(0.55f, 0.95f)
            BuildingType.GAS_STATION -> rng.nextFloat(0.70f, 1.0f)
            BuildingType.AUTO_SHOP -> rng.nextFloat(0.75f, 1.0f)
        }
        return if (rng.chance(0.08f)) base * rng.nextFloat(0.4f, 0.7f) else base
    }

    private fun tableFor(type: BuildingType): List<Entry> = when (type) {
        // Dom je najčastejšia budova, takže práve on určuje, či sa loot opakuje.
        // Kvapaliny tu mali 64 % váhy a vypadávali stále dokola – teraz je
        // z domu skôr zmes haraburdia než ďalší kanister.
        BuildingType.HOUSE -> listOf(
            Entry(ItemCatalog.FUEL_CAN, 11f),
            Entry(ItemCatalog.OIL_BOTTLE, 12f),
            Entry(ItemCatalog.COOLANT_BOTTLE, 10f),
            Entry(ItemCatalog.WATER, 7f),
            Entry(ItemCatalog.HOOD, 9f),
            Entry(ItemCatalog.DOORS, 7f),
            Entry(ItemCatalog.WINDOWS, 7f),
            Entry(ItemCatalog.BACKPACK, 8f),
            Entry(ItemCatalog.BOOT_CRATE, 5f),
            Entry(ItemCatalog.ROOF_RACK, 4f),
            Entry(ItemCatalog.FRONT_BUMPER, 7f),
            Entry(ItemCatalog.TRUNK_LID, 7f),
            Entry(ItemCatalog.HEADLIGHT, 6f),
            Entry(ItemCatalog.TAILLIGHT, 7f),
            Entry(ItemCatalog.SEAT_FRONT, 6f),
            Entry(ItemCatalog.SEAT_REAR, 6f),
            Entry(ItemCatalog.REAR_BUMPER, 7f),
            Entry(ItemCatalog.TIRE_POOR, 8f),
            Entry(ItemCatalog.TIRE, 5f),
            Entry(ItemCatalog.BATTERY, 5f),
            Entry(ItemCatalog.SNOW_CHAINS, 3f),
            Entry(ItemCatalog.ALTERNATOR, 4f),
            Entry(ItemCatalog.BRAKES, 4f)
        )
        BuildingType.GARAGE -> listOf(
            Entry(ItemCatalog.FUEL_CAN, 25f),
            Entry(ItemCatalog.OIL_BOTTLE, 20f),
            Entry(ItemCatalog.COOLANT_BOTTLE, 18f),
            Entry(ItemCatalog.TIRE_POOR, 5f),
            Entry(ItemCatalog.TIRE, 8f),
            Entry(ItemCatalog.TIRE_SPORT, 2f),
            Entry(ItemCatalog.TIRE_OFFROAD, 2f),
            Entry(ItemCatalog.TIRE_WINTER, 4f),
            Entry(ItemCatalog.SNOW_CHAINS, 4f),
            Entry(ItemCatalog.BOOT_CRATE, 6f),
            Entry(ItemCatalog.ROOF_RACK, 4f),
            Entry(ItemCatalog.SUSPENSION, 4f),
            Entry(ItemCatalog.SUSPENSION_LOW, 2f),
            Entry(ItemCatalog.DRIVE_RWD, 3f),
            Entry(ItemCatalog.DRIVE_FWD, 2f),
            Entry(ItemCatalog.BATTERY, 7f),
            Entry(ItemCatalog.BRAKES, 5f),
            Entry(ItemCatalog.RADIATOR, 5f),
            Entry(ItemCatalog.ALTERNATOR, 5f),
            Entry(ItemCatalog.DOORS, 8f),
            Entry(ItemCatalog.HOOD, 10f),
            Entry(ItemCatalog.WINDOWS, 6f),
            Entry(ItemCatalog.TRUNK_LID, 8f),
            Entry(ItemCatalog.HEADLIGHT, 7f),
            Entry(ItemCatalog.TAILLIGHT, 8f),
            Entry(ItemCatalog.SEAT_FRONT, 7f),
            Entry(ItemCatalog.SEAT_REAR, 6f),
            Entry(ItemCatalog.ENGINE_A, 2f)
        )
        BuildingType.GAS_STATION -> listOf(
            Entry(ItemCatalog.FUEL_CAN, 45f),
            Entry(ItemCatalog.OIL_BOTTLE, 20f),
            Entry(ItemCatalog.COOLANT_BOTTLE, 20f),
            Entry(ItemCatalog.TIRE_POOR, 5f),
            Entry(ItemCatalog.TIRE, 6f),
            Entry(ItemCatalog.BATTERY, 5f),
            Entry(ItemCatalog.FUEL_TANK, 3f)
        )
        BuildingType.AUTO_SHOP -> listOf(
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
            Entry(ItemCatalog.SNOW_CHAINS, 5f),
            Entry(ItemCatalog.BOOT_CRATE, 5f),
            Entry(ItemCatalog.ROOF_RACK, 5f),
            Entry(ItemCatalog.SUSPENSION_GOOD, 4f),
            Entry(ItemCatalog.SUSPENSION_LIFT, 3f),
            Entry(ItemCatalog.SUSPENSION_LOW, 3f),
            Entry(ItemCatalog.DRIVE_AWD, 3f),
            Entry(ItemCatalog.DRIVE_FWD, 3f),
            Entry(ItemCatalog.FUEL_TANK_BIG, 3f),
            Entry(ItemCatalog.DOORS, 10f),
            Entry(ItemCatalog.HOOD, 8f),
            Entry(ItemCatalog.WINDOWS, 10f),
            Entry(ItemCatalog.FRONT_BUMPER, 7f),
            Entry(ItemCatalog.TRUNK_LID, 7f),
            Entry(ItemCatalog.HEADLIGHT, 6f),
            Entry(ItemCatalog.TAILLIGHT, 7f),
            Entry(ItemCatalog.SEAT_FRONT, 6f),
            Entry(ItemCatalog.SEAT_REAR, 6f),
            Entry(ItemCatalog.REAR_BUMPER, 7f),
            Entry(ItemCatalog.OIL_BOTTLE, 8f)
        )
    }

    private fun weightedPick(
        rng: SeededRandom,
        table: List<Entry>,
        distBonus: Float,
        distance: Float
    ): ItemDef? {
        if (table.isEmpty()) return null
        // Na začiatku cesty drž rare/legendary nízko – žiadny „jackpot dom“ hneď.
        val earlySuppress = (1f - (distance / 1800f).coerceIn(0f, 1f))
        var total = 0f
        val adjusted = table.map { e ->
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
            val w = e.weight * rarityBoost.coerceAtMost(3.2f)
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
