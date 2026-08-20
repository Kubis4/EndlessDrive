package sk.kubis.endlessdrive.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import sk.kubis.endlessdrive.R
import sk.kubis.endlessdrive.domain.model.BiomeType
import kotlin.math.max

/**
 * Pozadia a vrstvy sedanu (rozobranie / montáž).
 */
class GameAssets(context: Context) {
    private val app = context.applicationContext

    /**
     * Auto: holá karoséria plus kresby dielov. Diely idú v polovičnom
     * rozlíšení – na aute majú pri bežnej mierke pár desiatok pixelov.
     */
    val sedan = SedanLayers(
        // Základ aj diely v rovnakom zmenšení – inak by si nesedeli mierkou.
        decodeBitmap(R.drawable.car_base_body, HALF),
        BodyPartCatalog.specs.mapValues { (_, spec) -> decode(spec.res, HALF) },
        BodyPartCatalog.variants.mapValues { (_, parts) ->
            parts.mapValues { (_, res) -> decode(res, HALF) }
        },
        // Kolesá v plnom rozlíšení – sú malé (256 px) a v paneli CAR veľké.
        // Zdrojové gumy nemajú rovnaký transparentný okraj; pred kreslením ich
        // preto zrovnáme na rovnaký priemer aj os otáčania.
        WheelCatalog.all.associateWith { decodeWheel(it) }
    )

