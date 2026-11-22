package com.allan.mydroid.state

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDoneException
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import androidx.core.database.sqlite.transaction
import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.au.module_android.Globals
import com.au.module_gson.mmkv.mmkvGetInt
import com.au.module_gson.mmkv.mmkvSetAny

/** 保存手机端共用的聊天时间线和保留天数。 */
object TextChatBackup {
    private const val RETENTION_DAYS_KEY = "key_text_chat_history_retention_days"
    private const val TABLE = "chat_timeline"
    private const val CURSOR_TEXT_BYTES = 64 * 1024
    private const val HOST_REPLAY_LIMIT = 100
    private const val ROW_FIELDS = "id, timestamp, ip, host, icon_color, is_mine, divider_kind"
    private const val INLINE_TEXT_FIELD =
        "CASE WHEN length(CAST(message_text AS BLOB)) <= $CURSOR_TEXT_BYTES THEN message_text END"
    private val database by lazy { HistoryDatabase().apply { setWriteAheadLoggingEnabled(true) } }

    var retentionDays: Int
        get() = when (val days = mmkvGetInt(RETENTION_DAYS_KEY)) {
            7, 30, 120 -> days
            else -> 0
        }
        set(value) {
            mmkvSetAny(RETENTION_DAYS_KEY, value)
        }

