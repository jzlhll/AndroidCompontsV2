package com.allan.mydroid.views.send

import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.au.module_android.Globals
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible
import kotlinx.coroutines.CancellationException
import com.au.module_android.log.logEx
import com.au.module_androidui.toast.ToastBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.allan.mydroid.R
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.repository.GlobalShareInRepoObj
import com.allan.mydroid.repository.DownloadTransfer
import com.au.module_nested.recyclerview.IMultiViewTypeBean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class SendListSelectorCommon(val isNoDeleteBtn: Boolean) : KoinComponent {
    private val shareInRepository: GlobalShareInRepoObj by inject()

    private val mAdapter = SendListAdapter {it, mode->
        if (isSelectionMode && it != null && !it.isLocalReceiver) {
            if (!selectedIds.add(it.uriUuid)) selectedIds.remove(it.uriUuid)
            refreshSelection()
        } else {
            onItemClick(it, mode)
        }
    }

    var isSelectionMode = false
        private set
    private val selectedIds = mutableSetOf<String>()
    val selectedUriUuids: List<String> get() = selectedIds.toList()

    open fun onSelectionChanged() = Unit
    open fun onVisibleSectionChanged(title: String) = Unit

    fun updateSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        selectedIds.clear()
        refreshSelection()
    }

    private fun refreshSelection() {
        mAdapter.datas.forEachIndexed { index, item ->
            if (item is ShareInBean) {
                item.isSelectionMode = isSelectionMode && !item.isLocalReceiver
                item.isSelected = item.uriUuid in selectedIds
                mAdapter.notifyItemChanged(index)
            }
        }
        onSelectionChanged()
    }

    private var downloads: Map<String, DownloadTransfer> = emptyMap()

    abstract fun rcv(): RecyclerView
    abstract fun empty(): TextView?

    abstract fun onItemClick(bean: ShareInBean?, mode:String)

    fun onCreated() {
        val rcv = rcv()
        rcv.adapter = mAdapter
        rcv.layoutManager = LinearLayoutManager(rcv.context)
        rcv.setHasFixedSize(true)
        if (!isNoDeleteBtn) {
            rcv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateVisibleSection(recyclerView)
                }
            })
        }
    }

    private fun updateVisibleSection(recyclerView: RecyclerView) {
        val firstVisiblePosition = (recyclerView.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition() ?: return
        val section = mAdapter.datas.take(firstVisiblePosition + 1).lastOrNull { it is IconTitle } as? IconTitle
        onVisibleSectionChanged(section?.title ?: Globals.app.getString(R.string.share_in))
    }

    suspend fun reload() {
        try {
            val scanList = shareInRepository.loadShareInAndReceiveBeans()
            withContext(Dispatchers.Main) { updateList(scanList) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logEx(throwable = e) { "Load shared files failed" }
            withContext(Dispatchers.Main) {
                ToastBuilder().setOnTop().setMessage(Globals.getString(R.string.something_error)).toast()
            }
        }
    }

    fun updateDownloads(results: Map<String, DownloadTransfer>) {
        downloads = results
        if (!isNoDeleteBtn) return
        mAdapter.datas.forEachIndexed { index, item ->
            if (item is ShareInBean) {
                val latest = results.values.lastOrNull { it.uriUuid == item.uriUuid }
                val md5 = if (latest?.state == "completed") latest.md5 else null
                if (item.completedMd5 != md5) {
                    item.completedMd5 = md5
                    mAdapter.notifyItemChanged(index)
                }
            }
        }
    }

    fun updateList(sendUriList: List<ShareInBean>) {
        sendUriList.forEach { bean ->
            val latest = downloads.values.lastOrNull { it.uriUuid == bean.uriUuid }
            bean.completedMd5 = if (isNoDeleteBtn && latest?.state == "completed") latest.md5 else null
        }
        val newList = ArrayList<IMultiViewTypeBean>()
        val shareInList = sendUriList.filter { !it.isLocalReceiver }
        if (shareInList.isNotEmpty()) {
            if (isNoDeleteBtn) newList.add(IconTitle(Globals.getString(R.string.share_in)))
            newList.addAll(shareInList)
        }

        val receiverList = sendUriList.filter { it.isLocalReceiver }
        if (receiverList.isNotEmpty()) {
            newList.add(IconTitle(Globals.getString(R.string.my_transfer_list).trim()))
            newList.addAll(receiverList)
        }

        selectedIds.retainAll(shareInList.map { it.uriUuid }.toSet())
        sendUriList.forEach { bean ->
            bean.isSelectionMode = isSelectionMode && !bean.isLocalReceiver
            bean.isSelected = bean.uriUuid in selectedIds
        }
        mAdapter.submitList(newList, false)
        if (!isNoDeleteBtn) updateVisibleSection(rcv())
        onSelectionChanged()
        if (newList.isEmpty()) {
            empty()?.visible()
        } else {
            empty()?.gone()
        }
    }

    /**
     * 1表示有数据。2表示有数据，但是全都没勾；0表示无数据。
     */
    fun isEmpty() : Boolean {
        return mAdapter.datas.isEmpty()
    }
}