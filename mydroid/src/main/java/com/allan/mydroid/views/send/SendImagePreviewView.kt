package com.allan.mydroid.views.send

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.allan.mydroid.R
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.views.compose.SendImagePreviewScreen
import com.au.module_android.Globals
import com.au.module_android.log.loge
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_androidui.widget.AbsFullScreenPreviewView

/** 发送列表的沉浸式图片预览，承载等比前景、模糊背景和图片手势，关闭时释放组合及加载请求。 */
class SendImagePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbsFullScreenPreviewView(context, attrs, defStyleAttr) {
    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    init {
        setBackgroundResource(android.R.color.black)
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun show(activity: Activity, bean: ShareInBean) {
        composeView.disposeComposition()
        composeView.setContent {
            SendImagePreviewScreen(
                model = bean.uri,
                contentDescription = bean.name,
                onClose = { hide(activity) },
                onLoadFailed = {
                    loge { "Load image preview failed" }
                    hide(activity)
                    ToastBuilder().setOnTopLater()
                        .setMessage(Globals.app.getString(R.string.something_error)).toast()
                },
            )
        }
        if (!attachToContent(activity)) {
            composeView.disposeComposition()
            return
        }
        showPreview(activity)
    }

    override fun onHide() {
        composeView.disposeComposition()
    }
}
