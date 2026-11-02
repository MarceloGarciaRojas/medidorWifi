package cl.medidorwifi

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.flow.map

@Entity
data class Antenna(
    @PrimaryKey val bssid: String,
    val name: String,
    val zone: String,
    val managementIp: String,
)

@Entity
data class Reading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val zone: String,
    val name: String,
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequency: Int,
    val speed: Int?,
    val ip: String?,
)

@Entity
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val kind: String,
    val source: String,
    val destination: String,
    val fromRssi: Int?,
    val toRssi: Int?,
    val dwell: Long?,
    val interval: Long?,
    val gap: Long?,
    val sourceName: String = source,
    val destinationName: String = destination,
)

@Dao
interface Records {
    @Query("SELECT * FROM Antenna ORDER BY name")
    fun antennas(): kotlinx.coroutines.flow.Flow<List<Antenna>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun register(value: Antenna)

    @Delete suspend fun remove(value: Antenna)

    @Query(
        "UPDATE Antenna SET name = :newName, zone = :zone, managementIp = :ip WHERE name = :oldName"
    )
    suspend fun renameGroup(oldName: String, newName: String, zone: String, ip: String)

    @Query("SELECT * FROM Reading ORDER BY time DESC")
    fun readings(): kotlinx.coroutines.flow.Flow<List<Reading>>

    @Insert suspend fun save(value: Reading)

    @Query("SELECT * FROM Event ORDER BY time DESC")
    fun events(): kotlinx.coroutines.flow.Flow<List<Event>>

    @Insert suspend fun event(value: Event)

    @Query("DELETE FROM Reading") suspend fun clearReadings()

    @Query("DELETE FROM Event") suspend fun clearEvents()

    @Query("DELETE FROM Reading WHERE time < :cutoff") suspend fun pruneReadings(cutoff: Long)

    @Query("DELETE FROM Event WHERE time < :cutoff") suspend fun pruneEvents(cutoff: Long)
}

@Database(
    entities = [Antenna::class, Reading::class, Event::class],
    version = 1,
    exportSchema = false,
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun records(): Records
}

val Context.preferences by preferencesDataStore("settings")

data class Settings(
    val theme: String = "Automático",
    val beep: Boolean = false,
    val blink: Boolean = false,
    val vibration: Boolean = false,
    val weak: Int = -75,
    val good: Int = -65,
    val delta: Int = 15,
    val persistence: Int = 10,
    val pinned: String = "",
)

class Preferences(private val context: Context) {
    val flow =
        context.preferences.data.map { p ->
            Settings(
                p[stringPreferencesKey("theme")] ?: "Automático",
                p[booleanPreferencesKey("beep")] ?: false,
                p[booleanPreferencesKey("blink")] ?: false,
                p[booleanPreferencesKey("vibration")] ?: false,
                p[intPreferencesKey("weak")] ?: -75,
                p[intPreferencesKey("good")] ?: -65,
                p[intPreferencesKey("delta")] ?: 15,
                p[intPreferencesKey("persistence")] ?: 10,
                p[stringPreferencesKey("pinned")] ?: "",
            )
        }

    suspend fun set(name: String, value: Boolean) {
        context.preferences.edit { it[booleanPreferencesKey(name)] = value }
    }

    suspend fun set(name: String, value: String) {
        context.preferences.edit { it[stringPreferencesKey(name)] = value }
    }

    suspend fun set(name: String, value: Int) {
        context.preferences.edit { it[intPreferencesKey(name)] = value }
    }
}
