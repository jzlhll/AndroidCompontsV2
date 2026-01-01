package com.au.module_simplepermission

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.max
import kotlin.math.min

/**
 * 支持 > 1
 * 如果需要支持 >=1 使用compat版本
 */
@Deprecated("instead with CompatMultiPickVisualMedia.")
class DynamicPickMultiVisualMedia(currentMaxItems:Int) : ActivityResultContracts.PickMultipleVisualMedia(2) //随便给一个，只是为了父类不报错。
{
    private var mCurrentMax = currentMaxItems
    fun setCurrentMaxItems(max:Int) {
        if (max == 1) {
            throw RuntimeException("max must > 1.")
        }
        mCurrentMax = max
    }

    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent {
        val systemLimit = ignoreError { max(100, MediaStore.getPickImagesMaxLimit()) } ?: 100
        val max = min(mCurrentMax, systemLimit)
        return super.createIntent(context, input).apply {
            when (action) {
                MediaStore.ACTION_PICK_IMAGES -> {
                    Log.d("permission", "MediaStore.getPickImagesMaxLimit() " + MediaStore.getPickImagesMaxLimit())
                    putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, max)
                }
                ActivityResultContracts.PickVisualMedia.ACTION_SYSTEM_FALLBACK_PICK_IMAGES -> {
                    Log.d("permission", "FallBack to system")
                    putExtra(ActivityResultContracts.PickVisualMedia.EXTRA_SYSTEM_FALLBACK_PICK_IMAGES_MAX, Int.MAX_VALUE) //later: 其实这里可以使用Int.MAX_VALUE
                }
                "com.google.android.gms.provider.action.PICK_IMAGES" -> {
                    Log.d("permission", "FallBack to gms")
                    putExtra("com.google.android.gms.provider.extra.PICK_IMAGES_MAX", max) //later: 其实这里可以使用Int.MAX_VALUE
                }
            }
        }
    }

}