package sk.kubis.endlessdrive.game.inventory

import sk.kubis.endlessdrive.core.GameConfig
import sk.kubis.endlessdrive.domain.model.ItemStack

class Inventory(
    private val baseSlots: Int = GameConfig.INVENTORY_SLOTS,
    private val baseWeight: Float = GameConfig.INVENTORY_MAX_WEIGHT
) {
    val slots = ArrayList<ItemStack?>(baseSlots)

    init {
        repeat(baseSlots) { slots.add(null) }
    }

    /** Miesto navyše z batoha / debny v kufri / strešného nosiča. */
    var bonusSlots = 0
        private set
    var bonusWeight = 0f
        private set

    val maxWeight: Float get() = baseWeight + bonusWeight

    /**
     * Prepočíta kapacitu po (od)montovaní úložiska. Zmenšenie prejde len vtedy,
     * keď sa má čo zmenšovať – obsadené sloty sa nikdy nezahodia.
     *
     * @return true, ak sa kapacita naozaj zmenila
     */
    fun applyCapacity(extraSlots: Int, extraWeight: Float): Boolean {
        bonusWeight = extraWeight
        val target = baseSlots + extraSlots
        if (target == slots.size) {
            bonusSlots = extraSlots
            return false
        }
        if (target > slots.size) {
            repeat(target - slots.size) { slots.add(null) }
        } else {
            // Zhora dole zmažeme len prázdne sloty; obsah sa nestratí.
            var i = slots.lastIndex
            while (slots.size > target && i >= 0) {
                if (slots[i] == null) slots.removeAt(i)
                i--
            }
        }
        bonusSlots = slots.size - baseSlots
        return true
    }

    /** Koľko slotov by zostalo voľných po zmenšení na [extraSlots]. */
    fun fitsWithin(extraSlots: Int): Boolean =
        usedSlots <= baseSlots + extraSlots

    val usedSlots: Int get() = slots.count { it != null }

    val totalWeight: Float
        get() = slots.sumOf { ((it?.def?.weight ?: 0f) * (it?.count ?: 0)).toDouble() }.toFloat()

    fun canFit(stack: ItemStack): Boolean {
        val def = stack.def
        val added = def.weight * stack.count
        if (totalWeight + added > maxWeight) return false
        // Kvapalina sa priloží k rovnakému kanistru – miesto v batohu nepotrebuje,
        // hmotnosť sa jej ale počíta rovnako ako všetkému ostatnému.
        if (def.fluid != null && slots.any { it?.defId == stack.defId }) return true
        return slots.any { it == null }
    }

    fun add(stack: ItemStack): Boolean {
        if (!canFit(stack)) return false
        if (stack.def.fluid != null) {
            val idx = slots.indexOfFirst { it?.defId == stack.defId }
            if (idx >= 0) {
                val existing = slots[idx]!!
                // Zlievame do jednej nádoby → čistota sa mieša podľa objemu,
                // nie podľa počtu kusov; nádoby môžu byť načaté.
                val have = existing.fluidLitres
                val add = stack.fluidLitres
                val total = have + add
                if (total > 0.001f) {
                    existing.purity = (existing.purity * have + stack.purity * add) / total
                }
                existing.setFluidLitres(total)
                return true
            }
        }
        val empty = slots.indexOfFirst { it == null }
        if (empty < 0) return false
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
