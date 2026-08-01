package com.allan.androidlearning.androidui.shadow

import android.graphics.Color
import android.graphics.Outline
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.allan.androidlearning.databinding.FragmentAuDebugXmlShadowBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_android.log.logEx
import com.au.module_android.utils.withIOThread
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_imagecompressed.pickForResult
import com.au.module_imagecompressed.loader.SYS_MIN_SIZE
import com.au.module_imagecompressed.loader.loadThumbnailUriOrFile
import com.au.module_simplepermission.PickerType
import eightbitlab.com.blurview.BlurView3Util
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@EntryFrgName(customName = "Au Debug XML Shadow")
class AuDebugXmlShadowFragment : BindingFragment<FragmentAuDebugXmlShadowBinding>() {
    private val photoPickerResult = pickForResult()

    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.FullImmersive callback@{ statusBarHeight, navBarHeight ->
            if (!isAdded || !isBindingInit()) return@callback
            binding.root.updatePadding(top = statusBarHeight, bottom = navBarHeight)
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.backBtn.onClick {
            requireActivity().finishAfterTransition()
        }
        binding.circleBlurHost.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        binding.circleBlurHost.clipToOutline = true
        photoPickerResult.launchByAll(PickerType.IMAGE, null) { uris ->
            uris.firstOrNull()?.let(::loadImage)
        }
    }

    private fun loadImage(imageUri: Uri) {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val image = try {
                withIOThread {
                    loadThumbnailUriOrFile(context, imageUri, SYS_MIN_SIZE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logEx(throwable = e) { "Load Au debug XML shadow image failed" }
                null
            } ?: return@launch

            if (!isAdded || !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return@launch
            }
            binding.centeredImage.setImageBitmap(image)
            binding.offsetImage.setImageBitmap(image)
            binding.backdropImage.setImageBitmap(image)
            binding.circleBlurSourceImage.setImageBitmap(image)
            binding.contentHost.visibility = View.VISIBLE
            configureBlurViews()
        }
    }

    private fun configureBlurViews() {
        val backdropOverlay = Color.argb(0xCC, 0xFF, 0xFF, 0xFF)
        val density = resources.displayMetrics.density
        listOf(
            binding.backdropTopStartBlurView,
            binding.backdropTopEndBlurView,
            binding.backdropCenterEndBlurView,
            binding.backdropBottomStartBlurView,
            binding.backdropBottomEndBlurView,
        ).forEach { blurView ->
            BlurView3Util(blurView, 16, 16f * density).setBlurWithOverlayNoNoise(
                binding.backdropBlurTarget,
                backdropOverlay,
                Color.TRANSPARENT,
            )
        }
        BlurView3Util(binding.circleBlurView, 44, 4f * density).setBlurWithOverlayNoNoise(
            binding.circleBlurTarget,
            Color.argb(0x80, 0xD9, 0xD9, 0xD9),
            Color.TRANSPARENT,
        )
    }
}
