package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.unifiedreader.core.database.entity.ItemDetailsEntity

@Dao
abstract class ItemDetailsDao {
    @Query("SELECT * FROM item_details WHERE itemId = :itemId")
    abstract fun getDetails(itemId: String): ItemDetailsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplace(details: ItemDetailsEntity)

    /** The whole description its catalog entry carries; the library's rows keep the first 512 characters. */
    @Query("SELECT description FROM readable_items WHERE id = :itemId")
    abstract fun getCatalogDescription(itemId: String): String?
}
