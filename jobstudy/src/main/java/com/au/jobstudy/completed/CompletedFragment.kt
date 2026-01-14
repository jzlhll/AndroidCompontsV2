package com.au.jobstudy.completed

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.au.jobstudy.check.bean.CompletedEntity
import com.au.jobstudy.checkwith.CheckWithFragment
import com.au.jobstudy.databinding.FragmentCompletedBinding
import com.au.jobstudy.utils.ISingleDayer
import com.au.jobstudy.utils.WeekDateUtil
import com.au.module_android.Globals
import com.au.module_gson.fromGson
import com.au.module_simplepermission.createActivityForResult
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_androidui.ui.views.YourToolbarInfo
import com.au.module_android.utils.unsafeLazy
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel

class CompletedFragment : BindingFragment<FragmentCompletedBinding>() {
    private lateinit var adpater:CompletedAdapter

    private val viewModel:CompletedViewModel by viewModel(ownerProducer = {
        requireActivity()
    })

    private var loadedLastDay : Int = 0

    private var isInited = false

    private val isWeek by unsafeLazy { arguments?.getBoolean("isWeek") ?: false }

    val activityLauncher = createActivityForResult()

    override fun toolbarInfo(): YourToolbarInfo? {
        return YourToolbarInfo.Defaults("任务列表")
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.rcv.layoutManager = LinearLayoutManager(requireContext())
        binding.rcv.adapter = CompletedAdapter(itemClick = { bean->
            CheckWithFragment.start(Globals.app, activityLauncher,
                bean.workEntity, bean.completedEntity) {
                val completedEntity = it.data?.getStringExtra("completedEntity")
                if (completedEntity != null) {
                    completedEntity.fromGson<CompletedEntity>()?.dayWorkId?.let { workId->
                        val completedBean = adpater.datas.find { d-> (d is CompletedBean) && d.workEntity.id == workId }
                        if (completedBean != null) {
                            val index = adpater.datas.indexOf(completedBean)
                            viewModel.updateABean(completedBean as CompletedBean) {
                                adpater.notifyItemChanged(index)
                            }
                        }
                    }
                }
            }
        }).also {
            adpater = it
            it.loadMoreAction = {
                if (!isWeek) {
                    viewModel.fetch(getNewDays(loadedLastDay))
                } else {
                    viewModel.fetchWeek(getWeeks(loadedLastDay))
                }
            }
        }

        viewModel.completedBeans.observe(viewLifecycleOwner) {
            binding.loading.hide()
            val isEmpty = it.isEmpty()
            if (!isInited) {
                adpater.initDatas(it, true)
                isInited = true
            } else {
                if (!isEmpty) {
                    adpater.appendDatas(it, true)
                } else {
                    adpater.setNoMore()
                }
            }
        }

        val dayer = get<ISingleDayer>()
        if (!isWeek) {
            val days = getNewDays(dayer.currentDay)
            viewModel.fetch(days)

        } else {
            val weeks = getWeeks(dayer.weekStartDay)
            viewModel.fetchWeek(weeks)
        }

    }

    private fun getWeeks(weekStartDay:Int) : IntArray {
        val lastWeek = WeekDateUtil.lastWeekStartDay(weekStartDay)
        loadedLastDay = WeekDateUtil.lastWeekStartDay(lastWeek)
        return intArrayOf(weekStartDay, lastWeek)
    }

    private fun getNewDays(day:Int) : IntArray {
        val yesterday = WeekDateUtil.getYesterday(day)
        val yesterday2 = WeekDateUtil.getYesterday(yesterday)
        val yesterday3 = WeekDateUtil.getYesterday(yesterday2)
        loadedLastDay = WeekDateUtil.getYesterday(yesterday3)
        return intArrayOf(day, yesterday, yesterday2, yesterday3)
    }
}