package com.example.unifiedreader.core.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.unifiedreader.core.database.entity.ReaderSessionStateEntity;

@Dao
public interface ReaderSessionStateDao {
    @Query("SELECT * FROM reader_session_state")
    java.util.List<ReaderSessionStateEntity> getAll();

    @Query("SELECT EXISTS(SELECT 1 FROM reader_session_state LIMIT 1)")
    boolean hasAny();

    @Query("SELECT * FROM reader_session_state WHERE itemId = :itemId")
    ReaderSessionStateEntity getByItemId(String itemId);

    @Query("DELETE FROM reader_session_state WHERE itemId = :itemId")
    void deleteByItemId(String itemId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(ReaderSessionStateEntity entity);
}
