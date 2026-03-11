package com.allan.androidlearning.activities

import android.content.Context
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_android.log.logt
import com.au.module_android.utils.dp
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utilthread.CoroutineConcurrentLimiter2
import com.au.module_android.utilthread.CoroutineConcurrentLimiter3
import com.au.module_android.utilthread.PauseController
import com.au.module_android.utilthread.SerialTaskExecutor
import com.au.module_android.utilthread.SingleCoroutineTaskExecutor
import com.au.module_androidui.databinding.SimpleTextBinding
import com.au.module_androidui.selectlist.SimpleItem
import com.au.module_androidui.selectlist.SimpleListFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * @author allan
 * @date :2024/7/29 10:50
 * @description:
 */
@EntryFrgName(priority = 1)
class CoroutineFragment(override val title: String = "Coroutine")
        : SimpleListFragment<KotlinCoroutineSelectListItem>() {

    override fun itemTopMargin(): Int {
        return 8.dp
    }

    override fun createItemView(
        context: Context,
        value: KotlinCoroutineSelectListItem
    ): ViewBinding {
        return SimpleTextBinding.inflate(LayoutInflater.from(context))
    }

    override fun bindItemView(vb: ViewBinding, item: KotlinCoroutineSelectListItem) {
        vb as SimpleTextBinding
        vb.root.onClick {
            item.onItemClick.invoke()
        }
        vb.root.text = item.itemName
    }

    private val coroutineConcurrentLimiter = CoroutineConcurrentLimiter2(2)

    private val mSerialTaskExecutor by lazy {
        SerialTaskExecutor<Pair<String, Long>>() { bean->
            logt{"SerialTaskExecutor: $bean"}
            Thread.sleep(bean.second)
            logt{"SerialTaskExecutor: $bean over!"}
        }
    }

    /**
     *模拟另外一个串行任务执行器，确保不同的任务可以并行执行
     */
    private val mOtherSerialTaskExecutor by lazy {
        SerialTaskExecutor<Pair<String, Long>> { bean->
            logt{"Other SerialTaskExecutor: $bean"}
            Thread.sleep(bean.second)
            logt{"Other SerialTaskExecutor: $bean 完成!"}
        }
    }

    private val singleCoroutineTaskExecutor = SingleCoroutineTaskExecutor(scope = lifecycleScope)

    private val _items = listOf(
        KotlinCoroutineSelectListItem("CoroutineConcurrentLimiter") {
            coroutineConcurrentLimiter.submit {
                val randomIndex = (0..1000).random()
                logt { "CoroutineConcurrentLimiter: 开始执行 $randomIndex" }
                Thread.sleep(2000)
//                delay(2000)
                logt { "CoroutineConcurrentLimiter: 执行完成 $randomIndex" }
            }
        },

        KotlinCoroutineSelectListItem("CoroutineConcurrentLimiter 10000") {
            lifecycleScope.launchOnThread {
                for (i in 0 until 20) {
                    coroutineConcurrentLimiter.submit {
                        val randomIndex = (0..1000).random()
                        logt { "CoroutineConcurrentLimiter: 开始执行 $randomIndex" }
                        Thread.sleep(2000)
                        logt { "CoroutineConcurrentLimiter: 执行完成 $randomIndex" }
                    }
                }
//                coroutineConcurrentLimiter.joinAll()
                logt { "CoroutineConcurrentLimiter 10000: 所有任务完成" }
            }
        },

        /////////////////
        KotlinCoroutineSelectListItem("SingleCoroutineTaskExecutor") {
            singleCoroutineTaskExecutor.submit {
                logt { "SingleCoroutineTaskExecutor: 开始执行" }
                Thread.sleep(2000)
//                delay(2000)
                logt { "SingleCoroutineTaskExecutor: 执行完成" }
            }
        },
        KotlinCoroutineSelectListItem("SingleCoroutineTaskExecutor await") {
            lifecycleScope.launchOnThread {
                singleCoroutineTaskExecutor.await {
                    logt { "SingleCoroutineTaskExecutor: 开始执行" }
                    Thread.sleep(2000)
//                    delay(2000)
                    logt { "SingleCoroutineTaskExecutor: 执行完成" }
                }

                logt { "SingleCoroutineTaskExecutor: await 完成" }
            }
        },

        ////////////////////////
        KotlinCoroutineSelectListItem("SerialTaskExecutor") {
            mSerialTaskExecutor.submit("test5" to 4000)
        },
        KotlinCoroutineSelectListItem("SerialTaskExecutor") {
            mSerialTaskExecutor.submit("test1" to 2000)
        },
        KotlinCoroutineSelectListItem("SerialTaskExecutor2") {
            mSerialTaskExecutor.submit("test2" to 0)
        },
        KotlinCoroutineSelectListItem("SerialTaskExecutor Other") {
            mOtherSerialTaskExecutor.submit("other1" to 800)
        },

        /////测试协程等待
        KotlinCoroutineSelectListItem("协程等待测试开始") {
            lifecycleScope.launchOnThread {
                for (i in 0 until 100) {
                    val ans = pauseController.waitIfPaused()
                    logt { "waitCode $ans 协程等待测试开始 $i" }
                    delay(1000)
                }
            }
        },
        KotlinCoroutineSelectListItem("协程等待测试Pause") {
            pauseController.pause()
        },
        KotlinCoroutineSelectListItem("协程等待测试Resume") {
            pauseController.resume()
        },
        KotlinCoroutineSelectListItem("协程等待测试Stop") {
            pauseController.stop()
        },
        KotlinCoroutineSelectListItem("测试CoroutineConcurrentLimiter3") {
            lifecycleScope.launch {
                testV3()
            }
        }
    )

    suspend fun testV3() {
        println("=== 开始测试 CoroutineConcurrentLimiter3 ===")

        // 1. 创建限流器，最大并发
        val limiter = CoroutineConcurrentLimiter3<Int>(
            maxConcurrency = 2,
            baseDispatcher = Dispatchers.Default // 测试用 Default
        ) { taskId ->
            // 模拟耗时任务
            println("Task $taskId START at ${Thread.currentThread().name}")
            delay(500) // 模拟 100ms 耗时
            println("Task $taskId END")
        }

        // 2. 批量提交 20 个任务
        println("\n--- 提交 20 个任务 ---")
        val tasks = (1..50).toList()
        limiter.submitList(tasks)

        // 观察运行状态
        repeat(5) {
            delay(100)
            println("Status: Pending=${limiter.getPendingCount()}, Running=${limiter.getRunningCount()}")
        }

        // 3. 追加任务
        println("\n--- 追加单个任务 999 ---")
        limiter.submit(999)

        delay(3000) // 等待一部分任务跑完

        // 4. 测试 StopAll (优雅停止)
//        println("\n--- 测试 StopAll(false) - 清空队列但不杀当前任务 ---")
//        limiter.submitList((100..110).toList()) // 再加点任务
//        println("Before stop: Pending=${limiter.getPendingCount()}")
//        limiter.stopAll(ifStopCurrent = false)
//        println("After stop: Pending=${limiter.getPendingCount()} (Should be 0)")
//
        delay(500) // 观察是否有任务还在跑（应该只有 stop 前已经开始的在跑）

        // 5. 测试 StopAll (强制停止)
        println("\n--- 测试 StopAll(true) - 强制杀所有 ---")
        limiter.submitList((200..210).toList())
        delay(1500) // 让一些跑起来
        println("Running before kill: ${limiter.getRunningCount()}")
        limiter.stopAll(ifStopCurrent = true)
        delay(300)
        println("Running after kill: ${limiter.getRunningCount()} (Should be 0)")

        println("\n=== 测试结束 ===")
    }

    private val pauseController = PauseController()

    override val items: List<KotlinCoroutineSelectListItem>
        get() = _items

    override fun onDestroyView() {
        super.onDestroyView()
        mSerialTaskExecutor.close()
        mOtherSerialTaskExecutor.close()
//        coroutineConcurrentLimiter.cancelAll()
    }

}

class KotlinCoroutineSelectListItem(override val itemName: String, override val onItemClick: () -> Unit) : SimpleItem()