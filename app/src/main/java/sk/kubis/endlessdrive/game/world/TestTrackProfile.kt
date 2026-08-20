package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.domain.model.BranchStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Profil terénu, ktorý dáva čo skúšať.
 *
 * Na náhodnej ceste sa pruženie ani handling poriadne overiť nedajú – kým
 * príde poriadny hrbol, prejde pol kilometra. Tu ide jedna prekážka za druhou
 * v známom poradí a stále dokola, takže sa dá jeden diel vymeniť a hneď prejsť
 * tú istú sekciu znova.
 *
 * Všetko sú kosínusové hrbole s nulovou výškou aj sklonom na okrajoch, sčítané
 * na rovinu. Vďaka tomu na seba úseky nadväzujú hladko a nikde nevznikne zráz,
 * na ktorom by sa auto zaseklo.
 */
class TestTrackProfile : Terrain {

    override fun heightAt(worldX: Float, style: BranchStyle, challengeMul: Float): Float {
        val x = ((worldX % LAP) + LAP) % LAP
        var h = 0f

        // 45–95 m: vlnitá dlažba. Rýchle malé vlny – najlepšie ukážu, či
        // pruženie pracuje, alebo sa všetko prenáša do karosérie.
        h += washboard(x, from = 45f, to = 95f, wave = 6.5f, height = 0.16f)

        // 120 m: jeden ostrý hrbol. Prejde ho vždy len jedno koleso naraz,
        // takže je na ňom vidieť artikuláciu náprav.
        h += bump(x, at = 122f, halfWidth = 5f, height = 0.55f)

        // 165–200 m: nájazd na skok. Dosť dlhý na rozbeh, dosť prudký na to,
        // aby auto pri rýchlosti odlepilo kolesá.
        h += bump(x, at = 182f, halfWidth = 17f, height = 2.4f)

        // 245–320 m: dlhé stúpanie a zjazd – trakcia do kopca a brzdenie dole.
        h += bump(x, at = 282f, halfWidth = 38f, height = 6.5f)

        // 340–395 m: séria priehlbín, teda opak hrboľov.
        h -= bump(x, at = 352f, halfWidth = 7f, height = 0.75f)
        h -= bump(x, at = 372f, halfWidth = 7f, height = 0.95f)
        h -= bump(x, at = 392f, halfWidth = 7f, height = 1.15f)

        // 415–470 m: schody. Každý ďalší väčší – kde presne pruženie dôjde,
        // sa dá odčítať priamo z jazdy.
        h += bump(x, at = 418f, halfWidth = 4.5f, height = 0.30f)
        h += bump(x, at = 436f, halfWidth = 4.5f, height = 0.55f)
        h += bump(x, at = 454f, halfWidth = 4.5f, height = 0.85f)
        h += bump(x, at = 472f, halfWidth = 4.5f, height = 1.20f)

        return h
    }

    /**
     * Hrbol s nulovou výškou aj sklonom na okrajoch. Tým sa na rovinu napojí
     * bez zlomu, nech ho postavím kamkoľvek.
     */
    private fun bump(x: Float, at: Float, halfWidth: Float, height: Float): Float {
        val t = (x - at) / halfWidth
        if (t <= -1f || t >= 1f) return 0f
        return height * 0.5f * (1f + cos(t * PI.toFloat()))
    }

    /** Rad vĺn, ktorý sa na oboch koncoch plynulo stratí. */
    private fun washboard(x: Float, from: Float, to: Float, wave: Float, height: Float): Float {
        if (x <= from || x >= to) return 0f
        val span = to - from
        val t = (x - from) / span
        // Okno tlmí amplitúdu na krajoch, aby dlažba nezačala schodom.
        val window = 0.5f * (1f - cos(t * 2f * PI.toFloat()))
        return height * window * sin((x - from) / wave * 2f * PI.toFloat())
    }

    companion object {
        /** Dĺžka okruhu. Za ňou sa celá zostava opakuje. */
        const val LAP = 520f
    }
}
