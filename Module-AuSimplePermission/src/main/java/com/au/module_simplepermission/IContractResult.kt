package com.au.module_simplepermission

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

/**
 * @author Allan
 * @date :2023/12/13 10:52
 */
abstract class IContractResult<I, O> (val cxt:Any, resultContract:ActivityResultContract<I, O>) {
    private var onResultCallback: ActivityResultCallback<O>? = null

    private val mResultCallback = ActivityResultCallback<O> { result -> onResultCallback?.onActivityResult(result) }

    abstract fun start(input:I, callback:ActivityResultCallback<O>?)

    val launcher: ActivityResultLauncher<I> = when (cxt) {
        is Fragment -> {
            cxt.registerForActivityResult(resultContract, mResultCallback)
        }

        is AppCompatActivity -> {
            cxt.registerForActivityResult(resultContract, mResultCallback)
        }

        is View -> {
            val activity = cxt.context as? AppCompatActivity
            activity?.registerForActivityResult(resultContract, mResultCallback) ?: throw IllegalArgumentException("init at onCreate $cxt is not illegal.")
        }

        else -> {
            throw IllegalArgumentException("init at onCreate $cxt is not illegal.")
        }
    }

    /**
     * 要求在launch之前调用
     */
    protected open fun setResultCallback(callback: ActivityResultCallback<O>) {
        onResultCallback = callback
    }

    fun toContext() : Context {
        if (cxt is Fragment) {
            return cxt.requireContext()
        }
        if (cxt is Activity) {
            return cxt
        }
        if (cxt is View) {
            return cxt.context
        }
        throw RuntimeException()
    }
}