package com.au.module_android.init

import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.launchOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 延迟任务，跟随某个界面启动后，只干一次的，使用全局 scope 执行；每次都干的跟随 lifecycle 的 scope 走。
 */
class DelayWorks(private val scope: CoroutineScope) {
    companion object {
        private var sWorkList: CopyOnWriteArraySet<IWork>? = CopyOnWriteArraySet()

        private var isInit = false

        fun isInited() = isInit

        fun addWorkList(workList: List<IWork>) {
            isInit = true
            this.sWorkList?.addAll(workList)
        }
    }

    interface IWork {
        val tag: String
    }

    /**
     * 描述一份工作；
     * @param mainThread 是否是主线程
     * @param coldOnce 是否是冷启动干一次, true就是干一次。false就是每次owner启动都干。
     * @param block 执行任务; 返回值的数值表示下一个任务需要延迟启动的时间。
     */
    data class Work(
        override val tag:String = "",
        val mainThread:Boolean,
        val coldOnce:Boolean,
        val block:()->Long) : IWork

    /**
     * 目前描述一份独立的工作，不串入队列，且只是冷启动一次，而且主线程执行。
     */
    data class IndependentWork(
        override val tag: String,
        val delayTs: Long,
        val block:()-> Unit
    ) : IWork

    fun startDelayWorks() {
        val workList = sWorkList ?: return
        //先执行独立的，并直接移除
        val independentWorkList = workList.filterIsInstance<IndependentWork>()
        independentWorkList.forEach {
            workList.remove(it)
            scope.launchOnUi {
                delay(it.delayTs)
                it.block()
            }
        }

        //分裂为4种情况
        val allColdOnceAndMainList = workList.filterIsInstance<Work>().filter { it.coldOnce && it.mainThread }
        val allColdOnceAndSubList = workList.filterIsInstance<Work>().filter { it.coldOnce && !it.mainThread }
        val allNotColdOnceAndMainList = workList.filterIsInstance<Work>().filter { !it.coldOnce && it.mainThread }
        val allNotColdOnceAndSubList = workList.filterIsInstance<Work>().filter { !it.coldOnce && !it.mainThread }
        scope.launchOnUi {
            //排队执行，先做冷启动的，再做非冷启动的
            serialWorksInner(allColdOnceAndMainList)
            serialWorksInner(allNotColdOnceAndMainList)
        }
        scope.launchOnThread {
            //排队执行，先做冷启动的，再做非冷启动的
            serialWorksInner(allColdOnceAndSubList)
            serialWorksInner(allNotColdOnceAndSubList)
        }
    }

    private suspend fun serialWorksInner(works:List<Work>) {
        val iterator = works.iterator()
        var nextNeedDelay:Long = 0
        while (iterator.hasNext()) {
            val work = iterator.next()
            delay(nextNeedDelay)
            nextNeedDelay = work.block() //执行并得到结果
            if (work.coldOnce) {
                sWorkList?.let { workList ->
                    workList.remove(work)
                    if (workList.isEmpty()) {
                        sWorkList = null
                    }
                }
            }
        }
    }

}