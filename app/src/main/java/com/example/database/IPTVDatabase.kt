package com.example.database

import android.content.Context
import androidx.room.*
import com.example.model.FavoriteChannel
import com.example.model.PlaylistSource
import kotlinx.coroutines.flow.Flow

@Dao
interface IPTVDao {
    // ----------------- Favorites -----------------
    @Query("SELECT * FROM favorites ORDER BY name ASC")
    fun getAllFavorites(): Flow<List<FavoriteChannel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(channel: FavoriteChannel)

    @Query("DELETE FROM favorites WHERE streamUrl = :streamUrl")
    suspend fun deleteFavoriteByUrl(streamUrl: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE streamUrl = :streamUrl LIMIT 1)")
    suspend fun isFavoriteDirect(streamUrl: String): Boolean

    // ----------------- Playlists -----------------
    @Query("SELECT * FROM playlist_sources ORDER BY addedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistSource>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistSource)

    @Query("DELETE FROM playlist_sources WHERE id = :id")
    suspend fun deletePlaylistById(id: Int)
}

@Database(entities = [FavoriteChannel::class, PlaylistSource::class], version = 1, exportSchema = false)
abstract class IPTVDatabase : RoomDatabase() {
    abstract fun iptvDao(): IPTVDao

    companion object {
        @Volatile
        private var INSTANCE: IPTVDatabase? = null

        fun getDatabase(context: Context): IPTVDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IPTVDatabase::class.java,
                    "iptv_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Repository separating UI operations from direct DB access.
 */
class IPTVRepository(private val iptvDao: IPTVDao) {
    val favoriteChannels: Flow<List<FavoriteChannel>> = iptvDao.getAllFavorites()
    val playlists: Flow<List<PlaylistSource>> = iptvDao.getAllPlaylists()

    suspend fun addFavorite(fav: FavoriteChannel) {
        iptvDao.insertFavorite(fav)
    }

    suspend fun removeFavorite(streamUrl: String) {
        iptvDao.deleteFavoriteByUrl(streamUrl)
    }

    suspend fun isFavorite(streamUrl: String): Boolean {
        return iptvDao.isFavoriteDirect(streamUrl)
    }

    suspend fun addPlaylist(playlist: PlaylistSource) {
        iptvDao.insertPlaylist(playlist)
    }

    suspend fun removePlaylist(id: Int) {
        iptvDao.deletePlaylistById(id)
    }
}
