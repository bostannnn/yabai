package com.example.unifiedreader.feature.library

data class LibraryReadingListScopeState(
    val options: List<LibraryReadingListScopeOption> = emptyList(),
    val selectedOptionId: String? = null,
    val isLoadingOptions: Boolean = false,
    val isLoadingSelectedItems: Boolean = false,
    val errorMessage: String? = null,
) {
    val selectedOption: LibraryReadingListScopeOption?
        get() = options.firstOrNull { option -> option.id == selectedOptionId }

    val shouldShow: Boolean
        get() = options.isNotEmpty() ||
            selectedOptionId != null ||
            isLoadingOptions ||
            isLoadingSelectedItems ||
            errorMessage != null
}

data class LibraryReadingListScopeOption(
    val id: String,
    val title: String,
    val itemCount: Int,
)
