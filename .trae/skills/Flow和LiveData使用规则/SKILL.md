---
name: Flow或LiveData使用规则
description: 当你想使用Flow或LiveData时，需遵守它。
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