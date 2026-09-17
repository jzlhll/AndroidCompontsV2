package com.au.module_androiduiex.dialogs

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.au.module_androiduiex.styles.ComposeColors
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.noBackClickable

/** 键盘上方展示的故事内容编辑浮层，仅支持顶部、居中和底部对齐。 */
@Composable
fun ComposeStoryEditor(
    initialText: String,
    title: String,
    @DrawableRes closeIconResId: Int,
    @DrawableRes saveIconResId: Int,
    @StringRes counterTextResId: Int,
    maxLength: Int,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopCenter,
    onClose: () -> Unit,
    onSave: (String) -> Unit,
) {
    var textValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val canSave = textValue.text.isNotEmpty() || initialText.isNotEmpty()
    val shape = RoundedCornerShape(24.dp)
    require(
        alignment == Alignment.TopCenter || alignment == Alignment.Center || alignment == Alignment.BottomCenter,
    ) { "ComposeStoryEditor supports only TopCenter, Center, and BottomCenter alignments." }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .noBackClickable(onClick = {})
            .background(Color.Black.copy(alpha = 0.3f))
            .statusBarsPadding()
            .imePadding()
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 20.dp),
        contentAlignment = alignment,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(shape)
                .background(ComposeColors.PrimaryBg)
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            ) {
                Image(
                    painter = painterResource(closeIconResId),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(36.dp)
                        .noBackClickable {
                            keyboardController?.hide()
                            onClose()
                        },
                )
                Text(
                    text = title,
                    style = ComposeTypography.Font16M,
                    modifier = Modifier.align(Alignment.Center),
                )
                val saveModifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(36.dp)
                Image(
                    painter = painterResource(saveIconResId),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        ComposeColors.Orange.copy(alpha = if (canSave) 1f else 0.4f),
                    ),
                    modifier = if (canSave) {
                        saveModifier.noBackClickable {
                            keyboardController?.hide()
                            onSave(textValue.text)
                        }
                    } else {
                        saveModifier
                    },
                )
            }
            BasicTextField(
                value = textValue,
                onValueChange = { nextValue ->
                    if (nextValue.text.length <= maxLength) {
                        textValue = nextValue
                    }
                },
                textStyle = ComposeTypography.Font16,
                cursorBrush = SolidColor(ComposeColors.Orange),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 16.dp)
                    .focusRequester(focusRequester),
            )
            Text(
                text = stringResource(counterTextResId, textValue.text.length, maxLength),
                style = ComposeTypography.Font16Desc,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 4.dp),
            )
        }
    }
}
