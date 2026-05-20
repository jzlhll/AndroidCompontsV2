---
name: fragment-bottom-sheet-dialog
description: 规定 FragmentBottomSheetDialog 展示 BindingFragment 子页、参数与关闭方式。在通过 BottomSheet 展示 Fragment 内容页时使用。
---

# FragmentBottomSheetDialog 使用规则

代码路径：`Module-AndroidUi/src/main/java/com/au/module_androidui/dialogs/FragmentBottomSheetDialog.kt`

## 使用方式

子页面必须是 `Fragment`，推荐直接继承 `BindingFragment<XXXBinding>`。

在子 `Fragment` 中提供统一入口：

```kotlin
companion object {
    fun pop(f: Fragment) {
        FragmentBottomSheetDialog.show<YourFragment>(f.childFragmentManager)
    }
}
```

## show 参数说明

- `manager`：通常传 `f.childFragmentManager`
- `fgBundle`：需要给子 Fragment 传参时使用
- `height`：指定弹窗高度；不传时走最大可用高度
- `paddingMode`：改为带左右 padding 的容器样式
- `hasEditText`：内容里有输入框时传 `true`
- `canCancel`：是否允许点击外部关闭
- `showHeadLine`：是否显示顶部小横线

## 页面内容要求

- 不要在子布局里重复做顶部圆角
- 不要在子布局里重复做顶部小横线
- 子布局只实现内容区
- 关闭弹窗时使用 `findDialog(this)?.dismissAllowingStateLoss()`

## 适用场景

- 底部选择面板
- 底部设置面板
- 轻量级表单或确认页
