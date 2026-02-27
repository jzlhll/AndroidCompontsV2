package com.allan.autoclickfloat.activities.recordprojects

import android.graphics.drawable.Drawable
import com.au.module_nested.recyclerview.IViewTypeBean

/**
 * @author allan
 * @date :2024/6/5 15:08
 * @description:
 */
data class AllAppListItemBean(val name:CharSequence, val pkgName:String, val drawable:Drawable?) : IViewTypeBean