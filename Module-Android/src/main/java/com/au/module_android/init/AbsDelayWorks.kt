package com.au.module_android.init

import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.CopyOnWriteArraySet

abstract class AbsDelayWorks {
    /**
     * 描述一份工作；
     * @param mainThread 是否是主线程
     * @param coldOnce 是否是冷启动干一次, true就是干一次。false就是每次owner启动都干。
     * @param block 执行任务; 返回值false，则下一个任务可以不做delay。
     */
    private data class Work(val name:String,
                            val delayTs:Long,
                            val mainThread:Boolean,
                            val coldOnce:Boolean,
                            val block:()->Boolean)
    companion object {
        private var sWorks: CopyOnWriteArraySet<Work>? = null
    }

    private inline fun safeRun(block: () -> Unit) {
        block()
    }

    private fun getWorks(): CopyOnWriteArraySet<Work> {
        if (sWorks == null) {
            sWorks = CopyOnWriteArraySet<Work>().also { list->
                //新功能提醒
                createShowNewFeature()?.let { list.add(it) }
                //监听变化检查协议变化。要的就是立刻监听直接生效。
                list.add(createObserveDictCheckPrivacyWork())
                //googlePlay评分。始终放在最后
                list.add(createGooglePlayWork())
                //创建通知通道
                list.add(createNotificationChannelsWork())
            }
        }
        return sWorks!!
    }

    fun startDelayWorks() {
        val works = getWorks()
        //串行的主线程任务
        frg.lifecycleScope.launch {
            serialWorksInner(works.filter { it.mainThread })
        }

        //分开的子线程任务
        BaseGlobalConst.mainScope.launchOnThread {
            serialWorksInner(works.filter { !it.mainThread })
        }
    }

    private suspend fun serialWorksInner(works:List<Work>) {
        val iterator = works.iterator()
        var lastBlockRet:Boolean? = null
        while (iterator.hasNext()) {
            val work = iterator.next()
            if(lastBlockRet == null || lastBlockRet == true) delay(work.delayTs)
            lastBlockRet = work.block() //执行并得到结果
            if (work.coldOnce) {
                sWorks?.remove(work)
            }
        }
    }

    private fun showNewFeature() : Boolean{
        if (true) { //用于调试让NewFeature必定出现，写成false即可
            if (BuildConfig.NEW_FEATURE_VERSION_CODE != BuildConfig.VERSION_CODE) {
                return false
            }

            val saveCode = MmkvConst.newFeatureAlert
            val alwaysShow = false
            if (saveCode == BuildConfig.NEW_FEATURE_VERSION_CODE && !alwaysShow) {
                return false
            }

            MmkvConst.newFeatureAlert = BuildConfig.NEW_FEATURE_VERSION_CODE
        }

        val context = frg.context ?: return false
        FragmentContainerActivity.startContainerFragment(context, NewFeatureFragment::class.java)
        return true
    }

    /**
     * 如果显示了就return true
     */
    private fun checkPrivacyAndUserAgreement() : Boolean {
        val topActivity = BaseGlobalConst.activityList.lastOrNull().asOrNull<FragmentContainerActivity>() ?: return false

        if (UserConst.token.isNullOrEmpty()) {
            return false
        }

        val savedPrivacyVersion = MmkvConst.privacyStatementVersion
        val savedAgreeVersion = MmkvConst.userAgreementVersion
        val dictAgreeVersion = userAgreementAppDictVersion()
        val dictPrivacyVersion = pravicyPolicyAppDictVersion()
        logD { "delay work savedPrivacyVersion$savedPrivacyVersion savedAgreeVersion$savedAgreeVersion dictPrivacyVersion$dictPrivacyVersion dictAgreeVersion$dictAgreeVersion " }
        //四种情况写全

        if (savedPrivacyVersion >= dictPrivacyVersion && savedAgreeVersion >= dictAgreeVersion) {
            return false
        }
        var title:String? = null
        var desc:String? = null
        if (savedPrivacyVersion < dictPrivacyVersion && savedAgreeVersion < dictAgreeVersion) {
            title = getStringCompat(R.string.user_agreement_privacy_updated)
            desc = topActivity.getString(R.string.user_agreement_privacy_adjustments)
        } else if (savedPrivacyVersion >= dictPrivacyVersion && savedAgreeVersion < dictAgreeVersion) {
            title = getStringCompat(R.string.user_agreement_updated)
            desc = topActivity.getString(R.string.user_agreement_adjustments)
        } else if (savedPrivacyVersion < dictPrivacyVersion && savedAgreeVersion >= dictAgreeVersion) {
            title = getStringCompat(R.string.privacy_statement_updated)
            desc = topActivity.getString(R.string.privacy_statement_adjustments)
        }

        MmkvConst.privacyStatementVersion = dictPrivacyVersion
        MmkvConst.userAgreementVersion = dictAgreeVersion

        StronglyCenterDialogV2.show(topActivity.supportFragmentManager, title = title, content = desc,
            sureText = getStringCompat(R.string.check),
            cancelText = getStringCompat(R.string.dismiss)
        ) {
            FragmentContainerActivity.startContainerFragment(topActivity, AboutFragment::class.java)
            it.dismissAllowingStateLoss()
        }
        return true
    }

    private fun generateNotificationChannel(channelId:String) {
        BaseGlobalConst.app.apply {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(channelId) != null) {
                logd("create notifi channel $channelId exist.")
                return@apply
            }

            val channelName = TyiotPush.channelIdToChannelName(channelId)
            val soundId = TyiotPush.channelIdToSoundId(channelId)
            val importance = TyiotPush.channelIdToImportant(channelId)

            val channel = NotificationChannel(channelId, channelName, importance)
            channel.setShowBadge(false)
            channel.enableVibration(importance > NotificationManager.IMPORTANCE_DEFAULT)
            channel.enableLights(importance > NotificationManager.IMPORTANCE_DEFAULT)
            if (soundId > 0) {
                val sound = (ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId).toUri()
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                channel.setSound(sound, audioAttributes)
            }
            mgr.createNotificationChannel(channel)
            logd("create new notifi channel $channelId!")
        }
    }
}