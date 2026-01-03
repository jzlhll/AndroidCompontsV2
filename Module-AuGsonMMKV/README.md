# Module-AuGsonMMKV
<img src="project_logo.png" alt="logo" width="300"/>

基于 Gson 和 MMKV 的数据存储模块，提供 JSON 序列化和本地数据存储能力。

## 功能特性

- **JSON 序列化工具**：提供对象与 JSON 字符串互转的扩展函数
- **MMKV 存储工具**：支持基础类型、集合、Map、Parcelable 对象的存储
- **DataStore 缓存**：通过 JSON 字符串实现 DataStore 的对象缓存
- **类型适配器**：内置 Color、Date、Uri 等 Android 常用类型的 Gson 适配器

## 核心组件

### JsonUtils.kt
- `toJsonString()` - 对象转 JSON 字符串
- `fromJson()` - JSON 字符串转对象
- `fromJsonList()` - JSON 字符串转 List
- `formatJsonBeautiful()` - 格式化 JSON 输出

### MmkvUtil.kt
- `mmkvSetAny()` / `mmkvGet()` - 任意类型存储
- `mmkvSetArrayList()` / `mmkvGetArrayList()` - List 存储
- `mmkvSetMap()` / `mmkvGetMap()` - Map 存储
- `mmkvForceSync()` - 强制同步

### AppDataStoreJsonCache.kt
- 基于 AppDataStoreStringCache 的 JSON 对象缓存实现
  注意：使用的是CompileOnly引入的Module-AndroidCommon，所以在使用的时候，需要自己implementation进来。

### 类型适配器
- `JsonColorAdapter` - Color 类型序列化
- `JsonDateAdapter` - Date 类型序列化
- `JsonUriAdapter` - Uri 类型序列化
