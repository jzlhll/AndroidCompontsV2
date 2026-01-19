---
name: Flow ViewModel LiveData使用规则
description: 当你想使用Flow/ViewModel/LiveData时，需遵守它。
---

# LiveData
代码目录：Module-AndroidCommon/src/main/java/com/au/module_android/simplelivedata

优先使用NoStickLiveData类
setValueSafe函数更新value，通过扩展函数realValue()获取值
asReadOnly转换NoStickLiveData为只允许注册，不允许setValue的类
asReadOnlyMustNoStick转换NoStickLiveData为只允许注册，不允许setValue的类，且只能通过observeUnStick/observeForeverUnStick来注册
没有我的要求不得主动使用StatusLiveData带状态的LiveData

# Flow
代码目录：Module-AndroidCommon/src/main/java/com/au/module_android/simpleflow

createNoStickyFlow() 创建一个不粘滞的Flow，用来替代NoStickLiveData
createStickyFlow() 创建一个粘滞的Flow，用来替代LiveData
createStatusStateFlow(initialValue?) 创建MutableStateFlow<StatusState<T>>，常用于ViewModel中申明可监听对象
createSharedStatusFlow(replay=0) 创建MutableSharedFlow<StatusState<T>>

# ViewModel
代码目录：Module-AndroidCommon/src/main/java/com/au/module_android/simpleflow

## 继承 AbsActionDispatcherViewModel
- 定义Action：`object/class MyAction : IStateAction`
- 注册Action：在`init`中调用 `getActionStore().reduce(MyAction::class.java) { ... }`
- 触发Action：UI层调用 `viewModel.dispatch(MyAction)`

## StatusState 状态管理
- 包含 Uninitialized/Loading/Success/Error 四种状态
- filterSuccess/filterError/... 过滤特定状态的数据流
- collectStatusState(onSuccess, onError...) 在Fragment等地方一次性处理所有状态
- asStatusState 将普通Flow转换为StatusState流
- 使用扩展函数 `setSuccess`/`setError`/`setLoading` 更新 `MutableStateFlow<StatusState<T>>`
- 使用 `runCallCatch` 包裹挂起函数调用，自动处理异常和状态更新：
  ```kotlin
  runCallCatch(hasLoading=true, call={...}, onState=myStateFlow)
  ```

# 如果需要跨多Activity的ViewModel
参考 `ShareViewModelManager.kt`
