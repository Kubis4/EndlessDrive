package sk.kubis.endlessdrive.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.game.car.Car

/** Separate bolt-on artwork: original PNGs, mount anchors and wheel geometry stay intact. */
internal object ExpeditionEquipment {
    fun installed(car: Car): Boolean =
        car.parts[ComponentSlot.ROOF_RACK]?.defId == ItemCatalog.EXPEDITION_RACK.id

    fun DrawScope.draw(car: Car, layout: CarArtist.SpriteLayout) {
        if (!installed(car)) return
        withTransform({
            translate(layout.originX, layout.originY)
            scale(layout.drawW / 1472f, layout.drawH / 459f, Offset.Zero)
        }) {
            fun moulding(x: Float, w: Float) {
                drawRoundRect(Color(0xFF171E20), Offset(x,332f), Size(w,24f), CornerRadius(7f))
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF596366),Color(0xFF282F32)),332f,354f),
                    Offset(x+2f,333f),Size(w-4f,18f),CornerRadius(5f))
                drawLine(Color(0xFF8B9695),Offset(x+8f,336f),Offset(x+w-8f,336f),2f)
                for (boltX in listOf(x+12f,x+w-12f)) {
                    drawCircle(Color(0xFF111617),3f,Offset(boltX,343f))
                    drawCircle(Color(0xFF9EA8A4),1.2f,Offset(boltX,342f))
                }
            }
            if (car.hasPart(ComponentSlot.DOOR_REAR)) moulding(365f,327f)
            if (car.hasPart(ComponentSlot.DOOR_FRONT)) moulding(739f,330f)
            // Low-profile strapped canvas bag. Highlights follow the car's upper light.
            drawOval(Color.Black.copy(alpha=.32f),Offset(478f,-21f),Size(329f,15f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF889071),Color(0xFF424C3B)),-78f,-17f),
                Offset(485f,-78f),Size(173f,61f),CornerRadius(20f))
            drawRoundRect(Color(0xFF363F31),Offset(487f,-76f),Size(168f,57f),CornerRadius(19f),style=Stroke(3f))
            drawLine(Color(0xFFB0B49B),Offset(508f,-68f),Offset(634f,-68f),2f)
            for (x in listOf(514f,614f)) {
                drawRect(Color(0xFF343735),Offset(x,-78f),Size(10f,65f))
                drawRoundRect(Color(0xFFA3A58D),Offset(x-2f,-40f),Size(14f,10f),CornerRadius(2f),style=Stroke(2f))
            }
            // Weatherproof equipment case, intentionally distinct from carried loot.
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFBE8C49),Color(0xFF725432)),-68f,-17f),
                Offset(671f,-68f),Size(126f,51f),CornerRadius(7f))
            drawRoundRect(Color(0xFF3D3E32),Offset(671f,-68f),Size(126f,51f),CornerRadius(7f),style=Stroke(3f))
            drawLine(Color(0xFFD9B475),Offset(677f,-60f),Offset(791f,-60f),3f)
            for (x in listOf(692f,767f)) {
                drawRect(Color(0xFF333A36),Offset(x,-71f),Size(9f,56f))
                drawRect(Color(0xFFB4B7A3),Offset(x-1f,-42f),Size(11f,8f))
            }
            drawRoundRect(Color(0xFF232C2E),Offset(842f,-38f),Size(77f,27f),CornerRadius(6f))
            drawLine(Color(0xFF737F7E),Offset(847f,-37f),Offset(913f,-37f),2f)
            for (x in listOf(854f,876f,898f)) {
                drawRoundRect(Color(0xFFAFBAB5),Offset(x,-32f),Size(14f,14f),CornerRadius(3f))
                drawRect(Color(0xFFF0E9CF),Offset(x+3f,-30f),Size(5f,8f))
            }
        }
    }
}
