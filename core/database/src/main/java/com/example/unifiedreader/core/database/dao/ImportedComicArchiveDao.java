package com.example.unifiedreader.core.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.unifiedreader.core.database.entity.ImportedComicArchiveEntity;

import java.util.List;

@Dao
public interface ImportedComicArchiveDao {
    @Query("SELECT * FROM imported_comic_archives ORDER BY CASE WHEN title = '' THEN displayName ELSE title END COLLATE NOCASE ASC, displayName COLLATE NOCASE ASC")
    List<ImportedComicArchiveEntity> getAll();

    @Query("SELECT * FROM imported_comic_archives WHERE itemId = :itemId")
    ImportedComicArchiveEntity getByItemId(String itemId);

    @Query("DELETE FROM imported_comic_archives WHERE itemId = :itemId")
    void deleteByItemId(String itemId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(ImportedComicArchiveEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(List<ImportedComicArchiveEntity> entities);
}
