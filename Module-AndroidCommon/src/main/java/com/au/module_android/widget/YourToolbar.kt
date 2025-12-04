package com.au.module_android.widget

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.au.module_android.R
import com.au.module_android.click.onClick
import com.au.module_android.databinding.ToolbarNormalBinding
import com.au.module_android.utils.asOrNull

class YourToolbar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private var toolbarView: View? = null

    var normalBinding: ToolbarNormalBinding? = null
    private var isInited = false

    init {
        attrs?.let { attributeSet ->
            val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.YourToolbar)
            val useDefault = typedArray.getBoolean(R.styleable.YourToolbar_toolbarLayoutDefault, false)
            val title = typedArray.getString(R.styleable.YourToolbar_toolbarNormalTitle)
            //要求必须配置toolbarLayout属性或者使用 default，否则就不做任何初始化，等待调用者自行 initAsXXX。
            val toolbarLayoutResId: Int = if (useDefault) {
                R.layout.toolbar_normal
            } else {
                typedArray.getResourceId(R.styleable.YourToolbar_toolbarLayout, 0)
            }

            typedArray.recycle()
            if (useDefault) {
                initAsNormal(title)
            } else if (toolbarLayoutResId != 0) {
                initView(toolbarLayoutResId)
            }
            // 如果toolbarLayoutResId为0且useDefault为false，则不进行初始化，等待调用者手动初始化
        }
    }

    /**
     * 初始化，使用默认的toolbar_normal布局
     */
    fun initAsNormal(title: String?) : ToolbarNormalBinding {
        var vb = getAsYourBinding { ToolbarNormalBinding.bind(it) }
        if (vb != null) {
            return vb
        }

        initView(R.layout.toolbar_normal)
        val v = toolbarView!!
        vb = ToolbarNormalBinding.bind(v)
        vb.backIcon.onClick {
            context.asOrNull<Activity>()?.finishAfterTransition()
        }
        vb.toolbarTitle.text = title
        normalBinding = vb
        return vb
    }

    /**
     * 将toolbarView初始化，并返回初始化后的ViewBinding对象
     */
    fun <VB> initAsYourBinding(toolbarLayoutResId:Int, bindingInitializer: (View) -> VB): VB? {
        val vb = getAsYourBinding(bindingInitializer)
        if (vb != null) {
            return vb
        }

        val v = initView(toolbarLayoutResId)
        return bindingInitializer(v)
    }

    /**
     * 获取初始化后的ViewBinding对象
     */
    fun <VB> getAsYourBinding(bindingInitializer: (View) -> VB): VB? {
        val v = toolbarView
        return if (v != null) {
            bindingInitializer(v)
        } else {
            null
        }
    }

    internal fun initView(toolbarLayoutResId: Int) : View{
        if (isInited) {
            throw IllegalStateException("YourToolbar has been initialized")
        }

        isInited = true
        val layoutInflater = LayoutInflater.from(context)
        val v = layoutInflater.inflate(toolbarLayoutResId, this, true)
        //注意这里是RelativeLayout
        val params = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        v.layoutParams = params
        toolbarView = v
        return v
    }
}