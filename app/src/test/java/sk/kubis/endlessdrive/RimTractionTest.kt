package sk.kubis.endlessdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.ItemStack
import sk.kubis.endlessdrive.domain.model.SedanSpec
import sk.kubis.endlessdrive.domain.model.TIRE_SLOTS
import sk.kubis.endlessdrive.game.car.Car
import sk.kubis.endlessdrive.game.car.TireInjury

class RimTractionTest {

    @Test
    fun shreddedLaunchSpinsAndTakesLongerThanInflated() {
        val inflated = launch(InjurySet.INFLATED)
        val punctured = launch(InjurySet.BOTH_PUNCTURED)
        val oneDrivenRim = launch(InjurySet.REAR_RIM)
        val bothRims = launch(InjurySet.BOTH_RIMS)

        assertTrue("nafuknuté sa musia chytiť, slip ${inflated.peakSlip}", inflated.peakSlip < 0.25f)
        assertTrue("nafuknuté musia nabrať rýchlosť, ${inflated.speed}", inflated.speed > 5.5f)

        assertTrue(
            "ráfik vzadu (RWD) musí pretáčať, slip ${oneDrivenRim.peakSlip}",
            oneDrivenRim.peakSlip > 0.45f
        )
        assertTrue(
            "rozjazd na ráfiku musí byť pomalší (${oneDrivenRim.speed} vs ${inflated.speed})",
            oneDrivenRim.speed < inflated.speed * 0.62f
        )
        assertTrue(
            "dva ráfiky ešte pomalšie ako jeden (${bothRims.speed} vs ${oneDrivenRim.speed})",
            bothRims.speed < oneDrivenRim.speed * 0.95f
        )
        assertTrue(
            "defekt nesmie byť ako ráfik (${punctured.speed} vs ${oneDrivenRim.speed})",
            punctured.speed > oneDrivenRim.speed * 1.15f
        )
    }

    @Test
    fun undrivenRimLaunchesBetterThanDrivenRim() {
        val driven = launch(InjurySet.REAR_RIM)
        val undriven = launch(InjurySet.FRONT_RIM)
        assertTrue(
            "RWD s predným ráfikom sa musí rozbehnúť lepšie ako so zadným " +
                "(${undriven.speed} vs ${driven.speed})",
            undriven.speed > driven.speed * 1.25f
        )
        assertTrue("voľný ráfik nemá páliť hnanú gumu, slip ${undriven.peakSlip}",
            undriven.peakSlip < 0.30f)
        assertEquals(1f, rwdCar().apply {
            parts[ComponentSlot.TIRE_REAR]!!.injury = TireInjury.SHREDDED
        }.drivenRimBlend(), 0.001f)
        assertEquals(0f, rwdCar().apply {
            parts[ComponentSlot.TIRE_FRONT]!!.injury = TireInjury.SHREDDED
        }.drivenRimBlend(), 0.001f)
    }

    @Test
    fun shreddedBrakesLongerThanInflated() {
        val inflated = brakeDistance(InjurySet.INFLATED)
        val punctured = brakeDistance(InjurySet.BOTH_PUNCTURED)
        val frontRim = brakeDistance(InjurySet.FRONT_RIM)
        val rearRim = brakeDistance(InjurySet.REAR_RIM)
        val bothRims = brakeDistance(InjurySet.BOTH_RIMS)

        assertTrue("predný ráfik predĺži brzdenie ($frontRim vs $inflated)", frontRim > inflated * 1.15f)
        assertTrue("zadný ráfik tiež brzdí horšie ($rearRim vs $inflated)", rearRim > inflated)
        assertTrue("dva ráfiky brzdia najhoršie ($bothRims vs $frontRim)", bothRims > frontRim * 1.20f)
        assertTrue("dva ráfiky brzdia horšie ako jeden zadný ($bothRims vs $rearRim)",
            bothRims > rearRim * 1.20f)
        assertTrue("defekt nesmie brzdiť ako dva ráfiky ($punctured vs $bothRims)",
            punctured < bothRims * 0.85f)
    }

