package com.allan.mydroid.views.receiver.nativefiles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.allan.mydroid.ui.theme.AndroidCompontsTheme
import com.au.module_android.utils.unsafeLazy

class ReceiveFromPhoneNativeFragment : Fragment() {
    private val viewModel by unsafeLazy {
        ViewModelProvider(this)[ReceiveFromPhoneNativeViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsState()
                AndroidCompontsTheme {
                    ReceiveFromPhoneNativeScreen(
                        state = state,
                        onIpChange = viewModel::updateIp,
                        onPortChange = viewModel::updatePort,
                        onConnectClick = viewModel::connect,
                        onRefreshClick = viewModel::refreshFiles,
                        onDownloadClick = viewModel::downloadFile,
                    )
                }
            }
        }
    }
}
