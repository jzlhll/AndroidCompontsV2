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
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

@EntryFrgName(priority = 10)
class FlowDistinctUntilChangedTestFragment : BindingFragment<FragmentFlowDistinctUntilChangedTestBinding>() {

    override fun onBindingCreated(savedInstanceState: Bundle?) {

        binding.appendBtn.onClick {
            WebsocketFlowResearch.randomAppendWebsocket()
        }

        binding.removeBtn.onClick {
            WebsocketFlowResearch.randomRemoveWebsocket()
        }

        binding.toggleBtn.onClick {
            WebsocketFlowResearch.randomAdvanceWebsocketState()
        }

        binding.tickBtn.onClick {
            WebsocketFlowResearch.tickCurrentFrameFromTime()
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WebsocketFlowResearch.deviceStateFlow
                    .collect { result ->
                    Log.d("alland", "final changed $result")
                    binding.resultText.text = "" + System.currentTimeMillis() + " " + result
                }

                //try distinctUntilChangedBy
            }
        }
    }
}