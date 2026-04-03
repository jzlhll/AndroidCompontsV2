---
name: Glide加载图片到ImageView
description: Glide图片加载工具方法集合。调用场景：使用Glide加载图片、设置圆角/圆形、加载视频帧、ContentUri加载时使用。
---

# Glide 使用指南

## 基础加载
```kotlin
imageView.glideSetAny(url)                                    // 通用加载
imageView.glideSetAnyWithDefault(url, colorGray)              // 带颜色占位图
imageView.glideSetAnyWithResDefault(url, R.drawable.placeholder) // 带资源占位图
imageView.glideLoadFile(file, R.drawable.error)               // 本地文件
```

## 圆角与圆形
```kotlin
imageView.glideSetAnyAsCircleCrop(url)           // 圆形
imageView.glideSetAnyAsRoundedCorners(url, 16)   // 圆角(像素)
```

## 视频帧加载
```kotlin
imageView.glideLoadVideoFirstFrame(url) { drawable, width, height -> }
```

## ContentUri 加载
```kotlin
imageView.glideLoadContentUri(uri)               // 简单加载
imageView.glideLoadContentUri(uri, signature)    // 带签名(推荐)
imageView.glideLoadContentUri(uri, 200, 200)     // 协程+指定尺寸

// 获取签名
val (mimeType, dateModified, orientation) = getMediaStoreMeta(context, uri)
val signature = MediaStoreSignature(mimeType, dateModified, orientation)
```

## 缓存控制
```kotlin
clearGlideImageDiskCache()      // 清除磁盘缓存
clearAppFileDir()               // 清除文件目录
imageView.clearByGlide()        // 取消加载
```

## URL 处理
```kotlin
val sizedUrl = resizeImgUrl(url, "200x200")  // 替换后缀裁剪
```

## 签名策略
- `KeyedGlideUrl` - 自定义缓存Key
- `LimitTimeGlideUrl` - 限定缓存时间(5-13天)
- `MediaStoreSignature` - 媒体库元数据签名

## 注意
- ContentUri加载推荐带签名，确保媒体库更新时缓存刷新
- 视频帧使用 `MediaMetadataRetriever.OPTION_CLOSEST`
