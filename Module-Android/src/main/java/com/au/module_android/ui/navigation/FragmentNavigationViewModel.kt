package com.au.module_android.ui.navigation

import android.os.Bundle
import androidx.lifecycle.ViewModel
import com.au.module_android.json.putAny
import com.au.module_android.simplelivedata.NoStickLiveData

class FragmentNavigationViewModel : ViewModel() {
    private var dataListLiveData = mutableMapOf<String, NoStickLiveData<Bundle>>()

    fun initAllPages(pageIds:List<String>) {
        for (pageId in pageIds) {
            val dataMap = NoStickLiveData<Bundle>()
            dataListLiveData[pageId] = dataMap
        }
    }

    fun savePageData(pageId:String, bundle:Bundle) {
        val dataMap = dataListLiveData.getOrPut(pageId) {
            NoStickLiveData()
        }
        dataMap.setValueSafe(bundle)
    }

    fun getPageData(pageId:String):Bundle? {
        return dataListLiveData[pageId]?.value
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