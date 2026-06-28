package com.au.module_androiduiex.styles

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 左侧图标 + 居中文案的顶栏。
 */
@Composable
fun ComposeHeaderLeftTitle(
    title: String,
    @DrawableRes leftIconResId: Int,
    onLeftClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftIconContentDescription: String? = null,
    titleStyle: TextStyle = ComposeTypography.Font20M,
    applyStatusBarsPadding: Boolean = true,
) {
    Row(
        modifier = modifier.applyHeaderStatusBarsPadding(applyStatusBarsPadding)
            .fillMaxWidth()
            .padding(horizontal = ComposeDimens.PaddingEdge)
            .padding(top = ComposeDimens.ToolbarMarginTop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComposeHeaderIcon(
            iconResId = leftIconResId,
            contentDescription = leftIconContentDescription,
            iconSize = ComposeDimens.ToolbarHeight,
            onClick = onLeftClick,
        )

        BasicText(
            text = title,
            style = titleStyle.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = ComposeDimens.PaddingEdge / 2),
        )

        // 与左侧图标等宽，保证标题相对屏幕居中
        Spacer(modifier = Modifier.size(ComposeDimens.ToolbarHeight))
    }
}

/**
 * 左侧图标 + 居中文案 + 右侧图标的顶栏。
 */
@Composable
fun ComposeHeaderLeftTitleRight(
    title: String,
    @DrawableRes leftIconResId: Int,
    onLeftClick: () -> Unit,
    @DrawableRes rightIconResId: Int,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftIconContentDescription: String? = null,
    rightIconContentDescription: String? = null,
    titleStyle: TextStyle = ComposeTypography.Font20M,
    applyStatusBarsPadding: Boolean = true,
) {
    Row(
        modifier = modifier.applyHeaderStatusBarsPadding(applyStatusBarsPadding)
            .fillMaxWidth()
            .padding(horizontal = ComposeDimens.PaddingEdge)
            .padding(top = ComposeDimens.ToolbarMarginTop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComposeHeaderIcon(
            iconResId = leftIconResId,
            contentDescription = leftIconContentDescription,
            iconSize = ComposeDimens.ToolbarHeight,
            onClick = onLeftClick,
        )

        BasicText(
            text = title,
            style = titleStyle.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = ComposeDimens.PaddingEdge / 2),
        )

        ComposeHeaderIcon(
            iconResId = rightIconResId,
            contentDescription = rightIconContentDescription,
            iconSize = ComposeDimens.ToolbarHeight,
            onClick = onRightClick,
        )
    }
}

/**
 * 左侧图标 + 居中文案 + 右侧文字按钮的顶栏。
 */
@Composable
fun ComposeHeaderLeftTitleRightText(
    title: String,
    @DrawableRes leftIconResId: Int,
    onLeftClick: () -> Unit,
    rightText: String,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftIconContentDescription: String? = null,
    titleStyle: TextStyle = ComposeTypography.Font20M,
    rightEnabled: Boolean = true,
    applyStatusBarsPadding: Boolean = true,
) {
    Box(
        modifier = modifier.applyHeaderStatusBarsPadding(applyStatusBarsPadding)
            .fillMaxWidth()
            .padding(top = ComposeDimens.ToolbarMarginTop),
        contentAlignment = Alignment.Center,
    ) {
        ComposeHeaderIcon(
            iconResId = leftIconResId,
            contentDescription = leftIconContentDescription,
            iconSize = ComposeDimens.ToolbarHeight,
            onClick = onLeftClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = ComposeDimens.PaddingEdge),
        )

        BasicText(
            text = title,
            style = titleStyle.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 72.dp)
                .align(Alignment.Center),
        )

        val rightTextModifier = if (rightEnabled) {
            Modifier
                .align(Alignment.CenterEnd)
                .noBackClickable(onClick = onRightClick)
        } else {
            Modifier.align(Alignment.CenterEnd)
        }
        val rightTextStyle = if (rightEnabled) {
            ComposeTypography.Font16
        } else {
            ComposeTypography.Font16DescC0
        }

        BasicText(
            text = rightText,
            style = rightTextStyle.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = rightTextModifier.padding(horizontal = ComposeDimens.PaddingEdge20, vertical = 10.dp),
        )
    }
}

/**
 * 仅右侧文字按钮的顶栏。
 */
@Composable
fun ComposeHeaderRightText(
    rightText: String,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarsPadding: Boolean = true,
) {
    Box(
        modifier = modifier.applyHeaderStatusBarsPadding(applyStatusBarsPadding)
            .fillMaxWidth()
            .padding(top = ComposeDimens.ToolbarMarginTop),
        contentAlignment = Alignment.CenterEnd,
    ) {
        BasicText(
            text = rightText,
            style = ComposeTypography.Font16.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .noBackClickable(onClick = onRightClick)
                .padding(horizontal = ComposeDimens.PaddingEdge20, vertical = 10.dp),
        )
    }
}

private fun Modifier.applyHeaderStatusBarsPadding(apply: Boolean): Modifier {
    return if (apply) {
        statusBarsPadding()
    } else {
        this
    }
}

@Composable
private fun ComposeHeaderIcon(
    @DrawableRes iconResId: Int,
    contentDescription: String?,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(iconResId),
        contentDescription = contentDescription,
        modifier = modifier
            .size(iconSize)
            .noBackClickable(onClick = onClick),
    )
}
