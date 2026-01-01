package com.au.jobstudy.completed

import android.os.Bundle
import com.au.jobstudy.databinding.FragmentCompletedBeforeBinding
import com.au.module_android.click.onClick
import com.au.module_androidui.ui.FragmentShellActivity
import com.au.module_androidui.ui.bindings.BindingFragment

class CompletedBeforeFragment : BindingFragment<FragmentCompletedBeforeBinding>() {
    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.weekBtn.onClick {
            FragmentShellActivity.start(requireContext(), CompletedFragment::class.java, arguments = Bundle().also { it.putBoolean("isWeek", true) })
        }

        binding.dayButton.onClick {
            FragmentShellActivity.start(requireContext(), CompletedFragment::class.java, arguments = Bundle().also { it.putBoolean("isWeek", false) })
        }
    }
}