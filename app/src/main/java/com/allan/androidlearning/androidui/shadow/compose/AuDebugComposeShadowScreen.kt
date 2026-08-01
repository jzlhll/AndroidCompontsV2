package com.allan.androidlearning.androidui.shadow.compose

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.androidlearning.R
import com.au.module_android.log.logEx
import com.au.module_android.utils.withIOThread
import com.au.module_androiduiex.styles.ComposeBackdropBlurState
import com.au.module_androiduiex.styles.ComposeBackdropBlurWhiteBlock
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.composeBackdropBlurSource
import com.au.module_androiduiex.styles.composeBlurredCircleBackground
import com.au.module_androiduiex.styles.composeCenteredBlurShadow
import com.au.module_androiduiex.styles.composeOffsetBlurShadow
import com.au.module_androiduiex.styles.composeShadowWhiteBlock
import com.au.module_androiduiex.styles.rememberComposeBackdropBlurState
import com.au.module_imagecompressed.loader.SYS_MIN_SIZE
import com.au.module_imagecompressed.loader.loadThumbnailUriOrFile
import kotlinx.coroutines.CancellationException

private data class AuDebugComposeShadowUiState(
    val image: ImageBitmap? = null,
)

@Composable
fun AuDebugComposeShadowScreen(
    imageUri: Uri,
    onBackClick: () -> Unit,
) {
    val uiState = rememberAuDebugComposeShadowUiState(imageUri)
    val image = uiState.image ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF6F1)),
    ) {
        AuDebugComposeShadowHeader(onBackClick = onBackClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(64.dp),
        ) {
            AuDebugShadowSampleColumnItem {
                AuDebugShadowWhiteBlockSample()
            }
            AuDebugShadowSampleColumnItem {
                AuDebugCenteredBlurShadowSample(image = image)
            }
            AuDebugShadowSampleColumnItem {
                AuDebugOffsetBlurShadowSample(image = image)
            }
            AuDebugShadowSampleColumnItem(widthFraction = 0.85f) {
                AuDebugBackdropBlurSample(image = image)
            }
            AuDebugShadowSampleColumnItem {
                AuDebugBlurredCircleBackgroundSample(image = image)
            }
        }
    }
}

@Composable
private fun rememberAuDebugComposeShadowUiState(imageUri: Uri): AuDebugComposeShadowUiState {
    val context = LocalContext.current.applicationContext
    var imageBitmap by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imageUri) {
        imageBitmap = try {
            withIOThread {
                loadThumbnailUriOrFile(context, imageUri, SYS_MIN_SIZE)
            }?.asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logEx(throwable = e) { "Load Au debug compose shadow image failed" }
            null
        }
    }

    return AuDebugComposeShadowUiState(image = imageBitmap)
}

@Composable
private fun AuDebugComposeShadowHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_au_debug_shadow_back),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onBackClick),
        )
        BasicText(
            text = "Compose Shadow",
            style = ComposeTypography.Font20M.copy(
                color = Color(0xFF404040),
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Spacer(modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun AuDebugShadowSampleColumnItem(
    widthFraction: Float = 0.65f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxWidth(widthFraction)) {
            content()
        }
    }
}

@Composable
private fun AuDebugShadowSampleCard(
    title: String,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AuDebugShadowText(text = title)
        Spacer(modifier = Modifier.height(48.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
            content = content,
        )
    }
}

@Composable
private fun AuDebugShadowText(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = ComposeTypography.Font12M.copy(color = Color(0xFF404040)),
        modifier = modifier,
    )
}

