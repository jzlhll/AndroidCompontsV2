---
name: Flow或LiveData使用规则
description: 当你想使用Flow时，需遵守它。
---

# Flow
代码目录：Module-AndroidCommon/src/main/java/com/au/module_android/simpleflow

eg:
```kotlin
private val _dataState = createStatusStateFlow<List<Item>>()
val dataState = _dataState.asStateFlow()
```

## Flow 最佳实践

### 流类型选择

- createNoStickyFlow() 创建一个不粘滞的Flow，即监听后不会立即触发，只有当数据改变时才会触发
- createStickyFlow() 创建一个粘滞的Flow，即监听后会将最新的数据立即触发
- createStatusStateFlow(initialValue?) 创建MutableStateFlow<StatusState<T>>，常用于ViewModel中申明可监听对象
- createSharedStatusFlow(replay=0) 创建MutableSharedFlow<StatusState<T>>

### 状态过滤
- `filterSuccess()`：只接收成功状态数据
- `filterError()`：只接收错误状态异常
- `filterLoading()`：只接收加载状态
- `filterUninitialized()`：只接收未初始化状态

### 状态收集

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