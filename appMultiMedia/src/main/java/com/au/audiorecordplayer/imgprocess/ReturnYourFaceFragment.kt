package com.au.audiorecordplayer.imgprocess

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import com.au.audiorecordplayer.databinding.FragmentReturnYourFaceBinding
import com.au.module_android.click.onClick
import com.au.module_android.glide.glideSetAny
import com.au.module_android.ui.bindings.BindingFragment
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible
import com.au.module_imagecompressed.MultiPhotoPickerContractResult
import com.au.module_imagecompressed.photoPickerForResult

class ReturnYourFaceFragment : BindingFragment<FragmentReturnYourFaceBinding>() {
    val singleResult = photoPickerForResult().also { it.setNeedLubanCompress(2048) }

    override fun isPaddingStatusBar(): Boolean {
        return false
    }

    override fun isPaddingNavBar(): Boolean {
        return false
    }

    private val listener = object : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {

        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.selectedImageButton.onClick {
            singleResult.launchOneByOne(MultiPhotoPickerContractResult.PickerType.IMAGE, null) { uri->
                binding.selectedImageButton.gone()
                binding.adjustImageGroup.visible()
                binding.viewFinder.glideSetAny(uri.uri)
            }
        }

        binding.root.setOnTouchListener(listener)
    }
}