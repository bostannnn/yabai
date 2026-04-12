package com.example.unifiedreader.feature.navigation

import com.example.unifiedreader.core.model.SourceType

data class SourceNavigationState(
    val sourceOrder: List<SourceType> = SourceType.entries.toList(),
) {
    val startupSource: SourceType
        get() = sourceOrder.firstOrNull() ?: SourceType.KAVITA
}

enum class SourceOrderMoveDirection(
    internal val step: Int,
) {
    Up(step = -1),
    Down(step = 1),
}

data class SourceOrderSelection(
    val state: SourceNavigationState,
    val sourceOrder: List<SourceType>,
)

class SourceNavigationStateCoordinator(
    private val preferencesStore: SourceNavigationPreferencesStore,
) {
    fun restoreState(): SourceNavigationState = preferencesStore.preferencesFlow.value.toState()

    fun moveSource(
        currentState: SourceNavigationState,
        sourceType: SourceType,
        direction: SourceOrderMoveDirection,
    ): SourceOrderSelection? {
        val updatedOrder = currentState.sourceOrder.moveSource(
            sourceType = sourceType,
            direction = direction,
        ) ?: return null
        return SourceOrderSelection(
            state = SourceNavigationState(sourceOrder = updatedOrder),
            sourceOrder = updatedOrder,
        )
    }

    suspend fun persistSourceOrder(
        sourceOrder: List<SourceType>,
    ) {
        preferencesStore.setSourceOrder(sourceOrder)
    }
}

private fun SourceNavigationPreferences.toState(): SourceNavigationState = SourceNavigationState(
    sourceOrder = sourceOrder,
)

internal fun List<SourceType>.moveSource(
    sourceType: SourceType,
    direction: SourceOrderMoveDirection,
): List<SourceType>? {
    val normalizedOrder = normalizeSourceOrder(this)
    val currentIndex = normalizedOrder.indexOf(sourceType)
    if (currentIndex < 0) return null
    val targetIndex = currentIndex + direction.step
    if (targetIndex !in normalizedOrder.indices) return null
    return normalizedOrder.toMutableList().apply {
        val item = removeAt(currentIndex)
        add(targetIndex, item)
    }
}
