package sk.kubis.endlessdrive.game

import sk.kubis.endlessdrive.domain.model.BuildingType

/** Krátky kontrakt, ktorý beží popri hlavnej jazde bez samostatnej obrazovky. */
enum class SideMissionKind {
    DELIVERY,
    EXPRESS_DELIVERY,
    FRAGILE_DELIVERY,
    SALVAGE
}

val SideMissionKind.isDelivery: Boolean
    get() = this != SideMissionKind.SALVAGE

data class ActiveSideMission(
    val kind: SideMissionKind,
    val sourceBuildingId: Long,
    val startWorldX: Float,
    /** DELIVERY: najskorší bod odovzdania. SALVAGE: nepoužíva sa. */
    val targetWorldX: Float,
    /** Konkrétna budova, nie iba približný počet metrov na HUD. */
    val targetBuildingId: Long = 0L,
    val targetBuildingType: BuildingType? = null,
    /** Počet zozbieraných kusov pri prijatí kontraktu. */
    val startItemsLooted: Int,
    val goalCount: Int,
    /** Istá odmena; časový bonus sa vedie zvlášť. */
    val rewardScrap: Int,
    val bonusRewardScrap: Int = 0,
    var timeRemainingS: Float = 0f,
    var packageLoaded: Boolean = false,
    /** 0..1; pri bežnom/expresnom balíku ostáva 1. */
    var cargoIntegrity: Float = 1f
)

data class SideMissionOffer(
    val kind: SideMissionKind,
    val targetDistanceM: Int,
    val goalCount: Int,
    val rewardScrap: Int,
    val targetWorldX: Float = 0f,
    val targetBuildingId: Long = 0L,
    val targetBuildingType: BuildingType? = null,
    val bonusRewardScrap: Int = 0,
    val timeLimitS: Float = 0f
)

data class SideMissionCompletion(
    val kind: SideMissionKind,
    val rewardScrap: Int,
    val bonusScrap: Int = 0,
    val cargoIntegrity: Float = 1f
)
