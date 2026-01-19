package com.au.module_cached

import com.au.module_android.Globals
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 不支持Set，List。请自行使用Gson进行转换存储。
 * 支持如下：
 *             Int
 *             Long
 *             Double
 *             Float
 *             Boolean
 *             String
 *             ByteArray
 */
object AppDataStore {
    class OnceDataStore(val context:Context, dataStoreName:String) {
        private val Context.mDataStore by preferencesDataStore(name = dataStoreName)
        val dataStore = context.mDataStore
    }

    //对应最终件:/data/data/xxxx/files/datastore/globalDataStore.preferences_pb
    val Context.globalDataStore by preferencesDataStore(
        name = "global_data_store",//指定名称
//    produceMigrations = {context ->  //指定要恢复的sp文件，无需恢复可不写
//        listOf(SharedPreferencesMigration(context, SP_PREFERENCES_NAME))
//    }
    )

    fun onceDataStore(context: Context, dataStoreName:String) = OnceDataStore(context, dataStoreName).dataStore

    fun clear(dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch {dataStore.edit { it.clear() } }
    }

    // Int
    @Deprecated("use containsIntKey instead")
    fun containsIntKeyBlocked(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        return runBlocking { containsIntKey(key, dataStore) }
    }

    suspend fun containsIntKey(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        val prefKey = intPreferencesKey(key)
        return dataStore.data.map { it.contains(prefKey) }.first()
    }

    fun saveInt(key:String, value: Int, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { dataStore.edit { it[intPreferencesKey(key)] = value } }
    }

