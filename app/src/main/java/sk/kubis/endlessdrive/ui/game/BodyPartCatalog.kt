package sk.kubis.endlessdrive.ui.game

import sk.kubis.endlessdrive.R
import sk.kubis.endlessdrive.domain.model.ComponentSlot

/** Diel karosérie, ktorý je na aute vidieť a dá sa namontovať zvlášť. */
enum class BodyPart {
    REAR_SEAT, FRONT_SEAT,
    TRUNK, HOOD, TAILLIGHT, HEADLIGHT, BUMPER_REAR, BUMPER_FRONT, DOOR_REAR, DOOR_FRONT,
    ROOF_RACK
}

/**
 * Kde na aute diel sedí a z čoho sa kreslí.
 *
 * Predlohy sú v tej istej mierke ako základ, takže sa nikam neťahajú – určuje
 * sa len ľavý horný roh. Je v podieloch základu, nie v pixeloch, aby platil
 * pri akejkoľvek veľkosti vykreslenia aj pri zmene rozlíšenia predlôh.
 */
data class BodyPartSpec(
    val res: Int,
    val fx: Float,
    val fy: Float
)

object BodyPartCatalog {

    /**
     * Poradie kreslenia zdola nahor. Sedadlá sú v kabíne, teda pod karosériou;
     * dvere navrch, aby prekryli otvor aj stĺpik.
     */
    val order = listOf(
        BodyPart.REAR_SEAT,
        BodyPart.FRONT_SEAT,
        BodyPart.TRUNK,
        BodyPart.HOOD,
        BodyPart.TAILLIGHT,
        BodyPart.HEADLIGHT,
        BodyPart.BUMPER_REAR,
        BodyPart.BUMPER_FRONT,
        BodyPart.DOOR_REAR,
        BodyPart.DOOR_FRONT,
        // Nosič je na streche, teda nad všetkým ostatným.
        BodyPart.ROOF_RACK
    )

    /** Sedadlá patria za karosériu – vidno ich cez okná a prázdne otvory. */
    fun behindBody(part: BodyPart): Boolean =
        part == BodyPart.REAR_SEAT || part == BodyPart.FRONT_SEAT

    // Namerané na základe 1472×459; delíme, aby z toho boli podiely.
    private const val BASE_W = 1472f
    private const val BASE_H = 459f

    private fun at(res: Int, dx: Int, dy: Int) = BodyPartSpec(res, dx / BASE_W, dy / BASE_H)
    //Menšie X = doľava, menšie Y = hore.
    /**
     * NEMENIŤ. Polohy sú odsúhlasené proti predlohe a auto s nimi sedí.
     *
     * Sú to ľavé horné rohy dielov v pixeloch základu 1472 × 459, teda presne
     * tie čísla, ktoré ukazuje Photoshop v paneli vrstiev. Meniť ich má zmysel
     * len vtedy, keď sa vymení samotná predloha – nie pri ladení niečoho iného.
     */
    val specs: Map<BodyPart, BodyPartSpec> = mapOf(
        BodyPart.REAR_SEAT to at(R.drawable.car_seat_rear, 450, 85),
        BodyPart.FRONT_SEAT to at(R.drawable.car_seat_front, 750, 85),
        BodyPart.TRUNK to at(R.drawable.car_trunk, -15, 120),
        BodyPart.HOOD to at(R.drawable.car_hood, 1120, 153),
        BodyPart.TAILLIGHT to at(R.drawable.car_taillight_std, 5, 201),
        BodyPart.HEADLIGHT to at(R.drawable.car_headlight_std, 1295, 258),
        BodyPart.BUMPER_REAR to at(R.drawable.car_bumper_rear_std, -10, 300),
        BodyPart.BUMPER_FRONT to at(R.drawable.car_bumper_front_fog, 1290, 315),
        BodyPart.DOOR_REAR to at(R.drawable.car_door_rear, 340, 45),
        BodyPart.DOOR_FRONT to at(R.drawable.car_door_front, 715, 45),
        BodyPart.ROOF_RACK to at(R.drawable.car_roof_rack, 442, -15)
    )

