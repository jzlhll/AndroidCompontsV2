package com.au.module_androiduiex.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.au.module_simplepermission.CompatMultiPickVisualMedia

/** 纯 Compose 系统选图入口；结果回调属于组合，launch 只携带本次请求参数。 */
class ComposePhotoPicker(private val launchRequest: (Int, PickVisualMedia.VisualMediaType, Boolean) -> Unit) {
    fun launch(maxItems: Int = Int.MAX_VALUE, mediaType: PickVisualMedia.VisualMediaType = PickVisualMedia.ImageOnly, orderedSelection: Boolean = true) {
        launchRequest(maxItems, mediaType, orderedSelection)
    }
}

@Composable
fun rememberComposePhotoPicker(onResult: (List<Uri>) -> Unit): ComposePhotoPicker {
    val callback by rememberUpdatedState(onResult)
    var launchedMax by rememberSaveable { mutableIntStateOf(Int.MAX_VALUE) }
    var inFlight by rememberSaveable { mutableStateOf(false) }
    val contract = remember { CompatMultiPickVisualMedia(launchedMax) }
    val launcher = rememberLauncherForActivityResult(contract) { result ->
        inFlight = false
        callback(result.take(launchedMax))
    }
    return remember(contract, launcher) {
        ComposePhotoPicker { maxItems, mediaType, orderedSelection ->
            require(maxItems > 0) { "Max items must be positive" }
            check(!inFlight) { "Photo picker is already running" }
            launchedMax = maxItems
            contract.setCurrentMaxItems(maxItems)
            inFlight = true
            try {
                val request = PickVisualMediaRequest.Builder().setMediaType(mediaType).setOrderedSelection(orderedSelection)
                if (maxItems > 1) request.setMaxItems(maxItems)
                launcher.launch(request.build())
            } catch (e: Exception) {
                inFlight = false
                throw e
            }
        }
    }
}
