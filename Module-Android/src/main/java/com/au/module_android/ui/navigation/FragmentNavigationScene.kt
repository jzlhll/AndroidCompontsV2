package com.au.module_android.ui.navigation

import android.content.Intent
import android.os.Bundle
import androidx.annotation.Keep
import com.au.module_android.utils.parcelableArrayListExtraCompat

@Keep
data class FragmentNavigationScene(
    val sceneId:String,
    val list: List<FragmentNavigationPage>,
    val startPageId: String,
    val entryParams: Bundle? = null,
) {
    companion object {
        const val KEY_SCENE_ID = "FragmentNavigationScene_key_scene_id"
        const val KEY_PAGE_LIST = "FragmentNavigationScene_key_page_list"
        const val KEY_START_PAGE_ID = "FragmentNavigationScene_key_start_page_id"
        const val KEY_ENTRY_PARAMS = "FragmentNavigationScene_key_entry_params"

        fun fromIntent(intent: Intent) : FragmentNavigationScene {
            val scene = FragmentNavigationScene(
                intent.getStringExtra(KEY_SCENE_ID)!!,
                intent.parcelableArrayListExtraCompat<FragmentNavigationPage>(KEY_PAGE_LIST)!!,
                intent.getStringExtra(KEY_START_PAGE_ID)!!,
                intent.getBundleExtra(KEY_ENTRY_PARAMS)
            )
            return scene
        }
    }

    fun packAsBundle() : Bundle {
        val bundle = Bundle()
        bundle.putString(KEY_SCENE_ID, sceneId)
        bundle.putParcelableArrayList(KEY_PAGE_LIST, ArrayList(list))
        bundle.putString(KEY_START_PAGE_ID, startPageId)
        bundle.putBundle(KEY_ENTRY_PARAMS, entryParams)
        return bundle
    }
}