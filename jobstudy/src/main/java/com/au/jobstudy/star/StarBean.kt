package com.au.jobstudy.star

import com.au.module_nested.recyclerview.IMultiViewTypeBean

/**
 * @author allan
 * @date :2024/7/15 10:59
 * @description:
 */

data class StarItemBean(val name:String, var starNum:Int, var dingNum:Int, var isDing:Boolean? = null,
                        override val viewType: Int = VIEW_TYPE_ITEM) : IMultiViewTypeBean
data class StarHeadBean(val html:String, override val viewType: Int = VIEW_TYPE_HEAD) : IMultiViewTypeBean
class StarMarkupBean(override val viewType: Int = VIEW_TYPE_MARKUP):IMultiViewTypeBean
