package com.au.module_android.ui.navigation

import android.os.Bundle
import androidx.lifecycle.ViewModel
import com.au.module_android.json.putAny
import com.au.module_android.simplelivedata.NoStickLiveData

class FragmentNavigationViewModel : ViewModel() {
    private var dataListLiveData = mutableMapOf<String, NoStickLiveData<Bundle>>()

    fun savePageData(pageId:String, bundle:Bundle) {
        dataMap[pageId] = bundle
    }

    fun getPageData(pageId:String):Bundle? {
        return dataMap[pageId]
    }

    fun updatePageData(pageId:String, kv:Map<String, Any?>) {
        var bundle = dataMap[pageId]
        if (bundle == null) {
            bundle = Bundle()
            dataMap[pageId] = bundle
        }
        kv.forEach { (k, v) ->
            if(v != null) bundle.putAny(k, v)
            else bundle.remove(k)
        }
    }
}