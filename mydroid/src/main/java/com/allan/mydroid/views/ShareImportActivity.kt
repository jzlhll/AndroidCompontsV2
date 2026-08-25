package com.allan.mydroid.views

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.MyDroidActivity
import com.allan.mydroid.R
import com.allan.mydroid.beansinner.FROM_SHARE_IN
import com.allan.mydroid.databinding.ActivityImportBinding
import com.allan.mydroid.repository.GlobalShareInRepoObj
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.parcelableArrayListExtraCompat
import com.au.module_android.utils.parcelableExtraCompat
import com.au.module_android.utilsmedia.isFromMyApp
import com.au.module_androidui.ui.bindings.BindingActivity
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ShareImportActivity : BindingActivity<ActivityImportBinding>() {
    private val shareInRepository: GlobalShareInRepoObj by inject()

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
            Toast.makeText(this, Globals.getString(R.string.import_to_send_list_hint), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Globals.mainScope.launchOnThread {
            shareInRepository.addShareInUris(uris, FROM_SHARE_IN)

            lifecycleScope.launch {
                jumpNext()
                finish()
            }
        }
    }

    private fun jumpNext() {
        val intent = Intent(this, MyDroidActivity::class.java).apply {
            action = MyDroidActivity.ACTION_OPEN_SEND_SELECTOR
            putExtra(MyDroidActivity.EXTRA_AUTO_ENTER_SEND, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
}