    /**
     * Rozkreslené biómy. Hráč vidí naraz jeden, takže sady sa dekódujú až keď
     * na ne príde rad a v pamäti ostávajú len posledné [MAX_BACKDROPS].
     *
     * Načítať všetkých sedem naraz stálo 21 MB, ktoré tam ležali celý beh –
     * a s každým ďalším biómom by to rástlo o ďalšie tri megabajty.
     */
    private val backdrops = object : LinkedHashMap<BiomeType, BiomeBackdrop>(
        MAX_BACKDROPS + 1, 0.75f, /* accessOrder = */ true
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<BiomeType, BiomeBackdrop>) =
            size > MAX_BACKDROPS
    }

    private fun loadBackdrop(spec: BackdropSpec): BiomeBackdrop {
        val farBmp = decodeBitmap(spec.far, HALF)
        return BiomeBackdrop(
            far = farBmp.asImageBitmap(),
            mid = decode(spec.mid, HALF),
            near = decode(spec.near, HALF),
            groundColor = bottomColor(farBmp),
            tint = spec.tint ?: Color.White,
            bakedSun = spec.bakedSun
        )
    }

    /**
     * Farba spodného okraja kresby. Vypĺňa sa ňou pás medzi pozadím a terénom –
     * cesta môže klesnúť hlboko pod horizont a bez výplne by tam zívala diera.
     */
    private fun bottomColor(bmp: Bitmap): Color {
        if (bmp.width < 1 || bmp.height < 1) return Color(0xFF6B5340)
        val argb = bmp.getPixel(bmp.width / 2, bmp.height - 1)
        return Color(argb)
    }

    /**
     * Vraky pri ceste. Sú to iné karosérie než hráčova – rad rovnakých
     * sedanov vyzeral ako sklad, nie ako opustená cesta. V polovičnom
     * rozlíšení, lebo v kulise sú malé.
     */
    val wreckSprites: List<WreckSprite> = listOf(
        R.drawable.wreck_minivan,
        R.drawable.wreck_pickup,
        R.drawable.wreck_cabrio
    ).map {
        // Orezanie na obsah je nutné, nie kozmetika: predloha má okolo auta
        // priehľadný okraj (auto zaberá ~riadky 248–752 z 1024). Bez orezania
        // tvorí ten okraj neviditeľný podstavec a vrak visí nad zemou.
        measureWreck(SedanLayers.cropToOpaque(decodeBitmap(it, HALF), pad = 0))
    }

    /**
     * Vyčíta blatníky priamo z obrázka a dopočíta, kam patrí koleso.
     *
     * Predlohy sú karosérie bez kolies s vyrezanými blatníkmi. Spodnú hranu
     * tvorí prah, blatník je nad ním hlboký zárez. Pevné podiely šírky by
     * sadli len jednej karosérii – minivan má rázvor inde než pickup.
     */
    private fun measureWreck(bmp: Bitmap): WreckSprite {
        val w = bmp.width
        val h = bmp.height
        val fallback = WreckSprite(bmp.asImageBitmap(), 0.18f, 0.82f, 0.082f, 0.93f, 1.18f)
        if (w < 32 || h < 32) return fallback

        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        fun opaque(x: Int, y: Int) = ((px[y * w + x] ushr 24) and 0xFF) >= 24

        // Spodný obrys karosérie. Blatníky sú v ňom výrazne hlbšie než
        // nárazníky po krajoch, takže ich oddelí prah z najhlbšieho zárezu.
        val bottom = IntArray(w) { x ->
            var b = 0
            for (y in h - 1 downTo 0) if (opaque(x, y)) { b = y; break }
            b
        }
        val maxDepth = (0 until w).maxOf { h - 1 - bottom[it] }
        if (maxDepth < h / 8) return fallback
        val thr = maxDepth * 0.45f

        val runs = ArrayList<IntRange>()
        var start = -1
        for (x in 0 until w) {
            val deep = (h - 1 - bottom[x]) > thr
            if (deep && start < 0) start = x
            if (!deep && start >= 0) {
                runs += start until x
                start = -1
            }
        }
        if (start >= 0) runs += start until w
        val arches = runs
            .sortedByDescending { it.last - it.first }
            .take(2)
            .sortedBy { it.first }
        if (arches.size < 2) return fallback

        var rearFx = 0f
        var frontFx = 0f
        var radiusSum = 0f
        var centreSum = 0f
        arches.forEachIndexed { i, arch ->
            val apex = (arch.first..arch.last).minOf { bottom[it] }
            val cx = (arch.first + arch.last) / 2
            val t = ((h - 1 - apex) / 3).coerceAtLeast(2)
            val probe = (apex + t).coerceAtMost(h - 1)
            var lx = cx
            while (lx > 0 && !opaque(lx - 1, probe)) lx--
            var rx = cx
            while (rx < w - 1 && !opaque(rx + 1, probe)) rx++
            // Oblúk blatníka je kruh. Z tetivy nameranej v známej hĺbke pod
            // vrcholom vyjde jeho polomer aj stred, takže koleso doň sadne
            // presne a nedrhne ani nevisí v prázdne.
            val half = (rx - lx + 1) * 0.5f
            val r = (half * half + t * t) / (2f * t)
            radiusSum += r
            centreSum += apex + r
            val fx = (arch.first + arch.last) * 0.5f / w
            if (i == 0) rearFx = fx else frontFx = fx
        }
        val radius = radiusSum * 0.5f
        val centreY = centreSum * 0.5f
        return WreckSprite(
            image = bmp.asImageBitmap(),
            rearFx = rearFx,
            frontFx = frontFx,
            wheelRadiusFx = radius / w,
            axleFy = centreY / h,
            groundFy = (centreY + radius) / h
        )
    }

    /**
     * Pozadie biómu. Prvý pohľad doň sadu dekóduje (~30 ms), ďalšie ju už len
     * vytiahnu z cache. Deje sa to hneď po križovatke, kde auto stojí, takže
     * to prípadné zaváhanie nikoho nepripraví o riadenie.
     */
    fun backdropFor(biome: BiomeType): BiomeBackdrop {
        backdrops[biome]?.let { return it }
        val spec = BackdropCatalog.specs.getValue(biome)
        return loadBackdrop(spec).also { backdrops[biome] = it }
    }

    private fun decode(resId: Int, sample: Int = 1): ImageBitmap =
        decodeBitmap(resId, sample).asImageBitmap()

    /**
     * Zjednotí priemer a stred všetkých predlôh kolies. Niektoré majú viac
     * prázdneho miesta hore alebo po bokoch, a pri rovnakom dstSize potom
     * pôsobia menšie či excentrické voči ostatným.
     */
    private fun decodeWheel(resId: Int): ImageBitmap {
        val trimmed = SedanLayers.cropToOpaque(decodeBitmap(resId), pad = 2)
        val side = max(trimmed.width, trimmed.height)
        val normalized = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(normalized).drawBitmap(
            trimmed,
            (side - trimmed.width) * 0.5f,
            (side - trimmed.height) * 0.5f,
            null
        )
        return normalized.asImageBitmap()
    }

    private fun decodeBitmap(resId: Int, sample: Int = 1): Bitmap {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeResource(app.resources, resId, opts)
            ?: Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val HALF = 2
        /**
         * Koľko sád pozadí sa drží naraz. Tri stačia na aktuálny bióm aj na
         * návrat cez križovatku späť, odkiaľ hráč prišiel.
         */
        const val MAX_BACKDROPS = 3
    }
}

/**
 * Kreslené pozadie biómu: obloha so vzdialenou krajinou, stredná silueta
 * a najbližšia vrstva. Každá sa hýbe inou rýchlosťou – z toho vzniká hĺbka.
 * Všetky sú dlaždice 4:1 ukotvené spodkom na horizont.
 */
/**
 * Karoséria vraku aj s tým, kam na nej patria kolesá. Podiely sú relatívne
 * k orezanému obrázku, takže platia pri akejkoľvek veľkosti vykreslenia.
 *
 * [groundFy] je nad 1 – spodok obrázka je prah, ale auto stojí na kolesách,
 * ktoré siahajú nižšie.
 */
