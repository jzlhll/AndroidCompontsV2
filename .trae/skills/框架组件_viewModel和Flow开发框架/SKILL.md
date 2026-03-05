---
name: viewModel开发框架
description: 当涉及到编写ViewModel和Flow的代码时候遵守该技能。
---

# ViewModel 开发框架
代码位置：`Module-AndroidCommon/src/main/java/com/au/module_android/simpleflow`

**核心原则**：单向数据流UI → Action → ViewModel → State → UI、状态驱动UI、生命周期安全。

## 1. 核心架构
- 继承 `AbsActionDispatcherViewModel`，提供Action分发机制。
- 构造函数注入依赖（Repository等）。

## 2. Action 设计
- 定义Action：
    实现 `IStateAction`，无参用 `object`，有参用 `data class`，放在 `init{}` 前。

- 注册Reducer：
   在 `init` 中注册：`getActionStore().reduce(XxxAction::class.java) { ... }`。

## 3. Flow状态管理

### 3.1 Flow 创建
- `createStatusStateFlow()`: 创建 `MutableStateFlow<StatusState<T>>` (带状态的数据流)。
- `createSharedStatusFlow()`: 创建 `MutableSharedFlow<StatusState<T>>` (带状态的事件流，如Toast)。
- `createStickyFlow()`: 创建粘性普通Flow (新订阅者会立即收到最新值)。
- `createNoStickyFlow()`: 创建非粘性普通Flow (仅新订阅后的数据更新会触发)。

### 3.2 状态更新与使用
- **状态类型**：`Uninitialized`, `Loading`, `Success<T>`, `Error`。
- **更新方法**：`setLoading()`, `setSuccess(data)`, `setError(e)`。

```kotlin
// 声明
private val _dataState = createStatusStateFlow<List<Item>>()
val dataState = _dataState.asStateFlow()

// 更新
_dataState.setSuccess(items)
```


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

## 5 Activity/Fragment使用ViewModel

### 5.1 依赖注入
- **标准注入**：使用 `by viewModel()` (Koin扩展)。
- **跨页面共享**：使用 `by viewModel(ownerProducer = { requireActivity() })`。
- **禁止**：直接 `new ViewModel()`。

### 5.2 触发 Action
- 调用 `viewModel.dispatch(Action)` 触发业务逻辑。

### 5.3 状态收集 (Collect)
- **StatusState Flow** ：推荐使用 `collectStatusState`，并包裹在 `repeatOnLifecycle` 中。
- **普通 Flow**：使用标准 `collect`。

```kotlin
// 示例：在Fragment中收集状态
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.dataState.collectStatusState(
            onSuccess = { data -> updateUI(data) },
            onError = { e -> ... },
            onLoading = { showLoading() }
        )
    }
}
```

## 6. 其他
- 性能：避免不必要更新，耗时操作用 `async/await`。
- 过滤：`filterSuccess()`, `filterError()`, `filterLoading()`, `filterUninitialized()`。