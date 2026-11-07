package com.au.agit.ui

import com.au.agit.ui.components.AGitButton as Button
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import com.au.agit.R
import com.au.agit.model.AGitException
import com.au.module_imagecompressed.loader.ImageLoadConfig
import com.au.module_imagecompressed.loader.loadCompressUriOrFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MediaRoute(key: MediaKey, app: AppViewModel, onBack: () -> Unit) {
    var file by remember(key) { mutableStateOf<File?>(null) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        try { file = app.repository.file(key.projectId, key.path) } catch (error: CancellationException) { throw error } catch (error: Exception) { failed = true; app.failure("media open", error, key.projectId) }
    }
    PageScaffold(key.path.substringAfterLast('/'), onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            val source = file
            when {
                failed -> Text(stringResource(R.string.media_error))
                source == null -> CircularProgressIndicator()
                fileMime(source).startsWith("video/") -> VideoPreview(source, onError = { app.failure("video playback", it, key.projectId) })
                else -> ImagePreview(source, onError = { app.failure("image decode", it, key.projectId) })
            }
        }
    }
}

@Composable
private fun ImagePreview(file: File, onError: (Throwable) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    val errorMessage = stringResource(R.string.media_error)
    LaunchedEffect(file) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                loadCompressUriOrFile(context.applicationContext, file, ImageLoadConfig(maxWidth = 2048, maxHeight = 2048, ignoreFileTypes = emptyList(), ignoreSizeInKB = 0))
            }
            if (bitmap == null) { failed = true; onError(AGitException(errorMessage)) }
        } catch (error: CancellationException) { throw error } catch (error: Exception) { failed = true; onError(error) }
    }
    when {
        failed -> Text(errorMessage)
        bitmap == null -> CircularProgressIndicator()
        else -> Image(bitmap!!.asImageBitmap(), file.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPreview(file: File, onError: (Throwable) -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by rememberSaveable(file.path) { mutableLongStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val reportError by rememberUpdatedState(onError)
    LifecycleStartEffect(file) {
        val current = ExoPlayer.Builder(context.applicationContext).build()
        current.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) { failed = true; reportError(error) }
        })
        current.setMediaItem(MediaItem.fromUri(file.toUri()))
        current.seekTo(position)
        current.prepare()
        player = current
        onStopOrDispose { position = current.currentPosition; current.release(); player = null; playing = false }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            player?.let { PlayerSurface(player = it, modifier = Modifier.fillMaxSize()) }
            if (failed) Text(stringResource(R.string.media_error), color = Color.White)
        }
        Button(onClick = { player?.let { if (it.playbackState == Player.STATE_ENDED) it.seekTo(0); if (it.isPlaying) it.pause() else it.play() } }) {
            Text(stringResource(if (playing) R.string.pause else R.string.play))
        }
    }
}
