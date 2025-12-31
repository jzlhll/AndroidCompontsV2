package com.au.module_android.viewmodel

import androidx.lifecycle.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/*
使用范例：

第一步：申明单例：
object AllShareViewModelManager {
    // 蓝牙业务的ViewModel管理器（泛型指定BTViewModel）
    val btViewModelManager = ShareViewModelManager(BTViewModel::class)
    // 可扩展：WiFi业务的管理器（示例）
    // val wifiViewModelManager = ShareViewModelManager(
          vmKClass = WiFiViewModel::class,
           factory = WiFiViewModel.Factory(WiFiRepository()) // 传自定义 factory
           )
}

第二步1：创建ViewModel1 BTViewModel（支持无参构造，也可扩展为带参构造）
class BTViewModel : ViewModel() {
    val btConnectState = MutableLiveData<Boolean>(false)
    override fun onCleared() {
        super.onCleared()
        // 释放蓝牙资源
        btConnectState.value = false
    }
}

第二步2：创建ViewModel2 带参构造的ViewModel（需自定义Factory）
class WiFiViewModel(private val repo: WiFiRepository) : ViewModel() {
    // 自定义工厂
    class Factory(private val repo: WiFiRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WiFiViewModel(repo) as T
        }
    }
}

第三步：在Activity/fragment中使用
class BTConnectActivity : AppCompatActivity() {
    // 无需关注底层逻辑，直接通过全局入口获取
    private val btViewModel by lazy {
        AllShareViewModelManager.btViewModelManager.getViewModel(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        btViewModel.btConnectState.observe(this) { /* 更新UI */ }
    }
}

class BTPairFragment : Fragment() {
    private val btViewModel by lazy {
        // 传viewLifecycleOwner + 标记isViewLifecycle=true，避免计数提前减少
        AllShareViewModelManager.btViewModelManager.getViewModel(
            lifecycleOwner = viewLifecycleOwner,
            isViewLifecycle = true
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btViewModel.btConnectState.observe(viewLifecycleOwner) { /* 更新Fragment UI */ }
    }
}
 */

/**
 * 通用共享ViewModel管理器
 *
 * @param vmKClass ViewModel的KClass（替代Class，更贴合Kotlin语法）
 * @param factory 可选的自定义工厂（支持依赖注入，突破无参构造限制）
 */
class ShareViewModelManager<VM : ViewModel>(
    private val vmKClass: KClass<VM>,
    private val factory: ViewModelProvider.Factory = DefaultViewModelFactory(vmKClass)
) {
    private val viewModelStore = ViewModelStore()
    private val activeComponentCount = AtomicInteger(0)

    /**
     * 获取共享ViewModel
     * @param lifecycleOwner 生命周期所有者（Activity/Fragment/viewLifecycleOwner）
     * @param isViewLifecycle Fragment传viewLifecycleOwner时需标记，避免计数提前减少
     */
    fun getViewModel(
        lifecycleOwner: LifecycleOwner,
        isViewLifecycle: Boolean = false
    ): VM {
        // 计数+1（原子操作，线程安全）
        activeComponentCount.incrementAndGet()

        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                // 区分：Fragment视图销毁（ON_DESTROY） vs 组件本身销毁
                val isDestroy = if (isViewLifecycle) {
                    // viewLifecycleOwner的ON_DESTROY是视图销毁，需等Fragment本身销毁才减计数
                    event == Lifecycle.Event.ON_DESTROY && source.lifecycle.currentState == Lifecycle.State.DESTROYED
                } else {
                    // Activity/Fragment本身销毁
                    event == Lifecycle.Event.ON_DESTROY
                }

                if (isDestroy) {
                    // 安全移除Observer（避免内存泄漏）
                    if (source.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                        source.lifecycle.removeObserver(this)
                    }
                    // 计数减1，为0则清理ViewModel
                    if (activeComponentCount.decrementAndGet() == 0) {
                        viewModelStore.clear() // 触发ViewModel.onCleared()
                    }
                }
            }
        })

        return ViewModelProvider(viewModelStore, factory)[vmKClass.java]
    }

    // 新增：手动强制清理（应对主动退出业务场景）
    fun forceClear() {
        activeComponentCount.set(0)
        viewModelStore.clear()
    }

    // 默认ViewModel工厂（处理反射异常，支持无参构造）
    private class DefaultViewModelFactory<VM : ViewModel>(private val vmKClass: KClass<VM>) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return try {
                // 优先尝试无参构造
                vmKClass.java.getDeclaredConstructor().newInstance() as T
            } catch (e: NoSuchMethodException) {
                throw IllegalArgumentException("${vmKClass.simpleName} 必须提供无参构造函数，或传入自定义Factory", e)
            } catch (e: Exception) {
                throw RuntimeException("创建 ${vmKClass.simpleName} 失败", e)
            }
        }
    }
}