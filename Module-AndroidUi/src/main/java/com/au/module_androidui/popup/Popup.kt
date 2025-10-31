package com.au.module_androidui.popup

import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.viewbinding.ViewBinding
import com.au.module_android.utils.dp

abstract class Popup(val anchorView: View) {
    private var popupMenuWindow: PopupWindow? = null

    /**
     *  //FragmentPresetPreviewPopMenuBinding.inflate(LayoutInflater.from(requireContext()))
     */
    abstract fun createPopBinding() : ViewBinding

    fun hidePopMenu() {
        popupMenuWindow?.dismiss()
        popupMenuWindow = null
    }

    fun showOrHidePopMenu() {
        if (popupMenuWindow != null) {
            hidePopMenu()
        } else {
            showPopMenu()
        }
    }

    private fun showPopMenu() {
        val popBinding = createPopBinding()

        val popupWindow = PopupWindow(
            popBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            //setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        this.popupMenuWindow = popupWindow

        popupWindow.showAtView(
            anchorView,
            true,
            xoff = (4.dp),
            yoff = -(8.dp)
        )
    }
}