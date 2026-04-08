---
name: viewmodel-flow-framework
description: 规定 ViewModel、Flow、StatusState 与 AbsActionDispatcherViewModel 在本仓库 Module-AndroidCommon simpleflow 包下的用法。在编写 ViewModel、Flow 状态管理或页面与 ViewModel 协作时使用。
---

# ViewModel 开发框架
代码位置：`Module-AndroidCommon/src/main/java/com/au/module_android/simpleflow`

**核心原则**：状态驱动UI、生命周期安全；是否引入 Action/Reducer 取决于业务复杂度，不强制所有 ViewModel 都走 `UI → Action → ViewModel → State → UI`。

## 1. 核心架构
- 构造函数注入依赖（Repository等）。
- **默认先判断复杂度**：
  - action 很少、只有 1~3 个明确入口、函数调用已经足够清晰时：继承普通 `ViewModel()`，由 `Fragment/Activity` 直接调用公开函数触发逻辑。
  - 页面状态复杂、入口多、需要统一收口、跨事件复用 reducer 逻辑时：再继承 `AbsActionDispatcherViewModel`。
- 不要为了“形式统一”给简单页面硬套 `Action + reduce`。

## 2. Action 设计
- **仅在使用 `AbsActionDispatcherViewModel` 时才需要本节内容。**
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

### 4.3 异步任务try-catch写法
```kotlin
//1. 一般情况不写loading
//xxStateFlow.setLoading()

try {
    //2. request data
    //...
    val result = ...
    xxStateFlow.setSuccess(result)
}
catch (e: Exception) {
    //3. error赋值
    xxStateFlow.setError(e)
}
```

## 5 Activity/Fragment使用ViewModel

### 5.1 依赖注入
- **标准注入**：使用 `by viewModel()` (Koin扩展)。
- **跨页面共享**：使用 `by viewModel(ownerProducer = { requireActivity() })`。
- **禁止**：直接 `new ViewModel()`。

### 5.2 触发业务逻辑
- 简单场景：直接调用 `viewModel.xxx()`。
- 复杂场景：调用 `viewModel.dispatch(Action)` 触发业务逻辑。

```kotlin
// 简单场景
viewModel.refresh(currentFrame, forceRefresh = true)

// 复杂场景
viewModel.dispatch(RefreshAction(currentFrame))
```

### 5.3 状态收集 (Collect)
- **StatusState Flow** ：推荐使用 `collectStatusState`，并包裹在 `repeatOnLifecycle` 中。
- **普通 Flow**：使用标准 `collect`。
- 如果是页面相关的业务 Flow 监听，优先放在 `Fragment/Activity` 中，使用 `lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { ... } }` 收集，然后再调用 `ViewModel` 的公开函数或 `dispatch(Action)` 执行，避免把界面生命周期监听直接写进 `ViewModel`。

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
