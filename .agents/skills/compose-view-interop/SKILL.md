---
name: compose-view-interop
description: 在 Compose 页面中接入旧 View、XML 或 ViewBinding，使用 AndroidView 或 AndroidViewBinding 做兼容接入。用于复用旧控件、把传统 View 嵌入 Compose、或渐进迁移 XML 页面时。
---

# Compose 兼容旧 View/XML

## 什么时候用
- 单个原生控件或已有自定义 `View`，使用 `AndroidView`。
- XML 布局与 ViewBinding，优先 `AndroidViewBinding`。
- 只有必须复用旧 UI 时才接入；不要把整页 XML 当默认方案继续开发。

## 直接按这套做
1. 先确认这块 UI 是否必须复用旧 `View/XML`。
2. 单控件用 `AndroidView`，XML 用 `AndroidViewBinding`。
3. Compose 状态从参数传入，在 `update` 或 binding 块里同步到旧 View。
4. 监听器、播放器、地图、WebView 这类重对象补释放逻辑。
5. 互操作逻辑包在独立 Compose 组件里，不把 XML 细节散落到业务页面。

## 规则
- `factory` 创建，`update` 同步状态。
- 不要在外层 `remember` 一个 `View` 再交给 `AndroidView`。
- 列表中使用传统 `View` 时，优先考虑复用与释放回调。
- 状态从 Compose 传入，不额外维护第二份状态源。

## 示例
```kotlin
@Composable
fun LegacyViewScreen(
    title: String,
    onActionClick: () -> Unit,
) {
    Column {
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    textSize = 18f
                }
            },
            update = { view ->
                view.text = title
            },
        )

        AndroidViewBinding(LegacyPanelBinding::inflate) {
            titleView.text = title
            actionButton.setOnClickListener { onActionClick() }
        }
    }
}
```

## 不要这样做
- 不要把整页业务继续写成 XML 再嵌回 Compose。
- 不要同时维护 Compose 和旧 View 两份状态源。
- 不要忽略旧 View 的监听器、生命周期和资源释放。
