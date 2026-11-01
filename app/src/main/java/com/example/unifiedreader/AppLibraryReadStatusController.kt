package com.example.unifiedreader
import com.example.unifiedreader.core.model.ReadableItem
import javax.inject.Inject

class AppLibraryReadStatusController @Inject constructor(
    private val applyReadStatusChangeUseCase: ApplyReadStatusChangeUseCase,
) {
    /** Marks [items] finished or unread; [onProgressChanged] runs once, with how many changed, when any did. */
    suspend fun setReadStatus(
        items: List<ReadableItem>,
        markAsRead: Boolean,
        onProgressChanged: (changed: Int) -> Unit,
    ) {
        val changed = items.count { item ->
            applyReadStatusChangeUseCase(item = item, markAsRead = markAsRead).applied
        }
        if (changed == 0) return
        onProgressChanged(changed)
    }
}
