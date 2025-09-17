package com.allan.mydroid.nanohttp.wsmsger

import androidx.lifecycle.Observer
import com.allan.mydroid.R
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_SEND_FILE_LIST
import com.allan.mydroid.beans.WSResultBox
import com.allan.mydroid.beans.wsdata.FileListForHtmlData
import com.allan.mydroid.beansinner.UriRealInfoEx
import com.allan.mydroid.beansinner.UriRealInfoHtml
import com.allan.mydroid.globals.CODE_SUC
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.nanohttp.AbsWebSocketClientMessenger
import com.allan.mydroid.nanohttp.WebsocketClientInServer
import com.au.module_android.Globals
import com.au.module_android.Globals.resStr
import com.au.module_android.json.toJsonString
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logt
import kotlinx.coroutines.launch
import org.json.JSONObject

class WebsocketSendModeMessenger(client: WebsocketClientInServer) : AbsWebSocketClientMessenger(client) {

    private val sendUriMapOb = object : Observer<HashMap<String, UriRealInfoEx>> {
        override fun onChanged(value: HashMap<String, UriRealInfoEx>) {
            val cvtList = mutableListOf<UriRealInfoHtml>()
            value.values.forEach { urlRealInfoEx->
                if (urlRealInfoEx.isChecked) {
                    cvtList.add(urlRealInfoEx.copyToHtml())
                }
            }
            client.server.scope.launchOnThread {
                val msg = R.string.send_files_to_html.resStr()
                val data = FileListForHtmlData(cvtList)
                val ret = WSResultBox(CODE_SUC, msg, API_WS_SEND_FILE_LIST, data)
                val json = ret.toJsonString()
                logt { "${Thread.currentThread()} on map changed. send file list to html" }
                logt { "send:$json" }
                client.send(json)
            }
        }
    }

    override fun onOpen() {
        Globals.mainScope.launch {
            MyDroidConst.sendUriMap.observeForever(sendUriMapOb) //监听没问题
        }
    }

    override fun onClose() {
        Globals.mainScope.launch {
            MyDroidConst.sendUriMap.removeObserver(sendUriMapOb)
        }
    }

    override fun onMessage(origJsonStr:String, api:String, json: JSONObject) {
    }
}