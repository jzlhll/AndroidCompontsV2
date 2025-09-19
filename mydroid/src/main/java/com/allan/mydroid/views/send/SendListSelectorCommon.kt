package com.allan.mydroid.views.send

import android.widget.TextView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.allan.mydroid.beansinner.UriRealInfoEx
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.globals.MyDroidMess
import com.au.module_android.Globals
import com.au.module_android.utils.gone
import com.au.module_android.utils.launchOnIOThread
import com.au.module_android.utils.visible
import com.au.module_android.utilsmedia.getRealInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.allan.mydroid.R

abstract class SendListSelectorCommon(val f : Fragment, noSelectBtns:Boolean) {
    private val adapter = SendListAdapter(noSelectBtns) {it, mode->
        itemClick(it, mode)
    }

    abstract fun rcv(): RecyclerView
    abstract fun empty(): TextView

    abstract fun itemClick(bean: UriRealInfoEx?, mode:String)

    private var mIsScanFiles = false
    private var mIsObserverChanged = false

    private var mScanList: List<UriRealInfoEx>? = null

    fun onCreated() {
        initRcv()
    }

    fun onStart() {
        f.lifecycleScope.launchOnIOThread {
            val scanList = MyDroidMess().loadFileList().map { it ->
                val uri = it.file.toUri()
                val real = uri.getRealInfo(Globals.app)
                UriRealInfoEx.Companion.copyFrom(real)
            }
            mIsScanFiles = true
            mScanList = scanList

            if (mIsObserverChanged) {
                val sendUriList = ArrayList<UriRealInfoEx>()
                sendUriList.addAll(MyDroidConst.sendUriMap.realValue?.values ?: emptyList())

                withContext(Dispatchers.Main) {
                    updateList(sendUriList, scanList)
                }
            }
        }
    }

    fun updateList(sendUriList: List<UriRealInfoEx>, scanList: List<UriRealInfoEx>) {
        val newList = ArrayList<Any>()
        if (sendUriList.isNotEmpty()) {
            newList.add(IconTitle(R.drawable.ic_share, Globals.getString(R.string.share_in)))
        }
        newList.addAll(sendUriList)

        if (scanList.isNotEmpty()) {
            newList.add(IconTitle(R.drawable.ic_receivered, Globals.getString(R.string.transfer_list).trim()))
        }
        newList.addAll(scanList)

        adapter.submitList(newList, false)
        if (newList.isEmpty()) {
            empty().visible()
        } else {
            empty().gone()
        }
    }

    fun isEmptyList() = adapter.datas.isEmpty()

    private fun initRcv() {
        val rcv = rcv()
        rcv.adapter = adapter
        rcv.layoutManager = LinearLayoutManager(rcv.context)
        rcv.setHasFixedSize(true)

        MyDroidConst.sendUriMap.observe(f) { map-> //监听没问题
            mIsObserverChanged = true
            if (mIsScanFiles) {
                val sendUriList = ArrayList<UriRealInfoEx>()
                sendUriList.addAll(map?.values ?: emptyList())
                updateList(sendUriList, mScanList ?: emptyList())
            }
        }
    }
}