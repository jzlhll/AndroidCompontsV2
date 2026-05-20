---
name: input-method
description: 规定 ImeUtils 与 ImeHelper 在软键盘显示/隐藏及高度监听中的用法。在处理键盘弹出收起或随键盘位移动画时使用。
---

# 1. ImeUtils
提供了一些基础的键盘控制和状态查询功能。

- `hideImeNew(window: Window, view: View)`: 隐藏软键盘。需要传入当前 Activity 或 Dialog 的 Window 对象和当前获取焦点的 View。
- `showImeNew(window: Window, view: EditText)`: 显示软键盘并让 EditText 获取焦点。注意：在界面刚显示时调用可能无效，建议延迟调用或在用户交互（如点击事件）后调用。
- `showImeNewOnCreate(window: Window, et: EditText)`: 在界面创建时直接显示软键盘（设置 `SOFT_INPUT_STATE_VISIBLE` 并请求焦点）。
- `View.imeVisible()`: 扩展属性，判断当前软键盘是否可见。

# 2. ImeHelper
用于监听键盘高度的连续变化，特别适用于修复全屏状态下 `adjustResize` 不生效的问题，或者需要实现 UI 随键盘平滑移动的动画效果。

## 使用方法

1. **初始化**: 在 Activity 或 Fragment 中，使用 `ImeHelper.assist(activity)` 获取实例。
   - *注意*: 该方法会自动将 Window 的 `softInputMode` 设置为 `SOFT_INPUT_ADJUST_NOTHING`，并作为 LifecycleObserver 绑定到 Activity 的生命周期中，自动处理解绑。
2. **设置监听**: 调用 `setOnImeListener` 监听键盘高度变化。
   ```kotlin
   val imeHelper = ImeHelper.assist(requireActivity())
   imeHelper.setOnImeListener { imeOffset, imeMaxHeight, statusBarHeight, navigationBarHeight ->
       // imeOffset: 当前键盘弹起的高度（动画过程中不断变化）
       // imeMaxHeight: 键盘完全弹起时的最大高度
       
       // 推荐的布局位移代码：
       // moveView.translationY = min(0f, -imeOffset.toFloat() + navigationBarHeight)
   }
   ```
3. **计算位移**: 如果需要更精确地将某个 View 刚好顶在键盘上方，可以使用内置的 `calculate` 方法：
   ```kotlin
   // rootHeight: 根布局总高度
   // moveView: 需要移动的 View
   // offset: 额外的间距，默认 10.dp
   val transY = imeHelper.calculate(rootHeight, moveView, imeCurrentHeight, imeMaxHeight, navigationBarHeight)
   if (transY != null) {
       moveView.translationY = transY.toFloat()
   }
   ```
