package com.example.unifiedreader.feature.navigation

import com.example.unifiedreader.core.model.SourceType

data class SourceNavigationState(
    val sourceOrder: List<SourceType> = SourceType.entries.toList(),
    /** See [SourceNavigationPreferencesStore.lastConnectedSources]. */
    val lastConnectedSources: Set<SourceType>? = null,
) {
    /** The first source the rail lists: a server not connected last time is skipped. */
    val startupSource: SourceType
        get() = navigationSources(sourceOrder, lastConnectedSources ?: NavigationServerSources.toSet())
            .firstOrNull() ?: SourceType.LOCAL
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
        .copy(lastConnectedSources = preferencesStore.lastConnectedSources())

    fun persistConnectedSources(sources: Set<SourceType>) {
        preferencesStore.setLastConnectedSources(sources)
    }

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
            state = currentState.copy(sourceOrder = updatedOrder),
            sourceOrder = updatedOrder,
        )
    }

    /** The full order from a drag-to-reorder list; `null` when nothing changed. */
    fun reorderSources(
        currentState: SourceNavigationState,
        sourceOrder: List<SourceType>,
    ): SourceOrderSelection? {
        val updatedOrder = normalizeSourceOrder(sourceOrder)
        if (updatedOrder == currentState.sourceOrder) return null
        return SourceOrderSelection(
            state = currentState.copy(sourceOrder = updatedOrder),
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
