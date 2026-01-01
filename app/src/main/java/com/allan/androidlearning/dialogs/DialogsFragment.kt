package com.allan.androidlearning.dialogs

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import com.allan.androidlearning.databinding.DialogDemoBinding
import com.allan.androidlearning.databinding.FakeDialogTestBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_androidui.ui.views.ViewFragment
import com.au.module_android.utils.dp
import com.au.module_androidui.widget.FlowLayout
import com.au.module_androidui.dialogs.AbsCenterFakeDialog
import com.au.module_androidui.dialogs.ConfirmCenterDialog
import com.au.module_androidui.toast.ToastUtil.toastOnTop
import com.google.android.material.button.MaterialButton

/**
 * @author au
 * @date :2023/11/8 14:16
 * @description:
 */
@EntryFrgName
class DialogsTestFragment : ViewFragment() {
    override fun onUiCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = inflater.context
        val ll = FlowLayout(context)
        ll.childSpacing = 8.dp
        ll.rowSpacing = 4f.dp
        ll.addView(MaterialButton(context).apply {
            text = "showViewDialog"
            onClick {
                val dialog = MyCenterDialog()
                dialog.show(this@DialogsTestFragment.childFragmentManager, "MyDialog#12")
            }
        })

        ll.addView(MaterialButton(context).apply {
            text = "showBindingDialog"
            onClick {
                val dialog = MyBottomDialog()
                dialog.show(this@DialogsTestFragment.childFragmentManager, "MyDialog#12")
            }
        })

        ll.addView(MaterialButton(context).apply {
            text = "ConfirmCenterDialog"
            onClick {
                ConfirmCenterDialog.show(childFragmentManager, "Hello", "This is a small content.", "OK") {}
            }
        })

        ll.addView(MaterialButton(context).apply {
            text = "FakeDialog"
            onClick {
                MyCenterFakeDialog().pop(this@DialogsTestFragment)
            }
        })
        return ll
    }

    class MyCenterFakeDialog : AbsCenterFakeDialog<FakeDialogTestBinding>() {
        override fun onShow(
            activity: ComponentActivity,
            binding: FakeDialogTestBinding
        ) {
            binding.cancelButton.onClick {
                hide()
            }
            binding.sureButton.onClick {
                toastOnTop("点击了确定: " + binding.edit.text)
                hide()
            }
        }

        override fun onHide(binding: FakeDialogTestBinding) {
        }
    }
}