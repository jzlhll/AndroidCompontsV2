package com.au.module_android.init

import com.au.module_android.Globals
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.launchOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 延迟任务，跟随某个界面启动后，只干一次的，使用全局 scope 执行；每次都干的跟随 lifecycle 的 scope 走。
 */
class DelayWorks(private val scope: CoroutineScope) {
    open class Config {
        var workList: CopyOnWriteArraySet<Work> = CopyOnWriteArraySet()

        fun workList(workList: MutableList<Work>): Config {
            this.workList.addAll(workList)
            return this
        }

        companion object {
            var sConfig: Config? = null

            fun builder(): Config {
                if (sConfig != null) {
                    throw RuntimeException("config is not null can only do once!")
                }
                return Config().also {
                    sConfig = it
                }
            }
        }
    }

    /**
     * 描述一份工作；
     * @param mainThread 是否是主线程
     * @param coldOnce 是否是冷启动干一次, true就是干一次。false就是每次owner启动都干。
     * @param block 执行任务; 返回值false，则下一个任务可以不做delay。
     */
    data class Work(val name:String,
                            val delayTs:Long,
                            val mainThread:Boolean,
                            val coldOnce:Boolean,
                            val block:()->Boolean)

    fun startDelayWorks() {
        val workList = Config.sConfig?.workList ?: return

        workList.filter { it.coldOnce }.let { allOnceWorks ->
            if (allOnceWorks.isEmpty()) return
            Globals.mainScope.launchOnUi {
                serialWorksInner(allOnceWorks.filter { it.mainThread })
            }
            Globals.mainScope.launchOnThread {
                serialWorksInner(allOnceWorks.filter { !it.mainThread })
            }
        }

        workList.filter { !it.coldOnce }.let { allWorks ->
            if (allWorks.isEmpty()) return
            scope.launchOnUi {
                serialWorksInner(allWorks.filter { it.mainThread })
            }
            scope.launchOnThread {
                serialWorksInner(allWorks.filter { !it.mainThread })
            }
        }
    }

    private suspend fun serialWorksInner(works:List<Work>) {
        val iterator = works.iterator()
        var lastBlockRet:Boolean? = null
        while (iterator.hasNext()) {
            val work = iterator.next()
            if((lastBlockRet == null) || lastBlockRet) delay(work.delayTs)
            lastBlockRet = work.block() //执行并得到结果
            if (work.coldOnce) {
                Config.sConfig?.workList?.remove(work)
            }
        }
    }

}