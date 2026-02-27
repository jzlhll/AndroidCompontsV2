package com.au.module_nested.recyclerview

import androidx.recyclerview.widget.DiffUtil
import com.au.module_android.Globals
import com.au.module_android.utils.launchOnThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun <DATA: IViewTypeBean> BaseAdapter<DATA, *>.diffUpdate(
    differ : DiffCallback<DATA>,
    newList: List<DATA>,
    endCallback:()->Unit
) {
    Globals.mainScope.launchOnThread {
        val result = DiffUtil.calculateDiff(differ, true)

        withContext(Dispatchers.Main) {
            //完事后，再更改本地list
            datas.clear()
            datas.addAll(newList)
            result.dispatchUpdatesTo(this@diffUpdate)
            endCallback()
        }
    }
}

internal fun <DATA:IViewTypeBean> BaseAdapter<DATA, *>.initDatasCommon(
    newDatas: List<DATA>?,
    differProvider : (oldDatas:List<DATA>, newDatas:List<DATA>)->DiffCallback<DATA>?,
    isTraditionalUpdate: Boolean,
    endInitDatasBlock:(oldDataSize: Int, newDataSize: Int)->Unit) {
    val oldDatas = this.datas
    val newList = newDatas ?: emptyList()

    //必须在前面
    val oldDataSize = oldDatas.size
    val newDataSize = newList.size

    val differ = differProvider(oldDatas, newList)

    if (newList.isEmpty() || differ == null || isPlacesHolder || isTraditionalUpdate) {
        isPlacesHolder = false
        submitTraditional(newList)
        endInitDatasBlock(oldDataSize, newDataSize)
    } else {
        diffUpdate(differ, newList) {
            endInitDatasBlock(oldDataSize, newDataSize)
        }
    }
}
