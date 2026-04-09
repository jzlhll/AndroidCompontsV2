package com.allan.androidlearning.picwall

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.allan.androidlearning.databinding.FragmentPicStackBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.simpleflow.collectStatusState
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_androidui.toast.ToastUtil
import com.au.module_simplepermission.PermissionMediaType
import com.au.module_simplepermission.createMediaPermissionForResult
import kotlinx.coroutines.launch

@EntryFrgName(priority = 10)
class PicStackFragment : BindingFragment<FragmentPicStackBinding>() {
    companion object {
        private const val DEFAULT_IMAGE_LIMIT = 10
    }

    private val viewModel by viewModels<PicWallViewModel>()
    private val mediaPermissionHelper = createMediaPermissionForResult(arrayOf(PermissionMediaType.IMAGE))

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.localMediaState.collectStatusState(
                    onSuccess = { data ->
                        val imageList = data
                            .filter { it.isUriImage() }
                            .take(DEFAULT_IMAGE_LIMIT)
                        binding.stackLayout.submitList(imageList)
                    },
                )
            }
        }

        mediaPermissionHelper.safeRun(notGivePermissionBlock = {
            ToastUtil.toastOnTop("请先授权媒体权限")
        }) {
            viewModel.dispatch(PicWallViewModel.RequestLocalAction(requireActivity()))
        }
    }
}
