package com.example.unifiedreader
import com.example.unifiedreader.core.model.ReadableItem
import javax.inject.Inject

class AppLibraryReadStatusController @Inject constructor(
    private val applyReadStatusChangeUseCase: ApplyReadStatusChangeUseCase,
) {
    suspend fun setReadStatus(
        item: ReadableItem,
        markAsRead: Boolean,
        onProgressChanged: () -> Unit,
    ) {
        val result = applyReadStatusChangeUseCase(
            item = item,
            markAsRead = markAsRead,
        )
        if (!result.applied) return
        onProgressChanged()
    }
}
