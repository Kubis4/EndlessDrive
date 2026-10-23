package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.ui.game.GameUiState
import sk.kubis.endlessdrive.ui.game.composeHudAlerts
import sk.kubis.endlessdrive.ui.game.tireWarningLabel

class HudAlertsTest {

    private fun healthyUi() = GameUiState(
        fuelL = 30f,
        oilL = 3f,
        coolantL = 4f,
        overallHealth = 0.8f,
        batteryCharge = 0.9f
    )

    @Test
    fun deadBatteryKeepsOnlyCantStart() {
        val ui = healthyUi().copy(
            blockedReason = "Battery is flat",
            message = "Battery is flat — fit a charged one",
            events = listOf("NO CHARGE" to 26),
            batteryCharge = 0.05f,
            engineRunning = false
        )
        val alerts = composeHudAlerts(ui)
        assertEquals("CAN'T START: Battery is flat", alerts.block)
        assertNull(alerts.message)
        assertTrue(alerts.events.isEmpty())
        assertTrue(alerts.chips.none { it.text.contains("BATTERY", ignoreCase = true) })
        assertTrue("okrem blokácie žiadny ďalší riadok o batérii", alerts.chips.isEmpty())
    }

    @Test
    fun beltChipStaysWhenBatteryIsStillFine() {
        val ui = healthyUi().copy(
            events = listOf("NO CHARGE" to 40),
            message = "Alternator belt snapped — battery not charging.",
            engineRunning = true,
            batteryHoldCapacity = 0.9f
        )
        val alerts = composeHudAlerts(ui)
        assertNull(alerts.block)
        assertNull(alerts.message)
        assertTrue(alerts.events.isEmpty())
        assertEquals(listOf("NOT CHARGING"), alerts.chips.map { it.text })
    }

    @Test
    fun tinyBatteryHoldShowsReplaceMessage() {
        val ui = healthyUi().copy(
            engineRunning = true,
            batteryHoldCapacity = 0.12f,
            alternatorOutput = 1f,
            batteryCharge = 0.50f
        )
        val alerts = composeHudAlerts(ui)
        assertEquals(
            listOf("The battery will not hold a charge — fit another."),
            alerts.chips.map { it.text }
        )
    }

    @Test
    fun healthyChargingSetupDoesNotWarnAboutHoldingCharge() {
        val ui = healthyUi().copy(
            engineRunning = true,
            batteryHoldCapacity = 0.90f,
            alternatorOutput = 0.85f,
            batteryCharge = 0.50f
        )
        val alerts = composeHudAlerts(ui)
        assertTrue(alerts.chips.none { it.text.contains("hold", ignoreCase = true) })
        assertTrue(alerts.chips.none { it.text.contains("CHARGING") })
    }

    @Test
    fun punctureAndRimCollapseToOneChip() {
        assertEquals(
            "ON RIM · PUNCTURE",
            tireWarningLabel(TireInjury.SHREDDED, TireInjury.PUNCTURED)
        )
        val ui = healthyUi().copy(
            message = "Puncture — the rear tyre is going flat.",
            frontTireInjury = TireInjury.SHREDDED,
            rearTireInjury = TireInjury.PUNCTURED
        )
        val alerts = composeHudAlerts(ui)
        assertNull(alerts.message)
        assertEquals(listOf("ON RIM · PUNCTURE"), alerts.chips.map { it.text })
    }

    @Test
    fun tireFlavorYieldsToRimChip() {
        val ui = healthyUi().copy(
            message = "Blowout — the front tyre is shredded.",
            frontTireInjury = TireInjury.SHREDDED
        )
        val alerts = composeHudAlerts(ui)
        assertNull(alerts.block)
        assertNull(alerts.message)
        assertEquals(listOf("RUNNING ON RIM"), alerts.chips.map { it.text })
    }

    @Test
    fun batteryBlockDoesNotHideUnrelatedRim() {
        val ui = healthyUi().copy(
            blockedReason = "Battery is flat",
            batteryCharge = 0.05f,
            frontTireInjury = TireInjury.SHREDDED
        )
        val alerts = composeHudAlerts(ui)
        assertEquals("CAN'T START: Battery is flat", alerts.block)
        assertEquals(listOf("RUNNING ON RIM"), alerts.chips.map { it.text })
    }

    @Test
    fun unrelatedWinterAndFuelBothStay() {
        val ui = healthyUi().copy(
            isWinter = true,
            hasChains = false,
            fuelL = 6f
        )
        val alerts = composeHudAlerts(ui)
        assertEquals(listOf("SNOW · NO CHAINS"), alerts.chips.map { it.text })
    }

    @Test
    fun fuelCriticalHidesFuelLeakTimer() {
        val ui = healthyUi().copy(
            fuelL = 2f,
            fuelCapacityL = 40f,
            events = listOf("FUEL LEAK" to 20)
        )
        val alerts = composeHudAlerts(ui)
        assertEquals(listOf("FUEL CRITICAL"), alerts.chips.map { it.text })
        assertTrue(alerts.events.isEmpty())
    }
}
