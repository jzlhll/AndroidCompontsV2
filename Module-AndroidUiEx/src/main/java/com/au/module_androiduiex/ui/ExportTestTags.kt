package com.au.module_androiduiex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * 把子树里的 `Modifier.testTag` 导出为 UiAutomator 可见的 resource-id，见 docs/ui-automation-accessibility.md。
 * 每个 Compose 根（ComposeView.setContent、Dialog、Popup、ModalBottomSheet）都要各自包一层，弹层不继承页面根的设置。
 * propagateMinConstraints 让子内容拿到与直接放在根上时相同的约束，不改变原有布局。
 */
@Composable
fun ExportTestTags(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        propagateMinConstraints = true,
    ) {
        content()
    }
}
