package sk.kubis.endlessdrive

import org.junit.Test
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.game.world.TerrainProfile
import sk.kubis.endlessdrive.game.world.WorldGenerator

class AuditSimulationTest {
    private data class FuelEvent(val distance: Float, val supplyL: Float, val drainMul: Float)

    @Test
    fun simulateLootFocusedRuns() {
        val maxDistance = 25_000f
        val runs = 500
        val bucketEdges = listOf(0f, 2_000f, 5_000f, 8_000f, 11_000f, 16_000f, maxDistance)
        val buildingBuckets = IntArray(bucketEdges.size - 1)
        val lootBuckets = IntArray(bucketEdges.size - 1)
        val buildingTypeCounts = IntArray(BuildingType.entries.size)
        val gaps = ArrayList<Float>()
        val runDistances = ArrayList<Float>()
        var firstWinterGear = 0
        var runsWithDepot = 0
        var totalDepots = 0
        var totalLoot = 0
        var totalBuildings = 0
        var longestGap = 0f
        var longestGapSeed = 0L
        var longestGapFrom = ""
        var longestGapTo = ""
        var totalTransitions = 0
        var scenicRepeats = 0
        val reachableDistances = ArrayList<Float>()

        repeat(runs) { index ->
            val seed = 0x5EED_2026_0000L + index * 7919L
            val terrain = TerrainProfile(seed)
            var distance = 0f
            var segmentSeed = seed xor 0x13579BDFL
            var style = WorldGenerator.startingStyle(seed)
            var previousBuildingAt = 0f
            var previousBuildingType = ""
            var havePreviousBuilding = false
            var previousBiome = style.biome
            var segmentIndex = 0
            var runHasDepot = false
            var reachedWinterGear = false
            val fuelEvents = ArrayList<FuelEvent>()

            while (distance < maxDistance) {
                val tutorial = distance == 0f
                val segment = WorldGenerator.createSegment(
                    segmentSeed = segmentSeed,
                    style = style,
                    worldOrigin = distance,
                    tripDistance = distance,
                    terrain = terrain,
                    isTutorial = tutorial
                )
                val plan = WorldGenerator.planSegment(segmentSeed, style, distance, tutorial)
                for (building in segment.buildings.sortedBy { it.localX }) {
                    val at = distance + building.localX
                    if (at > maxDistance) continue
                    val bucket = bucketEdges.zipWithNext().indexOfFirst { at >= it.first && at < it.second }
                        .coerceIn(0, buildingBuckets.lastIndex)
                    buildingBuckets[bucket]++
                    lootBuckets[bucket] += building.loot.size
                    buildingTypeCounts[building.type.ordinal]++
                    totalBuildings++
                    totalLoot += building.loot.size
                    val lootFuel = building.loot.sumOf { stack ->
                        if (stack.def.fluid?.name == "FUEL") stack.fluidLitres.toDouble() else 0.0
                    }.toFloat()
                    val pumpFuel = if (building.type == BuildingType.GAS_STATION) {
                        building.pumpFuelL + building.pumpDieselL
                    } else 0f
                    fuelEvents += FuelEvent(at, lootFuel + pumpFuel, style.fuelDrainMul)
                    if (building.landmark) {
                        totalDepots++
                        runHasDepot = true
                    }
                    if (building.loot.any { it.defId == ItemCatalog.TIRE_WINTER.id || it.defId == ItemCatalog.SNOW_CHAINS.id }) {
                        reachedWinterGear = true
                    }
                    if (havePreviousBuilding) {
                        val gap = at - previousBuildingAt
                        gaps += gap
                        if (gap > longestGap) {
                            longestGap = gap
                            longestGapSeed = seed
                            longestGapFrom = "${"%.2f".format(previousBuildingAt / 1000f)}km/$previousBuildingType"
                            longestGapTo = "${"%.2f".format(at / 1000f)}km/${building.type.name}"
                        }
                    }
                    previousBuildingAt = at
                    previousBuildingType = building.type.name
                    havePreviousBuilding = true
                }
                if (segmentIndex > 0) {
                    totalTransitions++
                    if (segment.style.biome == previousBiome) scenicRepeats++
                }
                previousBiome = segment.style.biome
                segmentIndex++
                val next = segment.choices.firstOrNull()
                distance += plan.length
                if (next == null) break
                segmentSeed = next.segmentSeed
                style = next.style
            }
            runDistances += distance
            if (reachedWinterGear) firstWinterGear++
            if (runHasDepot) runsWithDepot++

            fuelEvents.sortBy { it.distance }
            var fuel = 16f
            var cursor = 0f
            var reachable = maxDistance
            for (event in fuelEvents) {
                val burn = (event.distance - cursor) / 1000f * 5f * event.drainMul
                if (fuel < burn) {
                    reachable = cursor + fuel / (5f * event.drainMul) * 1000f
                    break
                }
                fuel -= burn
                fuel += event.supplyL
                cursor = event.distance
            }
            if (cursor < maxDistance && reachable == maxDistance) {
                val burn = (maxDistance - cursor) / 1000f * 5f
                if (fuel < burn) reachable = cursor + fuel / 5f * 1000f
            }
            reachableDistances += reachable
        }

        val sortedGaps = gaps.sorted()
        fun percentile(p: Double): Float = sortedGaps[(p * (sortedGaps.size - 1)).toInt()]
        val avgBuildings = totalBuildings.toFloat() / runs
        val avgLoot = totalLoot.toFloat() / runs
        val avgDistance = runDistances.average()
        val depotCoverage = runsWithDepot.toFloat() / runs
        val sortedReach = reachableDistances.sorted()

        println("AUDIT_SIM runs=$runs horizon_km=${maxDistance / 1000f}")
        println("distance avg_km=${"%.2f".format(avgDistance / 1000.0)} min_km=${"%.2f".format(runDistances.min() / 1000.0)} max_km=${"%.2f".format(runDistances.max() / 1000.0)}")
        println("buildings avg_per_run=${"%.2f".format(avgBuildings)} total=$totalBuildings")
        println("loot avg_items_per_run=${"%.2f".format(avgLoot)} avg_items_per_building=${"%.2f".format(totalLoot.toFloat() / totalBuildings)}")
        println("building_types ${BuildingType.entries.joinToString { "${it.name}=${buildingTypeCounts[it.ordinal]}" }}")
        println("cadence gaps_m_p50=${"%.0f".format(percentile(0.50))} p90=${"%.0f".format(percentile(0.90))} max=${"%.0f".format(longestGap)} max_seed=$longestGapSeed from=$longestGapFrom to=$longestGapTo")
        println("buckets buildings=${buildingBuckets.joinToString()} loot=${lootBuckets.joinToString()}")
        println("depots total=$totalDepots runs_with_depot=${"%.1f".format(depotCoverage * 100)}% transitions=$totalTransitions same_biome_transitions=$scenicRepeats")
        println("winter_gear_before_25km_runs=$firstWinterGear/${runs}")
        println("optimistic_loot_all_fuel_reach_km p10=${"%.2f".format(sortedReach[(runs * 0.10).toInt()] / 1000f)} p50=${"%.2f".format(sortedReach[(runs * 0.50).toInt()] / 1000f)} min=${"%.2f".format(sortedReach.first() / 1000f)}")
        println("reach_thresholds runs_reach_5km=${reachableDistances.count { it >= 5_000f }} runs_reach_10km=${reachableDistances.count { it >= 10_000f }} runs_reach_20km=${reachableDistances.count { it >= 20_000f }}")
    }
}
