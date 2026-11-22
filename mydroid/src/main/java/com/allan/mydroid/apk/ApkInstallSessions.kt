package com.allan.mydroid.apk

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.IntentCompat
import com.au.module_android.Globals
import com.au.module_android.log.logEx
import com.au.module_android.log.loge
import com.au.module_android.utils.launchOnIOThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.util.UUID

/** 绑定本进程提交的 APK 与系统安装回执。 */
object ApkInstallSessions {
    enum class Phase(val blocksQueue: Boolean = false) {
        AwaitingResult(true), AwaitingConfirmation(true), Succeeded(true), Verified, Unverified, Unconfirmed, Aborted, Failed,
    }

    data class Attempt(
        val sessionId: Int,
        val token: String,
        val target: ApkInstallVerifier.Target,
        val phase: Phase,
        val apkPath: String,
        val confirmation: Intent? = null,
        val confirmationOpened: Boolean = false,
    )

    private const val CALLBACK_ACTION = "com.allan.mydroid.action.APK_INSTALL_RESULT"
    private val lock = Any()
    private val submitLock = Mutex()
    val activityResultPrefix = "mydroid.apk.${UUID.randomUUID()}"
    private val currentAttemptFlow = MutableStateFlow<Attempt?>(null)
    val attemptFlow = currentAttemptFlow.asStateFlow()
    private var installSourceSettingsOpened = false
    private val processPreparation by lazy {
        val context = Globals.app
        context.deleteSharedPreferences("apk_install_verification")
        val installer = context.packageManager.packageInstaller
        for (session in installer.mySessions) {
            // 上一进程可能在复制 APK 时退出，只清理尚未提交的临时会话。
            if (!session.isCommitted && !session.isSealed) abandon(session.sessionId)
        }
    }

    suspend fun prepare() = withContext(Dispatchers.IO) {
        processPreparation
    }

    fun claimInstallSourceSettings(explicit: Boolean): Boolean = synchronized(lock) {
        if (installSourceSettingsOpened && !explicit) return@synchronized false
        installSourceSettingsOpened = true
        true
    }

    suspend fun submit(apk: ReceivedApk, automatic: Boolean): Boolean = submitLock.withLock {
        withContext(Dispatchers.IO) {
            processPreparation
            if (attemptFlow.value?.phase?.blocksQueue == true || automatic && apk.file in ApkReceiveSession.handledFilesFlow.value) {
                return@withContext false
            }
            submitSession(apk)
        }
    }

    private suspend fun submitSession(apk: ReceivedApk): Boolean {
        val source = ApkReceiveSession.openCurrentApk(apk) ?: return false
        return source.use { stageAndCommit(apk, it) }
    }

