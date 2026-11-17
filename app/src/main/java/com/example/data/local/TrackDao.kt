package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadStatus
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE sourceType = 'LOCAL' OR downloadStatus = 'DOWNLOADED' ORDER BY addedAt DESC")
    fun observeOfflineReadyTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun observeFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 10")
    fun observeRecentlyPlayed(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Update
    suspend fun update(track: TrackEntity)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun markPlayed(id: String, timestamp: Long)

    @Query(
        "UPDATE tracks SET downloadStatus = :status, localFilePath = :localFilePath, " +
            "fileSizeBytes = :fileSizeBytes, sourceType = :sourceType WHERE id = :id"
    )
    suspend fun updateDownloadState(
        id: String,
        status: DownloadStatus,
        localFilePath: String?,
        fileSizeBytes: Long,
        sourceType: TrackSourceType,
    )

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)
}
