package childmonitor.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import childmonitor.model.AvatarCatalog
import childmonitor.model.DefaultMonitorConfig
import childmonitor.model.UserPreferences
import childmonitor.model.UserPreferencesPatch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 偏好与监控配置分开版本化，读取失败或未知格式不会被默认值覆盖。 */
class UserPreferencesRepository(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("user_preferences_v1")
    val preferencesFlow = store.data.map { values ->
        values[key]?.let { Json.decodeFromString<UserPreferences>(it).also(UserPreferences::validate) }
            ?: DefaultMonitorConfig.preferences
    }
    suspend fun read() = preferencesFlow.first()

    suspend fun update(patch: UserPreferencesPatch, expectedRevision: Long): UserPreferences {
        var saved: UserPreferences? = null
        store.edit { values ->
            val old = values[key]?.let { Json.decodeFromString<UserPreferences>(it).also(UserPreferences::validate) }
                ?: DefaultMonitorConfig.preferences
            check(old.preferencesRevision == expectedRevision) { "Preferences revision conflict" }
            patch.avatarId?.let { require(it in AvatarCatalog.ids) }
            val updated = old.copy(
                preferencesRevision = old.preferencesRevision + 1,
                avatarId = patch.avatarId ?: old.avatarId,
                darkenAfterMs = patch.darkenAfterMs ?: old.darkenAfterMs,
                retentionDays = patch.retentionDays ?: old.retentionDays,
            ).also(UserPreferences::validate)
            values[key] = Json.encodeToString(updated)
            saved = updated
        }
        return checkNotNull(saved)
    }
}