    @Test
    fun shreddedClimbsWorseAndSlidesOnSteepGrade() {
        val slope = 0.25f
        val inflated = hillSpeed(InjurySet.INFLATED, slope)
        val punctured = hillSpeed(InjurySet.BOTH_PUNCTURED, slope)
        val drivenRim = hillSpeed(InjurySet.REAR_RIM, slope)
        val bothRims = hillSpeed(InjurySet.BOTH_RIMS, slope)

        assertTrue("nafuknuté musia 25 % utiahnuť, ${inflated * 3.6f} km/h", inflated > 2.2f)
        assertTrue(
            "hnaný ráfik ide do kopca horšie ($drivenRim vs $inflated)",
            drivenRim < inflated * 0.45f
        )
        assertTrue(
            "dva ráfiky na 25 % skĺznu alebo takmer stoja ($bothRims)",
            bothRims < 1.2f
        )
        assertTrue(
            "defekt ostáva výrazne nad ráfikom ($punctured vs $drivenRim)",
            punctured > drivenRim + 1.5f
        )
        val rimClimb = rwdCar().apply {
            parts[ComponentSlot.TIRE_REAR]!!.injury = TireInjury.SHREDDED
        }.maxClimbSlope(0f, 0f)
        val rubberClimb = rwdCar().maxClimbSlope(0f, 0f)
        assertTrue("limit stúpania na ráfiku musí klesnúť ($rimClimb vs $rubberClimb)",
            rimClimb < rubberClimb * 0.35f)
    }

    private enum class InjurySet {
        INFLATED, BOTH_PUNCTURED, FRONT_RIM, REAR_RIM, BOTH_RIMS
    }

    private data class Launch(val speed: Float, val peakSlip: Float)

    private fun rwdCar(): Car {
        val car = Car()
        car.installStarterKit()
        car.mount(
            ComponentSlot.DRIVETRAIN,
            ItemStack(ItemCatalog.DRIVE_RWD.id, ComponentCondition.NEW, 1f)
        )
        TIRE_SLOTS.forEach {
            car.mount(it, ItemStack(ItemCatalog.TIRE.id, ComponentCondition.USED, 0.9f))
        }
        car.fuel = 20f
        car.engineRunning = true
        return car
    }

    private fun Car.applyInjury(set: InjurySet) {
        val front = parts[ComponentSlot.TIRE_FRONT]!!
        val rear = parts[ComponentSlot.TIRE_REAR]!!
        when (set) {
            InjurySet.INFLATED -> {
                front.injury = TireInjury.INFLATED
                rear.injury = TireInjury.INFLATED
            }
            InjurySet.BOTH_PUNCTURED -> {
                front.injury = TireInjury.PUNCTURED
                rear.injury = TireInjury.PUNCTURED
            }
            InjurySet.FRONT_RIM -> {
                front.injury = TireInjury.SHREDDED
                rear.injury = TireInjury.INFLATED
            }
            InjurySet.REAR_RIM -> {
                front.injury = TireInjury.INFLATED
                rear.injury = TireInjury.SHREDDED
            }
            InjurySet.BOTH_RIMS -> {
                front.injury = TireInjury.SHREDDED
                rear.injury = TireInjury.SHREDDED
            }
        }
    }

    private fun launch(set: InjurySet): Launch {
        val car = rwdCar()
        car.applyInjury(set)
        car.snapToGround(0f)
        var peakSlip = 0f
        repeat(90) {
            car.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f)
            peakSlip = maxOf(peakSlip, car.wheelSlip)
        }
        return Launch(car.speed, peakSlip)
    }

    private fun brakeDistance(set: InjurySet): Float {
        val car = rwdCar()
        car.snapToGround(0f)
        repeat(90) { car.applyDrive(1f / 60f, 1f, 0f, 0f, 0f, 0f) }
        val cruise = 6f
        car.speed = cruise
        car.applyInjury(set)
        val start = car.x
        var frames = 0
        while (car.speed > 0.35f && frames < 900) {
            car.applyDrive(1f / 60f, 0f, 1f, 0f, 0f, 0f)
            frames++
        }
        assertTrue("auto sa musí zastaviť, ostalo ${car.speed}", car.speed <= 0.35f)
        return car.x - start
    }

    private fun hillSpeed(set: InjurySet, slope: Float): Float {
        val car = rwdCar()
        car.applyInjury(set)
        val wb = SedanSpec.wheelOffsetX * 2f
        car.snapToGround(0f, slope)
        repeat(240) {
            car.applyDrive(1f / 60f, 1f, 0f, 0f, slope * wb, 0f)
        }
        return car.speed
    }
}
