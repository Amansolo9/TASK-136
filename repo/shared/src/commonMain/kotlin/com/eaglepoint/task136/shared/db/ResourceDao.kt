package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ResourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(resource: ResourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(resources: List<ResourceEntity>)

    @Update
    suspend fun update(resource: ResourceEntity)

    @Query("SELECT * FROM resources WHERE id = :id")
    suspend fun getById(id: String): ResourceEntity?

    @Query("SELECT * FROM resources ORDER BY name ASC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<ResourceEntity>

    @Query("SELECT COUNT(*) FROM resources")
    suspend fun countAll(): Int

    @Query("DELETE FROM resources WHERE id = :id")
    suspend fun deleteById(id: String)
}