data class WreckSprite(
    val image: ImageBitmap,
    val rearFx: Float,
    val frontFx: Float,
    val wheelRadiusFx: Float,
    val axleFy: Float,
    val groundFy: Float
)

/**
 * Z čoho je pozadie biómu poskladané. Sú to len čísla zdrojov, takže tabuľka
 * nepotrebuje [Context] a dá sa overiť obyčajným testom – chýbajúci bióm by
 * inak nikto nezachytil, hra by sa len ticho kreslila inak než ostatné vetvy.
 */
data class BackdropSpec(
    val far: Int,
    val mid: Int,
    val near: Int,
    /** Prefarbenie celej sady – tak sa jedna kresba použije pre dva biómy. */
    val tint: Color? = null,
    /** true = obloha má namaľované slnko, druhé by sme nad ňu kresliť nemali. */
    val bakedSun: Boolean = false
)

object BackdropCatalog {
    private val desert = Triple(
        R.drawable.bg_desert_far, R.drawable.bg_desert_mid, R.drawable.bg_desert_near
    )
    private val forestAlive = Triple(
        R.drawable.bg_forest_alive_far, R.drawable.bg_forest_alive_mid, R.drawable.bg_forest_alive_near
    )

    val specs: Map<BiomeType, BackdropSpec> = mapOf(
        BiomeType.DESERT to BackdropSpec(desert.first, desert.second, desert.third, bakedSun = true),
        // Súmrak má slnko namaľované v predlohe, takže ďalšie sa nekreslí.
        BiomeType.DESERT_DUSK to BackdropSpec(
            R.drawable.bg_desert_dusk_far,
            R.drawable.bg_desert_dusk_mid,
            R.drawable.bg_desert_dusk_near,
            bakedSun = true
        ),
        BiomeType.FOREST to BackdropSpec(
            R.drawable.bg_forest_far, R.drawable.bg_forest_mid, R.drawable.bg_forest_near
        ),
        BiomeType.FOREST_ALIVE to BackdropSpec(
            forestAlive.first, forestAlive.second, forestAlive.third
        ),
        BiomeType.INDUSTRIAL to BackdropSpec(
            R.drawable.bg_industry_far, R.drawable.bg_industry_mid, R.drawable.bg_industry_near
        ),
        BiomeType.SANDSTORM to BackdropSpec(
            R.drawable.bg_sandstorm_far, R.drawable.bg_sandstorm_mid, R.drawable.bg_sandstorm_near,
            bakedSun = true
        ),
        BiomeType.DUST_STORM to BackdropSpec(
            R.drawable.bg_duststorm_far, R.drawable.bg_duststorm_mid, R.drawable.bg_duststorm_near,
            bakedSun = true
        ),
        // Vidiek a pustatina vlastnú kresbu nemajú – požičiavajú si najbližšiu
        // a odlišuje ich tónovanie.
        BiomeType.RURAL to BackdropSpec(
            forestAlive.first, forestAlive.second, forestAlive.third,
            tint = Color(0xFFF6F2DA)
        ),
        BiomeType.WASTELAND to BackdropSpec(
            desert.first, desert.second, desert.third,
            tint = Color(0xFFD6CEC4), bakedSun = true
        )
    )
}

data class BiomeBackdrop(
    val far: ImageBitmap,
    val mid: ImageBitmap,
    val near: ImageBitmap,
    /** Farba vzdialenej zeme – vypĺňa pás medzi pozadím a terénom. */
    val groundColor: Color = Color(0xFF6B5340),
    /** Prefarbenie celej sady – tak sa jedna kresba použije pre dva biómy. */
    val tint: Color = Color.White,
    /** true = obloha má namaľované slnko, druhé by sme nad ňu kresliť nemali. */
    val bakedSun: Boolean = false
)

/**
 * Auto poskladané z dielov: holá karoséria a na nej to, čo je namontované.
 *
 * Predtým sa jeden obrázok auta krájal na obdĺžniky, ktoré sa tvárili ako
 * diely – auto tak bolo vždy celé a „chýbajúce“ dvere sa len nezvýraznili.
 * Teraz je základ naozaj holá škrupina a každý plech je vlastná kresba, takže
 * hráč na aute vidí presne to, čo naň zatiaľ našiel.
 */
