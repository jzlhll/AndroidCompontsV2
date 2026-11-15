package com.au.module_simplepermission

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/** 单选与动态多选共用入口，不申请图库权限。 */
class CompatMultiPickVisualMedia(max: Int) : ActivityResultContract<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>() {
    private val pickMedia = ActivityResultContracts.PickVisualMedia()
    private var currentMax = max
    private var launchedMax = max
    private val multiPickMedia by lazy { DynamicPickMultiVisualMedia(currentMax) }

    init { require(max > 0) { "Max items must be positive" } }

    fun setCurrentMaxItems(max: Int) {
        require(max > 0) { "Max items must be positive" }
        currentMax = max
        if (max > 1) multiPickMedia.setCurrentMaxItems(max)
    }

    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent {
        launchedMax = if (currentMax < input.maxItems) currentMax else input.maxItems
        require(launchedMax > 0) { "Max items must be positive" }
        return if (launchedMax == 1) pickMedia.createIntent(context, input) else multiPickMedia.createIntent(context, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        val result = if (launchedMax == 1) listOfNotNull(pickMedia.parseResult(resultCode, intent))
            else multiPickMedia.parseResult(resultCode, intent)
        return result.take(launchedMax)
    }

    override fun getSynchronousResult(context: Context, input: PickVisualMediaRequest): SynchronousResult<List<Uri>>? = null
}
