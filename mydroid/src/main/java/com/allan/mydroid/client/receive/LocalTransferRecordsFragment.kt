package com.allan.mydroid.client.receive

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.fragment.findNavController
import com.allan.mydroid.R
import com.au.module_androiduiex.ui.ComposeViewFragment
import org.koin.androidx.viewmodel.ext.android.viewModel

/** 本地下载记录入口，不依赖扫描结果、主机模式或网络连接。 */
class LocalTransferRecordsFragment : ComposeViewFragment() {
    private val model: ConnectToHostReceiveViewModel by viewModel()
    override val customBackAction: () -> Boolean = {
        findNavController().popBackStack()
        false
    }

    override fun onResume() {
        super.onResume()
        model.refreshLocalFiles()
    }

    @Composable
    override fun ScreenContent() {
        ConnectToHostReceiveScreen(model, null, stringResource(R.string.transfer_records),
            onBack = { findNavController().popBackStack() })
    }
}
