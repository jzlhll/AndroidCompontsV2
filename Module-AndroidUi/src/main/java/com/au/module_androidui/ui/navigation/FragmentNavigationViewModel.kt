package com.au.module_androidui.ui.navigation

import android.os.Bundle
import androidx.lifecycle.ViewModel
import com.au.module_android.simplelivedata.NoStickLiveData
import com.au.module_android.utils.putAny

/**
 * 他们都在同一个 Activity 上，因此可以通过 getPageData 来获取缓存内容。也可以相互赋值和获取。
 *
 */
class FragmentNavigationViewModel : ViewModel() {
    private var dataListLiveData = mutableMapOf<String, NoStickLiveData<Bundle>>()

    lateinit var scene : FragmentNavigationScene

    fun initScene(scene:FragmentNavigationScene) {
        this.scene = scene
        for (page in scene.list) {
            val data = NoStickLiveData<Bundle>()
            data.setValueSafe(page.params ?: Bundle())
            dataListLiveData[page.pageId] = data
        }
    }

    fun savePageData(pageId:String, bundle:Bundle) {
        val dataMap = dataListLiveData.getOrPut(pageId) {
            NoStickLiveData()
        }
        dataMap.setValueSafe(bundle)
    }

    fun restorePageData(pageId: String) {
        val data = dataListLiveData.getOrPut(pageId) {
            NoStickLiveData()
        }
        data.setValueSafe(scene.list.find { it.pageId == pageId }?.params ?: Bundle())
    }

    fun getPageData(pageId:String):Bundle? {
        return dataListLiveData[pageId]?.value
    }

    fun getPageLiveData(pageId:String):NoStickLiveData<Bundle>? {
        return dataListLiveData[pageId]
    }

    fun updatePageData(pageId:String, kv:Map<String, Any?>) {
        val bundle = getPageData(pageId) ?: Bundle()
        kv.forEach { (k, v) ->
            if(v != null) bundle.putAny(k, v)
            else bundle.remove(k)
        }
        savePageData(pageId, bundle)
    }
}