package sk.kubis.endlessdrive.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import sk.kubis.endlessdrive.R
import sk.kubis.endlessdrive.domain.model.BuildingType

/** Authored front-facing artwork. Decode/key/crop once, never in the draw loop. */
class BuildingSprites(context: Context) {
    val house = load(context, R.drawable.building_house)
    val garage = load(context, R.drawable.building_garage)
    val fuel = load(context, R.drawable.building_fuel)
    val repair = load(context, R.drawable.building_repair)
    val paint = load(context, R.drawable.building_paint)
    val relay = load(context, R.drawable.building_relay)

    private data class SeasonalSet(val house: ImageBitmap, val garage: ImageBitmap,
        val fuel: ImageBitmap, val repair: ImageBitmap, val paint: ImageBitmap, val relay: ImageBitmap)

    // Preload with GameAssets, so crossing a biome never decodes art on a frame.
    private val sets = mapOf(
        BuildingSeason.DEFAULT to SeasonalSet(house, garage, fuel, repair, paint, relay),
        BuildingSeason.AUTUMN to SeasonalSet(
            load(context, R.drawable.building_house_autumn), load(context, R.drawable.building_garage_autumn),
            load(context, R.drawable.building_fuel_autumn), load(context, R.drawable.building_repair_autumn),
            load(context, R.drawable.building_paint_autumn), load(context, R.drawable.building_relay_autumn)),
        BuildingSeason.WINTER to SeasonalSet(
            load(context, R.drawable.building_house_winter), load(context, R.drawable.building_garage_winter),
            load(context, R.drawable.building_fuel_winter), load(context, R.drawable.building_repair_winter),
            load(context, R.drawable.building_paint_winter), load(context, R.drawable.building_relay_winter))
    )

    fun relay(season: BuildingSeason): ImageBitmap = sets.getValue(season).relay

    fun building(type: BuildingType, paintService: Boolean,
        season: BuildingSeason = BuildingSeason.DEFAULT): ImageBitmap? {
        val set = sets.getValue(season)
        return when (type) {
            BuildingType.HOUSE -> set.house
            BuildingType.GARAGE -> set.garage
            BuildingType.GAS_STATION -> set.fuel
            BuildingType.AUTO_SHOP -> if (paintService) set.paint else set.repair
            BuildingType.WRECK -> null
        }
    }

    private fun load(context: Context, resource: Int): ImageBitmap {
        val source = BitmapFactory.decodeResource(context.resources, resource,
            BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 })
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        source.recycle()
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c ushr 16) and 255
            val g = (c ushr 8) and 255
            val b = c and 255
            // Some authored sources already have alpha; others use a magenta key.
            // Key the spaces inside the mast too, not only the exterior border.
            val key = minOf(r, b) - g
            if (key > 55) pixels[i] = 0
            else if ((c ushr 24) > 24) {
                left = minOf(left, i % w); right = maxOf(right, i % w)
                top = minOf(top, i / w); bottom = maxOf(bottom, i / w)
            }
        }
        check(right >= left && bottom >= top) { "Empty building artwork: $resource" }
        val keyed = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        val cropped = Bitmap.createBitmap(keyed, left, top, right - left + 1, bottom - top + 1)
        if (cropped !== keyed) keyed.recycle()
        return cropped.asImageBitmap()
    }
}
