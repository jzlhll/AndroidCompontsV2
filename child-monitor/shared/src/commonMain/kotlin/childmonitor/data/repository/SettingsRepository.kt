package childmonitor.data.repository

import childmonitor.data.database.dao.MonitorDao
import childmonitor.data.database.entity.MonitorSettingsEntity
import childmonitor.data.preferences.UserPreferencesRepository
import childmonitor.model.DefaultMonitorConfig
import childmonitor.model.MonitorSettings
import childmonitor.model.SettingsSnapshot
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 合并两个持久化来源，但不提供跨 Room 和 DataStore 的伪事务。 */
class SettingsRepository(val dao: MonitorDao, val preferences: UserPreferencesRepository) {
    private val initialization = Mutex()
    val settingsFlow = combine(dao.settingsFlow(), preferences.preferencesFlow) { row, prefs ->
        checkNotNull(row) { "Settings not initialized" }
        SettingsSnapshot(row.monitorRevision, decode(row), prefs)
    }

    suspend fun read(): SettingsSnapshot = initialization.withLock {
        var row = dao.settings()
        if (row == null) {
            row = MonitorSettingsEntity(monitorRevision = 0, settingsJson = Json.encodeToString(DefaultMonitorConfig.settings))
            dao.insertSettings(row)
        }
        SettingsSnapshot(row.monitorRevision, decode(row), preferences.read())
    }

    private fun decode(row: MonitorSettingsEntity) = Json.decodeFromString<MonitorSettings>(row.settingsJson).also(MonitorSettings::validate)
}
