package com.allan.autoclickfloat.activities.recordprojects

import com.allan.autoclickfloat.database.Project
import com.au.module_nested.recyclerview.IViewTypeBean

/**
 * @author allan
 * @date :2024/4/23 17:51
 * @description:
 */
data class RecordProjectsItemInfo(var isSelectMode:Boolean, val project: Project) : IViewTypeBean