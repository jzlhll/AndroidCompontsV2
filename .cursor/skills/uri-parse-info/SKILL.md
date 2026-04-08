---
name: uri-parse-info
description: 规定 UriParsedInfo、myParse 扩展与 UriUtil 在本项目中的解析与缓存复制用法。在解析 Uri 元数据或做 Uri 存在性/大小查询时使用。
---

# Uri 解析与操作

## 核心功能

解析 Uri 提取元数据（`UriParsedInfo`），并提供 Uri 存在性检查、大小获取、缓存复制等工具方法。

## 1. 解析结果 (UriParsedInfo)

```kotlin
data class UriParsedInfo(
    val uri: Uri,
    val name: String,          // 文件名
    val fileLength: Long,      // 大小（字节）
    val extension: String,     // 扩展名
    val mimeType: String = "", // MIME 类型
    val fullPath: String? = null,     // 完整路径（File/Content _data）
    val relativePath: String? = null, // 相对路径
    val videoDuration: Long? = null,  // 视频时长（毫秒）
    val isFile: Boolean = false,      // 是否为 File 类型
    val lastModified: Long? = null    // 最后修改时间（毫秒）
) {
    fun isUriVideo(): Boolean
    fun isUriImage(): Boolean
    fun file(): File? // 仅当路径有效时返回 File
}
```

## 2. 解析方法 (Uri/File 扩展)

推荐使用扩展函数解析：

```kotlin
// 1. 同步解析
val info = uri.myParse() // 使用 App Context
val info = uri.myParse(contentResolver)
val info = file.myParse()

// 2. 协程解析（推荐用于大文件/网络 Uri）
val info = uri.myParseSuspend()
```

## 3. 实用工具 (UriUtil)

```kotlin
// 获取大小 (支持 file/content/resource)
val size = uri.length(contentResolver)

// 检查存在性 (1:存在, 0:不存在, -1:无权限)
val status = uri.isUriExists(context)

// 复制到缓存 (自动命名或指定名称)
val cacheFile = uri.copyToCache() 

// 来源检查 (ContentProvider 包名或私有目录路径)
val isMine = uri.isFromMyApp(context)

// 图片压缩检查 (本地非 http 图片)
val canCompress = isPicCanCompress(path)
```

## 4. 支持路径与注意事项

- **支持类型**: `file://`, `content://`, `android.resource://`, 以及映射路径（`/files_path/`, `/cache_path/` 等）。
- **权限**: 解析 `content://` 需相应读取权限。
- **性能**: 涉及 IO，建议使用 `myParseSuspend` 或后台线程。
