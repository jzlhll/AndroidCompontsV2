package com.allan.androidlearning.pictureselector

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.au.module_android.log.logt
import com.au.module_android.utils.launchOnThread
import com.au.module_imagecompressed.compressor.useCompress
import com.luck.picture.lib.engine.CompressFileEngine
import com.luck.picture.lib.interfaces.OnKeyValueResultCallbackListener

class MeCompressEngine : CompressFileEngine{

    override fun onStartCompress(
        context: Context?,
        source: java.util.ArrayList<Uri?>?,
        call: OnKeyValueResultCallbackListener?
    ) {
        if (context != null && context is LifecycleOwner) {
            context.lifecycleScope.launchOnThread {
                source?.forEach { uri->
                    if (uri != null) {
                        logt { "on Start compress $uri" }
                        val file = useCompress(context, uri)
                        call?.onCallback(uri.toString(), file?.absolutePath)
                    }
                }
            }
        }
    }
}