    /** Čo na aute pribudne, keď je slot obsadený. */
    fun partsOf(slot: ComponentSlot): List<BodyPart> = when (slot) {
        // Dvere sa nachádzajú aj montujú v páre – jeden diel, dva plechy.
        ComponentSlot.DOORS -> listOf(BodyPart.DOOR_REAR, BodyPart.DOOR_FRONT)
        ComponentSlot.SEAT_FRONT -> listOf(BodyPart.FRONT_SEAT)
        ComponentSlot.SEAT_REAR -> listOf(BodyPart.REAR_SEAT)
        ComponentSlot.HOOD -> listOf(BodyPart.HOOD)
        ComponentSlot.TRUNK_LID -> listOf(BodyPart.TRUNK)
        ComponentSlot.FRONT_BUMPER -> listOf(BodyPart.BUMPER_FRONT)
        ComponentSlot.REAR_BUMPER -> listOf(BodyPart.BUMPER_REAR)
        ComponentSlot.HEADLIGHT -> listOf(BodyPart.HEADLIGHT)
        ComponentSlot.TAILLIGHT -> listOf(BodyPart.TAILLIGHT)
        ComponentSlot.ROOF_RACK -> listOf(BodyPart.ROOF_RACK)
        else -> emptyList()
    }

    /** Slot, ktorý daný diel zobrazuje. */
    fun slotOf(part: BodyPart): ComponentSlot = when (part) {
        BodyPart.DOOR_REAR, BodyPart.DOOR_FRONT -> ComponentSlot.DOORS
        BodyPart.FRONT_SEAT -> ComponentSlot.SEAT_FRONT
        BodyPart.REAR_SEAT -> ComponentSlot.SEAT_REAR
        BodyPart.HOOD -> ComponentSlot.HOOD
        BodyPart.TRUNK -> ComponentSlot.TRUNK_LID
        BodyPart.BUMPER_FRONT -> ComponentSlot.FRONT_BUMPER
        BodyPart.BUMPER_REAR -> ComponentSlot.REAR_BUMPER
        BodyPart.HEADLIGHT -> ComponentSlot.HEADLIGHT
        BodyPart.TAILLIGHT -> ComponentSlot.TAILLIGHT
        BodyPart.ROOF_RACK -> ComponentSlot.ROOF_RACK
    }

    /**
     * Iná kresba podľa toho, aký kus je namontovaný: id predmetu → diely,
     * ktoré preň vyzerajú inak.
     *
     * Diely sa dodávajú vo verziách („standard“, „with fog light“) a toto je
     * miesto, kde sa ďalšia pripojí: nové PNG, nový [ItemDef] a jeden riadok
     * tu. Kotva ostane, lebo predlohy sú v jednej mierke a jednom ráme –
     * variant sa nakreslí presne tam, kde základný.
     */
    val variants: Map<String, Map<BodyPart, Int>> = emptyMap()
}

/**
 * Kresby kolies podľa namontovanej gumy.
 *
 * Predlohy sú štvorcové a stred obrázka je stred kolesa, takže sa kreslia
 * okolo osi a otáčajú s ňou – žiadna kotva netreba. Zimná guma vlastnú
 * kresbu (zatiaľ) nemá a berie si štandardnú.
 */
object WheelCatalog {
    const val DEFAULT = R.drawable.wheel_std

    private val byTyre = mapOf(
        "tire_poor" to R.drawable.wheel_poor,
        // Staré uloženia poznajú ešte pôvodné id zodratej gumy.
        "tire_bald" to R.drawable.wheel_poor,
        "tire_std" to R.drawable.wheel_std,
        "tire_good" to R.drawable.wheel_std,
        "tire_winter" to R.drawable.wheel_std,
        "tire_sport" to R.drawable.wheel_sport,
        "tire_offroad" to R.drawable.wheel_offroad
    )

    /** Všetky kresby – načítavajú sa naraz, sú malé a treba ich každú snímku. */
    val all: List<Int> = byTyre.values.distinct()

    fun resFor(tyreId: String?): Int = byTyre[tyreId] ?: DEFAULT
}