class SedanLayers(
    shellRaw: Bitmap,
    private val partImages: Map<BodyPart, ImageBitmap>,
    /** Kresby pre konkrétne kusy: id predmetu → diel → obrázok. */
    private val variantImages: Map<String, Map<BodyPart, ImageBitmap>> = emptyMap(),
    /** Kresby kolies podľa zdroja – vyberá sa z nich podľa namontovanej gumy. */
    private val wheelImages: Map<Int, ImageBitmap> = emptyMap()
) {

    /** Holá karoséria – to, s čím jazda začína. */
    val stripped: ImageBitmap
    val imageWidth: Int
    val imageHeight: Int
    val worldWidthM = 5.6f

    /** Stredy oblúkov blatníkov, namerané z karosérie. */
    val rearWheelFx = 0.191f
    val frontWheelFx = 0.828f
    val wheelCenterFy = 0.913f
    val wheelRadiusFx = 0.083f

    /**
     * Odkiaľ svieti kužeľ. Sedí na stred svetlometu podľa jeho kotvy, aby
     * lúč vychádzal zo skla a nie z plechu vedľa neho.
     */
    val headlightFx: Float
    val headlightFy: Float
    val taillightFx: Float
    val taillightFy: Float

    init {
        val cropped = cropToOpaque(shellRaw, pad = 0)
        imageWidth = cropped.width
        imageHeight = cropped.height
        stripped = cropped.asImageBitmap()

        // Kužeľ svieti zo stredu svetlometu, nie z jeho rohu.
        fun centre(part: BodyPart): Pair<Float, Float> {
            val spec = BodyPartCatalog.specs.getValue(part)
            val img = partImages[part]
            val w = (img?.width ?: 0) * 0.5f / imageWidth
            val h = (img?.height ?: 0) * 0.5f / imageHeight
            return (spec.fx + w) to (spec.fy + h)
        }
        centre(BodyPart.HEADLIGHT).let { headlightFx = it.first; headlightFy = it.second }
        centre(BodyPart.TAILLIGHT).let { taillightFx = it.first; taillightFy = it.second }
    }

    /**
     * Kresba dielu podľa toho, aký kus je namontovaný. [defId] rozhoduje len
     * vtedy, keď preň existuje vlastná predloha – inak sa použije základná.
     */
    fun partImage(part: BodyPart, defId: String?): ImageBitmap? =
        variantImages[defId]?.get(part) ?: partImages[part]

    /** Kresba kolesa podľa namontovanej gumy; neznáma dostane štandardnú. */
    fun wheelImage(tyreId: String?): ImageBitmap? =
        wheelImages[WheelCatalog.resFor(tyreId)] ?: wheelImages[WheelCatalog.DEFAULT]

    companion object {
        /**
         * Flood-fill z okrajov: čierne pozadie → alpha 0.
         * Vnútorné čierne (B-stĺpik, zrkadlo) ostane.
         */
        fun keyOutEdgeBlack(src: Bitmap, threshold: Int = 32): Bitmap {
            val w = src.width
            val h = src.height
            val out = src.copy(Bitmap.Config.ARGB_8888, true)
            val px = IntArray(w * h)
            out.getPixels(px, 0, w, 0, 0, w, h)

            fun isBg(i: Int): Boolean {
                val c = px[i]
                val a = (c ushr 24) and 0xFF
                if (a < 8) return true
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                return r <= threshold && g <= threshold && b <= threshold
            }

            val seen = BooleanArray(w * h)
            val queue = IntArray(w * h)
            var qh = 0
            var qt = 0

            fun offer(i: Int) {
                if (i !in 0 until seen.size || seen[i] || !isBg(i)) return
                seen[i] = true
                queue[qt++] = i
            }

            for (x in 0 until w) {
                offer(x)
                offer((h - 1) * w + x)
            }
            for (y in 0 until h) {
                offer(y * w)
                offer(y * w + (w - 1))
            }

            while (qh < qt) {
                val i = queue[qh++]
                px[i] = 0
                val x = i % w
                val y = i / w
                if (x > 0) offer(i - 1)
                if (x + 1 < w) offer(i + 1)
                if (y > 0) offer(i - w)
                if (y + 1 < h) offer(i + w)
            }
            out.setPixels(px, 0, w, 0, 0, w, h)
            return out
        }

        fun cropToOpaque(src: Bitmap, pad: Int = 2): Bitmap {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if ((px[row + x] ushr 24) and 0xFF > 8) {
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < minX) return src
            minX = (minX - pad).coerceAtLeast(0)
            minY = (minY - pad).coerceAtLeast(0)
            maxX = (maxX + pad).coerceAtMost(w - 1)
            maxY = (maxY + pad).coerceAtMost(h - 1)
            return Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1)
        }

        /** Odstráni polopriehľadný okraj (často vyzerá ako „box“ na sprite). */
        fun scrubFringe(src: Bitmap) {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            for (i in px.indices) {
                val a = (px[i] ushr 24) and 0xFF
                if (a in 1..40) px[i] = 0
            }
            src.setPixels(px, 0, w, 0, 0, w, h)
        }
    }
}
