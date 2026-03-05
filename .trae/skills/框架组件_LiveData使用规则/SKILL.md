---
name: Flow或LiveData使用规则
description: 当你想使用LiveData时，需遵守它。
---

# LiveData
代码目录：Module-AndroidCommon/src/main/java/com/au/module_android/simplelivedata

优先使用NoStickLiveData类
setValueSafe函数更新value，通过扩展函数realValue()获取值
asReadOnly转换NoStickLiveData为只允许注册，不允许setValue的类
asReadOnlyMustNoStick转换NoStickLiveData为只允许注册，不允许setValue的类，且只能通过observeUnStick/observeForeverUnStick来注册
没有我的要求不得主动使用StatusLiveData带状态的LiveData