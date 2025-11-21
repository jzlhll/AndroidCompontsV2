package com.au.jobstudy

import android.os.Bundle
import com.au.jobstudy.check.NameList
import com.au.jobstudy.databinding.FragmentMainHomeBinding
import com.au.jobstudy.utils.WeekDateUtil
import com.au.jobstudy.utils.WeekDateUtil.currentTimeToHelloGood
import com.au.jobstudy.words.loading.EnglishCheckFragment
import com.au.jobstudy.words.loading.ExcelLoadingFragment
import com.au.module_android.click.onClick
import com.au.module_android.ui.bindings.BindingFragment

class MainHomeFragment : BindingFragment<FragmentMainHomeBinding>() {
    private val userName = NameList.NAMES_JIANG_TJ

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.startBtn.onClick {
            // 判断是否已经导入Excel数据到数据库
            // 这里使用一个todo变量来模拟判断（实际项目中应该从数据库或SharedPreferences中获取）
            val isExcelDataImported = false // 假设初始状态为未导入
            
            if (isExcelDataImported) {
                // 如果已经导入，直接跳转到单词检查页面
                EnglishCheckFragment.start(requireActivity(), 0, 100)
            } else {
                // 如果未导入，先跳转到加载页面进行导入
                ExcelLoadingFragment.start(requireActivity())
            }
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