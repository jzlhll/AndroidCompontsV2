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
import com.au.module_android.log.logEx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import com.au.module_android.utils.parcelableArrayListExtraCompat
import com.au.module_android.utils.parcelableExtraCompat
import com.au.module_android.utilsmedia.isFromMyApp
import com.au.module_androidui.ui.bindings.BindingActivity
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** 在临时授权仍有效时保存外部分享文件，完成后进入发送列表。 */
class ShareImportActivity : BindingActivity<ActivityImportBinding>() {
    private val shareInRepository: GlobalShareInRepoObj by inject()
    private var importJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dealWithIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dealWithIntent(intent)
    }

    private fun dealWithIntent(intent: Intent?) {
        try {
            val sharedImportUris = mutableListOf<Uri>()
            when (intent?.action) {
                Intent.ACTION_SEND -> intent.parcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let(sharedImportUris::add)
                Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let(sharedImportUris::addAll)
                else -> {
                    if (importJob?.isActive != true) finish()
                    return
                }
            }
            if (sharedImportUris.isEmpty()) {
                intent?.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(sharedImportUris::add)
                }
            }
            require(sharedImportUris.isNotEmpty() && sharedImportUris.all { it.scheme == "content" })
            handleIncreaseUris(sharedImportUris.distinct())
        } catch (e: Exception) {
            logEx(throwable = e) { "Read shared intent failed" }
            Toast.makeText(this, getString(R.string.something_error), Toast.LENGTH_SHORT).show()
            if (importJob?.isActive != true) finish()
        }
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

        val previous = importJob
        importJob = lifecycleScope.launch {
            try {
                previous?.join()
                shareInRepository.addShareInUris(uris, FROM_SHARE_IN)
                if (importJob === currentCoroutineContext()[Job]) {
                    jumpNext()
                    finish()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logEx(throwable = e) { "Import shared files failed" }
                Toast.makeText(this@ShareImportActivity, getString(R.string.something_error), Toast.LENGTH_SHORT).show()
                if (importJob === currentCoroutineContext()[Job]) finish()
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
