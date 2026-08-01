package com.allan.androidlearning.androidui.shadow

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.allan.androidlearning.androidui.shadow.compose.AuDebugComposeShadowScreen
import com.allan.classnameanno.EntryFrgName
import com.au.module_androiduiex.ui.ComposeViewFragment
import com.au.module_imagecompressed.pickForResult
import com.au.module_simplepermission.PickerType

@EntryFrgName(customName = "Au Debug Compose Shadow")
class AuDebugComposeShadowFragment : ComposeViewFragment() {
    private val photoPickerResult = pickForResult()
    private var imageUri by mutableStateOf<Uri?>(null)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        photoPickerResult.launchByAll(PickerType.IMAGE, null) { uris ->
            imageUri = uris.firstOrNull()
        }
    }

    @Composable
    override fun ScreenContent() {
        val selectedImageUri = imageUri ?: return
        AuDebugComposeShadowScreen(
            imageUri = selectedImageUri,
            onBackClick = { requireActivity().finishAfterTransition() },
        )
    }
}
