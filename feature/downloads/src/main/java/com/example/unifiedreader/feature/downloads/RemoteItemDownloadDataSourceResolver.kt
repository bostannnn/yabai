package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.ReadableItem

fun List<RemoteItemDownloadDataSource>.downloadDataSourceFor(
    item: ReadableItem,
): RemoteItemDownloadDataSource? = firstOrNull { dataSource ->
    dataSource.supports(item)
}
