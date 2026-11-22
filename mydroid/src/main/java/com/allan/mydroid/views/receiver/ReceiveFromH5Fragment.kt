package com.allan.mydroid.views.receiver

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.databinding.FragmentReceiveFromH5Binding
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.allan.mydroid.state.GlobalClientListFlowsObj
import com.allan.mydroid.state.GlobalServerRuntimeObj
import com.allan.mydroid.views.AbsLiveFragment
import com.allan.mydroid.views.SlidingOverlayTransition
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utils.changeBarsColor
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_android.utilsmedia.getExternalFreeSpace
import com.au.module_androidui.ui.ToolbarMenuManager
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_gson.toGsonString
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class ReceiveFromH5Fragment : AbsLiveFragment<FragmentReceiveFromH5Binding>() {
    private val serverRuntimeState: GlobalServerRuntimeObj by inject()
    private val clientListState: GlobalClientListFlowsObj by inject()
    private val mViewModel: ReceiveFromH5ViewModel by viewModels()

    private var exportHistoryExpanded = false
    private var exportHistoryTransition: SlidingOverlayTransition? = null
    private val exportHistoryBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            animateExportHistory(false)
        }
    }

    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.FullImmersive { _, _ ->
            requireActivity().changeBarsColor(statusBarTextDark = false)
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        super.onBindingCreated(savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = bars.top
            }
            binding.receiveContentLayout.updatePadding(bottom = bars.bottom)
            binding.exportHistory.historyToolbarLayout.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.exportHistory.historyScrollLayout.updatePadding(
                left = bars.left, right = bars.right, bottom = bars.bottom,
            )
            insets
        }

        ToolbarMenuManager(viewLifecycleOwner, binding.toolbar, R.menu.menu_more, Color.WHITE) { item ->
            if (item.itemId == R.id.more) animateExportHistory(true)
        }.showMenu()

        setupExportHistory(savedInstanceState)

        val leftStr = getString(R.string.storage_remaining)
        binding.descTitle.text = "${leftStr.trim()} ${getExternalFreeSpace(requireActivity())}"

        launchRepeatOnStarted(get<GlobalNetworkMonitorObj>().networkInfoFlow) { netInfo->
            if (netInfo == null) {
                binding.title.setText(R.string.connect_wifi_or_hotspot)
            } else {
                if (netInfo.httpPort == null) {
                    binding.title.text = netInfo.ip
                } else if (serverRuntimeState.serverIsOpenFlow.value) {
                    binding.title.text = Globals.app.getString(R.string.lan_access_fmt, netInfo.urlHost, netInfo.httpPort.toString())
                } else {
                    binding.title.text = netInfo.urlHost + ":" + netInfo.httpPort
                }
            }
        }

        launchRepeatOnStarted(clientListState.clientListFlow) { clientList->
            logdNoFile {
                ">>client List:" + clientList.toGsonString()
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupExportHistory(savedInstanceState: Bundle?) {
        val history = binding.exportHistory
        history.historyToolbar.setNavigationOnClickListener { animateExportHistory(false) }
        ViewCompat.setAccessibilityPaneTitle(history.root, getString(R.string.export_history))
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, exportHistoryBackCallback)

        exportHistoryExpanded = savedInstanceState?.getBoolean(KEY_EXPORT_HISTORY_EXPANDED) ?: exportHistoryExpanded
        exportHistoryTransition = SlidingOverlayTransition(
            binding.receiveContentLayout,
            binding.exportHistoryHost,
            history.root,
        ) { visible ->
            exportHistoryBackCallback.isEnabled = visible
        }.also {
            it.setExpanded(exportHistoryExpanded, animated = false)
        }

        if (exportHistoryExpanded) {
            mViewModel.dispatch(ReceiveFromH5ViewModel.LoadHistoryAction())
        }
        launchRepeatOnStarted(mViewModel.historyState) { state ->
            val app = Globals.app
            history.historyTv.text = when (state) {
                is StatusState.Success -> if (state.data.isBlank()) {
                    app.getString(R.string.export_history_empty)
                } else {
                    app.getString(R.string.keep_recent_records) + "\n\n" + state.data.trimEnd('\n')
                }
                is StatusState.Error -> app.getString(R.string.something_error)
                StatusState.Loading, StatusState.Uninitialized -> app.getString(R.string.keep_recent_records)
            }
        }
    }

    private fun animateExportHistory(expanded: Boolean) {
        val transition = exportHistoryTransition ?: return
        if (exportHistoryExpanded == expanded) return
        exportHistoryExpanded = expanded
        if (expanded) {
            binding.exportHistory.historyScrollLayout.scrollTo(0, 0)
            mViewModel.dispatch(ReceiveFromH5ViewModel.LoadHistoryAction())
        }

        transition.setExpanded(expanded)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_EXPORT_HISTORY_EXPANDED, exportHistoryExpanded)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        exportHistoryTransition?.dispose()
        exportHistoryTransition = null
        exportHistoryBackCallback.isEnabled = false
        super.onDestroyView()
    }

    override fun getMode() = MyDroidMode.Receiver

    private companion object {
        const val KEY_EXPORT_HISTORY_EXPANDED = "export_history_expanded"
    }
}
