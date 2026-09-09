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
import sk.kubis.endlessdrive.game.car.WHEEL_HUB_FRAC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.hypot
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
    val sedan: SedanLayers

    init {
        // Kolesá v plnom rozlíšení – sú malé (256 px) a v paneli CAR veľké.
        // Zdrojové gumy nemajú rovnaký transparentný okraj; pred kreslením ich
        // preto zrovnáme na rovnaký priemer aj os otáčania. Defekt je odvodená
        // textúra z tej istej predlohy, nie jedna sivá placka pre všetky modely.
        val wheels = WheelCatalog.all.associateWith { decodeWheelPair(it) }
        val chains = decodeWheelPair(R.drawable.wheel_chains_overlay)
        sedan = SedanLayers(
            // Základ aj diely v rovnakom zmenšení – inak by si nesedeli mierkou.
            decodeBitmap(R.drawable.car_base_body, HALF),
            BodyPartCatalog.specs.mapValues { (part, spec) ->
                SedanLayers.splitPaintLayers(
                    decodeBitmap(spec.res, HALF),
                    extractPaint = BodyPartCatalog.appliesPaintTint(part)
                )
            },
            BodyPartCatalog.variants.mapValues { (_, parts) ->
                parts.mapValues { (part, res) ->
                    SedanLayers.splitPaintLayers(
                        decodeBitmap(res, HALF),
                        extractPaint = BodyPartCatalog.appliesPaintTint(part)
                    )
                }
            },
            wheels.mapValues { it.value.first },
            chains.first,
            wheels.mapValues { it.value.second },
            chains.second
        )
    }

    /**
     * Rozkreslené biómy. Hráč vidí naraz jeden, takže sady sa dekódujú až keď
     * na ne príde rad a v pamäti ostávajú len posledné [MAX_BACKDROPS].
     *
     * Načítať všetkých sedem naraz stálo 21 MB, ktoré tam ležali celý beh –
     * a s každým ďalším biómom by to rástlo o ďalšie tri megabajty.
     */
    private val backdrops = object : LinkedHashMap<BackdropCacheKey, BiomeBackdrop>(
        MAX_BACKDROPS + 1, 0.75f, /* accessOrder = */ true
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<BackdropCacheKey, BiomeBackdrop>) =
            size > MAX_BACKDROPS
    }
    private val backdropLock = Any()
    private val backdropPreloads = mutableSetOf<BackdropCacheKey>()
    private val backdropPreloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun loadBackdrop(spec: BackdropSpec): BiomeBackdrop {
        val material = if (spec.bakedSun) MaterialKind.SAND else MaterialKind.LEAVES
        fun finish(res: Int, kind: MaterialKind, strength: Float): Bitmap {
            val source = decodeBitmap(res, HALF)
            return TiledArtwork.finish(source, kind, strength,
                preserveSeam = authoredLoop(res)).also { source.recycle() }
        }
        val farBmp = finish(spec.far, MaterialKind.PAPER, 0.25f)
        val midBmp = finish(spec.mid, material, 0.65f)
        val nearBmp = finish(spec.near, material, 0.90f)
        return BiomeBackdrop(
            far = farBmp.asImageBitmap(),
            mid = midBmp.asImageBitmap(),
            near = nearBmp.asImageBitmap(),
            groundColor = bottomColor(farBmp),
            meadowColor = bottomColor(midBmp),
            tint = spec.tint ?: Color.White,
            bakedSun = spec.bakedSun,
            horizonCover = spec.horizonCover,
            landscapeLift = spec.landscapeLift,
            widthScale = spec.widthScale,
            heightScale = spec.heightScale,
            skyWash = spec.skyWash,
            farSkyOpacity = spec.farSkyOpacity,
            farBottomInset = transparentBottomInset(farBmp),
            midBottomInset = transparentBottomInset(midBmp),
            nearBottomInset = transparentBottomInset(nearBmp),
            skyEdgeColor = averageSkyColor(farBmp),
            midHaze = spec.midHaze,
            nearHaze = spec.nearHaze,
            hazeDay = spec.hazeDay,
            midRise = spec.midRise
        )
    }

    /**
     * Podiel priehľadného priestoru pod posledným súvislým obsahom PNG.
     * Staršie 3:1 sady majú pod kresbou až štvrtinu prázdneho plátna, zatiaľ
     * čo nové 4:1 sady končia na hrane. Kotvenie podľa rámu bitmapy preto
     * vytváralo medzi púšťou a cestou veľký pás.
     */
    private fun transparentBottomInset(bitmap: Bitmap): Float {
        if (bitmap.width < 1 || bitmap.height < 1) return 0f
        val required = (bitmap.width * 0.015f).toInt().coerceAtLeast(1)
        for (y in bitmap.height - 1 downTo 0) {
            var count = 0
            for (x in 0 until bitmap.width) {
                if ((bitmap.getPixel(x, y) ushr 24) >= 48 && ++count >= required) {
                    return (bitmap.height - 1 - y) / bitmap.height.toFloat()
                }
            }
        }
        return 0f
    }

    /** Stable sky colour sampled once at load time, without repeating a stretched scanline. */
    private fun averageSkyColor(bitmap: Bitmap): Color {
        if (bitmap.width < 1 || bitmap.height < 1) return Color(0xFF9BABB5)
        // Generované PNG môžu mať nad kresbou priehľadný technický okraj.
        // RGB v ňom býva čierne, hoci sa pixel vôbec nekreslí.
        for (y in 0 until bitmap.height) {
            opaqueRowColor(bitmap, y, minCoverage = 0.65f)?.let { return it }
        }
        return Color(0xFF9BABB5)
    }

    /** Ground fill below the backdrop when the road descends. */
    private fun bottomColor(bmp: Bitmap): Color {
        if (bmp.width < 1 || bmp.height < 1) return Color(0xFF6B5340)
        // Spodná kresba tiež nemusí siahať po samotný okraj bitmapy. Hľadáme
        // prvý súvislý nepriehľadný pás, nie jeden náhodný pixel v strede.
        for (y in bmp.height - 1 downTo 0) {
            opaqueRowColor(bmp, y, minCoverage = 0.08f)?.let { return it }
        }
        return Color(0xFF6B5340)
    }

    private fun opaqueRowColor(bitmap: Bitmap, y: Int, minCoverage: Float): Color? {
        var r = 0L; var g = 0L; var b = 0L; var count = 0
        for (x in 0 until bitmap.width) {
            val argb = bitmap.getPixel(x, y)
            if ((argb ushr 24) >= 200) {
                r += (argb ushr 16) and 255
                g += (argb ushr 8) and 255
                b += argb and 255
                count++
            }
        }
        if (count < bitmap.width * minCoverage) return null
        return Color((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
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

    /** Pozadie biómu, ktoré už je bezpečne pripravené pre render frame. */
    fun backdropFor(biome: BiomeType, segmentSeed: Long = 0L): BiomeBackdrop {
        val variant = BackdropCatalog.variantIndex(biome, segmentSeed)
        val key = BackdropCacheKey(biome, variant)
        synchronized(backdropLock) {
            backdrops[key]?.let { return it }
        }
        val spec = BackdropCatalog.specFor(biome, variant)
        val loaded = loadBackdrop(spec)
        return synchronized(backdropLock) {
            // A background preload may have won the race while this call was
            // decoding. Reuse it instead of replacing the cached instance.
            backdrops[key] ?: loaded.also { backdrops[key] = it }
        }
    }

    /**
     * Starts decoding a future biome away from the UI/render thread. The
     * renderer can then use [cachedBackdropFor] without ever blocking a frame.
     */
    fun preloadBackdrop(biome: BiomeType, segmentSeed: Long = 0L) {
        val variant = BackdropCatalog.variantIndex(biome, segmentSeed)
        val key = BackdropCacheKey(biome, variant)
        val shouldLoad = synchronized(backdropLock) {
            key !in backdrops && backdropPreloads.add(key)
        }
        if (!shouldLoad) return
        val spec = BackdropCatalog.specFor(biome, variant)
        backdropPreloadScope.launch {
            try {
                val loaded = loadBackdrop(spec)
                synchronized(backdropLock) {
                    if (key !in backdrops) backdrops[key] = loaded
                    backdropPreloads.remove(key)
                }
            } catch (_: Throwable) {
                // A failed warm-up must not affect gameplay. The normal
                // backdropFor path remains able to load the asset on demand.
                synchronized(backdropLock) { backdropPreloads.remove(key) }
            }
        }
    }

    /** Returns a warm asset without doing any decoding on the render thread. */
    fun cachedBackdropFor(biome: BiomeType, segmentSeed: Long = 0L): BiomeBackdrop? {
        val variant = BackdropCatalog.variantIndex(biome, segmentSeed)
        return synchronized(backdropLock) { backdrops[BackdropCacheKey(biome, variant)] }
    }

    private fun decode(resId: Int, sample: Int = 1): ImageBitmap =
        decodeBitmap(resId, sample).asImageBitmap()

    /**
     * Zjednotí priemer a stred všetkých predlôh kolies. Niektoré majú viac
     * prázdneho miesta hore alebo po bokoch, a pri rovnakom dstSize potom
     * pôsobia menšie či excentrické voči ostatným.
     */
    private fun decodeWheelPair(resId: Int): Pair<ImageBitmap, ImageBitmap> {
        val normalized = normalizeWheel(decodeBitmap(resId))
        val flat = SedanLayers.flattenPuncturedWheel(normalized)
        return normalized.asImageBitmap() to flat.asImageBitmap()
    }

    private fun normalizeWheel(src: Bitmap): Bitmap {
        val trimmed = SedanLayers.cropToOpaque(src, pad = 2)
        val side = max(trimmed.width, trimmed.height)
        val normalized = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(normalized).drawBitmap(
            trimmed,
            (side - trimmed.width) * 0.5f,
            (side - trimmed.height) * 0.5f,
            null
        )
        return normalized
    }

    private fun decodeBitmap(resId: Int, sample: Int = 1): Bitmap {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeResource(app.resources, resId, opts)
            ?: Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    }

    /**
     * Les aj priemysel majú už periodickú siluetu – druhé prelínanie by znova
     * rozrezalo koruny a veže. Zrno ostane; šev v kopci zatvorí heal.
     */
    private fun authoredLoop(res: Int): Boolean = when (res) {
        R.drawable.bg_forest_far, R.drawable.bg_forest_mid, R.drawable.bg_forest_near,
        R.drawable.bg_forest_alive_far, R.drawable.bg_forest_alive_mid, R.drawable.bg_forest_alive_near,
        R.drawable.bg_industry_far, R.drawable.bg_industry_mid, R.drawable.bg_industry_near,
        R.drawable.bg_sandstorm_far, R.drawable.bg_sandstorm_mid, R.drawable.bg_sandstorm_near,
        R.drawable.bg_rural_far, R.drawable.bg_rural_mid, R.drawable.bg_rural_near,
        R.drawable.bg_autumn_far, R.drawable.bg_autumn_mid, R.drawable.bg_autumn_near,
        R.drawable.bg_quarry_far, R.drawable.bg_quarry_mid, R.drawable.bg_quarry_near,
        R.drawable.bg_marsh_far, R.drawable.bg_marsh_mid, R.drawable.bg_marsh_near,
        R.drawable.bg_winter_alpine_far, R.drawable.bg_winter_alpine_mid, R.drawable.bg_winter_alpine_near,
        R.drawable.bg_winter_pines_far, R.drawable.bg_winter_pines_mid, R.drawable.bg_winter_pines_near -> true
        else -> false
    }

    private data class BackdropCacheKey(val biome: BiomeType, val variant: Int)

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
    val bakedSun: Boolean = false,
    /** Podiel spodku far vrstvy, ktorý prekryje slnko za krajinou. */
    val horizonCover: Float = 0.34f,
    /** Nízke nové vrstvy zdvihneme nad zadnú hranu lúky. */
    val landscapeLift: Float = 0f,
    /** Horizontal art correction independent of horizon height. */
    val widthScale: Float = 0.85f,
    /** Vertical scale for artwork whose silhouettes occupy only a low PNG band. */
    val heightScale: Float = 1f,
    /** Fraction of the far image blended gradually into the generated sky. */
    val skyWash: Float = 0.18f,
    /** Opacity of the far PNG in the sky pass; horizon artwork is redrawn separately. */
    val farSkyOpacity: Float = 1f,
    val midHaze: Float = 0.42f,
    val nearHaze: Float = 0.20f,
    val hazeDay: Color = Color(0xFFE8EEF2),
    /** Extra lift of the mid band as a fraction of screen height. */
    val midRise: Float = 0f
)

object BackdropCatalog {
    private val desert = Triple(
        R.drawable.bg_desert_far, R.drawable.bg_desert_mid, R.drawable.bg_desert_near
    )
    private val forestAlive = Triple(
        R.drawable.bg_forest_alive_far, R.drawable.bg_forest_alive_mid, R.drawable.bg_forest_alive_near
    )

    val specs: Map<BiomeType, BackdropSpec> = mapOf(
        BiomeType.DESERT to BackdropSpec(
            desert.first, desert.second, desert.third, bakedSun = true, landscapeLift = 0.08f,
            midHaze = 0.10f, nearHaze = 0.05f, hazeDay = Color(0xFFE8B56A)
        ),
        // Súmrak má slnko namaľované v predlohe, takže ďalšie sa nekreslí.
        BiomeType.DESERT_DUSK to BackdropSpec(
            R.drawable.bg_desert_dusk_far,
            R.drawable.bg_desert_dusk_mid,
            R.drawable.bg_desert_dusk_near,
            bakedSun = true, landscapeLift = 0.08f,
            midHaze = 0.08f, nearHaze = 0.04f, hazeDay = Color(0xFFE8A867)
        ),
        BiomeType.FOREST to BackdropSpec(
            R.drawable.bg_forest_far, R.drawable.bg_forest_mid, R.drawable.bg_forest_near,
            horizonCover = 0f, landscapeLift = 0.05f,
            midHaze = 0.08f, nearHaze = 0.03f, hazeDay = Color(0xFF8A9488), midRise = 0.04f
        ),
        BiomeType.FOREST_ALIVE to BackdropSpec(
            forestAlive.first, forestAlive.second, forestAlive.third,
            horizonCover = 0f, landscapeLift = 0.05f,
            midHaze = 0.07f, nearHaze = 0.03f, hazeDay = Color(0xFF8FA882), midRise = 0.045f
        ),
        BiomeType.INDUSTRIAL to BackdropSpec(
            R.drawable.bg_industry_far, R.drawable.bg_industry_mid, R.drawable.bg_industry_near,
            horizonCover = 0.38f, widthScale = 0.70f, heightScale = 1.22f,
            skyWash = 0.56f, landscapeLift = 0.05f,
            // Teplý opar zosvetlí tmavé haly bez straty priemyselnej palety.
            midHaze = 0.20f, nearHaze = 0.11f, hazeDay = Color(0xFF776B60), midRise = 0.028f
        ),
        BiomeType.SANDSTORM to BackdropSpec(
            R.drawable.bg_sandstorm_far, R.drawable.bg_sandstorm_mid, R.drawable.bg_sandstorm_near,
            tint = Color(0xFFFFD49B), bakedSun = true, horizonCover = 0.55f,
            skyWash = 0.82f, farSkyOpacity = 0f,
            landscapeLift = 0.08f, midHaze = 0.12f, nearHaze = 0.05f,
            hazeDay = Color(0xFFD3A365)
        ),
        BiomeType.DUST_STORM to BackdropSpec(
            R.drawable.bg_duststorm_far, R.drawable.bg_duststorm_mid, R.drawable.bg_duststorm_near,
            tint = Color(0xFFE8C19A), bakedSun = true, horizonCover = 0.55f,
            skyWash = 0.78f, farSkyOpacity = 0f,
            landscapeLift = 0.06f, midHaze = 0.13f, nearHaze = 0.06f,
            hazeDay = Color(0xFFB88D62)
        ),
        // Zachované pôvodné kreslené sady; príbuzné biómy odlišuje tónovanie.
        BiomeType.RURAL to BackdropSpec(
            forestAlive.first, forestAlive.second, forestAlive.third,
            tint = Color(0xFFF4F0D5), horizonCover = 0f, landscapeLift = 0.05f,
            midHaze = 0.08f, nearHaze = 0.04f, hazeDay = Color(0xFFB8C4A0), midRise = 0.04f
        ),
        BiomeType.WASTELAND to BackdropSpec(
            desert.first, desert.second, desert.third,
            tint = Color(0xFFE8D2B0), bakedSun = true, landscapeLift = 0.08f,
            midHaze = 0.10f, nearHaze = 0.05f, hazeDay = Color(0xFFE0B57A)
        ),
        BiomeType.ALPINE to BackdropSpec(
            forestAlive.first, forestAlive.second, forestAlive.third,
            tint = Color(0xFF9BBED2), horizonCover = 0f, landscapeLift = 0.05f,
            midHaze = 0.08f, nearHaze = 0.04f, hazeDay = Color(0xFFA8C0C8), midRise = 0.04f
        )
    )

    private fun newSpec(
        far: Int,
        mid: Int,
        near: Int,
        hazeDay: Color,
        landscapeLift: Float = 0.05f,
        midHaze: Float = 0.08f,
        nearHaze: Float = 0.035f,
        midRise: Float = 0.04f
    ) = BackdropSpec(
        far, mid, near,
        horizonCover = 0f,
        landscapeLift = landscapeLift,
        widthScale = 0.85f,
        heightScale = 1f,
        skyWash = 0.18f,
        midHaze = midHaze,
        nearHaze = nearHaze,
        hazeDay = hazeDay,
        midRise = midRise
    )

    /**
     * Nové kresby rozširujú existujúce biómy, takže nemenia fyziku ani formát
     * uložených hier. Seed segmentu vyberie stabilný variant a rovnaký seed sa
     * používa aj počas prechodu na ďalší úsek.
     */
    val variants: Map<BiomeType, List<BackdropSpec>> = specs.mapValues { (biome, original) ->
        when (biome) {
            BiomeType.RURAL -> listOf(
                newSpec(
                    R.drawable.bg_rural_far, R.drawable.bg_rural_mid, R.drawable.bg_rural_near,
                    hazeDay = Color(0xFF9DAFA6), midHaze = 0.07f, nearHaze = 0.03f
                )
            )
            BiomeType.FOREST_ALIVE -> listOf(
                newSpec(
                    R.drawable.bg_autumn_far, R.drawable.bg_autumn_mid, R.drawable.bg_autumn_near,
                    hazeDay = Color(0xFFB49B8D), midHaze = 0.06f, nearHaze = 0.025f
                )
            )
            BiomeType.WASTELAND -> listOf(
                newSpec(
                    R.drawable.bg_quarry_far, R.drawable.bg_quarry_mid, R.drawable.bg_quarry_near,
                    hazeDay = Color(0xFFA6AFAC), midHaze = 0.09f, nearHaze = 0.04f
                )
            )
            BiomeType.FOREST -> listOf(
                newSpec(
                    R.drawable.bg_marsh_far, R.drawable.bg_marsh_mid, R.drawable.bg_marsh_near,
                    hazeDay = Color(0xFF879F9B), midHaze = 0.09f, nearHaze = 0.04f
                )
            )
            BiomeType.ALPINE -> listOf(
                newSpec(
                    R.drawable.bg_winter_alpine_far,
                    R.drawable.bg_winter_alpine_mid,
                    R.drawable.bg_winter_alpine_near,
                    hazeDay = Color(0xFFC3D4DA), midHaze = 0.08f, nearHaze = 0.035f
                ),
                newSpec(
                    R.drawable.bg_winter_pines_far,
                    R.drawable.bg_winter_pines_mid,
                    R.drawable.bg_winter_pines_near,
                    hazeDay = Color(0xFFB7CDD1), midHaze = 0.075f, nearHaze = 0.03f
                )
            )
            else -> listOf(original)
        }
    }

    val allSpecs: List<BackdropSpec> = variants.values.flatten()

    fun variantIndex(biome: BiomeType, segmentSeed: Long): Int {
        val count = variants.getValue(biome).size
        if (count == 1) return 0
        return Math.floorMod(segmentSeed xor VARIANT_SALT, count.toLong()).toInt()
    }

    fun specFor(biome: BiomeType, variant: Int): BackdropSpec =
        variants.getValue(biome)[variant]

    private const val VARIANT_SALT = 0x4B1D5A77C3E9210L
}

data class BiomeBackdrop(
    val far: ImageBitmap,
    val mid: ImageBitmap,
    val near: ImageBitmap,
    /** Farba vzdialenej zeme – vypĺňa pás medzi pozadím a terénom. */
    val groundColor: Color = Color(0xFF6B5340),
    /** Spodok mid vrstvy – apron a lúka sa k nemu priblížia. */
    val meadowColor: Color = Color(0xFF6B5340),
    /** Prefarbenie celej sady – tak sa jedna kresba použije pre dva biómy. */
    val tint: Color = Color.White,
    /** true = obloha má namaľované slnko, druhé by sme nad ňu kresliť nemali. */
    val bakedSun: Boolean = false,
    val horizonCover: Float = 0.34f,
    val landscapeLift: Float = 0f,
    /** Horizontal art correction independent of horizon height. */
    val widthScale: Float = 0.85f,
    val heightScale: Float = 1f,
    val skyWash: Float = 0.18f,
    val farSkyOpacity: Float = 1f,
    /** Transparent space below the visible content in each PNG layer. */
    val farBottomInset: Float = 0f,
    val midBottomInset: Float = 0f,
    val nearBottomInset: Float = 0f,
    val skyEdgeColor: Color = Color(0xFF9BABB5),
    val midHaze: Float = 0.42f,
    val nearHaze: Float = 0.20f,
    val hazeDay: Color = Color(0xFFE8EEF2),
    val midRise: Float = 0f
) {
    fun tinted(color: Color): Color = Color(
        (color.red * tint.red).coerceIn(0f, 1f),
        (color.green * tint.green).coerceIn(0f, 1f),
        (color.blue * tint.blue).coerceIn(0f, 1f),
        color.alpha
    )
}

/**
 * Auto poskladané z dielov: holá karoséria a na nej to, čo je namontované.
 *
 * Predtým sa jeden obrázok auta krájal na obdĺžniky, ktoré sa tvárili ako
 * diely – auto tak bolo vždy celé a „chýbajúce“ dvere sa len nezvýraznili.
 * Teraz je základ naozaj holá škrupina a každý plech je vlastná kresba, takže
 * hráč na aute vidí presne to, čo naň zatiaľ našiel.
 */
data class PaintedSprite(val fixed: ImageBitmap, val paint: ImageBitmap)

class SedanLayers(
    shellRaw: Bitmap,
    private val partImages: Map<BodyPart, PaintedSprite>,
    /** Kresby pre konkrétne kusy: id predmetu → diel → obrázok. */
    private val variantImages: Map<String, Map<BodyPart, PaintedSprite>> = emptyMap(),
    /** Kresby kolies podľa zdroja – vyberá sa z nich podľa namontovanej gumy. */
    private val wheelImages: Map<Int, ImageBitmap> = emptyMap(),
    private val chainOverlayImage: ImageBitmap? = null,
    /** Spľasnutá guma z tej istej predlohy – cache podľa modelu kolesa. */
    private val flatWheelImages: Map<Int, ImageBitmap> = emptyMap(),
    private val flatChainOverlayImage: ImageBitmap? = null
) {

    /** Holá karoséria – to, s čím jazda začína. */
    val stripped: PaintedSprite
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
        stripped = splitPaintLayers(cropped)

        // Kužeľ svieti zo stredu svetlometu, nie z jeho rohu.
        fun centre(part: BodyPart): Pair<Float, Float> {
            val spec = BodyPartCatalog.specs.getValue(part)
            val img = partImages[part]
            val w = (img?.fixed?.width ?: 0) * 0.5f / imageWidth
            val h = (img?.fixed?.height ?: 0) * 0.5f / imageHeight
            return (spec.fx + w) to (spec.fy + h)
        }
        centre(BodyPart.HEADLIGHT).let {
            headlightFx = it.first + 0.012f
            headlightFy = it.second
        }
        centre(BodyPart.TAILLIGHT).let { taillightFx = it.first; taillightFy = it.second }
    }

    /**
     * Kresba dielu podľa toho, aký kus je namontovaný. [defId] rozhoduje len
     * vtedy, keď preň existuje vlastná predloha – inak sa použije základná.
     */
    fun partImage(part: BodyPart, defId: String?): PaintedSprite? =
        variantImages[defId]?.get(part) ?: partImages[part]

    /** Kresba kolesa podľa namontovanej gumy; neznáma dostane štandardnú. */
    fun wheelImage(tyreId: String?): ImageBitmap? {
        wheelImages[WheelCatalog.resFor(tyreId)]?.let { return it }
        return wheelImages.values.firstOrNull()
    }

    /** Defekt z predlohy daného modelu – rovnaký ráfik aj vzorka gumy. */
    fun flatWheelImage(tyreId: String?): ImageBitmap? {
        flatWheelImages[WheelCatalog.resFor(tyreId)]?.let { return it }
        return flatWheelImages.values.firstOrNull()
    }

    fun chainOverlay(): ImageBitmap? = chainOverlayImage

    fun flatChainOverlay(): ImageBitmap? = flatChainOverlayImage

    companion object {
        /**
         * Rozdelí sprite na nemenné sklá/plasty a lakovateľný plech.
         * Zdrojové obrázky sú oranžové; maska vyberá iba ich žlto-oranžové
         * pixely, takže nový lak nepremaľuje modré sklo ani čierne zrkadlo.
         *
         * Svetlá a sedadlá [extractPaint] vypínajú – celá predloha ostane
         * v [PaintedSprite.fixed], inak by sa teplé odlesky skla zobrali
         * do lakovej masky a zafarbili ako dvere.
         */
        fun splitPaintLayers(src: Bitmap, extractPaint: Boolean = true): PaintedSprite {
            if (!extractPaint) {
                val empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                return PaintedSprite(src.asImageBitmap(), empty.asImageBitmap())
            }
            val w = src.width
            val h = src.height
            val original = IntArray(w * h)
            src.getPixels(original, 0, w, 0, 0, w, h)
            val fixedPx = original.copyOf()
            val paintPx = IntArray(original.size)
            for (i in original.indices) {
                val c = original[i]
                val a = (c ushr 24) and 0xFF
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val painted = a > 8 && r >= g && g > b + 24 && r > b + 68 && r > 105
                if (painted) {
                    fixedPx[i] = 0
                    // Sivá maska drží svetlá a tiene pôvodného laku. Násobenie
                    // vybranou farbou potom nevyrába oranžové medzivýsledky.
                    val luma = ((r * 35 + g * 50 + b * 15) / 100 * 1.28f)
                        .toInt().coerceIn(45, 255)
                    paintPx[i] = (a shl 24) or (luma shl 16) or (luma shl 8) or luma
                }
            }
            val fixed = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val paint = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            fixed.setPixels(fixedPx, 0, w, 0, 0, w, h)
            paint.setPixels(paintPx, 0, w, 0, 0, w, h)
            return PaintedSprite(fixed.asImageBitmap(), paint.asImageBitmap())
        }

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

        /**
         * Z nafúknutého kolesa urobí textúru defektu: disk ostane dierou
         * (kreslí sa točiaci z predlohy), guma sa v spodku roztiahne na placku.
         * Každý model si drží vlastnú vzorku aj ráfik.
         */
        fun flattenPuncturedWheel(src: Bitmap): Bitmap {
            val w = src.width
            val h = src.height
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            if (w < 2 || h < 2) return out
            val cx = (w - 1) * 0.5f
            val cy = (h - 1) * 0.5f
            val r = kotlin.math.min(cx, cy).coerceAtLeast(1f)
            val srcPx = IntArray(w * h)
            src.getPixels(srcPx, 0, w, 0, 0, w, h)
            val dstPx = IntArray(w * h)
            val hubFrac = WHEEL_HUB_FRAC
            for (y in 0 until h) {
                val dy = (y - cy) / r
                val row = y * w
                for (x in 0 until w) {
                    val dx = (x - cx) / r
                    val destR = hypot(dx, dy)
                    if (destR <= hubFrac) continue
                    val mapped = PuncturedTireShape.destToSource(dx, dy, hubFrac) ?: continue
                    dstPx[row + x] = sampleBilinear(
                        srcPx, w, h,
                        cx + mapped.first * r,
                        cy + mapped.second * r
                    )
                }
            }
            out.setPixels(dstPx, 0, w, 0, 0, w, h)
            return out
        }

        private fun sampleBilinear(px: IntArray, w: Int, h: Int, fx: Float, fy: Float): Int {
            if (fx < 0f || fy < 0f || fx > w - 1f || fy > h - 1f) return 0
            val x0 = fx.toInt().coerceIn(0, w - 1)
            val y0 = fy.toInt().coerceIn(0, h - 1)
            val x1 = (x0 + 1).coerceAtMost(w - 1)
            val y1 = (y0 + 1).coerceAtMost(h - 1)
            val tx = (fx - x0).coerceIn(0f, 1f)
            val ty = (fy - y0).coerceIn(0f, 1f)
            if (x0 == x1 && y0 == y1) return px[y0 * w + x0]
            return lerpArgb(
                lerpArgb(px[y0 * w + x0], px[y0 * w + x1], tx),
                lerpArgb(px[y1 * w + x0], px[y1 * w + x1], tx),
                ty
            )
        }

        private fun lerpArgb(a: Int, b: Int, t: Float): Int {
            val ia = 1f - t
            fun chan(shift: Int): Int {
                val ca = (a ushr shift) and 0xFF
                val cb = (b ushr shift) and 0xFF
                return (ca * ia + cb * t).toInt().coerceIn(0, 255)
            }
            return (chan(24) shl 24) or (chan(16) shl 16) or (chan(8) shl 8) or chan(0)
        }
    }
}
