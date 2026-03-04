package com.allan.androidlearning.activities2

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.allan.androidlearning.databinding.FragmentMediaVpTestBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.glide.glideSetAny
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.launchOnThread
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_imagecompressed.loader.SYS_MIN_SIZE
import com.au.module_imagecompressed.loader.loadCompressUriOrFile
import com.au.module_imagecompressed.loader.loadThumbnailUriOrFile

val uriList = listOf(
    "content://media/external/images/media/706".toUri(),
)

@EntryFrgName(priority = 2)
class MediaVpTestFragment : BindingFragment<FragmentMediaVpTestBinding>() {
    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.viewPager2.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val imageView = ImageView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                return object : RecyclerView.ViewHolder(imageView) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as ImageView).glideSetAny(uriList[position])
            }

            override fun getItemCount(): Int = uriList.size
        }

        binding.viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicator(position)
            }
        })
        updateIndicator(0)

        testLoad(uriList[0])
    }

    private fun updateIndicator(position: Int) {
        binding.indicatorTv.text = "${position + 1}/${uriList.size}"
    }

    private fun testLoad(uri: Uri) {
        lifecycleScope.launchOnThread {
            val thumbnailBitmap = loadThumbnailUriOrFile(requireContext(), uri, SYS_MIN_SIZE)
            logdNoFile { "thumbnail bitmap: ${thumbnailBitmap?.width} * ${thumbnailBitmap?.height}" }

            val compressBitmap = loadCompressUriOrFile(requireContext(), uri)
            logdNoFile { "compress bitmap: ${compressBitmap?.width} * ${compressBitmap?.height}" }
        }
    }
}
