---
name: LiveData使用规则
description: 使用 NoStickLiveData 时的写入、只读暴露与订阅方式约定。
---

# LiveData

代码目录：`Module-AndroidCommon/src/main/java/com/au/module_android/simplelivedata`

业务侧优先 **`NoStickLiveData`**，不要用 `MutableLiveData` 顶替（除非该处规范另有说明）。

## 写入

- 更新值统一用 **`setValueSafe`**；**任意线程**均可调用，无需自行切主线程。
- 不要用已 `@Deprecated` 的 `setValue` / `postValue`，除非明确例外。

## 读取

- 一般用 `value`；需要与「最后一次写入」对齐时用 **`realValue`**，或对 `LiveData` 用扩展 **`realValue()`**。

## 对外暴露

- 内部持有可写的 `NoStickLiveData`，对外返回 **`asReadOnly()`**。
- **`asReadOnlyMustNoStick()`** 仅在明确要求调用方**只能**走无粘性订阅时使用；一般不必。

## 订阅

- **默认**：使用标准 **`observe(owner, observer)`** / **`observeForever(observer)`**，解除用 **`removeObserver(observer)`**（有 `LifecycleOwner` 时 `observe` 仍随生命周期自动移除）。**一般情况下无需**使用 `observeUnStick`、`observeForeverUnStick`。
- **例外**：仅当业务需要「注册后不立刻收到已存在的历史值 / 一次性事件分发」等时，再使用 **`observeUnStick`** / **`observeForeverUnStick`**（及对应的 **`asReadOnlyMustNoStick`** 暴露）。
- **`observeForever`**：注意**重复注册会重复回调**，`init` 等入口要防多次注册；取消须对**同一** `Observer` 实例 **`removeObserver`**。
- Kotlin 可传 **`Observer { }`**，避免 SAM 歧义。

## 禁止

- 未要求时**不要**用 **`StatusLiveData`**。
