package com.example.unifiedreader.core.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.unifiedreader.core.database.entity.ReadingProgressEntity;

@Dao
public interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress")
    java.util.List<ReadingProgressEntity> getAll();

    @Query("SELECT * FROM reading_progress WHERE itemId = :itemId")
    ReadingProgressEntity getByItemId(String itemId);

    @Query("DELETE FROM reading_progress WHERE itemId = :itemId")
    void deleteByItemId(String itemId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(ReadingProgressEntity entity);
}
