package cl.medidorwifi

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.repeatOnLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StorageLifecycleTest {
    @Test
    fun recordsSurviveReopeningAndPruneIndependently() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filename = "persistence-test.db"
        context.deleteDatabase(filename)
        var db = Room.databaseBuilder(context, LocalDatabase::class.java, filename).build()
        val antenna = Antenna("aa:bb:cc:dd:ee:01", "Caja", "Entrada", "192.168.1.2")
        db.records().register(antenna)
        db.records()
            .save(
                Reading(
                    time = 10,
                    zone = "Caja",
                    name = "Caja",
                    bssid = antenna.bssid,
                    ssid = "Tienda",
                    rssi = -62,
                    frequency = 2412,
                    speed = 72,
                    ip = "192.168.1.3",
                )
            )
        db.records()
            .event(
                Event(
                    time = 20,
                    kind = "Información insuficiente",
                    source = antenna.bssid,
                    destination = "aa:bb:cc:dd:ee:02",
                    fromRssi = -70,
                    toRssi = -55,
                    dwell = 2000,
                    interval = 1000,
                    gap = null,
                )
            )
        db.close()
        db = Room.databaseBuilder(context, LocalDatabase::class.java, filename).build()
        assertEquals(antenna, db.records().antennas().first().single())
        assertEquals(-62, db.records().readings().first().single().rssi)
        assertEquals(1, db.records().events().first().size)
        db.records().pruneReadings(11)
        assertTrue(db.records().readings().first().isEmpty())
        assertEquals(1, db.records().events().first().size)
        db.records().clearEvents()
        assertEquals(1, db.records().antennas().first().size)
        db.close()
        context.deleteDatabase(filename)
        Unit
    }

    @Test
    fun groupRenameUpdatesEveryInterface() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).build()
        try {
            db.records().register(Antenna("aa:bb:cc:dd:ee:01", "Caja", "Entrada", ""))
            db.records().register(Antenna("aa:bb:cc:dd:ee:02", "Caja", "Entrada", ""))
            db.records().renameGroup("Caja", "Caja central", "Cajas", "192.168.1.2")
            val group = db.records().antennas().first()
            assertEquals(2, group.size)
            assertTrue(group.all { it.name == "Caja central" && it.zone == "Cajas" })
        } finally {
            db.close()
        }
    }

    @Test
    fun manifestHasNoBackgroundOrInternetCapability() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS or
                    android.content.pm.PackageManager.GET_SERVICES,
            )
        val permissions = info.requestedPermissions?.toList().orEmpty()
        assertTrue(permissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
        assertFalse(permissions.contains(android.Manifest.permission.INTERNET))
        assertFalse(permissions.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))
        assertFalse(permissions.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(info.services.isNullOrEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun resumedMonitoringCancelsAndStartsFresh() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val owner =
                object : LifecycleOwner {
                    val registry = LifecycleRegistry.createUnsafe(this)
                    override val lifecycle: Lifecycle
                        get() = registry
                }
            var starts = 0
            var stops = 0
            val job =
                launch(dispatcher) {
                    owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        starts++
                        try {
                            awaitCancellation()
                        } finally {
                            stops++
                        }
                    }
                }
            owner.registry.currentState = Lifecycle.State.RESUMED
            assertEquals(1, starts)
            owner.registry.currentState = Lifecycle.State.STARTED
            assertEquals(1, stops)
            owner.registry.currentState = Lifecycle.State.RESUMED
            assertEquals(2, starts)
            owner.registry.currentState = Lifecycle.State.DESTROYED
            job.join()
            assertEquals(2, stops)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
