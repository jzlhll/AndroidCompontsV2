package com.allan.mydroid.views.send

import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.databinding.HolderMydroidSendlistItemBinding
import com.allan.mydroid.databinding.HolderMydroidSendlistTitleBinding
import com.allan.mydroid.globals.getIcon
import com.au.module_android.click.onClick
import com.au.module_android.glide.glideSetAny
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible
import com.au.module_android.utilsmedia.MimeUtil
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import java.util.Locale
import kotlin.math.min

class SendTitleHolder(binding: HolderMydroidSendlistTitleBinding)
    : BindViewHolder<Any, HolderMydroidSendlistTitleBinding>(binding) {
    override fun bindData(bean: Any) {
        super.bindData(bean)
        if (bean !is IconTitle) return
        binding.icon.setImageResource(bean.icon)
        binding.title.text = bean.title
    }
}

class SendHolder(binding: HolderMydroidSendlistItemBinding,
                 val rootClick: (ShareInBean?) -> Unit,
                 val iconClick: (ShareInBean?) -> Unit,
                 val deleteClick: (ShareInBean?) -> Unit)
    : BindViewHolder<Any, HolderMydroidSendlistItemBinding>(binding) {
    init {
        binding.deleteBtn.onClick {
            deleteClick(currentData.asOrNull())
        }
        binding.icon.onClick {
            iconClick(currentData.asOrNull())
        }
        binding.root.onClick {
            rootClick(currentData.asOrNull())
        }
    }

    override fun bindData(bean: Any) {
        super.bindData(bean)
        if (bean !is ShareInBean) return

        val mimeUtil = MimeUtil(bean.mimeType)
        val isImg = mimeUtil.isUriImage()
        val isVideo = mimeUtil.isUriVideo()

        val goodName = bean.name
        binding.icon.setImageResource(getIcon(goodName))
        binding.fileNameTv.text = goodName ?: bean.uri.toString()
        binding.fileSizeAndMD5Tv.text =
            if(isVideo)
                bean.fileSizeStr + " | " + convertToVideoDuration(bean.videoDuration)
            else
                bean.fileSizeStr

        if(bean.isNoDeleteBtn) binding.deleteBtn.gone() else binding.deleteBtn.visible()

        if (isImg || isVideo) {
            binding.icon.glideSetAny(bean.uri) {
                it.override(120, 120)
            }
        }

        if(isVideo) binding.iconPlay.visible() else binding.iconPlay.gone()
    }

    /**
     * 将毫秒转换为时间格式，最大99:59:59
     */
    fun convertToVideoDuration(milliseconds: Long?) : String {
        milliseconds ?: return "-:-:-"

        // 1. 将毫秒转换为总秒数
        val totalSeconds = milliseconds / 1000

        // 2. 计算小时、分钟、秒
        var hours = totalSeconds / 3600
        var minutes = (totalSeconds % 3600) / 60
        var seconds = totalSeconds % 60

        // 3. 限制最大值 (99:59:59)
        hours = min(hours, 99)
        if (hours > 99) {
            minutes = 59
            seconds = 59
        }

        // 4. 格式化为两位字符串并用冒号连接
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }
}