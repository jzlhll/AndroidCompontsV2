package com.allan.mydroid.views.chat

import androidx.core.view.ViewCompat
import com.allan.mydroid.R
import com.allan.mydroid.databinding.LayoutTextChatSettingsBinding
import com.allan.mydroid.state.TextChatBackup

private val retentionDaysByOption = mapOf(
    R.id.retentionSevenDaysBtn to 7,
    R.id.retentionThirtyDaysBtn to 30,
    R.id.retentionOneHundredTwentyDaysBtn to 120,
    R.id.retentionNoCleanupBtn to 0,
)

fun LayoutTextChatSettingsBinding.bindChatSettings(
    onClose: () -> Unit,
    onRetentionChanged: () -> Unit = {},
) {
    val retentionDays = TextChatBackup.retentionDays
    val checkedId = retentionDaysByOption.entries.firstOrNull { it.value == retentionDays }?.key
        ?: R.id.retentionNoCleanupBtn
    retentionLayout.check(checkedId)
    retentionLayout.setOnCheckedChangeListener { _, option ->
        TextChatBackup.retentionDays = retentionDaysByOption[option] ?: return@setOnCheckedChangeListener
        onRetentionChanged()
    }
    settingsToolbar.setNavigationOnClickListener { onClose() }
    ViewCompat.setAccessibilityPaneTitle(root, root.context.getString(R.string.text_chat_settings))
}
