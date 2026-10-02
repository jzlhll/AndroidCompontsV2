package childmonitor.storage

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import childmonitor.data.database.MonitorDatabase
import childmonitor.data.preferences.UserPreferencesRepository
import childmonitor.data.repository.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath

/** 由 Application 单例容器持有，确保同一个偏好文件只有一个 DataStore。 */
class AndroidPersistence(context: Context, scope: CoroutineScope) {
    val files = AndroidFileStorage(context)
    private val databaseDir = File(files.root, "database").apply { check(isDirectory || mkdirs()) }
    private val preferencesDir = File(files.root, "preferences").apply { check(isDirectory || mkdirs()) }
    val database = Room.databaseBuilder<MonitorDatabase>(
        context.applicationContext, File(databaseDir, "monitor.db").absolutePath,
    ).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    val dao = database.monitorDao()
    val preferences = UserPreferencesRepository(PreferenceDataStoreFactory.createWithPath(
        scope = scope,
        produceFile = { File(preferencesDir, "user.preferences_pb").absolutePath.toPath() },
    ))
    val settings = SettingsRepository(dao, preferences)
}
