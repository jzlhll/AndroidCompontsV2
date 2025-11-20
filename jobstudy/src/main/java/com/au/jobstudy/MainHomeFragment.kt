package com.au.jobstudy

import android.os.Bundle
import android.view.View
import com.au.jobstudy.check.NameList
import com.au.jobstudy.databinding.FragmentMainHomeBinding
import com.au.jobstudy.utils.WeekDateUtil
import com.au.jobstudy.utils.WeekDateUtil.currentTimeToHelloGood
import com.au.jobstudy.words.EnglishCheckFragment
import com.au.module_android.click.onClick
import com.au.module_android.ui.bindings.BindingFragment

class MainHomeFragment : BindingFragment<FragmentMainHomeBinding>() {
    private val userName = NameList.NAMES_JIANG_TJ

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.startBtn.onClick {
            EnglishCheckFragment.start(requireActivity(), 0, 100)
        }
    }

    override fun onResume() {
        super.onResume()

        binding.weather.text = WeekDateUtil.getTodayWeekN()

        val time = String.format(getString(R.string.name_hello_format), userName, currentTimeToHelloGood())
        if (binding.title.text != time) {
            binding.title.text = time
        }
    }
}