@Composable
private fun AuDebugShadowWhiteBlockSample() {
    AuDebugShadowSampleCard(title = "composeShadowWhiteBlock") {
        Box(
            modifier = Modifier
                .size(88.dp)
                .composeShadowWhiteBlock(
                    cornerRadius = 16.dp,
                    strokeWidth = 1.dp,
                    shadowColor = Color(0x10000000),
                    shadowBlurRadius = 32.dp,
                    shadowSpread = 1.dp,
                    backgroundColor = Color(0xCCFFFFFF),
                    strokeColor = Color(0xFFFFFFFF),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AuDebugShadowText(text = "White")
        }
    }
}

@Composable
private fun AuDebugCenteredBlurShadowSample(image: ImageBitmap) {
    AuDebugShadowSampleCard(title = "composeCenteredBlurShadow") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .composeCenteredBlurShadow(
                    cornerRadius = 16.dp,
                    shadowColor = Color(0x1A404040),
                    blurRadius = 32.dp,
                )
                .background(Color(0xFFFFFFFF), RoundedCornerShape(16.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            AuDebugShadowText(
                text = "Now Playing",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun AuDebugOffsetBlurShadowSample(image: ImageBitmap) {
    AuDebugShadowSampleCard(title = "composeOffsetBlurShadow") {
        Column(
            modifier = Modifier.fillMaxWidth(0.75f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuDebugOffsetBlurShadowPreview(
                image = image,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Spacer(modifier = Modifier.height(36.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .composeOffsetBlurShadow(
                        cornerRadius = 24.dp,
                        shadowColor = Color(0x1A404040),
                        blurRadius = 10.dp,
                        offsetY = 12.dp,
                    )
                    .background(Color(0xFFFFFFFF), RoundedCornerShape(24.dp)),
            )
        }
    }
}

@Composable
private fun AuDebugOffsetBlurShadowPreview(
    image: ImageBitmap,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val panelWidth = maxWidth * 0.74f
        val panelHeight = maxHeight * 0.32f
        val horizontalPadding = maxWidth * 0.1f
        val verticalPadding = maxHeight * 0.1f

        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = horizontalPadding, top = verticalPadding)
                .width(panelWidth)
                .height(panelHeight)
                .composeOffsetBlurShadow(
                    cornerRadius = 18.dp,
                    shadowColor = Color(0x1A404040),
                    blurRadius = 8.dp,
                    offsetY = 2.dp,
                )
                .background(Color(0x94FFFFFF), RoundedCornerShape(18.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = horizontalPadding, bottom = verticalPadding)
                .width(panelWidth)
                .height(panelHeight)
                .composeOffsetBlurShadow(
                    cornerRadius = 18.dp,
                    shadowColor = Color(0x1A404040),
                    blurRadius = 16.dp,
                    offsetY = 4.dp,
                )
                .background(Color(0xBDFFFFFF), RoundedCornerShape(18.dp)),
        )
    }
}

@Composable
private fun AuDebugBackdropBlurSample(image: ImageBitmap) {
    AuDebugShadowSampleCard(title = "ComposeBackdropBlurWhiteBlock") {
        AuDebugBackdropBlurPreview(
            image = image,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.33f),
        )
    }
}

@Composable
private fun AuDebugBackdropBlurPreview(
    image: ImageBitmap,
    modifier: Modifier,
) {
    val blurState = rememberComposeBackdropBlurState()
    Box(modifier = modifier) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .composeBackdropBlurSource(blurState),
        )
        AuDebugBackdropBlurBlock(
            state = blurState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 18.dp),
        )
        AuDebugBackdropBlurBlock(
            state = blurState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 18.dp),
        )
        AuDebugBackdropBlurBlock(
            state = blurState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        )
        AuDebugBackdropBlurBlock(
            state = blurState,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 18.dp),
        )
        AuDebugBackdropBlurBlock(
            state = blurState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun AuDebugBackdropBlurBlock(
    state: ComposeBackdropBlurState,
    modifier: Modifier,
) {
    ComposeBackdropBlurWhiteBlock(
        state = state,
        modifier = modifier
            .width(112.dp)
            .height(48.dp),
        cornerRadius = 16.dp,
        backgroundBlurRadius = 16.dp,
        strokeWidth = 1.dp,
        shadowColor = Color(0x10000000),
        shadowBlurRadius = 32.dp,
        shadowSpread = 0.dp,
        backgroundColor = Color(0xCCFFFFFF),
        strokeColor = Color(0xFFFFFFFF),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AuDebugShadowText(text = "Backdrop")
        }
    }
}

@Composable
private fun AuDebugBlurredCircleBackgroundSample(image: ImageBitmap) {
    AuDebugShadowSampleCard(title = "composeBlurredCircleBackground") {
        Box(
            modifier = Modifier.size(88.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .composeBlurredCircleBackground(
                        blurRadius = 4.dp,
                        overlayColor = Color(0x80D9D9D9),
                    ),
            ) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_au_debug_shadow_play),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
