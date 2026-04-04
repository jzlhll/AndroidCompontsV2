---
name: 图片选择和拍照弹窗
description: 当需要系统相册查询或选择/拍照功能时，需了解它。
---

# 图片加载和处理框架

本技能提供相册查询和选择/拍照功能。

## 文档结构

本框架分为 4 个子文档，与本文档目录相同：


## 快速开始

```kotlin
// 3. 查询相册
val albums = MediaQueryManager(context).queryAllAlbums()

// 4. 选择图片
fragment.multiPickForResult(maxNum).launchByAll(PickerType.IMAGE, null) { uris ->
    // 处理选择的图片
}
```

## 模块架构

```
Module-ImageCompressed
└── 工具类          # 选择器、拍照弹窗
```
## 详情示例

如果快速开始不够用 请阅读[usage.md](usage.md)
