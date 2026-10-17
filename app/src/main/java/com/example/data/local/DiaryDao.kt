package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM local_diaries ORDER BY createdAtEpoch DESC")
    fun getAllDiariesFlow(): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM local_diaries ORDER BY createdAtEpoch DESC")
    suspend fun getAllDiaries(): List<DiaryEntity>

    @Query("SELECT * FROM local_diaries WHERE id = :id LIMIT 1")
    suspend fun getDiaryById(id: String): DiaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(diary: DiaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(diaries: List<DiaryEntity>)

    @Update
    suspend fun update(diary: DiaryEntity)

    @Query("DELETE FROM local_diaries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM local_diaries")
    suspend fun clearAll()
}
