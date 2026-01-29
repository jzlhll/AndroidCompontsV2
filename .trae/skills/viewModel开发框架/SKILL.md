---
name: viewModel开发框架
description: 当涉及到编写ViewModel的代码时候遵守该技能。
---

# ViewModel 开发框架

## 1. 核心架构

### 1.1 继承 AbsActionDispatcherViewModel
- 所有ViewModel必须继承 `AbsActionDispatcherViewModel`，提供基于Reducer模式的Action分发机制
- 代码位置：`Module-AndroidCommon/src/main/java/com/au/module_android/simpleflow`

### 1.2 依赖注入
- 使用 `by viewModels()` 扩展函数在Fragment/Activity中获取ViewModel实例
- 禁止直接创建ViewModel实例

## 2. Action 设计

### 2.1 定义 Action
- 所有Action实现 `IStateAction` 接口
- 无参数使用 `object`，带参数使用 `data class`
- 命名规范：动词+名词，如 `RequestDataAction`

### 2.2 注册 Action Reducer
- 在ViewModel的 `init` 块中注册Action处理器
- 使用 `getActionStore().reduce()` 绑定Action与处理逻辑

```kotlin
init {
    getActionStore().reduce(RefreshDataAction::class.java) {
        loadData()
    }
}
```

### 2.3 触发 Action
- UI层通过 `viewModel.dispatch(action)` 触发Action

## 3. 状态管理

### 3.1 StatusState
- 状态包含：`Uninitialized`、`Loading`、`Success<T>`、`Error`
- 使用扩展函数 `setLoading()`、`setSuccess(data)`、`setError(throwable)` 更新状态

### 3.2 状态流创建
- 使用 `createStatusStateFlow()` 创建 `MutableStateFlow<StatusState<T>>`
- 通过 `asStateFlow()` 暴露只读状态流给UI层

```kotlin
private val _dataState = createStatusStateFlow<List<Item>>()
val dataState = _dataState.asStateFlow()
```

### 3.3 状态更新
- 遵循单向数据流：UI → Action → ViewModel → State → UI
- 在Reducer或业务逻辑中更新状态

## 4. 协程与线程管理

### 4.1 协程作用域
- 使用 `viewModelScope` 作为协程作用域
- 禁止使用全局协程作用域

### 4.2 线程切换
- 后台线程：`launchOnThread()`（`Dispatchers.Default`）
- IO线程：`launchOnIOThread()`（`Dispatchers.IO`）
- 主线程：`launchOnUi()`（`Dispatchers.Main.immediate`）

### 4.3 异步任务封装
- 使用 `runCallCatch()` 自动处理异常和状态更新

```kotlin
runCallCatch(hasLoading = true, call = {
    repository.getData()
}, onState = _dataState)
```

## 5. Flow 最佳实践

### 5.1 流类型选择
- 无粘性流：`createNoStickyFlow()`
- 粘性流：`createStickyFlow()`
- 状态流：`createStatusStateFlow()`
- 共享流：`createSharedStatusFlow()`

### 5.2 状态过滤
- `filterSuccess()`：只接收成功状态数据
- `filterError()`：只接收错误状态异常
- `filterLoading()`：只接收加载状态
- `filterUninitialized()`：只接收未初始化状态

### 5.3 状态收集

- 普通的Flow类型的流，直接使用`collect()`函数来收集状态
- 如果是StatusState<*>类型的流，需要使用`collectStatusState()`函数来收集状态
- 在生命周期类Fragment/Activity中使用如下代码包裹collect/collectStatusState函数：
```kotlin
    lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        block()
    }
}
```

## 6. 性能与优化

### 6.1 避免不必要的状态更新
- 仅当数据确实变化时更新状态
- 频繁更新的数据考虑防抖或节流

### 6.2 合理使用协程
- 避免单个协程执行过多耗时操作
- 并发任务使用 `async/await` 模式

### 6.3 状态流生命周期管理
- 使用 `launchRepeatOnStarted()` 确保在 `STARTED` 状态下收集流

## 7. 核心原则

1. **单向数据流**：UI → Action → ViewModel → State → UI
2. **状态驱动UI**：UI只负责展示状态，不包含业务逻辑
3. **生命周期安全**：确保ViewModel与UI组件生命周期正确绑定
4. **线程安全**：使用协程和适当调度器处理并发问题
5. **可测试性**：业务逻辑与UI分离，便于单元测试