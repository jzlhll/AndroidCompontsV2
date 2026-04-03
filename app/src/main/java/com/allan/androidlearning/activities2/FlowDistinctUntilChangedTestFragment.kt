package com.allan.androidlearning.activities2

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.allan.androidlearning.databinding.FragmentFlowDistinctUntilChangedTestBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_androidui.ui.bindings.BindingFragment
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@EntryFrgName
class FlowDistinctUntilChangedTestFragment : BindingFragment<FragmentFlowDistinctUntilChangedTestBinding>() {

    override fun onBindingCreated(savedInstanceState: Bundle?) {

        binding.appendBtn.onClick {
            WebsocketFlowResearch.randomAppendWebsocket()
        }

        binding.removeBtn.onClick {
            WebsocketFlowResearch.randomRemoveWebsocket()
        }

        binding.toggleBtn.onClick {
            WebsocketFlowResearch.randomToggleWebsocketState()
        }

        binding.tickBtn.onClick {
            WebsocketFlowResearch.tickCurrentFrameFromTime()
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    WebsocketFlowResearch.currentFrameFlow,
                    WebsocketFlowResearch.connectorsStateChangedFlow
                ) { frame, changed ->
                    Log.d("alland", "change $changed")
                    "Frame: $frame\n" +
                    "List Size: ${WebsocketFlowResearch.websocketList.size}\n"
                   // "Details: ${WebsocketFlowResearch.websocketList.joinToString("\n")}"
                }.distinctUntilChanged().collect { result ->
                    Log.d("alland", "final changed")
                    binding.resultText.text = "" + System.currentTimeMillis() + " " + result
                }

                //try distinctUntilChangedBy
            }
        }
    }
}