---
name: Bitmap处理
description: 当需要把Uri/File进行压缩、加载成Bitmap的时，需了解它。
---

# 图片加载和处理框架

本技能提供 Module-ImageCompressed 模块的渐进式文档，涵盖图片压缩、加载、相册查询和选择/拍照功能。

## 文档结构

本框架分为 3个子文档，与本文档目录相同：

1. **[compressor](1-图片压缩.md)** - 图片压缩核心功能
2. **[loader](2-图片加载.md)** - 原图/压缩图/缩略图加载
3. **[query](3-相册查询.md)** - 系统相册媒体查询

## 快速开始

```kotlin
// 1. 压缩图片
val compressedFile = useCompress(context, uri, Config())

// 2. 加载图片
val bitmap = loadCompressUriOrFile(context, uri)
val thumbnailBitmap = loadThumbnailUriOrFile(context, uri, thumbnailSize) //SYS_MIN_SIZE, SYS_FULL_SIZE

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
├── compressor/    # 图片压缩
├── loader/        # 图片加载
├── query/         # 相册查询
```