    private suspend fun stageAndCommit(apk: ReceivedApk, input: FileInputStream): Boolean {
        val context = Globals.app
        val installer = context.packageManager.packageInstaller
        val coroutineContext = currentCoroutineContext()
        val size = input.channel.size()
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(apk.buildInfo.applicationId)
            setSize(size)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val attempt = Attempt(
            installer.createSession(params),
            UUID.randomUUID().toString(),
            ApkInstallVerifier.Target(apk.buildInfo.applicationId, apk.buildInfo.sha256),
            Phase.AwaitingResult,
            apkPath = apk.file.absolutePath,
        )
        var committed = false
        try {
            installer.openSession(attempt.sessionId).use { session ->
                session.openWrite("base.apk", 0, size).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                    }
                    session.fsync(output)
                }
                val callback = Intent(context, ApkInstallResultReceiver::class.java)
                    .setAction(CALLBACK_ACTION)
                    .setData(Uri.fromParts("apk-install", attempt.token, null))
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val sender = PendingIntent.getBroadcast(context, attempt.sessionId, callback, flags)
                committed = ApkReceiveSession.commitCurrentApk(apk) {
                    coroutineContext.ensureActive()
                    synchronized(lock) { currentAttemptFlow.value = attempt }
                    coroutineContext.ensureActive()
                    session.commit(sender.intentSender)
                }
                if (committed) ApkReceiveSession.markHandled(apk)
            }
        } finally {
            if (!committed) {
                abandon(attempt.sessionId)
                synchronized(lock) {
                    if (currentAttemptFlow.value?.token == attempt.token) {
                        currentAttemptFlow.value = attempt.copy(phase = Phase.Failed)
                    }
                }
            }
        }
        return committed
    }

    suspend fun exists(attempt: Attempt): Boolean = withContext(Dispatchers.IO) {
        Globals.app.packageManager.packageInstaller.getSessionInfo(attempt.sessionId)?.isCommitted == true
    }

    fun takeConfirmation(attempt: Attempt): Intent? {
        val confirmation = attempt.confirmation ?: return null
        return confirmation.takeIf {
            currentAttemptFlow.compareAndSet(attempt, attempt.copy(confirmation = null, confirmationOpened = true))
        }
    }

    fun finishConfirmation(token: String) {
        synchronized(lock) {
            val attempt = currentAttemptFlow.value ?: return
            if (attempt.token != token || attempt.phase != Phase.AwaitingConfirmation) return
            currentAttemptFlow.value = attempt.copy(phase = Phase.AwaitingResult, confirmationOpened = false)
        }
    }

    suspend fun abandonSuperseded(attempt: Attempt) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = currentAttemptFlow.value ?: return@synchronized
            if (current.token == attempt.token && current.phase == Phase.AwaitingConfirmation) abandon(current.sessionId)
        }
    }

    suspend fun updatePhase(attempt: Attempt, phase: Phase) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = currentAttemptFlow.value ?: return@synchronized
            if (current == attempt) {
                currentAttemptFlow.value = current.copy(phase = phase, confirmation = null)
                if (phase == Phase.Failed) abandon(current.sessionId)
            }
        }
    }

    fun receiveResult(intent: Intent) {
        if (intent.action != CALLBACK_ACTION || intent.data?.scheme != "apk-install") return
        val token = intent.data?.schemeSpecificPart ?: return
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sessionId == -1 || !intent.hasExtra(PackageInstaller.EXTRA_STATUS)) return
        if (intent.getBooleanExtra(PackageInstaller.EXTRA_PRE_APPROVAL, false)) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val installedPath = synchronized(lock) {
            val attempt = currentAttemptFlow.value ?: return
            if (attempt.token != token || attempt.sessionId != sessionId) return
            if (attempt.phase != Phase.AwaitingResult && attempt.phase != Phase.AwaitingConfirmation &&
                attempt.phase != Phase.Unconfirmed
            ) return
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                if (attempt.phase == Phase.Unconfirmed || attempt.confirmationOpened || attempt.confirmation != null) return
                // 此回执只能由系统持有的 PendingIntent 送达，保留系统确认页面使用的参数。
                val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmation != null) {
                    currentAttemptFlow.value = attempt.copy(
                        phase = Phase.AwaitingConfirmation, confirmation = confirmation, confirmationOpened = false,
                    )
                } else {
                    loge { "Invalid confirmation intent for APK install session $sessionId" }
                    currentAttemptFlow.value = attempt.copy(phase = Phase.Failed, confirmation = null)
                    abandon(sessionId)
                }
                return
            }
            val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
            val phase = when {
                status == PackageInstaller.STATUS_SUCCESS &&
                    (packageName == null || packageName == attempt.target.applicationId) -> Phase.Succeeded
                status == PackageInstaller.STATUS_FAILURE_ABORTED -> Phase.Aborted
                else -> Phase.Failed
            }
            if (phase == Phase.Failed) {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                loge { "APK install session $sessionId failed: status=$status package=$packageName message=$message" }
            }
            currentAttemptFlow.value = attempt.copy(phase = phase, confirmation = null)
            if (phase == Phase.Succeeded) attempt.apkPath else null
        }
        ApkReceiveSession.removeInstalled(installedPath)
    }

    private fun abandon(sessionId: Int) {
        try {
            Globals.app.packageManager.packageInstaller.abandonSession(sessionId)
        } catch (e: SecurityException) {
            logEx(throwable = e) { "Abandon APK install session $sessionId failed" }
        } catch (e: IllegalArgumentException) {
            logEx(throwable = e) { "Abandon APK install session $sessionId failed" }
        }
    }
}

/** 由 PackageInstaller 通过专用 PendingIntent 回传安装结果。 */
class ApkInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Globals.mainScope.launchOnIOThread {
            try {
                ApkInstallSessions.receiveResult(intent)
            } finally {
                result.finish()
            }
        }
    }
}
