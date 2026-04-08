---
name: room-database
description: 规定 Entity、DAO、Repository、Migration 与 Koin 注册在本项目 imagecho.reframe.database 包下的分层与线程约定。在开发或修改 Room 数据库相关代码时使用。
---

# Room 数据库开发指南

遵循 Entity -> DAO -> Repository 的分层架构。

## 1. 命名与目录规范

位于 `imagecho.reframe.database` 包下：

- **entity/**: `[ModelName]Entity.kt` (表名 snake_case)
- **dao/**: `[ModelName]Dao.kt`
- **repositories/**: `[ModelName]DatabaseRepository.kt` 或 `[ModelName]Repository.kt`
- **converters/**: `[TypeName]TypeConverter.kt`

## 2. 核心开发规范

### Entity
- 使用 `@Entity(tableName = "...")` 和 `@ColumnInfo(name = "snake_case")`。
- **工厂方法**: 默认 **不添加**。仅在明确要求时于 `companion object` 中实现 `newEntity`。
- 不主动给复杂类型编写TypeConverter，仅在用户要求的时候编写。

### DAO
- 必须是 `interface` 并使用 `@Dao`。
- 方法必须是 `suspend`。
- 插入使用 `@Insert(onConflict = OnConflictStrategy.REPLACE)`。

### Repository (核心)
- **线程控制**: 必须使用 `withIOThread` (来自 `com.au.module_android.utils`) 包裹所有操作。
- **异常处理**: 捕获异常并使用 `loge` 或 `logdNoFile` 记录，不抛出崩溃。
- **职责**: 负责 `Entity` 与 业务/网络 `Model` 之间的相互转换。
- **返回值**: 通常返回 `Boolean` (成功/失败) 或数据对象 (失败返回 null/empty)。
- **必要性**： 如果实现的类仅是对Dao的同名方法封装，那么不创建Repository类，必须在有数据转换的情况下才创建Repository类。

### 性能优化：Projection (投影查询) 与 Lite 对象

**原则**: 当 Entity 含大字段（大文本/Blob）时，**禁止** `SELECT *`，这会导致 `CursorWindow` 溢出（2MB）和严重内存抖动。

- **Lite 对象**: 创建不包含大字段的轻量级 Bean（如 `XxxLite`），仅保留 UI 必需字段。
- **DAO 优化**: 使用投影查询 `SELECT id, value1, value2 FROM table` 返回 `List<XxxLite>`。
- **场景**: 列表展示、扫描查重（仅查 `SELECT value1`）等无需完整数据的场景。

```kotlin
class XxxDatabaseRepository(private val dao: XxxDao) {
    suspend fun saveXxxs(models: List<MyModel>): Boolean = withIOThread {
        try {
            val entities = models.map { ... } // Model -> Entity
            dao.insertXxxs(entities)
            true
        } catch (e: Exception) {
            loge { "❌ Save failed: $e" }
            false
        }
    }
}
```

### 其他
- **Converters**: 处理非基础类型，需在 `AppDataBase` 注册。
- **AppDataBase**: 注册 Entity 和 Migration。

## 3. 依赖注入 (Koin)

参考 [koin-di](../koin-di/SKILL.md) 规则。
数据库相关组件需在 `Application` 模块中声明。

### 声明方式
- **DAO**: 通过 `AppDataBase` 实例获取。
  ```kotlin
  single { get<AppDataBase>().getXxxDao() }
  ```
- **Repository**: 使用 `singleOf` 注入。
  ```kotlin
  singleOf(::XxxDatabaseRepository)
  ```
