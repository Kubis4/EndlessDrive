package sk.kubis.endlessdrive.game.inventory

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.ItemStack

class Inventory(private val maxSlots: Int = GameConfig.INVENTORY_SLOTS) {
    val slots = ArrayList<ItemStack?>(maxSlots)

    init {
        repeat(maxSlots) { slots.add(null) }
    }

    val usedSlots: Int get() = slots.count { it != null }

    val totalWeight: Float
        get() = slots.sumOf { ((it?.def?.weight ?: 0f) * (it?.count ?: 0)).toDouble() }.toFloat()

    fun canFit(stack: ItemStack): Boolean {
        val def = stack.def
        // stackovanie kvapalín rovnakého typu
        if (def.fluid != null) {
            val existing = slots.indexOfFirst { it?.defId == stack.defId }
            if (existing >= 0) return true
        }
        if (slots.any { it == null }) {
            return totalWeight + def.weight * stack.count <= GameConfig.INVENTORY_MAX_WEIGHT
        }
        return false
    }

    fun add(stack: ItemStack): Boolean {
        if (stack.def.fluid != null) {
            val idx = slots.indexOfFirst { it?.defId == stack.defId }
            if (idx >= 0) {
                val existing = slots[idx]!!
                // Zlievame do jedného kanistra → čistota sa mieša podľa objemu.
                val total = existing.count + stack.count
                if (total > 0) {
                    existing.purity =
                        (existing.purity * existing.count + stack.purity * stack.count) / total
                }
                existing.count = total
                return true
            }
        }
        val empty = slots.indexOfFirst { it == null }
        if (empty < 0) return false
        if (totalWeight + stack.def.weight * stack.count > GameConfig.INVENTORY_MAX_WEIGHT) return false
        slots[empty] = stack
        return true
    }

    fun removeAt(index: Int): ItemStack? {
        if (index !in slots.indices) return null
        val item = slots[index]
        slots[index] = null
        return item
    }

    fun get(index: Int): ItemStack? = slots.getOrNull(index)

    fun clear() {
        for (i in slots.indices) slots[i] = null
    }
}
