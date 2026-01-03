# Module-AuKson
<img src="project_logo.png" alt="logo" width="300"/>

基于 kotlinx.serialization 的 Android JSON 序列化工具模块。

## 功能特性

- **JSON 序列化/反序列化**: 提供便捷的扩展函数，支持对象、List、Map 等类型的 JSON 转换
- **Android 类型支持**: 内置 Color、Uri、Date 等 Android 特定类型的序列化器
- **DataStore 缓存**: AppDataStoreKsonCache 支持将对象序列化为 JSON 字符串存储到 DataStore

## 核心组件

- `KsonUtil`: JSON 序列化工具类

  1. 提供 `toKsonString()`、`fromKson()` 泛型扩展函数

  2. 专攻`List<Any>`, `Map<String, Any?>`的toString版本, `toKsonStringLimited()`， 其中Any是基础类型。适用于给后端传参的时候把HashMap转成String。

- `AppDataStoreKsonCache`: 基于 DataStore 的 JSON 缓存实现

  这里是通过`compileOnly(project(":Module-AndroidCommon"))` 引入。如果你需要使用的时候，自行implementation进来。

- 自定义解析器

  - `ColorSerializer`: Color 类型序列化器
  - `UriSerializer`: Uri 类型序列化器
  - `DateSerializer`: Date 类型序列化器