    fun load(cutoff: Long? = null): List<TextChatRow> = buildList {
        val db = database.writableDatabase
        db.transaction(exclusive = false) {
            if (cutoff != null) deleteExpiredRows(cutoff)
            deleteRedundantCreatedDividers()
        }
        db.compileTextLookup().use { textLookup ->
            db.rawQuery("SELECT $ROW_FIELDS, $INLINE_TEXT_FIELD FROM $TABLE ORDER BY id", null).use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.isNull(6)) {
                        cursor.readMessage(textLookup)?.let(::add)
                    } else {
                        add(
                            TextChatRow.Divider(
                                cursor.getLong(0), cursor.getLong(1), TextChatDividerKind.valueOf(cursor.getString(6)),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun loadHostReplay(cutoff: Long): List<TextChatMessageBean> {
        val db = database.readableDatabase
        val sql = """
            SELECT $ROW_FIELDS, NULL FROM $TABLE
            WHERE room_key IS NULL AND divider_kind IS NULL AND timestamp >= ?
            ORDER BY id DESC LIMIT $HOST_REPLAY_LIMIT
        """.trimIndent()
        val newestFirst = ArrayList<TextChatMessageBean>(HOST_REPLAY_LIMIT)
        db.compileTextLookup().use { textLookup ->
            db.rawQuery(sql, arrayOf(cutoff.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    val timestamp = cursor.getLong(1)
                    val newer = newestFirst.lastOrNull()
                    if (newer != null && newer.timestamp - timestamp > TEXT_CHAT_GAP_MS) break
                    val message = cursor.readMessage(textLookup) ?: continue
                    newestFirst.add(message.bean)
                }
            }
        }
        return newestFirst.asReversed()
    }

    fun appendMessage(roomKey: String?, bean: TextChatMessageBean, isMine: Boolean): TextChatRow.Message? {
        val db = database.writableDatabase
        if (roomKey != null && db.hasClientMessage(roomKey, bean)) return null
        val values = ContentValues().apply {
            put("message_text", bean.text)
            put("ip", bean.ip)
            put("host", bean.host)
            put("timestamp", bean.timestamp)
            put("icon_color", bean.iconColor)
            put("is_mine", if (isMine) 1 else 0)
            put("room_key", roomKey)
        }
        return TextChatRow.Message(db.insertOrThrow(TABLE, null, values), bean, isMine)
    }

    fun appendDivider(timestamp: Long, kind: TextChatDividerKind, creationKey: String?): TextChatRow.Divider? {
        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("divider_kind", kind.name)
            put("creation_key", creationKey)
        }
        return database.writableDatabase.transaction(exclusive = false) {
            val id = insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (id == -1L) return@transaction null
            if (kind == TextChatDividerKind.CREATED) {
                delete(
                    TABLE,
                    "id = (SELECT id FROM $TABLE WHERE id < ? ORDER BY id DESC LIMIT 1) AND divider_kind = ?",
                    arrayOf(id.toString(), kind.name),
                )
            }
            TextChatRow.Divider(id, timestamp, kind)
        }
    }

    fun deleteBefore(cutoff: Long): Boolean = database.writableDatabase.transaction(exclusive = false) {
        val deleted = deleteExpiredRows(cutoff) > 0
        if (deleted) deleteRedundantCreatedDividers()
        deleted
    }

    private fun SQLiteDatabase.deleteExpiredRows(cutoff: Long): Int =
        delete(TABLE, "timestamp < ?", arrayOf(cutoff.toString()))

    private fun SQLiteDatabase.deleteRedundantCreatedDividers() {
        val created = TextChatDividerKind.CREATED.name
        // 按 id 找后一条实际存储的记录，保留每段连续 CREATED 的最后一条。
        delete(
            TABLE,
            """
            divider_kind = ? AND (
                SELECT divider_kind FROM $TABLE AS next_row
                WHERE next_row.id > $TABLE.id
                ORDER BY next_row.id LIMIT 1
            ) = ?
            """.trimIndent(),
            arrayOf(created, created),
        )
    }

    private fun SQLiteDatabase.hasClientMessage(roomKey: String, bean: TextChatMessageBean): Boolean {
        val sql = "SELECT EXISTS(SELECT 1 FROM $TABLE WHERE room_key = ? AND ip = ? AND timestamp = ? AND message_text = ?)"
        return compileStatement(sql).use { statement ->
            statement.bindString(1, roomKey)
            statement.bindString(2, bean.ip)
            statement.bindLong(3, bean.timestamp)
            statement.bindString(4, bean.text)
            statement.simpleQueryForLong() != 0L
        }
    }

    private fun SQLiteDatabase.compileTextLookup(): SQLiteStatement =
        compileStatement("SELECT message_text FROM $TABLE WHERE id = ?")

    private fun Cursor.readMessage(textLookup: SQLiteStatement): TextChatRow.Message? {
        val id = getLong(0)
        // 大段粘贴文本和回放正文逐条读取，让回放元数据留在同一个 CursorWindow 内。
        val text = if (isNull(7)) {
            textLookup.bindLong(1, id)
            try {
                textLookup.simpleQueryForString()
            } catch (_: SQLiteDoneException) {
                // 回放并行读取时，进入页面触发的清理可能已经删除了这一行。
                return null
            }
        } else {
            getString(7)
        }
        return TextChatRow.Message(
            id,
            TextChatMessageBean(
                text = text,
                ip = getString(2),
                host = getString(3),
                timestamp = getLong(1),
                iconColor = getString(4),
            ),
            isMine = getInt(5) != 0,
        )
    }

    private class HistoryDatabase : SQLiteOpenHelper(Globals.app, "text_chat_history.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    message_text TEXT,
                    ip TEXT,
                    host TEXT,
                    timestamp INTEGER NOT NULL,
                    icon_color TEXT,
                    is_mine INTEGER NOT NULL DEFAULT 0,
                    room_key TEXT,
                    divider_kind TEXT,
                    creation_key TEXT UNIQUE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX chat_timeline_timestamp ON $TABLE (timestamp)")
            db.execSQL("CREATE INDEX chat_timeline_client_lookup ON $TABLE (room_key, ip, timestamp) WHERE room_key IS NOT NULL")
            db.execSQL("CREATE INDEX chat_timeline_host_replay ON $TABLE (id, timestamp) WHERE room_key IS NULL AND divider_kind IS NULL")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion == 1) {
                onCreate(db)
                // v1 只保存原生主机一侧的聊天，专属头像色标识本机发送的消息。
                db.execSQL(
                    """
                    INSERT INTO $TABLE (id, message_text, ip, host, timestamp, icon_color, is_mine)
                    SELECT id, message_text, ip, host, timestamp, icon_color, icon_color = '#6A1B9A'
                    FROM chat_messages
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE chat_messages")
            }
        }
    }
}
