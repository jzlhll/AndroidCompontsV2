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
import com.au.module_android.utilthread.CoroutineConcurrentLimiter
import com.au.module_android.utilthread.SerialTaskExecutor
import com.au.module_android.utilthread.SingleCoroutineTaskExecutor
import com.au.module_androidui.databinding.SimpleTextBinding
import com.au.module_androidui.selectlist.SimpleItem
import com.au.module_androidui.selectlist.SimpleListFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    private val coroutineConcurrentLimiter = CoroutineConcurrentLimiter(2)

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
        }
    )

    override val items: List<KotlinCoroutineSelectListItem>
        get() = _items

    override fun onDestroyView() {
        super.onDestroyView()
        mSerialTaskExecutor.close()
        mOtherSerialTaskExecutor.close()
    }

}

class KotlinCoroutineSelectListItem(override val itemName: String, override val onItemClick: () -> Unit) : SimpleItem()