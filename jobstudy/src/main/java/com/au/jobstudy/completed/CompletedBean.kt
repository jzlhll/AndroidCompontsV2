package com.au.jobstudy.completed

import com.au.jobstudy.check.bean.CompletedEntity
import com.au.jobstudy.check.bean.WorkEntity
import com.au.module_nested.recyclerview.IMultiViewTypeBean

data class CompletedBean(val workEntity: WorkEntity, var completedEntity:CompletedEntity?, override val viewType: Int = 1) : IMultiViewTypeBean
data class CompletedDateBean(val day:Int, val isWeek:Boolean) : IMultiViewTypeBean {
    override val viewType: Int
        get() = 0
}