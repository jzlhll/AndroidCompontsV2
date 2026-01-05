package com.allan.androidlearning.pictureselector

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import com.allan.androidlearning.R
import com.au.module_android.click.onClick
import com.luck.picture.lib.widget.TitleBar

class CustomTitleBar : TitleBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private lateinit var selectAllImage: ImageView
    private var mCurrentNum: Int = 0
    private var mTotalNum: Int = 0
    private var mIsAllSelected: Boolean = false
    private var mOnSelectAllListener: OnSelectAllListener? = null

    /**
     * 不论哪个被调用，你需要调用[setCurrentNum]来刷新最新状态。
     */
    interface OnSelectAllListener {
        fun onSelectAll()
        fun onCancelSelectAll()
    }

    fun setOnSelectAllListener(listener: OnSelectAllListener?) {
        mOnSelectAllListener = listener
    }

    fun setCurrentNum(num: Int, totalNum: Int) {
        mCurrentNum = num
        mTotalNum = totalNum
        if (totalNum <= 0) {
            mIsAllSelected = false
            selectAllImage.isEnabled = false
            selectAllImage.contentDescription = context.getString(R.string.select_all)
            selectAllImage.setImageResource(R.drawable.select_all)
        } else {
            selectAllImage.isEnabled = true
            mIsAllSelected = num != 0
            selectAllImage.contentDescription = if (mIsAllSelected)
                context.getString(R.string.select_all_cancel)
            else
                context.getString(R.string.select_all)

            selectAllImage.setImageResource(if (mIsAllSelected) R.drawable.select_cancel else R.drawable.select_all)
        }
    }

    override fun inflateLayout() {
        LayoutInflater.from(context).inflate(R.layout.ps_custom_title_bar, this)
    }

    override fun init() {
        super.init()
        //修改掉它的点击事件。不让它可见，也不让他可点
        tvCancel.onClick {}
        selectAllImage = findViewById(R.id.ps_select_all)
        selectAllImage.onClick {
            if (mTotalNum <= 0) return@onClick
            mOnSelectAllListener?.let {
                if (mIsAllSelected) {
                    it.onSelectAll()
                } else {
                    it.onCancelSelectAll()
                }
            }
        }
    }

    override fun setTitleBarStyle() {
        super.setTitleBarStyle()
        tvCancel.visibility = GONE //始终让它不可见
    }
}
