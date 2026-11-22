package com.allan.mydroid.state

import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.au.module_android.Globals
import com.au.module_android.utilthread.SingleCoroutineTaskExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.days

/** 串行更新手机端共用的聊天数据库和时间线。 */
class GlobalTextChatObj {
    private val historyTasks = SingleCoroutineTaskExecutor(Dispatchers.IO, Globals.backgroundScope)
    private val _timelineFlow = MutableStateFlow<List<TextChatRow>>(emptyList())
    val timelineFlow: StateFlow<List<TextChatRow>> = _timelineFlow.asStateFlow()

    @Volatile
    private var replayCutoff = Long.MIN_VALUE
    private var historyLoaded = false
    private var lastMessage: TextChatRow.Message? = null

    fun prepareHistoryForEntry() {
        val days = TextChatBackup.retentionDays
        val cutoff = if (days > 0) System.currentTimeMillis() - days.days.inWholeMilliseconds else null
        replayCutoff = cutoff ?: Long.MIN_VALUE
        historyTasks.submit {
            if (!historyLoaded) {
                rebuildTimeline(TextChatBackup.load(cutoff).asSequence())
            } else if (cutoff != null && TextChatBackup.deleteBefore(cutoff)) {
                rebuildTimeline(_timelineFlow.value.asSequence().filter { row ->
                    row.timestamp >= cutoff && (row !is TextChatRow.Divider || row.kind != TextChatDividerKind.GAP)
                })
            }
        }
    }

    fun recordCreatedRoom(creationKey: String) {
        recordDivider(TextChatDividerKind.CREATED, creationKey)
    }

    fun recordJoinedRoom() {
        recordDivider(TextChatDividerKind.JOINED)
    }

    fun addHostMessage(bean: TextChatMessageBean, isMine: Boolean, onStored: (() -> Unit)? = null) {
        historyTasks.submit {
            val row = TextChatBackup.appendMessage(null, bean, isMine) ?: return@submit
            appendToTimeline(row)
            onStored?.invoke()
        }
    }

    fun addClientMessage(
        roomKey: String,
        bean: TextChatMessageBean,
        isMine: Boolean,
        replayed: Boolean = false,
        onStored: (() -> Unit)? = null,
    ) {
        historyTasks.submit {
            if (replayed && bean.timestamp < replayCutoff) return@submit
            val row = TextChatBackup.appendMessage(roomKey, bean, isMine) ?: return@submit
            appendToTimeline(row)
            onStored?.invoke()
        }
    }

    fun hostReplaySnapshot(): List<TextChatMessageBean> =
        TextChatBackup.loadHostReplay(replayCutoff)

    private fun recordDivider(kind: TextChatDividerKind, creationKey: String? = null) {
        val timestamp = System.currentTimeMillis()
        historyTasks.submit {
            val row = TextChatBackup.appendDivider(timestamp, kind, creationKey) ?: return@submit
            appendToTimeline(row)
        }
    }

    private fun appendToTimeline(row: TextChatRow) {
        if (!historyLoaded) {
            rebuildTimeline(TextChatBackup.load().asSequence())
            return
        }
        val timeline = _timelineFlow.value
        _timelineFlow.value = buildList(timeline.size + 2) {
            addAll(timeline)
            appendStoredRow(row)
        }
    }

    private fun rebuildTimeline(storedRows: Sequence<TextChatRow>) {
        lastMessage = null
        _timelineFlow.value = buildList {
            for (row in storedRows) {
                appendStoredRow(row)
            }
        }
        historyLoaded = true
    }

    private fun MutableList<TextChatRow>.appendStoredRow(row: TextChatRow) {
        if (row is TextChatRow.Message) {
            gapBefore(row)?.let(::add)
            lastMessage = row
        }
        val previous = lastOrNull()
        if (row is TextChatRow.Divider && row.kind == TextChatDividerKind.CREATED &&
            previous is TextChatRow.Divider && previous.kind == TextChatDividerKind.CREATED
        ) {
            this[lastIndex] = row
        } else {
            add(row)
        }
    }

    private fun gapBefore(message: TextChatRow.Message): TextChatRow.Divider? {
        val previous = lastMessage ?: return null
        if (message.timestamp - previous.timestamp <= TEXT_CHAT_GAP_MS) return null
        return TextChatRow.Divider(message.id.inv(), message.timestamp, TextChatDividerKind.GAP)
    }
}
