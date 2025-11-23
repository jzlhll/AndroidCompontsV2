package com.au.jobstudy.words.ui

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.au.jobstudy.databinding.FragmentLoadingBinding
import com.au.module_android.ui.FragmentShellActivity
import com.au.module_android.ui.bindings.BindingNoToolbarFragment
import com.au.module_android.utils.unsafeLazy
import kotlinx.coroutines.launch

/**
 * 导入Excel数据的加载页面
 */
class ExcelLoadingFragment : BindingNoToolbarFragment<FragmentLoadingBinding>() {
    private val viewModel by unsafeLazy {
        ViewModelProvider(this)[LoadingViewModel::class.java]
    }

    companion object {
        fun start(context: Context) {
            FragmentShellActivity.start(context, ExcelLoadingFragment::class.java)
        }
    }

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
            viewModel.load()
        }
    }
}