package com.au.module_android.init

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArraySet

abstract class ApplicationDelayWorks(val scope : CoroutineScope) {
    /**
     * 描述一份工作；
     * @param mainThread 是否是主线程
     * @param block 执行任务; 返回值false，则下一个任务可以不做delay。
     */
    open class Work(val name:String,
                            val delayTs:Long,
                            val mainThread:Boolean,
                            val block:()->Boolean)

    companion object {
        private var sWorks: CopyOnWriteArraySet<Work>? = null
    }

    private inline fun safeRun(block: () -> Unit) {
        block()
    }

    abstract fun createAllWorks(): CopyOnWriteArraySet<Work>

    private fun getWorks(): CopyOnWriteArraySet<Work> {
        if (sWorks == null) {
            sWorks = createAllWorks()
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
            if(lastBlockRet == null || lastBlockRet) delay(work.delayTs)
            lastBlockRet = work.block() //执行并得到结果
            if (work.coldOnce) {
                sWorks?.remove(work)
            }
        }
    }

}