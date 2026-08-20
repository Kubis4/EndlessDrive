package sk.kubis.endlessdrive.game.world

import sk.kubis.endlessdrive.domain.model.BranchStyle

/**
 * Výškový profil, po ktorom auto ide.
 *
 * Bežná jazda ho berie zo šumu ([TerrainProfile]), ladenie pruženia
 * z pripravenej trate ([TestTrackProfile]). Zvyšok hry medzi nimi
 * nerozlišuje – pýta sa len na výšku v danom bode.
 */
interface Terrain {
    /**
     * @param challengeMul násobiteľ členitosti z aktuálneho úseku trate
     *   (rovinka < 1, kopce > 1, most ≈ 0).
     */
    fun heightAt(worldX: Float, style: BranchStyle, challengeMul: Float = 1f): Float
}
