package sk.kubis.endlessdrive

import org.junit.Test
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.GamePhase
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.game.GameEngine

/**
 * Diagnostika, nie regresia: prejde jazdu na viacerých seedoch a vypíše,
 * čo ju ukončilo a kde. Slúži na ladenie balansu – preto nič netvrdí.
 *
 * Hráča modelujeme štedro: kvapaliny dolieva hneď, ako klesnú, a to čistotou,
 * akú reálne nachádza v domoch. Ak jazda skončí aj tak, chyba nie je v tom,
 * že hráč málo hľadal.
 */
class RunBalanceDiagnosticTest {

    /** Čistota kanistra z bežného domu – stred rozsahu z LootGenerator. */
    private val foundOilPurity = 0.64f
    private val foundCoolantPurity = 0.64f
    private val foundFuelPurity = 0.75f

    @Test
    fun whereDoRunsDie() {
        val rows = mutableListOf<String>()
        var sum = 0f
        val causes = HashMap<String, Int>()
        val seeds = 1L..12L

        for (seed in seeds) {
            val engine = GameEngine(seed, 0f)
            engine.setScreenHeight(1080f)
            engine.readyCar()
            engine.tryStartEngine()

            var t = 0f
            val dt = 1f / 60f
            var topUps = 0
            while (t < 1800f && engine.phase != GamePhase.GAME_OVER) {
                if (engine.phase == GamePhase.JUNCTION) {
                    engine.junctionChoices.firstOrNull()?.let { engine.chooseBranch(it.id) }
                }
                // Hráč, ktorý všetko nájde a hneď dolieva.
                with(engine.car) {
                    if (fuel < 8f) {
                        refill(FluidType.FUEL, 12f, foundFuelPurity); topUps++
                    }
                    // Zanesený olej sa dá vypustiť a naliať čerstvý – bez toho
                    // sa čistota mieša donekonečna a motor sa zodrie.
                    if (oilPurity < foundOilPurity - 0.08f && oil > 0.05f) {
                        drain(FluidType.OIL); topUps++
                    }
                    if (oil < 1.6f) {
                        refill(FluidType.OIL, 1f, foundOilPurity); topUps++
                    }
                    if (coolant < 2f) {
                        refill(FluidType.COOLANT, 1.5f, foundCoolantPurity); topUps++
                    }
                }
                engine.throttleInput = 1f
                engine.advance(dt)
                t += dt
            }

            val km = engine.car.x / 1000f
            sum += km
            val cause = engine.endReason?.name ?: "SURVIVED"
            causes[cause] = (causes[cause] ?: 0) + 1
            val eng = engine.car.parts[ComponentSlot.ENGINE]
            rows += ("seed %2d: %6.2f km %6.0f s  %-17s oilPur=%.2f coolPur=%.2f fuelPur=%.2f " +
                "temp=%3.0f engHp=%.2f cause=%s").format(
                seed, km, t, cause,
                engine.car.oilPurity, engine.car.coolantPurity, engine.car.fuelPurity,
                engine.car.temperature,
                eng?.health ?: -1f,
                engine.car.wearCause?.name ?: "-"
            )
        }

        println("=== RUN BALANCE ===")
        rows.forEach(::println)
        println("priemer: %.2f km".format(sum / (seeds.last - seeds.first + 1)))
        println("priciny: $causes")
    }

    /** Auto pripravené na jazdu: chýbajúce diely doplnené, nádrž natankovaná. */
    private fun GameEngine.readyCar() {
        listOf(
            ComponentSlot.BATTERY to ItemCatalog.BATTERY,
            ComponentSlot.STARTER to ItemCatalog.STARTER,
            ComponentSlot.RADIATOR to ItemCatalog.RADIATOR,
            ComponentSlot.ALTERNATOR to ItemCatalog.ALTERNATOR
        ).forEach { (slot, def) ->
            if (!car.hasPart(slot)) {
                car.mount(slot, ItemStack(def.id, ComponentCondition.USED, 0.7f))
            }
        }
        car.batteryCharge = 0.9f
        car.refill(FluidType.FUEL, 30f, foundFuelPurity)
        car.refill(FluidType.OIL, 4f, foundOilPurity)
        car.refill(FluidType.COOLANT, 5f, foundCoolantPurity)
    }
}