    @Deprecated("use readInt instead")
    fun readIntBlocked(key:String, defaultValue: Int, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Int {
        return runBlocking { readInt(key, defaultValue, dataStore) }
    }

    suspend fun readInt(key: String, defaultValue: Int, dataStore: DataStore<Preferences> = Globals.app.globalDataStore): Int {
        return dataStore.data.map { it[intPreferencesKey(key)] ?: defaultValue }.first()
    }

    fun removeInt(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { removeIntSuspend(key, dataStore) }
    }

    suspend fun removeIntSuspend(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Int? {
        if (!containsIntKey(key, dataStore)) return null
        var ret : Int? = null
        dataStore.edit { ret = it.remove(intPreferencesKey(key)) }
        return ret
    }

    // Long
    @Deprecated("use containsLongKey instead")
    fun containsLongKeyBlocked(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        return runBlocking { containsLongKey(key, dataStore) }
    }

    suspend fun containsLongKey(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        val prefKey = longPreferencesKey(key)
        return dataStore.data.map { it.contains(prefKey) }.first()
    }

    fun saveLong(key:String, value: Long, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { dataStore.edit { it[longPreferencesKey(key)] = value } }
    }

    @Deprecated("use readLong instead")
    fun readLongBlocked(key:String, defaultValue: Long, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Long {
        return runBlocking { readLong(key, defaultValue, dataStore) }
    }

    suspend fun readLong(key: String, defaultValue: Long, dataStore: DataStore<Preferences> = Globals.app.globalDataStore): Long {
        return dataStore.data.map { it[longPreferencesKey(key)] ?: defaultValue }.first()
    }

    fun removeLong(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { removeLongSuspend(key, dataStore) }
    }

    suspend fun removeLongSuspend(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Long? {
        if (!containsLongKey(key, dataStore)) return null
        var ret : Long? = null
        dataStore.edit { ret = it.remove(longPreferencesKey(key)) }
        return ret
    }

    // Double
    @Deprecated("use containsDoubleKey instead")
    fun containsDoubleKeyBlocked(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        return runBlocking { containsDoubleKey(key, dataStore) }
    }

    suspend fun containsDoubleKey(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        val prefKey = doublePreferencesKey(key)
        return dataStore.data.map { it.contains(prefKey) }.first()
    }

    fun saveDouble(key:String, value: Double, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { dataStore.edit { it[doublePreferencesKey(key)] = value } }
    }

    @Deprecated("use readDouble instead")
    fun readDoubleBlocked(key:String, defaultValue: Double, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Double {
        return runBlocking { readDouble(key, defaultValue, dataStore) }
    }

    suspend fun readDouble(key: String, defaultValue: Double, dataStore: DataStore<Preferences> = Globals.app.globalDataStore): Double {
        return dataStore.data.map { it[doublePreferencesKey(key)] ?: defaultValue }.first()
    }

    fun removeDouble(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { removeDoubleSuspend(key, dataStore) }
    }

    suspend fun removeDoubleSuspend(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Double? {
        if (!containsDoubleKey(key, dataStore)) return null
        var ret : Double? = null
        dataStore.edit { ret = it.remove(doublePreferencesKey(key)) }
        return ret
    }

    // Float
    @Deprecated("use containsFloatKey instead")
    fun containsFloatKeyBlocked(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        return runBlocking { containsFloatKey(key, dataStore) }
    }

    suspend fun containsFloatKey(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        val prefKey = floatPreferencesKey(key)
        return dataStore.data.map { it.contains(prefKey) }.first()
    }

    fun saveFloat(key:String, value: Float, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { dataStore.edit { it[floatPreferencesKey(key)] = value } }
    }

    @Deprecated("use readFloat instead")
    fun readFloatBlocked(key:String, defaultValue: Float, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Float {
        return runBlocking { readFloat(key, defaultValue, dataStore) }
    }

    suspend fun readFloat(key: String, defaultValue: Float, dataStore: DataStore<Preferences> = Globals.app.globalDataStore): Float {
        return dataStore.data.map { it[floatPreferencesKey(key)] ?: defaultValue }.first()
    }

    fun removeFloat(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { removeFloatSuspend(key, dataStore) }
    }

    suspend fun removeFloatSuspend(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Float? {
        if (!containsFloatKey(key, dataStore)) return null
        var ret : Float? = null
        dataStore.edit { ret = it.remove(floatPreferencesKey(key)) }
        return ret
    }

    // Boolean
    @Deprecated("use containsBooleanKey instead")
    fun containsBooleanKeyBlocked(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        return runBlocking { containsBooleanKey(key, dataStore) }
    }

    suspend fun containsBooleanKey(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        val prefKey = booleanPreferencesKey(key)
        return dataStore.data.map { it.contains(prefKey) }.first()
    }

    fun saveBoolean(key:String, value: Boolean, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { dataStore.edit { it[booleanPreferencesKey(key)] = value } }
    }

    @Deprecated("use readBoolean instead")
    fun readBooleanBlocked(key:String, defaultValue: Boolean, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        return runBlocking { readBoolean(key, defaultValue, dataStore) }
    }

    suspend fun readBoolean(key: String, defaultValue: Boolean, dataStore: DataStore<Preferences> = Globals.app.globalDataStore): Boolean {
        return dataStore.data.map { it[booleanPreferencesKey(key)] ?: defaultValue }.first()
    }

    fun removeBoolean(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { removeBooleanSuspend(key, dataStore) }
    }

    suspend fun removeBooleanSuspend(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean? {
        if (!containsBooleanKey(key, dataStore)) return null
        var ret : Boolean? = null
        dataStore.edit { ret = it.remove(booleanPreferencesKey(key)) }
        return ret
    }

    // String
    @Deprecated("use containsStringKey instead")
    fun containsStringKeyBlocked(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        return runBlocking { containsStringKey(key, dataStore) }
    }

    suspend fun containsStringKey(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        val prefKey = stringPreferencesKey(key)
        return dataStore.data.map { it.contains(prefKey) }.first()
    }

    fun saveString(key:String, value: String?, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        if (value == null) {
            removeString(key, dataStore)
        } else {
            Globals.backgroundScope.launch { dataStore.edit { it[stringPreferencesKey(key)] = value } }
        }
    }

    @Deprecated("use readString instead")
    fun readStringBlocked(key:String, defaultValue: String?, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : String? {
        return runBlocking { readString(key, defaultValue, dataStore) }
    }

    suspend fun readString(key: String, defaultValue: String?, dataStore: DataStore<Preferences> = Globals.app.globalDataStore): String? {
        return dataStore.data.map { it[stringPreferencesKey(key)] ?: defaultValue }.first()
    }

    fun removeString(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { removeStringSuspend(key, dataStore) }
    }

    suspend fun removeStringSuspend(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : String? {
        if (!containsStringKey(key, dataStore)) return null
        var ret : String? = null
        dataStore.edit { ret = it.remove(stringPreferencesKey(key)) }
        return ret
    }

    // ByteArray
    @Deprecated("use containsByteArrayKey instead")
    fun containsByteArrayKeyBlocked(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        return runBlocking { containsByteArrayKey(key, dataStore) }
    }

    suspend fun containsByteArrayKey(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : Boolean {
        val prefKey = byteArrayPreferencesKey(key)
        return dataStore.data.map { it.contains(prefKey) }.first()
    }

    fun saveByteArray(key:String, value: ByteArray?, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        if (value == null) {
            removeByteArray(key, dataStore)
        } else {
            Globals.backgroundScope.launch { dataStore.edit { it[byteArrayPreferencesKey(key)] = value } }
        }
    }

    @Deprecated("use readByteArray instead")
    fun readByteArrayBlocked(key:String, defaultValue: ByteArray?, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : ByteArray? {
        return runBlocking { readByteArray(key, defaultValue, dataStore) }
    }

    suspend fun readByteArray(key: String, defaultValue: ByteArray?, dataStore: DataStore<Preferences> = Globals.app.globalDataStore): ByteArray? {
        return dataStore.data.map { it[byteArrayPreferencesKey(key)] ?: defaultValue }.first()
    }

    fun removeByteArray(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) {
        Globals.backgroundScope.launch { removeByteArraySuspend(key, dataStore) }
    }

    suspend fun removeByteArraySuspend(key:String, dataStore: DataStore<Preferences> = Globals.app.globalDataStore) : ByteArray? {
        if (!containsByteArrayKey(key, dataStore)) return null
        var ret : ByteArray? = null
        dataStore.edit { ret = it.remove(byteArrayPreferencesKey(key)) }
        return ret
    }
}
