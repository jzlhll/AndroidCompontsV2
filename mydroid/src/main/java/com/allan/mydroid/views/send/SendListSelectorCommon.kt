package com.allan.mydroid.views.send

import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.au.module_android.Globals
import com.au.module_android.utils.gone
import com.au.module_android.utils.launchOnIOThread
import com.au.module_android.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.allan.mydroid.R
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.globals.ShareInUrisObj

abstract class SendListSelectorCommon(val f : Fragment, val isNoDeleteBtn: Boolean) {
    private val mAdapter = SendListAdapter {it, mode->
        onItemClick(it, mode)
    }

    abstract fun rcv(): RecyclerView
    abstract fun empty(): TextView

    abstract fun onItemClick(bean: ShareInBean?, mode:String)

    fun onCreated() {
        val rcv = rcv()
        rcv.adapter = mAdapter
        rcv.layoutManager = LinearLayoutManager(rcv.context)
        rcv.setHasFixedSize(true)
    }

    fun reload() {
        f.lifecycleScope.launchOnIOThread {
            val scanList = ShareInUrisObj.loadShareInAndReceiveBeans()
            withContext(Dispatchers.Main) {
                updateList(scanList)
            }
        }
    }

    fun updateList(sendUriList: List<ShareInBean>) {
        val newList = ArrayList<Any>()
        val shareInList = sendUriList.filter { !it.isLocalReceiver }
        if (shareInList.isNotEmpty()) {
            newList.add(IconTitle(R.drawable.ic_share, Globals.getString(R.string.share_in)))
            for (bean in shareInList) {
                bean.isNoDeleteBtn = isNoDeleteBtn
            }
            newList.addAll(shareInList)
        }

        val receiverList = sendUriList.filter { it.isLocalReceiver }
        if (receiverList.isNotEmpty()) {
            newList.add(IconTitle(R.drawable.ic_receivered, Globals.getString(R.string.transfer_list).trim()))
            for (bean in receiverList) {
                bean.isNoDeleteBtn = isNoDeleteBtn
            }
            newList.addAll(receiverList)
        }

        mAdapter.submitList(newList, false)
        if (newList.isEmpty()) {
            empty().visible()
        } else {
            empty().gone()
        }
    }

    /**
     * 1表示有数据。2表示有数据，但是全都没勾；0表示无数据。
     */
    fun isEmpty() : Boolean {
        return mAdapter.datas.isEmpty()
    }
}