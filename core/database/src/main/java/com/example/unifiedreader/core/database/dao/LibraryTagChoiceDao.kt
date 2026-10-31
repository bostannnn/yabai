package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.unifiedreader.core.database.entity.LibraryTagChoiceEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibraryTagChoiceDao {
    @Query("SELECT * FROM library_tag_choices")
    abstract fun observeChoices(): Flow<List<LibraryTagChoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun putChoices(choices: List<LibraryTagChoiceEntity>)
}
