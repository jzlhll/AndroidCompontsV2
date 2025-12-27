package com.au.jobstudy.words.ui

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.au.jobstudy.databinding.FragmentLoadingBinding
import com.au.module_android.ui.bindings.BindingFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 导入Excel数据的加载页面
 */
class ExcelLoadingFragment : BindingFragment<FragmentLoadingBinding>() {
    private val viewModel : LoadingViewModel by viewModel()

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.overFlow.collect {
                    requireActivity().finish()
                    // 导入成功后跳转到单词检查页面
                    EnglishCheckFragment.start(requireActivity(), 0, 100)
                }
            }
        }

        binding.loadingText.post {
            viewModel.checkAndImportExcel()
        }
    }
}