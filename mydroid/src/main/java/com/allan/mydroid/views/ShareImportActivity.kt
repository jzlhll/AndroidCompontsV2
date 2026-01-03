package com.allan.mydroid.views

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.beansinner.FROM_SHARE_IN
import com.allan.mydroid.databinding.ActivityImportBinding
import com.allan.mydroid.globals.ShareInUrisObj
import com.allan.mydroid.views.send.SendListSelectorFragment
import com.allan.mydroid.views.send.SendListSelectorFragment.Companion.KEY_START_TYPE
import com.allan.mydroid.views.send.SendListSelectorFragment.Companion.MY_DROID_SHARE_IMPORT_URIS
import com.au.module_android.Globals
import com.au.module_android.Globals.resStr
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.findMyLaunchActivity
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.parcelableArrayListExtraCompat
import com.au.module_android.utils.parcelableExtraCompat
import com.au.module_android.utilsmedia.isFromMyApp
import com.au.module_androidui.ui.bindings.BindingActivity
import com.au.module_androidui.ui.findCustomFragmentGetActivity
import com.au.module_androidui.ui.startActivityFix
import kotlinx.coroutines.launch

class ShareImportActivity : BindingActivity<ActivityImportBinding>() {
    override fun onDestroy() {
        super.onDestroy()
        MyDroidKeepLiveService.Companion.stopMyDroidAlive()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dealWithIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dealWithIntent(intent)
    }

    private fun dealWithIntent(intent: Intent?) {
        val sharedImportUris = mutableListOf<Uri>()
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                // 处理单文件分享
                val uri: Uri? = intent.parcelableExtraCompat(Intent.EXTRA_STREAM)
                uri?.let { sharedImportUris.add(it) }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                // 处理多文件分享
                intent.parcelableArrayListExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let { uris->
                    sharedImportUris.addAll(uris)
                }
            }
        }
        intent?.removeExtra(Intent.EXTRA_STREAM)
        handleIncreaseUris(sharedImportUris)
    }

    private fun ifUrisFromMyApp(sharedImportUris: List<Uri>) : Boolean{
        var isFromMyApp = false
        for (uri in sharedImportUris) {
            if (uri.isFromMyApp(this@ShareImportActivity)) {
                isFromMyApp = true
                break
            }
        }
        return isFromMyApp
    }

    private fun handleIncreaseUris(uris: List<Uri>) {
        logdNoFile { "handle increase uris $uris" }

        if (ifUrisFromMyApp(uris)) {
            Toast.makeText(this, com.allan.mydroid.R.string.import_to_send_list_hint.resStr(), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Globals.mainScope.launchOnThread {
            ShareInUrisObj.addShareInUris(uris, FROM_SHARE_IN)

            lifecycleScope.launch {
                jumpNext()
                finish()
            }
        }
    }

    private fun jumpNext() {
        val found = findCustomFragmentGetActivity(MyDroidAllFragment::class.java) != null
        if (!found) { //说明app没有启动过。需要先启动下首页，借过一下。
            val intent = findMyLaunchActivity(Globals.app).first
            intent.putExtra(KEY_START_TYPE, MY_DROID_SHARE_IMPORT_URIS)
            logdNoFile { "start entry activity " + intent.extras }
            startActivityFix(intent)
        } else { //app启动过了。有主界面，则直接跳入到ShareFragment
            SendListSelectorFragment.start(this, true)
        }
    }
}